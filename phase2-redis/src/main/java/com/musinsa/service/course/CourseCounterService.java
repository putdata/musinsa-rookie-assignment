package com.musinsa.service.course;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private static final DefaultRedisScript<Long> IS_FULL_SCRIPT;

    static {
        IS_FULL_SCRIPT = new DefaultRedisScript<>();
        IS_FULL_SCRIPT.setScriptText(
                "local enrolled = tonumber(redis.call('GET', KEYS[1]) or '0') " +
                "local capacity = tonumber(redis.call('GET', KEYS[2]) or '0') " +
                "if capacity > 0 and enrolled >= capacity then return 1 end " +
                "return 0"
        );
        IS_FULL_SCRIPT.setResultType(Long.class);
    }

    public void flushAll() {
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    public void initialize(Long courseId, int enrolled, int capacity) {
        redisTemplate.opsForValue().set(ENROLLED_KEY_PREFIX + courseId, String.valueOf(enrolled));
        redisTemplate.opsForValue().set(CAPACITY_KEY_PREFIX + courseId, String.valueOf(capacity));
    }

    /**
     * 정원 초과 여부를 읽기 전용으로 확인한다 (카운터 변경 없음).
     */
    public boolean isFull(Long courseId) {
        List<String> keys = List.of(ENROLLED_KEY_PREFIX + courseId, CAPACITY_KEY_PREFIX + courseId);
        Long result = redisTemplate.execute(IS_FULL_SCRIPT, keys);
        return result != null && result == 1;
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
