package com.musinsa.service.queue;

import com.musinsa.api.queue.dtos.QueueDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Profile("redis")
public class WaitingQueueService {

    private static final String QUEUE_KEY = "enrollment:waiting-queue";
    private static final int MAX_CONCURRENT = 50;

    private final StringRedisTemplate redisTemplate;

    public QueueDtos.EnterResponse enter(Long studentId) {
        String token = studentId.toString();
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(QUEUE_KEY, token, score);

        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token);
        Long total = redisTemplate.opsForZSet().size(QUEUE_KEY);
        long position = (rank != null) ? rank + 1 : -1;
        return new QueueDtos.EnterResponse(token, position, total != null ? total : 0);
    }

    public QueueDtos.StatusResponse getStatus(String token) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token);
        if (rank == null) {
            return new QueueDtos.StatusResponse(token, -1, 0, false);
        }

        Long total = redisTemplate.opsForZSet().size(QUEUE_KEY);
        long position = rank + 1;
        boolean allowed = position <= MAX_CONCURRENT;

        if (allowed) {
            redisTemplate.opsForZSet().remove(QUEUE_KEY, token);
        }
        return new QueueDtos.StatusResponse(token, position, total != null ? total : 0, allowed);
    }
}
