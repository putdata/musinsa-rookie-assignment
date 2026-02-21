package com.musinsa.api.course;

import com.musinsa.api.course.dtos.CourseDtos;
import com.musinsa.service.course.CourseCounterService;
import com.musinsa.service.course.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final ObjectProvider<CourseCounterService> counterProvider;
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    @GetMapping
    public List<CourseDtos.Response> findAll(
            @RequestParam(required = false) String department) {

        CourseCounterService counterService = counterProvider.getIfAvailable();
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();

        // Redis 없으면 DB 직접 조회 (phase1 동작)
        if (counterService == null || cacheManager == null) {
            return loadFromDb(department);
        }

        // 정적 강좌 정보는 캐시에서, 수강 인원은 Redis 카운터에서
        String cacheKey = (department != null && !department.isBlank()) ? department : "all";
        Cache coursesCache = cacheManager.getCache("courses");

        @SuppressWarnings("unchecked")
        List<CourseDtos.CachedCourse> cached = (coursesCache != null)
                ? coursesCache.get(cacheKey, () -> loadCachedCourses(department))
                : loadCachedCourses(department);

        List<Long> courseIds = cached.stream().map(CourseDtos.CachedCourse::id).toList();
        Map<Long, Integer> enrolledCounts = counterService.getEnrolledCounts(courseIds);

        return cached.stream()
                .map(c -> CourseDtos.Response.of(c, enrolledCounts.getOrDefault(c.id(), 0)))
                .toList();
    }

    private List<CourseDtos.CachedCourse> loadCachedCourses(String department) {
        if (department != null && !department.isBlank()) {
            return courseService.findByDepartmentName(department).stream()
                    .map(CourseDtos.CachedCourse::from)
                    .toList();
        }
        return courseService.findAll().stream()
                .map(CourseDtos.CachedCourse::from)
                .toList();
    }

    private List<CourseDtos.Response> loadFromDb(String department) {
        if (department != null && !department.isBlank()) {
            return courseService.findByDepartmentName(department).stream()
                    .map(CourseDtos.Response::from)
                    .toList();
        }
        return courseService.findAll().stream()
                .map(CourseDtos.Response::from)
                .toList();
    }
}
