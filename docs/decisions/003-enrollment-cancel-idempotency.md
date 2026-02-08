# ADR-003: 수강취소 DELETE 멱등성 보장

## 상태
Accepted

## 맥락
동일 enrollmentId에 대한 `DELETE /api/enrollments/{id}` 동시 요청 시 500 에러가 발생할 수 있다.

**레이스 컨디션 시나리오:**
1. 트랜잭션 A, B가 동시에 `findById()`로 같은 Enrollment를 읽음 (락 없음)
2. 트랜잭션 A가 Course 비관적 락 획득 → `cancel()` → `delete()` → 커밋
3. 트랜잭션 B가 Course 비관적 락 획득 → `cancel()`로 enrolled 이중 차감 → 이미 삭제된 Enrollment `delete()` 시 예외 발생

**문제점:**
- `Course.enrolled` 카운터가 이중 차감됨
- 두 번째 트랜잭션에서 JPA 예외 발생 → GlobalExceptionHandler에서 처리되지 않아 500 반환
- HTTP DELETE의 멱등성(RFC 9110 Section 9.3.5) 위반

## 선택지
1. **멱등 처리만 (findById 결과가 empty면 return)** — 간단하지만 두 트랜잭션이 동시에 findById를 통과하는 레이스 컨디션 자체는 해결되지 않음
2. **Enrollment 비관적 락만 (findByIdWithLock)** — 동시성은 해결되지만, 락 대기 후 행이 삭제된 경우의 멱등 처리가 여전히 필요
3. **Enrollment 비관적 락 + 멱등 처리** — 동시성과 멱등성 모두 보장
4. **GlobalExceptionHandler에 IllegalStateException 핸들러 추가 (방어)** — IllegalStateException은 다른 버그도 포함할 수 있어 진짜 500을 409로 숨길 위험

## 결정
**선택지 3: Enrollment 비관적 락 + 멱등 처리**

### 구현 설계

**EnrollmentRepository:**
- `findByIdWithLock()` 추가: `@Lock(PESSIMISTIC_WRITE)` + `JOIN FETCH e.course`
- JOIN FETCH로 후속 Course 접근 시 추가 쿼리 방지

**EnrollmentService.cancel():**
```
1. Enrollment 비관적 락 조회 (findByIdWithLock)
2. 결과가 empty → return (멱등: 이미 취소됨)
3. Course 비관적 락 획득 (락 순서: Enrollment → Course)
4. course.cancel() + enrollmentRepository.delete()
```

**EnrollmentController:**
- 응답을 `200 OK` + body → `204 No Content` + void로 변경
- 존재 여부와 무관하게 동일한 204 반환

**선택지 4(IllegalStateException 전역 핸들링) 기각 사유:**
- `IllegalStateException`은 Java 표준 예외로 다양한 버그 상황에서 발생 가능
- 전역 매핑 시 진짜 서버 에러(500)가 409로 숨겨져 디버깅이 어려워짐
- 선택지 3이 정확히 구현되면 `Course.cancel()`의 `enrolled <= 0` 가드는 도달 불가

## 결과
- 동시 취소 시 enrolled 이중 차감 방지
- 동시 취소 시 500 에러 방지
- HTTP DELETE 멱등성 보장 (RFC 9110 준수)
- 기존 테스트(cancelNotFound → 404 기대)와 API 문서(200 OK) 수정 필요
