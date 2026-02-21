package com.musinsa.service.enrollment;

import com.musinsa.api.exception.BusinessException;
import com.musinsa.api.exception.ErrorCode;
import com.musinsa.domain.course.Course;
import com.musinsa.domain.course.CourseRepository;
import com.musinsa.domain.enrollment.Enrollment;
import com.musinsa.domain.enrollment.EnrollmentRepository;
import com.musinsa.domain.student.Student;
import com.musinsa.domain.student.StudentRepository;
import com.musinsa.service.course.CourseCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private static final int MAX_CREDITS = 18;

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final ObjectProvider<CourseCounterService> counterProvider;

    @Transactional(readOnly = true)
    public List<Enrollment> findByStudentId(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        }
        return enrollmentRepository.findByStudentId(studentId);
    }

    @Transactional
    public Enrollment enroll(Long studentId, Long courseId) {
        CourseCounterService counterService = counterProvider.getIfAvailable();

        // 1. Redis fast rejection (read-only): 정원 초과면 DB까지 안 감
        if (counterService != null && counterService.isFull(courseId)) {
            throw new BusinessException(ErrorCode.CAPACITY_EXCEEDED);
        }

        // 2. DB 비관적 락으로 실제 수강신청
        Enrollment enrollment = doEnroll(studentId, courseId);

        // 3. 성공 후에만 카운터 증가 (보상 로직 불필요)
        if (counterService != null) {
            counterService.incrementEnrolled(courseId);
        }

        return enrollment;
    }

    private Enrollment doEnroll(Long studentId, Long courseId) {
        // 1. Student 비관적 락 획득
        Student student = studentRepository.findByIdWithLock(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));

        // 2. Course 비관적 락 획득
        Course course = courseRepository.findByIdWithLock(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        // 3. 중복 신청 검증
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
        }

        // 4. 현재 수강 목록 조회
        List<Enrollment> currentEnrollments = enrollmentRepository.findByStudentId(studentId);

        // 5. 학점 상한 검증
        int currentCredits = currentEnrollments.stream()
                .mapToInt(e -> e.getCourse().getCredits())
                .sum();
        if (currentCredits + course.getCredits() > MAX_CREDITS) {
            throw new BusinessException(ErrorCode.CREDIT_LIMIT_EXCEEDED);
        }

        // 6. 시간표 충돌 검증
        Set<String> newSlots = parseSchedule(course.getSchedule());
        for (Enrollment enrollment : currentEnrollments) {
            Set<String> existingSlots = parseSchedule(enrollment.getCourse().getSchedule());
            for (String slot : newSlots) {
                if (existingSlots.contains(slot)) {
                    throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT);
                }
            }
        }

        // 7. 정원 검증 + enrolled 증가 (DB 최종 방어)
        if (course.getEnrolled() >= course.getCapacity()) {
            throw new BusinessException(ErrorCode.CAPACITY_EXCEEDED);
        }
        course.enroll();

        // 8. Enrollment 저장
        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .course(course)
                .enrolledAt(LocalDateTime.now())
                .build();

        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void cancel(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdWithLock(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        Course course = courseRepository.findByIdWithLock(enrollment.getCourse().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        course.cancel();
        enrollmentRepository.delete(enrollment);

        // Redis 카운터 감소
        CourseCounterService counterService = counterProvider.getIfAvailable();
        if (counterService != null) {
            counterService.decrementEnrolled(course.getId());
        }
    }

    private Set<String> parseSchedule(String schedule) {
        return Arrays.stream(schedule.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}
