package com.musinsa.service.course;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("redis")
@RequiredArgsConstructor
public class CourseCounterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    private static final String ENROLLED_KEY_PREFIX = "course:enrolled:";
    private static final String CAPACITY_KEY_PREFIX = "course:capacity:";

    public void flushAll() {
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    public void initialize(Long courseId, int enrolled, int capacity) {
        redisTemplate.opsForValue().set(ENROLLED_KEY_PREFIX + courseId, String.valueOf(enrolled));
        redisTemplate.opsForValue().set(CAPACITY_KEY_PREFIX + courseId, String.valueOf(capacity));
    }

    /**
     * 정원 초과 여부를 개별 Redis GET 2회로 확인한다 (Lua Script 미사용).
     */
    public boolean isFull(Long courseId) {
        String enrolledStr = redisTemplate.opsForValue().get(ENROLLED_KEY_PREFIX + courseId);
        String capacityStr = redisTemplate.opsForValue().get(CAPACITY_KEY_PREFIX + courseId);

        int enrolled = (enrolledStr != null) ? Integer.parseInt(enrolledStr) : 0;
        int capacity = (capacityStr != null) ? Integer.parseInt(capacityStr) : 0;

        return capacity > 0 && enrolled >= capacity;
    }

    public void incrementEnrolled(Long courseId) {
        redisTemplate.opsForValue().increment(ENROLLED_KEY_PREFIX + courseId);
    }

    public void decrementEnrolled(Long courseId) {
        redisTemplate.opsForValue().decrement(ENROLLED_KEY_PREFIX + courseId);
    }

    public int getEnrolledCount(Long courseId) {
        String val = redisTemplate.opsForValue().get(ENROLLED_KEY_PREFIX + courseId);
        return val != null ? Integer.parseInt(val) : 0;
    }

    public Map<Long, Integer> getEnrolledCounts(List<Long> courseIds) {
        List<String> keys = courseIds.stream()
                .map(id -> ENROLLED_KEY_PREFIX + id)
                .toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < courseIds.size(); i++) {
            String val = (values != null) ? values.get(i) : null;
            result.put(courseIds.get(i), val != null ? Integer.parseInt(val) : 0);
        }
        return result;
    }
}
