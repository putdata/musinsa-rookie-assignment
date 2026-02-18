package com.musinsa.api.course.dtos;

import com.musinsa.domain.course.Course;

import java.io.Serializable;

public class CourseDtos {

    public record CachedCourse(
            Long id,
            String name,
            Integer credits,
            Integer capacity,
            String schedule,
            String departmentName,
            String professorName
    ) implements Serializable {
        public static CachedCourse from(Course course) {
            return new CachedCourse(
                    course.getId(),
                    course.getName(),
                    course.getCredits(),
                    course.getCapacity(),
                    course.getSchedule(),
                    course.getDepartment().getName(),
                    course.getProfessor().getName()
            );
        }
    }

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

        public static Response of(CachedCourse cached, int enrolled) {
            return new Response(
                    cached.id(),
                    cached.name(),
                    cached.credits(),
                    cached.capacity(),
                    enrolled,
                    cached.schedule(),
                    cached.departmentName(),
                    cached.professorName()
            );
        }
    }
}
