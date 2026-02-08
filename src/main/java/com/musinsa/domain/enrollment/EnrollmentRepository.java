package com.musinsa.domain.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course c JOIN FETCH c.department WHERE e.student.id = :studentId")
    List<Enrollment> findByStudentId(@Param("studentId") Long studentId);

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);
}
