package com.musinsa.api.student;

import com.musinsa.api.student.dtos.StudentDtos;
import com.musinsa.service.student.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public List<StudentDtos.Response> findAll() {
        return studentService.findAll().stream()
                .map(StudentDtos.Response::from)
                .toList();
    }
}
