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

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ENQUEUE_SCRIPT;
    private static final DefaultRedisScript<String> GET_RESULT_SCRIPT;

    static {
        ENQUEUE_SCRIPT = new DefaultRedisScript<>();
        ENQUEUE_SCRIPT.setScriptText(
                "redis.call('HSET', KEYS[1], 'studentId', ARGV[1], 'courseId', ARGV[2]) " +
                "redis.call('EXPIRE', KEYS[1], 1800) " +
                "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4]) " +
                "local rank = redis.call('ZRANK', KEYS[2], ARGV[4]) " +
                "local total = redis.call('ZCARD', KEYS[2]) " +
                "return {rank + 1, total}"
        );
        ENQUEUE_SCRIPT.setResultType(List.class);

        GET_RESULT_SCRIPT = new DefaultRedisScript<>();
        GET_RESULT_SCRIPT.setScriptText(
                "local status = redis.call('HGET', KEYS[1], 'status') " +
                "if status then " +
                "local message = redis.call('HGET', KEYS[1], 'message') or '' " +
                "return '1:' .. status .. ':' .. message " +
                "end " +
                "local rank = redis.call('ZRANK', KEYS[2], ARGV[1]) " +
                "if rank then " +
                "local total = redis.call('ZCARD', KEYS[2]) " +
                "return '2:' .. tostring(rank + 1) .. ':' .. tostring(total) " +
                "end " +
                "if redis.call('EXISTS', KEYS[3]) == 1 then " +
                "return '3' " +
                "end " +
                "return '4'"
        );
        GET_RESULT_SCRIPT.setResultType(String.class);
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

    @SuppressWarnings("unchecked")
    public QueueDtos.EnrollResponse enqueue(Long studentId, Long courseId) {
        String token = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();

        List<String> keys = List.of(REQUEST_PREFIX + token, QUEUE_KEY);
        List<String> args = List.of(
                studentId.toString(),
                courseId.toString(),
                String.valueOf(score),
                token
        );

        List<Long> result = (List<Long>) redisTemplate.execute(ENQUEUE_SCRIPT, keys, args.toArray(new String[0]));

        long position = 1;
        long totalWaiting = 1;
        if (result != null && result.size() >= 2) {
            position = result.get(0);
            totalWaiting = result.get(1);
        }

        return new QueueDtos.EnrollResponse(token, position, totalWaiting);
    }

    public QueueDtos.ResultResponse getResult(String token) {
        List<String> keys = List.of(RESULT_PREFIX + token, QUEUE_KEY, REQUEST_PREFIX + token);

        String result = redisTemplate.execute(GET_RESULT_SCRIPT, keys, token);
        if (result == null) {
            return new QueueDtos.ResultResponse(token, "NOT_FOUND", "요청을 찾을 수 없습니다");
        }

        String[] parts = result.split(":", 3);
        return switch (parts[0]) {
            case "1" -> new QueueDtos.ResultResponse(token, parts[1], parts.length > 2 ? parts[2] : "");
            case "2" -> new QueueDtos.ResultResponse(token, "WAITING",
                    "대기 순번: " + parts[1] + " / 전체 " + parts[2] + "명");
            case "3" -> new QueueDtos.ResultResponse(token, "WAITING", "처리 중입니다");
            default -> new QueueDtos.ResultResponse(token, "NOT_FOUND", "요청을 찾을 수 없습니다");
        };
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
