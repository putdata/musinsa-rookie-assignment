package com.musinsa.api.enrollment.dtos;

import com.musinsa.domain.enrollment.Enrollment;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class EnrollmentDtos {

    public record Request(
            @NotNull Long studentId,
            @NotNull Long courseId
    ) {
    }

    public record Response(
            Long id,
            Long studentId,
            Long courseId,
            String courseName,
            Integer credits,
            String schedule,
            LocalDateTime enrolledAt
    ) {
        public static Response from(Enrollment enrollment) {
            return new Response(
                    enrollment.getId(),
                    enrollment.getStudent().getId(),
                    enrollment.getCourse().getId(),
                    enrollment.getCourse().getName(),
                    enrollment.getCourse().getCredits(),
                    enrollment.getCourse().getSchedule(),
                    enrollment.getEnrolledAt()
            );
        }
    }
}
