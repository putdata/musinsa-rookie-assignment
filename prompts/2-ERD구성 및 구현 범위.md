## Opus 4.6

/superpowers:brainstorm
PROBLEM.md에 나온대로 기획팀 요구사항을 구현해야해. 그러기 위해서 우선 핵심 기능을 먼저 구현하려고해 (명시되지 않은 요구사항은 우선 후순위)
어떤 것부터 구현해 나갈지 정하기전에 우선 ERD를 먼저 정의하자.

---

- 구현하기 쉽게 우선 JPA 연관관계 사용
- 강의 시간 표현은 우선 구현을 위해 문자열로 저장("MON_1,MON_2" — 요일_교시 조합의 콤마 구분 문자열)

---

구현 범위는 우선 조회 API + 수강신청/취소 + 동시성 제어(비관적 락) + 데이터 생성 + 문서화. 인증은 제외.

## Codex 5.3

현재 Opus 4.6을 가지고 브레인스토밍을 완료했는데, 변경사항을 보고 피드백 부탁해.

## Opus 4.6

docs/api/endpoints.md는 지금 목표 스펙을 적은 것이므로 바로 커밋하는 것보다 구현이 완료되면 같이 커밋하는게 좋아보여.
그리고 동일 학생의 동시 신청(서로 다른 강좌)까지 엄격히 막을지 정책 결정을 문서에 명확히 넣어야겠어

동시 신청

동일 학생이 동시에 서로 다른 강좌를 신청하는 경우 어떻게 처리할까요? 예: 학생 A가 강좌1과 강좌2를 동시에 신청하는데, 둘 다 성공하면
18학점 초과가 되거나 시간 충돌이 발생하는 경우. => 학생별 락을 추가하여 동일 학생의 신청을 완전히 순서화


## 브레인스토밍을 통한 결정 내역

### ERD 설계

1. **엔티티 연관관계**: JPA @ManyToOne 연관관계 사용
   - Department -> Student (1:N)
   - Department -> Professor (1:N)
   - Department -> Course (1:N)
   - Professor -> Course (1:N)
   - Student -> Enrollment (1:N)
   - Course -> Enrollment (1:N)

2. **Schedule 형식**: 문자열 저장 (`"MON_1,MON_2,WED_1"`)
   - 요일: MON, TUE, WED, THU, FRI
   - 교시: 1~9 (1교시=09:00~09:50)

3. **Enrollment 설계**:
   - student, course를 @ManyToOne으로 참조
   - enrolledAt(신청 시각) 필드 추가
   - UNIQUE(student_id, course_id) 제약으로 중복 방지

### 구현 순서 (8단계)

1. Entity 수정 (ERD 반영)
2. DataInitializer (초기 데이터 생성)
3. DTO 정의
4. 조회 API 구현
5. 수강신청/취소 핵심 로직
6. 에러 처리 (GlobalExceptionHandler)
7. 문서화
8. 테스트 (동시성 테스트 포함)

### 문서화 우선 진행

- docs/REQUIREMENTS.md: 요구사항 분석 및 설계 결정 완성
- docs/api/endpoints.md: API 명세 완성 (요청/응답 예제 포함)
- docs/plans/2026-02-08-erd-design.md: ERD 설계 문서
- docs/plans/2026-02-08-implementation-plan.md: 구현 계획 문서

---

