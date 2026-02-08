# 구현 계획

## 개요

PROBLEM.md 기획팀 요구사항을 기반으로 수강신청 시스템의 핵심 기능을 구현한다. 인증/인가는 제외하고, 동작하는 핵심 기능에 집중한다.

## 구현 순서

### Phase 1: Entity 수정 (ERD 반영)

기존 Entity를 ERD 설계에 맞게 수정한다.

**변경 내용:**

| 엔티티 | 변경 사항 |
|--------|----------|
| Department | 변경 없음 |
| Professor | departmentName(String) -> department(@ManyToOne) |
| Student | departmentName(String) -> department(@ManyToOne) |
| Course | departmentName, professorName -> @ManyToOne + enroll()/cancel() 도메인 메서드 |
| Enrollment | studentId, courseId -> @ManyToOne + enrolledAt 추가 + UNIQUE(student, course) |

**Repository 변경:**
- StudentRepository: 비관적 락 쿼리 메서드 추가 (학생 단위 동시성 제어)
- CourseRepository: 비관적 락 쿼리 메서드 추가 (강좌 단위 동시성 제어)
- EnrollmentRepository: 학생별 수강 목록 조회, 중복 검사 메서드

**커밋**: `feat(domain): ERD 기반 엔티티 연관관계 및 도메인 메서드 추가`

### Phase 2: 초기 데이터 생성 (DataInitializer)

`CommandLineRunner` 구현체로 서버 시작 시 초기 데이터 생성.

**생성 데이터:**
- 학과 10개: 고정 목록 (컴퓨터공학과, 경영학과, 전자공학과 등)
- 교수 100명: 학과당 10명, 한국어 성+이름 조합
- 강좌 500개: 학과당 50개, 교수 배정, 교수별 시간 충돌 없는 schedule 생성
- 학생 10,000명: 학과/학년 균등 분배, 학번 자동 생성

**성능 목표:** 1분 이내 완료. `saveAll()` 배치 사용.

**커밋**: `feat(config): 초기 데이터 생성 로직 구현`

### Phase 3: DTO 정의

각 도메인별 DTO 파일 생성.

| 파일 | 내용 |
|------|------|
| StudentDtos.java | Response record |
| CourseDtos.java | Response record |
| ProfessorDtos.java | Response record |
| EnrollmentDtos.java | Request, Response record |

**커밋**: `feat(api): DTO 정의`

### Phase 4: 조회 API 구현

Service + Controller에 조회 로직 구현.

| 엔드포인트 | Service 메서드 |
|-----------|---------------|
| GET /api/students | findAll() |
| GET /api/courses | findAll(), findByDepartment(name) |
| GET /api/professors | findAll() |
| GET /api/enrollments?studentId={id} | findByStudentId(id) |

**커밋**: `feat(api): 학생/강좌/교수/시간표 조회 API 구현`

### Phase 5: 수강신청/취소 핵심 로직

**수강신청 (POST /api/enrollments):**
1. Student 비관적 락 획득 (학점/시간 검증 직렬화)
2. Course 비관적 락 획득 (정원 검증 직렬화) — 락 순서: Student → Course (데드락 방지)
3. 정원 초과 검증
4. 중복 신청 검증
5. 학점 상한 검증 (현재 + 신규 <= 18)
6. 시간표 충돌 검증
7. Enrollment 저장 + Course.enroll()
8. 트랜잭션 커밋 -> 락 해제

**수강취소 (DELETE /api/enrollments/{id}):** *(ADR-003에 의해 멱등 처리로 변경)*
1. Enrollment 비관적 락 조회 (findByIdWithLock, JOIN FETCH course)
2. 결과가 empty → 바로 리턴 (멱등: 이미 취소됨)
3. Course 비관적 락 획득 (락 순서: Enrollment → Course)
4. Course.cancel() + Enrollment 삭제
5. 트랜잭션 커밋 → 락 해제
6. 응답: 204 No Content (본문 없음)

**커밋**: `feat(enrollment): 수강신청/취소 API 및 동시성 제어 구현`

### Phase 6: 에러 처리

- GlobalExceptionHandler (@RestControllerAdvice)
- 비즈니스 예외 클래스 정의
- 통합 에러 응답 형식

**커밋**: `feat(api): 글로벌 예외 처리 구현`

### Phase 7: 문서화

- docs/REQUIREMENTS.md 완성
- docs/api/endpoints.md 완성
- prompts/ 프롬프트 이력 기록

**커밋**: `docs: 요구사항 분석 및 API 명세 작성`

### Phase 8: 테스트

- 동시성 테스트: 100 스레드 동시 수강신청 -> 정원만큼만 성공
- 비즈니스 규칙 테스트: 학점 초과, 시간 충돌, 중복 신청
- API 통합 테스트: MockMvc 활용

**커밋**: `test(enrollment): 동시성 제어 및 비즈니스 규칙 테스트`
