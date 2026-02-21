package com.musinsa.service.professor;

import com.musinsa.domain.professor.Professor;
import com.musinsa.domain.professor.ProfessorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfessorService {

    private final ProfessorRepository professorRepository;

    public List<Professor> findAll() {
        return professorRepository.findAllWithDepartment();
    }
}
