package com.musinsa.service.queue;

import com.musinsa.api.queue.dtos.QueueDtos;
import com.musinsa.service.enrollment.EnrollmentService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
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
    private static final Duration REQUEST_TTL = Duration.ofMinutes(30);
    private static final Duration RESULT_TTL = Duration.ofMinutes(10);

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

        // 개별 Redis 명령어로 요청 저장
        redisTemplate.opsForHash().put(REQUEST_PREFIX + token, "studentId", studentId.toString());
        redisTemplate.opsForHash().put(REQUEST_PREFIX + token, "courseId", courseId.toString());
        redisTemplate.expire(REQUEST_PREFIX + token, REQUEST_TTL);

        // 대기열에 추가
        redisTemplate.opsForZSet().add(QUEUE_KEY, token, score);

        // 순번 조회
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token);
        Long total = redisTemplate.opsForZSet().zCard(QUEUE_KEY);

        long position = (rank != null) ? rank + 1 : 1;
        long totalWaiting = (total != null) ? total : 1;

        return new QueueDtos.EnrollResponse(token, position, totalWaiting);
    }

    public QueueDtos.ResultResponse getResult(String token) {
        // 1. 결과 확인
        String status = (String) redisTemplate.opsForHash().get(RESULT_PREFIX + token, "status");
        if (status != null) {
            String message = (String) redisTemplate.opsForHash().get(RESULT_PREFIX + token, "message");
            return new QueueDtos.ResultResponse(token, status, message != null ? message : "");
        }

        // 2. 대기열에 있는지 확인
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token);
        if (rank != null) {
            Long total = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
            return new QueueDtos.ResultResponse(token, "WAITING",
                    "대기 순번: " + (rank + 1) + " / 전체 " + total + "명");
        }

        // 3. 처리 중인지 확인
        if (Boolean.TRUE.equals(redisTemplate.hasKey(REQUEST_PREFIX + token))) {
            return new QueueDtos.ResultResponse(token, "WAITING", "처리 중입니다");
        }

        // 4. 찾을 수 없음
        return new QueueDtos.ResultResponse(token, "NOT_FOUND", "요청을 찾을 수 없습니다");
    }

    @Scheduled(fixedDelay = 100)
    public void processQueue() {
        Set<ZSetOperations.TypedTuple<String>> items =
                redisTemplate.opsForZSet().popMin(QUEUE_KEY, BATCH_SIZE);

        if (items == null || items.isEmpty()) return;

        // Step 2: 20개 스레드 병렬 처리 (Lua Script 미사용)
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
