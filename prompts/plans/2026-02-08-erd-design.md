# ERD 설계

## 개요

대학교 수강신청 시스템의 데이터 모델 설계. JPA 연관관계를 사용하며, 동시성 제어는 Course 엔티티에 비관적 락을 적용한다.

## 엔티티 설계

### Department (학과)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO | 학과 ID |
| name | String | NOT NULL, UNIQUE | 학과명 (예: "컴퓨터공학과") |

### Professor (교수)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO | 교수 ID |
| name | String | NOT NULL | 교수명 |
| department | Department | FK, NOT NULL | 소속 학과 (@ManyToOne) |

### Student (학생)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO | 학생 ID |
| name | String | NOT NULL | 학생명 |
| studentNumber | String | NOT NULL, UNIQUE | 학번 (예: "20260001") |
| department | Department | FK, NOT NULL | 소속 학과 (@ManyToOne) |
| grade | Integer | NOT NULL | 학년 (1~4) |

### Course (강좌)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO | 강좌 ID |
| name | String | NOT NULL | 강좌명 (예: "자료구조") |
| credits | Integer | NOT NULL | 학점 (1~3) |
| capacity | Integer | NOT NULL | 정원 |
| enrolled | Integer | NOT NULL, DEFAULT 0 | 현재 수강 인원 |
| schedule | String | NOT NULL | 강의 시간 (예: "MON_1,MON_2") |
| department | Department | FK, NOT NULL | 개설 학과 (@ManyToOne) |
| professor | Professor | FK, NOT NULL | 담당 교수 (@ManyToOne) |

**도메인 메서드:**
- `enroll()` — enrolled 1 증가. capacity 초과 시 예외 발생.
- `cancel()` — enrolled 1 감소. 0 미만 시 예외 발생.

**비관적 락:** CourseRepository에서 `@Lock(PESSIMISTIC_WRITE)` 쿼리 메서드 제공.

### Enrollment (수강신청)

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO | 수강신청 ID |
| student | Student | FK, NOT NULL | 학생 (@ManyToOne) |
| course | Course | FK, NOT NULL | 강좌 (@ManyToOne) |
| enrolledAt | LocalDateTime | NOT NULL | 신청 시각 |

**제약:** `UNIQUE(student_id, course_id)` — 동일 강좌 중복 신청 DB 레벨 방지.

## 관계도

```
Department ──┬── 1:N ──→ Student
             ├── 1:N ──→ Professor
             └── 1:N ──→ Course

Professor ───── 1:N ──→ Course

Student ─────── 1:N ──→ Enrollment ←── N:1 ──── Course
```

Enrollment은 Student와 Course 간 다대다 관계의 중간 테이블 역할.

## Schedule 형식

- 형식: `"요일_교시"` 의 콤마 구분 문자열
- 요일: `MON`, `TUE`, `WED`, `THU`, `FRI`
- 교시: `1`~`9`
  - 1교시: 09:00~09:50
  - 2교시: 10:00~10:50
  - 3교시: 11:00~11:50
  - 4교시: 12:00~12:50
  - 5교시: 13:00~13:50
  - 6교시: 14:00~14:50
  - 7교시: 15:00~15:50
  - 8교시: 16:00~16:50
  - 9교시: 17:00~17:50
- 예시: 3학점 강좌 → `"MON_1,MON_2,WED_1"` (월요일 1-2교시, 수요일 1교시)

## 동시성 제어 흐름 (수강신청)

1. Student 행에 `@Lock(PESSIMISTIC_WRITE)` → 학생 단위 직렬화 (학점/시간 검증 보호)
2. Course 행에 `@Lock(PESSIMISTIC_WRITE)` → 강좌 단위 직렬화 (정원 검증 보호)
3. `enrolled < capacity` 검증 (정원 초과 방지)
4. 중복 신청 검증
5. 학생의 현재 총 학점 검증 (≤ 18학점)
6. 시간표 충돌 검증
7. Enrollment 저장 + `Course.enroll()` 호출
8. 트랜잭션 커밋 시 락 해제

**락 획득 순서**: Student → Course (데드락 방지)

**동일 학생 동시 신청**: Student 행 락으로 순차 처리되므로, 학점 초과/시간 충돌이 정확하게 검증됨.
