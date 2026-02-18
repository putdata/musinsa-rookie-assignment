package com.musinsa.api.professor.dtos;

import com.musinsa.domain.professor.Professor;

public class ProfessorDtos {

    public record Response(
            Long id,
            String name,
            String departmentName
    ) {
        public static Response from(Professor professor) {
            return new Response(
                    professor.getId(),
                    professor.getName(),
                    professor.getDepartment().getName()
            );
        }
    }
}
