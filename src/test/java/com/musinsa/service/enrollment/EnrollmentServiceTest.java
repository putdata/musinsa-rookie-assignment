package com.musinsa.service.enrollment;

import com.musinsa.api.exception.BusinessException;
import com.musinsa.api.exception.ErrorCode;
import com.musinsa.domain.course.Course;
import com.musinsa.domain.course.CourseRepository;
import com.musinsa.domain.department.Department;
import com.musinsa.domain.department.DepartmentRepository;
import com.musinsa.domain.enrollment.Enrollment;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class EnrollmentServiceTest {

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

    private Department dept;
    private Professor professor;
    private Student student;

    @BeforeEach
    void setUp() {
        dept = departmentRepository.save(
                Department.builder().name("테스트학과").build());

        professor = professorRepository.save(
                Professor.builder().name("테스트교수").department(dept).build());

        student = studentRepository.save(
                Student.builder()
                        .name("테스트학생")
                        .studentNumber("TEST0001")
                        .department(dept)
                        .grade(1)
                        .build());
    }

    @Test
    @DisplayName("정상 수강신청")
    void enroll_success() {
        Course course = courseRepository.save(
                Course.builder()
                        .name("자료구조")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());

        Enrollment enrollment = enrollmentService.enroll(student.getId(), course.getId());

        assertThat(enrollment.getId()).isNotNull();
        assertThat(enrollment.getStudent().getId()).isEqualTo(student.getId());
        assertThat(enrollment.getCourse().getId()).isEqualTo(course.getId());
        assertThat(enrollment.getEnrolledAt()).isNotNull();

        Course updatedCourse = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(updatedCourse.getEnrolled()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원 초과 시 CAPACITY_EXCEEDED 예외")
    void enroll_capacityExceeded() {
        Course course = courseRepository.save(
                Course.builder()
                        .name("인기강좌")
                        .credits(3)
                        .capacity(1)
                        .enrolled(1)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());

        assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), course.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CAPACITY_EXCEEDED));
    }

    @Test
    @DisplayName("동일 강좌 중복 신청 시 DUPLICATE_ENROLLMENT 예외")
    void enroll_duplicateEnrollment() {
        Course course = courseRepository.save(
                Course.builder()
                        .name("자료구조")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());

        enrollmentService.enroll(student.getId(), course.getId());

        assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), course.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_ENROLLMENT));
    }

    @Test
    @DisplayName("최대 학점(18) 초과 시 CREDIT_LIMIT_EXCEEDED 예외")
    void enroll_creditLimitExceeded() {
        // 6개 x 3학점 = 18학점 채우기
        for (int i = 0; i < 6; i++) {
            Course course = courseRepository.save(
                    Course.builder()
                            .name("강좌" + i)
                            .credits(3)
                            .capacity(30)
                            .enrolled(0)
                            .schedule("MON_" + (i + 1))
                            .department(dept)
                            .professor(professor)
                            .build());
            enrollmentService.enroll(student.getId(), course.getId());
        }

        // 19학점째 시도
        Course extraCourse = courseRepository.save(
                Course.builder()
                        .name("추가강좌")
                        .credits(1)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("FRI_9")
                        .department(dept)
                        .professor(professor)
                        .build());

        assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), extraCourse.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CREDIT_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("시간표 충돌 시 SCHEDULE_CONFLICT 예외")
    void enroll_scheduleConflict() {
        Course course1 = courseRepository.save(
                Course.builder()
                        .name("자료구조")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());
        enrollmentService.enroll(student.getId(), course1.getId());

        Course course2 = courseRepository.save(
                Course.builder()
                        .name("알고리즘")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_2,MON_3,MON_4")
                        .department(dept)
                        .professor(professor)
                        .build());

        assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), course2.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SCHEDULE_CONFLICT));
    }

    @Test
    @DisplayName("수강취소 성공")
    void cancel_success() {
        Course course = courseRepository.save(
                Course.builder()
                        .name("자료구조")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());

        Enrollment enrollment = enrollmentService.enroll(student.getId(), course.getId());
        enrollmentService.cancel(enrollment.getId());

        Course updatedCourse = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(updatedCourse.getEnrolled()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 수강신청 취소 시 예외 없이 정상 리턴 (멱등)")
    void cancel_notFound_idempotent() {
        enrollmentService.cancel(999999L);
        // 예외 없이 정상 리턴되면 멱등성 보장 성공
    }

    @Test
    @DisplayName("존재하지 않는 학생 ID로 수강신청 시 STUDENT_NOT_FOUND 예외")
    void enroll_studentNotFound() {
        Course course = courseRepository.save(
                Course.builder()
                        .name("자료구조")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());

        assertThatThrownBy(() -> enrollmentService.enroll(999999L, course.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
    }

    @Test
    @DisplayName("존재하지 않는 강좌 ID로 수강신청 시 COURSE_NOT_FOUND 예외")
    void enroll_courseNotFound() {
        assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), 999999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COURSE_NOT_FOUND));
    }
}
