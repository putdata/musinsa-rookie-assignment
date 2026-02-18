package com.musinsa.api.enrollment;

import com.musinsa.api.enrollment.dtos.EnrollmentDtos;
import com.musinsa.domain.enrollment.Enrollment;
import com.musinsa.service.enrollment.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping
    public List<EnrollmentDtos.Response> findByStudentId(@RequestParam Long studentId) {
        return enrollmentService.findByStudentId(studentId).stream()
                .map(EnrollmentDtos.Response::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentDtos.Response enroll(@Valid @RequestBody EnrollmentDtos.Request request) {
        Enrollment enrollment = enrollmentService.enroll(request.studentId(), request.courseId());
        return EnrollmentDtos.Response.from(enrollment);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        enrollmentService.cancel(id);
    }
}
