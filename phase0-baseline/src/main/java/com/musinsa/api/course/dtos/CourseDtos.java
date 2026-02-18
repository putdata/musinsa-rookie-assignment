package com.musinsa.api.course.dtos;

import com.musinsa.domain.course.Course;

public class CourseDtos {

    public record Response(
            Long id,
            String name,
            Integer credits,
            Integer capacity,
            Integer enrolled,
            String schedule,
            String departmentName,
            String professorName
    ) {
        public static Response from(Course course) {
            return new Response(
                    course.getId(),
                    course.getName(),
                    course.getCredits(),
                    course.getCapacity(),
                    course.getEnrolled(),
                    course.getSchedule(),
                    course.getDepartment().getName(),
                    course.getProfessor().getName()
            );
        }
    }
}
