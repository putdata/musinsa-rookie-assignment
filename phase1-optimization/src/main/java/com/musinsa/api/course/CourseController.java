package com.musinsa.api.course;

import com.musinsa.api.course.dtos.CourseDtos;
import com.musinsa.service.course.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public List<CourseDtos.Response> findAll(
            @RequestParam(required = false) String department) {
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
