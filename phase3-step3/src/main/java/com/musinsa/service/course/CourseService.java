package com.musinsa.service.course;

import com.musinsa.domain.course.Course;
import com.musinsa.domain.course.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;

    public List<Course> findAll() {
        return courseRepository.findAllWithDetails();
    }

    public List<Course> findByDepartmentName(String departmentName) {
        return courseRepository.findByDepartmentName(departmentName);
    }
}
