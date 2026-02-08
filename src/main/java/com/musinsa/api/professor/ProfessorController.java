package com.musinsa.api.professor;

import com.musinsa.api.professor.dtos.ProfessorDtos;
import com.musinsa.service.professor.ProfessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/professors")
@RequiredArgsConstructor
public class ProfessorController {

    private final ProfessorService professorService;

    @GetMapping
    public List<ProfessorDtos.Response> findAll() {
        return professorService.findAll().stream()
                .map(ProfessorDtos.Response::from)
                .toList();
    }
}
