# REST API 명세

> Swagger UI: http://localhost:8080/swagger-ui.html

## 공통 사항

### Base URL
```
http://localhost:8080
```

### 응답 형식
모든 응답은 JSON 형식입니다.

### 에러 응답 형식
```json
{
  "error": "에러 코드",
  "message": "사용자에게 표시할 메시지"
}
```

### HTTP 상태 코드

| 코드 | 의미 |
|------|------|
| 200 | 성공 |
| 201 | 생성 성공 |
| 400 | 잘못된 요청 (비즈니스 규칙 위반 등) |
| 404 | 리소스 없음 |
| 409 | 충돌 (정원 초과, 중복 신청 등) |
| 500 | 서버 내부 오류 |

---

## Health Check

### `GET /health`
서버 정상 구동 확인

**응답** `200 OK`
```json
{
  "status": "ok"
}
```

---

## 학생

### `GET /api/students`
학생 목록 조회

<!-- TODO: 요청 파라미터, 응답 예시, 페이징 -->

---

## 강좌

### `GET /api/courses`
강좌 목록 조회 (전체)

<!-- TODO: 응답 필드 (id, name, credits, capacity, enrolled, schedule) -->

### `GET /api/courses?department={학과명}`
강좌 목록 조회 (학과별)

<!-- TODO: 응답 예시 -->

---

## 교수

### `GET /api/professors`
교수 목록 조회

<!-- TODO: 응답 예시 -->

---

## 수강신청

### `POST /api/enrollments`
수강신청

<!-- TODO: 요청 본문, 성공/실패 응답, 에러 케이스 -->

### `DELETE /api/enrollments/{id}`
수강취소

<!-- TODO: 성공/실패 응답 -->

---

## 시간표

### `GET /api/enrollments?studentId={studentId}`
내 시간표(이번 학기) 조회

<!-- TODO: 응답 예시 -->
