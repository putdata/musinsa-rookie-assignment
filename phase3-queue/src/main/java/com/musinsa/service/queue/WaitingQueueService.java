package com.musinsa.service.queue;

import com.musinsa.api.queue.dtos.QueueDtos;
import com.musinsa.service.enrollment.EnrollmentService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Profile("redis")
public class WaitingQueueService {

    private static final String QUEUE_KEY = "enrollment:queue";
    private static final String REQUEST_PREFIX = "enrollment:request:";
    private static final String RESULT_PREFIX = "enrollment:result:";
    private static final int BATCH_SIZE = 50;
    private static final int WORKER_THREADS = 20;
    private static final Duration RESULT_TTL = Duration.ofMinutes(10);

    private static final DefaultRedisScript<List> ENQUEUE_SCRIPT;

    static {
        ENQUEUE_SCRIPT = new DefaultRedisScript<>();
        ENQUEUE_SCRIPT.setScriptText(
                "redis.call('HSET', KEYS[1], 'studentId', ARGV[1], 'courseId', ARGV[2]) " +
                "redis.call('EXPIRE', KEYS[1], 1800) " +
                "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4]) " +
                "local rank = redis.call('ZRANK', KEYS[2], ARGV[4]) " +
                "local total = redis.call('ZCARD', KEYS[2]) " +
                "return {rank, total}"
        );
        ENQUEUE_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final EnrollmentService enrollmentService;
    private final ExecutorService workerPool;

    public WaitingQueueService(StringRedisTemplate redisTemplate, EnrollmentService enrollmentService) {
        this.redisTemplate = redisTemplate;
        this.enrollmentService = enrollmentService;
        this.workerPool = Executors.newFixedThreadPool(WORKER_THREADS);
    }

    @PreDestroy
    public void shutdown() {
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public QueueDtos.EnrollResponse enqueue(Long studentId, Long courseId) {
        String token = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();

        // Lua Script: 6개 Redis 명령을 1회 왕복으로 처리
        List<String> keys = List.of(REQUEST_PREFIX + token, QUEUE_KEY);
        List<String> args = List.of(
                studentId.toString(),
                courseId.toString(),
                String.valueOf(score),
                token
        );

        List<?> result = redisTemplate.execute(ENQUEUE_SCRIPT, keys, args.toArray(new String[0]));

        long position = -1;
        long total = 0;
        if (result != null && result.size() == 2) {
            position = ((Long) result.get(0)) + 1;
            total = (Long) result.get(1);
        }

        return new QueueDtos.EnrollResponse(token, position, total);
    }

    public QueueDtos.ResultResponse getResult(String token) {
        // 결과가 있으면 반환
        String status = (String) redisTemplate.opsForHash().get(RESULT_PREFIX + token, "status");
        if (status != null) {
            String message = (String) redisTemplate.opsForHash().get(RESULT_PREFIX + token, "message");
            return new QueueDtos.ResultResponse(token, status, message);
        }

        // 아직 대기열에 있는지 확인
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token);
        if (rank != null) {
            return new QueueDtos.ResultResponse(token, "WAITING", "대기 순번: " + (rank + 1));
        }

        return new QueueDtos.ResultResponse(token, "NOT_FOUND", "요청을 찾을 수 없습니다");
    }

    @Scheduled(fixedDelay = 100)
    public void processQueue() {
        Set<ZSetOperations.TypedTuple<String>> items =
                redisTemplate.opsForZSet().popMin(QUEUE_KEY, BATCH_SIZE);

        if (items == null || items.isEmpty()) return;

        List<Future<?>> futures = new ArrayList<>(items.size());
        for (ZSetOperations.TypedTuple<String> item : items) {
            String token = item.getValue();
            if (token == null) continue;

            futures.add(workerPool.submit(() -> processEnrollment(token)));
        }

        // 배치 내 모든 작업 완료 대기 (다음 배치와 순서 보장)
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("배치 처리 중 예외 발생", e);
            }
        }
    }

    private void processEnrollment(String token) {
        String studentIdStr = (String) redisTemplate.opsForHash().get(REQUEST_PREFIX + token, "studentId");
        String courseIdStr = (String) redisTemplate.opsForHash().get(REQUEST_PREFIX + token, "courseId");

        if (studentIdStr == null || courseIdStr == null) {
            saveResult(token, "FAILED", "요청 정보가 만료되었습니다");
            return;
        }

        try {
            Long studentId = Long.parseLong(studentIdStr);
            Long courseId = Long.parseLong(courseIdStr);
            enrollmentService.enroll(studentId, courseId);
            saveResult(token, "SUCCESS", "수강신청이 완료되었습니다");
        } catch (Exception e) {
            saveResult(token, "FAILED", e.getMessage());
        } finally {
            redisTemplate.delete(REQUEST_PREFIX + token);
        }
    }

    private void saveResult(String token, String status, String message) {
        redisTemplate.opsForHash().put(RESULT_PREFIX + token, "status", status);
        redisTemplate.opsForHash().put(RESULT_PREFIX + token, "message", message != null ? message : "");
        redisTemplate.expire(RESULT_PREFIX + token, RESULT_TTL);
    }
}
