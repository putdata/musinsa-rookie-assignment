package com.musinsa.service.course;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
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

    private static final String ENROLLED_KEY_PREFIX = "course:enrolled:";
    private static final String CAPACITY_KEY_PREFIX = "course:capacity:";

    private static final DefaultRedisScript<Long> TRY_ENROLL_SCRIPT;

    static {
        TRY_ENROLL_SCRIPT = new DefaultRedisScript<>();
        TRY_ENROLL_SCRIPT.setScriptText(
                "local enrolled = redis.call('INCR', KEYS[1]) " +
                "local capacity = tonumber(redis.call('GET', KEYS[2])) " +
                "if capacity == nil then return enrolled end " +
                "if enrolled > capacity then " +
                "  redis.call('DECR', KEYS[1]) " +
                "  return -1 " +
                "end " +
                "return enrolled"
        );
        TRY_ENROLL_SCRIPT.setResultType(Long.class);
    }

    public void initialize(Long courseId, int enrolled, int capacity) {
        redisTemplate.opsForValue().set(ENROLLED_KEY_PREFIX + courseId, String.valueOf(enrolled));
        redisTemplate.opsForValue().set(CAPACITY_KEY_PREFIX + courseId, String.valueOf(capacity));
    }

    /**
     * 원자적으로 수강 인원을 증가시키고 정원을 검사한다.
     * @return 증가된 수강 인원. 정원 초과 시 -1 반환 (DECR 자동 롤백됨).
     */
    public long tryEnroll(Long courseId) {
        List<String> keys = List.of(ENROLLED_KEY_PREFIX + courseId, CAPACITY_KEY_PREFIX + courseId);
        Long result = redisTemplate.execute(TRY_ENROLL_SCRIPT, keys);
        return result != null ? result : -1;
    }

    public void cancelEnroll(Long courseId) {
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
