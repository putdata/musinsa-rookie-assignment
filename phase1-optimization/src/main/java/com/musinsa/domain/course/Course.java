package com.musinsa.domain.course;

import com.musinsa.domain.department.Department;
import com.musinsa.domain.professor.Professor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courses",
        indexes = {
            @Index(name = "idx_course_department_id", columnList = "department_id")
        })
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer credits;

    @Column(nullable = false)
    private Integer capacity;

    @Builder.Default
    @Column(nullable = false)
    private Integer enrolled = 0;

    @Column(nullable = false)
    private String schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    public void enroll() {
        if (this.enrolled >= this.capacity) {
            throw new IllegalStateException("강좌 정원이 초과되었습니다.");
        }
        this.enrolled++;
    }

    public void cancel() {
        if (this.enrolled <= 0) {
            throw new IllegalStateException("수강 인원이 0명입니다.");
        }
        this.enrolled--;
    }
}
