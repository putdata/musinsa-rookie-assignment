package com.musinsa.domain.enrollment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course WHERE e.id = :id")
    Optional<Enrollment> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course c JOIN FETCH c.department WHERE e.student.id = :studentId")
    List<Enrollment> findByStudentId(@Param("studentId") Long studentId);

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);
}
