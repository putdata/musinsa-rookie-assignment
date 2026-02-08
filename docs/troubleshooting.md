# Troubleshooting

구현 과정에서 발생한 문제와 해결 과정을 기록합니다.

<!-- 아래 형식으로 기록
## 문제 제목

**증상:** 어떤 문제가 발생했는가
**원인:** 왜 발생했는가
**해결:** 어떻게 해결했는가
-->

## 동시 수강취소 시 500 에러 및 enrolled 이중 차감

**증상:** 동일 enrollmentId에 대한 `DELETE /api/enrollments/{id}` 동시 요청 시 500 Internal Server Error 발생. `Course.enrolled` 카운터가 실제 취소 건수보다 더 많이 차감될 수 있음.

**원인:** `EnrollmentService.cancel()`에서 Enrollment를 락 없이 먼저 읽고(`findById`), 이후 Course에만 비관적 락을 걸고 있었음. 두 트랜잭션이 동시에 같은 Enrollment를 읽은 뒤, 첫 번째가 삭제를 완료한 후 두 번째가 `course.cancel()`로 enrolled를 이중 차감하고, 이미 삭제된 Enrollment를 다시 `delete()` 하면서 JPA 예외 발생. `GlobalExceptionHandler`에 해당 예외 핸들러가 없어 500으로 전파됨.

**해결:** Enrollment 조회 시에도 비관적 락(`findByIdWithLock`)을 적용하고, 결과가 empty면 이미 취소된 것으로 간주하여 바로 리턴(멱등 처리). 응답을 `204 No Content`로 변경하여 HTTP DELETE 멱등성(RFC 9110) 준수. 상세 설계 결정은 [ADR-003](decisions/003-enrollment-cancel-idempotency.md) 참고.
