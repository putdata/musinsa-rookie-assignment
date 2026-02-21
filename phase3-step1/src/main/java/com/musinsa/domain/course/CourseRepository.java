package com.musinsa.domain.course;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("SELECT c FROM Course c JOIN FETCH c.department JOIN FETCH c.professor")
    List<Course> findAllWithDetails();

    @Query("SELECT c FROM Course c JOIN FETCH c.department JOIN FETCH c.professor WHERE c.department.name = :departmentName")
    List<Course> findByDepartmentName(@Param("departmentName") String departmentName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdWithLock(@Param("id") Long id);
}
