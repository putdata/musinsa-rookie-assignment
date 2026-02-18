# ADR-003: OSIV (Open Session In View) 비활성화

## 상태
채택됨

## 맥락

Spring Boot는 기본적으로 `spring.jpa.open-in-view=true`로 설정되어 있다.
OSIV가 켜져 있으면 HTTP 요청이 시작될 때 Hibernate Session(= DB 커넥션)을 획득하고, **응답이 완전히 전송될 때까지 커넥션을 보유**한다.

이는 Controller/View 레이어에서 Lazy Loading을 가능하게 하기 위한 편의 기능이지만, 고부하 환경에서 심각한 성능 문제를 일으킨다.

## 문제

### 커넥션 보유 시간 비교

**OSIV ON (기본값):**
```
HTTP 요청 도착
  └─ DB 커넥션 획득 ◀──────────────────────────────────┐
       └─ @Transactional 시작                          │
            └─ 비관적 락 획득                           │
            └─ 비즈니스 로직 실행                        │  커넥션 점유
            └─ 트랜잭션 커밋, 락 해제                    │
       └─ JSON 직렬화 (Response 생성)                   │
       └─ 네트워크 전송                                 │
  └─ DB 커넥션 반환 ◀──────────────────────────────────┘
```

**OSIV OFF:**
```
HTTP 요청 도착
       └─ @Transactional 시작
            └─ DB 커넥션 획득 ◀────────────┐
            └─ 비관적 락 획득              │  커넥션 점유
            └─ 비즈니스 로직 실행           │  (짧음!)
            └─ 트랜잭션 커밋, 락 해제       │
            └─ DB 커넥션 반환 ◀────────────┘
       └─ JSON 직렬화 (커넥션 없이)
       └─ 네트워크 전송 (커넥션 없이)
```

### 수강신청 시스템에서의 영향

커넥션 풀이 N개인 상황에서 500 VU가 동시 요청할 때:

- **OSIV ON**: 커넥션 N개가 JSON 직렬화 + 네트워크 전송 동안까지 점유 → 나머지 요청이 커넥션 대기 → request timeout
- **OSIV OFF**: 트랜잭션 끝나면 즉시 커넥션 반환 → 같은 N개 커넥션이 훨씬 빠르게 회전 → 처리량 증가

특히 비관적 락을 사용하는 수강신청 시스템에서는 커넥션 보유 시간이 곧 락 경합 시간에 직결되므로 영향이 크다.

## 실측 결과

### enrollment-rush 시나리오 (100→500 VU, 4m10s) - 변수 분리 비교

| 지표 | Phase 0 (pool=10, OSIV ON) | OSIV OFF only (pool=10, OSIV OFF) | HikariCP only (pool=30, OSIV ON) | 둘 다 적용 (pool=30, OSIV OFF) |
|------|---------------------------|-----------------------------------|----------------------------------|-------------------------------|
| iterations/s | 1,310 | 1,307 | 1,474 | **1,494** |
| p90 | 198.53ms | 199.67ms | 138ms | **132.26ms** |
| p95 | 259.01ms | 252.59ms | 167.03ms | **153.85ms** |
| avg | 79.32ms | 77.86ms | 46.02ms | **44.12ms** |
| 총 iterations | 298,796 | 301,283 | 368,407 | **373,278** |
| request timeout | 0 | 0 | 0 | **0** |

### 핵심 관찰: 각 최적화의 효과

- **OSIV OFF만 적용 (pool=10)**: Phase 0과 거의 동일한 수치. pool=10이면 비관적 락 경합이 이미 병목이므로 커넥션 보유 시간을 줄여도 효과 미미.
- **pool 증가만 적용 (pool=30, OSIV ON)**: 처리량 12.5% 증가, avg 42% 감소. 커넥션 대기 병목이 해소되면서 전반적 개선.
- **둘 다 적용 (pool=30, OSIV OFF)**: Phase 0 대비 처리량 14.0% 증가, avg 44.4% 감소. pool 증가 효과에 OSIV OFF가 소폭 추가 개선.

> **인사이트**: 커넥션 풀 확대가 주요 개선 요인이며(+12.5%), OSIV 비활성화는 커넥션 회전율을 높여 추가 개선(+1.4%p 처리량, -4.1% avg)을 제공한다. OSIV OFF 단독으로는 pool=10 환경에서 효과가 미미하다.

## 결정

`spring.jpa.open-in-view=false` 설정을 적용한다.

## 주의사항

- OSIV OFF 시 트랜잭션 외부에서 Lazy Loading이 불가능하다.
- 따라서 Service 레이어에서 필요한 데이터를 모두 fetch join으로 로딩해야 한다.
- 이 프로젝트에서는 이미 `findAllWithDetails()`, `findByStudentId()` 등에서 fetch join을 사용하고 있어 문제없다.

## 참고

- [Spring Boot 공식 문서 - Open Session In View](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#data.sql.jpa-and-spring-data.open-entity-manager-in-view)
- [Vlad Mihalcea - The Open Session in View Anti-Pattern](https://vladmihalcea.com/the-open-session-in-view-anti-pattern/)
