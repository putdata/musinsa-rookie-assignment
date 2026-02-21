package com.musinsa.api.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "학생을 찾을 수 없습니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "강좌를 찾을 수 없습니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수강신청 내역을 찾을 수 없습니다."),
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "강좌 정원이 초과되었습니다."),
    DUPLICATE_ENROLLMENT(HttpStatus.CONFLICT, "이미 신청한 강좌입니다."),
    CREDIT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "최대 수강 가능 학점(18학점)을 초과합니다."),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "이미 신청한 강좌와 시간이 겹칩니다.");

    private final HttpStatus status;
    private final String message;
}
