package com.musinsa.service.enrollment;

import com.musinsa.domain.course.Course;
import com.musinsa.domain.course.CourseRepository;
import com.musinsa.domain.department.Department;
import com.musinsa.domain.department.DepartmentRepository;
import com.musinsa.domain.professor.Professor;
import com.musinsa.domain.professor.ProfessorRepository;
import com.musinsa.domain.student.Student;
import com.musinsa.domain.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class EnrollmentConcurrencyTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProfessorRepository professorRepository;

    private Course testCourse;
    private List<Student> testStudents;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.save(
                Department.builder().name("테스트학과").build());

        Professor professor = professorRepository.save(
                Professor.builder().name("테스트교수").department(dept).build());

        testCourse = courseRepository.save(
                Course.builder()
                        .name("테스트강좌")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());

        testStudents = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            testStudents.add(studentRepository.save(
                    Student.builder()
                            .name("학생" + i)
                            .studentNumber(String.format("TEST%04d", i))
                            .department(dept)
                            .grade(1)
                            .build()));
        }
    }

    @Test
    @DisplayName("100명이 동시에 정원 30명 강좌에 수강신청하면 정확히 30명만 성공해야 한다")
    void concurrentEnrollment_exactlyCapacityShouldSucceed() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    enrollmentService.enroll(testStudents.get(index).getId(), testCourse.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        Course updatedCourse = courseRepository.findById(testCourse.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(30);
        assertThat(failCount.get()).isEqualTo(70);
        assertThat(updatedCourse.getEnrolled()).isEqualTo(30);
    }
}
