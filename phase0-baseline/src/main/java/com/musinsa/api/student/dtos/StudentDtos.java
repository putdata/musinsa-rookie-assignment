package com.musinsa.api.student.dtos;

import com.musinsa.domain.student.Student;

public class StudentDtos {

    public record Response(
            Long id,
            String name,
            String studentNumber,
            String departmentName,
            Integer grade
    ) {
        public static Response from(Student student) {
            return new Response(
                    student.getId(),
                    student.getName(),
                    student.getStudentNumber(),
                    student.getDepartment().getName(),
                    student.getGrade()
            );
        }
    }
}
