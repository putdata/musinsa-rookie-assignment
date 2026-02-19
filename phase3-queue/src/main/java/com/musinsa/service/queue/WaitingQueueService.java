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
    private static final String SEQ_KEY = "enrollment:queue:seq";
    private static final String PROCESSED_KEY = "enrollment:queue:processed";
    private static final int BATCH_SIZE = 50;
    private static final int WORKER_THREADS = 20;
    private static final Duration RESULT_TTL = Duration.ofMinutes(10);

    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT;

    static {
        ENQUEUE_SCRIPT = new DefaultRedisScript<>();
        ENQUEUE_SCRIPT.setScriptText(
                "local seq = redis.call('INCR', KEYS[3]) " +
                "redis.call('HSET', KEYS[1], 'studentId', ARGV[1], 'courseId', ARGV[2], 'seq', seq) " +
                "redis.call('EXPIRE', KEYS[1], 1800) " +
                "redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4]) " +
                "return seq"
        );
        ENQUEUE_SCRIPT.setResultType(Long.class);
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

        List<String> keys = List.of(REQUEST_PREFIX + token, QUEUE_KEY, SEQ_KEY);
        List<String> args = List.of(
                studentId.toString(),
                courseId.toString(),
                String.valueOf(score),
                token
        );

        Long seq = redisTemplate.execute(ENQUEUE_SCRIPT, keys, args.toArray(new String[0]));

        long position = 1;
        if (seq != null) {
            String processedStr = redisTemplate.opsForValue().get(PROCESSED_KEY);
            long processed = processedStr != null ? Long.parseLong(processedStr) : 0;
            position = Math.max(seq - processed, 1);
        }

        return new QueueDtos.EnrollResponse(token, position, position);
    }

    public QueueDtos.ResultResponse getResult(String token) {
        // 1. 결과가 있으면 반환
        String status = (String) redisTemplate.opsForHash().get(RESULT_PREFIX + token, "status");
        if (status != null) {
            String message = (String) redisTemplate.opsForHash().get(RESULT_PREFIX + token, "message");
            return new QueueDtos.ResultResponse(token, status, message);
        }

        // 2. request:{token} 존재 → 대기 중 또는 처리 중 (popMin 후에도 request 해시는 남아있음)
        String seqStr = (String) redisTemplate.opsForHash().get(REQUEST_PREFIX + token, "seq");
        if (seqStr != null) {
            String processedStr = redisTemplate.opsForValue().get(PROCESSED_KEY);
            long seq = Long.parseLong(seqStr);
            long processed = processedStr != null ? Long.parseLong(processedStr) : 0;
            long approxPosition = Math.max(seq - processed, 1);
            return new QueueDtos.ResultResponse(token, "WAITING", "대기 순번: " + approxPosition);
        }

        // 3. request 만료 시 ZSET fallback (request TTL 30분 < 큐 체류 시간인 경우)
        Double score = redisTemplate.opsForZSet().score(QUEUE_KEY, token);
        if (score != null) {
            return new QueueDtos.ResultResponse(token, "WAITING", "대기 중입니다");
        }

        // 4. 어디에도 없으면 NOT_FOUND
        return new QueueDtos.ResultResponse(token, "NOT_FOUND", "요청을 찾을 수 없습니다");
    }

    @Scheduled(fixedDelay = 100)
    public void processQueue() {
        Set<ZSetOperations.TypedTuple<String>> items =
                redisTemplate.opsForZSet().popMin(QUEUE_KEY, BATCH_SIZE);

        if (items == null || items.isEmpty()) return;

        // 처리 카운터 증가 (근사 순번 계산용)
        redisTemplate.opsForValue().increment(PROCESSED_KEY, items.size());

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
