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

| 코드 | 의미 | 사용 상황 |
|------|------|----------|
| 200 | 성공 | 조회 성공 |
| 201 | 생성 성공 | 수강신청 성공 |
| 204 | 처리 완료 (본문 없음) | 수강취소 성공 (멱등) |
| 400 | 잘못된 요청 | 학점 초과, 잘못된 파라미터 |
| 404 | 리소스 없음 | 학생/강좌/수강신청 미존재 |
| 409 | 충돌 | 정원 초과, 시간 충돌, 중복 신청 |
| 500 | 서버 내부 오류 | 예기치 못한 서버 오류 |

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

**응답** `200 OK`
```json
[
  {
    "id": 1,
    "name": "김민준",
    "studentNumber": "20260001",
    "departmentName": "컴퓨터공학과",
    "grade": 3
  },
  {
    "id": 2,
    "name": "이서연",
    "studentNumber": "20260002",
    "departmentName": "경영학과",
    "grade": 1
  }
]
```

**응답 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 학생 ID |
| name | String | 학생 이름 |
| studentNumber | String | 학번 |
| departmentName | String | 소속 학과명 |
| grade | Integer | 학년 (1~4) |

---

## 강좌

### `GET /api/courses`
강좌 목록 조회 (전체)

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| department | String | N | 학과명으로 필터링 (예: "컴퓨터공학과") |

**응답** `200 OK`
```json
[
  {
    "id": 1,
    "name": "자료구조",
    "credits": 3,
    "capacity": 30,
    "enrolled": 25,
    "schedule": "MON_1,MON_2,WED_1",
    "departmentName": "컴퓨터공학과",
    "professorName": "김교수"
  },
  {
    "id": 2,
    "name": "경영학원론",
    "credits": 3,
    "capacity": 50,
    "enrolled": 48,
    "schedule": "TUE_3,TUE_4,THU_3",
    "departmentName": "경영학과",
    "professorName": "이교수"
  }
]
```

**응답 필드**

| 필드 | 타입 | 설명 | 예시 |
|------|------|------|------|
| id | Long | 강좌 ID | 1 |
| name | String | 강좌명 | "자료구조" |
| credits | Integer | 학점 | 3 |
| capacity | Integer | 정원 | 30 |
| enrolled | Integer | 현재 신청 인원 | 25 |
| schedule | String | 강의 시간 | "MON_1,MON_2,WED_1" |
| departmentName | String | 개설 학과명 | "컴퓨터공학과" |
| professorName | String | 담당 교수명 | "김교수" |

### `GET /api/courses?department=컴퓨터공학과`
학과별 강좌 조회

**응답**: 위와 동일한 형식. 해당 학과의 강좌만 필터링되어 반환.

---

## 교수

### `GET /api/professors`
교수 목록 조회

**응답** `200 OK`
```json
[
  {
    "id": 1,
    "name": "김교수",
    "departmentName": "컴퓨터공학과"
  },
  {
    "id": 2,
    "name": "이교수",
    "departmentName": "경영학과"
  }
]
```

**응답 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 교수 ID |
| name | String | 교수명 |
| departmentName | String | 소속 학과명 |

---

## 수강신청

### `POST /api/enrollments`
수강신청

**요청 본문**
```json
{
  "studentId": 1,
  "courseId": 42
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| studentId | Long | Y | 학생 ID |
| courseId | Long | Y | 강좌 ID |

**성공 응답** `201 Created`
```json
{
  "id": 1,
  "studentId": 1,
  "courseId": 42,
  "courseName": "자료구조",
  "credits": 3,
  "schedule": "MON_1,MON_2,WED_1",
  "enrolledAt": "2026-02-08T10:30:00"
}
```

**에러 응답**

| 상황 | HTTP 상태 | 에러 코드 | 메시지 |
|------|----------|----------|--------|
| 학생 미존재 | 404 | STUDENT_NOT_FOUND | 학생을 찾을 수 없습니다. |
| 강좌 미존재 | 404 | COURSE_NOT_FOUND | 강좌를 찾을 수 없습니다. |
| 정원 초과 | 409 | CAPACITY_EXCEEDED | 강좌 정원이 초과되었습니다. |
| 중복 신청 | 409 | DUPLICATE_ENROLLMENT | 이미 신청한 강좌입니다. |
| 학점 초과 | 400 | CREDIT_LIMIT_EXCEEDED | 최대 수강 가능 학점(18학점)을 초과합니다. |
| 시간 충돌 | 409 | SCHEDULE_CONFLICT | 이미 신청한 강좌와 시간이 겹칩니다. |

**에러 응답 예시** `409 Conflict`
```json
{
  "error": "CAPACITY_EXCEEDED",
  "message": "강좌 정원이 초과되었습니다."
}
```

### `DELETE /api/enrollments/{id}`
수강취소 (멱등)

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| id | Long | 수강신청 ID |

**응답** `204 No Content`

본문 없음. 해당 수강신청이 존재하면 취소하고, 이미 취소되었거나 존재하지 않으면 동일하게 204를 반환합니다.

**멱등성 보장**

동일한 `DELETE /api/enrollments/{id}` 요청을 여러 번 보내도 결과가 동일합니다 (RFC 9110 Section 9.3.5). 동시 요청 시에도 비관적 락을 통해 enrolled 카운터의 이중 차감을 방지합니다.

---

## 시간표

### `GET /api/enrollments?studentId={studentId}`
내 시간표(이번 학기) 조회

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| studentId | Long | Y | 학생 ID |

**응답** `200 OK`
```json
[
  {
    "id": 1,
    "studentId": 1,
    "courseId": 42,
    "courseName": "자료구조",
    "credits": 3,
    "schedule": "MON_1,MON_2,WED_1",
    "enrolledAt": "2026-02-08T10:30:00"
  },
  {
    "id": 2,
    "studentId": 1,
    "courseId": 15,
    "courseName": "알고리즘",
    "credits": 3,
    "schedule": "TUE_3,TUE_4,THU_3",
    "enrolledAt": "2026-02-08T10:31:00"
  }
]
```

**응답 필드**

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 수강신청 ID |
| studentId | Long | 학생 ID |
| courseId | Long | 강좌 ID |
| courseName | String | 강좌명 |
| credits | Integer | 학점 |
| schedule | String | 강의 시간 |
| enrolledAt | String | 신청 시각 (ISO 8601) |

**에러 응답**

| 상황 | HTTP 상태 | 에러 코드 | 메시지 |
|------|----------|----------|--------|
| 학생 미존재 | 404 | STUDENT_NOT_FOUND | 학생을 찾을 수 없습니다. |

---

## Schedule 형식 참고

강의 시간은 `"요일_교시"` 조합의 콤마 구분 문자열입니다.

**요일**: `MON`, `TUE`, `WED`, `THU`, `FRI`

**교시 시간표**:

| 교시 | 시간 |
|------|------|
| 1 | 09:00~09:50 |
| 2 | 10:00~10:50 |
| 3 | 11:00~11:50 |
| 4 | 12:00~12:50 |
| 5 | 13:00~13:50 |
| 6 | 14:00~14:50 |
| 7 | 15:00~15:50 |
| 8 | 16:00~16:50 |
| 9 | 17:00~17:50 |

**예시**: 3학점 강좌 `"MON_1,MON_2,WED_1"` = 월요일 1-2교시 + 수요일 1교시
