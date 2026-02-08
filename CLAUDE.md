# Project Instructions for AI Agent

## 프로젝트 개요
대학교 수강신청 시스템. 동시성 제어가 핵심인 REST API 서버.
- Java 21 + Spring Boot 3.5 + H2 내장 DB
- 루트 단일 프로젝트 (별도 backend/ 분리 없음)

## 아키텍처
레이어드 아키텍처. 의존 방향: `api → service → domain` (단방향).

| 레이어 | 패키지 | 역할 |
|--------|--------|------|
| Presentation | `com.musinsa.api` | Controller, DTO, 요청/응답 변환 |
| Business | `com.musinsa.service` | 비즈니스 로직, 트랜잭션 관리, 동시성 제어 |
| Domain | `com.musinsa.domain` | Entity, Repository (JPA) |

- `ApiApplication.java`는 `com.musinsa` 최상위 패키지에 위치
- 각 레이어 내 서브도메인별 패키지 분리: student, course, professor, enrollment, department

## 패키지 구조
```
src/main/java/com/musinsa/
├── ApiApplication.java
├── api/
│   ├── HealthController.java
│   ├── student/
│   │   ├── StudentController.java
│   │   └── dtos/
│   ├── course/
│   │   ├── CourseController.java
│   │   └── dtos/
│   ├── professor/
│   │   ├── ProfessorController.java
│   │   └── dtos/
│   └── enrollment/
│       ├── EnrollmentController.java
│       └── dtos/
├── service/
│   ├── student/StudentService.java
│   ├── course/CourseService.java
│   ├── professor/ProfessorService.java
│   └── enrollment/EnrollmentService.java
└── domain/
    ├── student/      (Student, StudentRepository)
    ├── course/       (Course, CourseRepository)
    ├── professor/    (Professor, ProfessorRepository)
    ├── enrollment/   (Enrollment, EnrollmentRepository)
    └── department/   (Department, DepartmentRepository)
```

## 빌드/실행 명령어
```bash
# 빌드
./gradlew build

# Spring Boot 실행
./gradlew bootRun

# 테스트
./gradlew test
```

## 코딩 컨벤션

### Java
- Google Java Style 기반
- 클래스명: PascalCase
- 메서드/변수명: camelCase
- 패키지명: 소문자

### Lombok
- `@Getter` 사용, `@Setter` 사용 금지 (DDD 원칙: 상태 변경은 도메인 메서드로)
- `@RequiredArgsConstructor` — Service, Controller 의존성 주입
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — Entity (JPA 요구)
- `@AllArgsConstructor(access = AccessLevel.PRIVATE)` — Builder와 함께 사용
- `@Builder` — Entity 생성

### REST API
- 경로: `/api/` 접두사 사용 (Health 제외)
- 리소스명: 복수형 (`/api/students`, `/api/courses`)
- HTTP 메서드: GET(조회), POST(생성), DELETE(삭제)

### DTO
- 도메인별 `*Dtos.java` 파일 하나에 Request, Response record를 함께 정의
- `api/{도메인}/dtos/` 폴더에 위치
- 예: `StudentDtos.java` → `StudentDtos.Request`, `StudentDtos.Response`

## 동시성 제어 원칙
- 비관적 락(Pessimistic Lock) 사용
- 수강신청/취소 시 강좌 엔티티에 대한 행 수준 락
- `@Transactional`과 함께 사용
- 정원 초과 절대 불가

## 데이터 생성 규칙
- 서버 시작 시 프로그래밍 로직으로 동적 생성 (정적 SQL/CSV 금지)
- 현실적인 한국어 데이터 (학과명, 강좌명, 학생 이름 등)
- 최소 수량: 학과 10개, 강좌 500개, 학생 10,000명, 교수 100명
- 1분 이내 완료
- 비즈니스 규칙(시간 충돌, 학점 제한 등) 위반하지 않는 데이터

## 비즈니스 규칙
- 학생당 최대 18학점
- 동일 시간대 중복 수강 불가
- 정원 초과 수강신청 불가
- 동일 강좌 중복 수강신청 불가

## Git 커밋 컨벤션

### 형식
```
type(scope): 제목

- 세부 변경사항 1
- 세부 변경사항 2
- 세부 변경사항 3
```

### Type
| type | 설명 |
|------|------|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| docs | 문서 변경 |
| style | 코드 포맷팅 (동작 변경 없음) |
| refactor | 리팩토링 (기능 변경 없음) |
| test | 테스트 추가/수정 |
| chore | 빌드, 설정, 의존성 등 기타 변경 |

### Scope
도메인 또는 레이어 단위: `student`, `course`, `enrollment`, `professor`, `department`, `api`, `domain`, `service`, `docs`, `config`

### 예시
```
feat(enrollment): 수강신청 API 구현

- POST /api/enrollments 엔드포인트 추가
- 정원 초과 검증 로직 구현
- 비관적 락을 통한 동시성 제어 적용
```

## 문서화 규칙
- 주요 의사결정: `docs/decisions/`에 ADR 작성 (000-template.md 참고)
- API 엔드포인트 추가 시: `docs/api/endpoints.md` 업데이트
- 요구사항 분석/설계 결정: `docs/REQUIREMENTS.md`에 기록
- 문제 해결 과정: `docs/troubleshooting.md`에 기록

## 주의사항
- H2 내장 DB 사용 (외부 DB 불필요)
- Health Check: `GET /health` (PROBLEM.md 필수 요구사항, /api 접두사 없음)
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:musinsadb`)
