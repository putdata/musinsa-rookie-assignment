# ADR-003: 수강취소 동시성 버그 수정 및 DELETE 멱등성

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

## 선택지

### 동시성 해결
1. **멱등 처리만 (findById 결과가 empty면 return)** — 간단하지만 두 트랜잭션이 동시에 findById를 통과하는 레이스 컨디션 자체는 해결되지 않음
2. **Enrollment 비관적 락만 (findByIdWithLock)** — 동시성은 해결되지만, 락 대기 후 행이 삭제된 경우의 처리가 필요
3. **Enrollment 비관적 락 + 404 반환** — 동시성 해결 + 클라이언트에 명확한 피드백
4. **Enrollment 비관적 락 + 무조건 204 반환 (멱등 처리)** — 동시성은 해결되지만 클라이언트가 실제 삭제 여부를 알 수 없음
5. **GlobalExceptionHandler에 IllegalStateException 핸들러 추가 (방어)** — IllegalStateException은 다른 버그도 포함할 수 있어 진짜 500을 409로 숨길 위험

### 멱등성 키 도입 검토
6. **Idempotency-Key 헤더 도입** — 클라이언트가 고유 키를 전송, 서버가 키-응답을 캐싱하여 동일 응답 반환

## 결정
**선택지 3: Enrollment 비관적 락 + 404 반환**

### 선택지 4(무조건 204) 기각 사유
- 클라이언트가 "실제로 취소됨"과 "원래 없었음/잘못된 ID"를 구분할 수 없음
- RFC 9110의 멱등성은 **서버 상태의 변화**가 동일하면 충족되며, 응답 코드까지 동일할 필요 없음
- 비관적 락이 동시성을 직렬화하므로, 동시 요청 시 첫 번째 → 204, 두 번째 → 404로 안전하게 처리됨

### 선택지 5(IllegalStateException 전역 핸들링) 기각 사유
- `IllegalStateException`은 Java 표준 예외로 다양한 버그 상황에서 발생 가능
- 전역 매핑 시 진짜 서버 에러(500)가 409로 숨겨져 디버깅이 어려워짐
- 방어를 넣고 싶다면 `Course.cancel()`에서 도메인 전용 예외(BusinessException)를 던지도록 변경해야 하나, 선택지 3이 정확히 구현되면 `enrolled <= 0` 가드는 도달 불가하므로 불필요

### 선택지 6(멱등성 키) 기각 사유
- `DELETE /api/enrollments/{id}`에서 URL 경로의 `{id}` 자체가 자연 멱등성 키 역할
- 별도 키를 도입하면 키-응답 저장소(테이블/Redis), TTL 관리, 동시 키 요청 처리, 클라이언트 협조 등 인프라 비용이 큼
- H2 인메모리 DB 환경에서 이 인프라 비용 대비 이득이 맞지 않음
- 멱등성 키가 진짜 필요한 곳은 `POST /api/enrollments`(재시도 시 중복 생성 가능성)이나, 현재 `existsByStudentIdAndCourseId` 검증이 같은 강좌에 대해서는 방어하고 있어 당장 불필요 (YAGNI)

### 구현 설계

**EnrollmentRepository:**
- `findByIdWithLock()` 추가: `@Lock(PESSIMISTIC_WRITE)` + `JOIN FETCH e.course`
- JOIN FETCH로 후속 Course 접근 시 추가 쿼리 방지

**EnrollmentService.cancel():**
```
1. Enrollment 비관적 락 조회 (findByIdWithLock)
2. 결과가 empty → ENROLLMENT_NOT_FOUND 예외 (404)
3. Course 비관적 락 획득 (락 순서: Enrollment → Course)
4. course.cancel() + enrollmentRepository.delete()
```

**EnrollmentController:**
- 성공 응답을 `200 OK` + body → `204 No Content` + void로 변경
- 존재하지 않는 수강신청 → 404 Not Found (기존 동작 유지)

## 결과
- 동시 취소 시 enrolled 이중 차감 방지
- 동시 취소 시 500 에러 방지
- 클라이언트에 명확한 피드백: 실제 삭제(204) vs 미존재(404)
- HTTP DELETE 멱등성 보장 — 서버 상태 기준 (RFC 9110 Section 9.2.2)
- 성공 응답 변경(200→204)에 따른 테스트/문서 수정 필요
