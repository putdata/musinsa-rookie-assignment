# Phase 0 Baseline 성능 측정 결과

**측정 일시:** 2026-02-19
**환경:** Java 25, Spring Boot 3.5.10, MySQL 8.0 (Docker), Gradle 9.1.0
**서버 설정:** HikariCP max-pool-size=10, min-idle=5

---

## Load Test (50→100 VU, 5분)

| 지표 | 값 |
|------|-----|
| iterations/s | 127.8 |
| http_req_duration p90 | 10.75ms |
| http_req_duration p95 | 11.78ms |
| http_req_duration p99 | 15.41ms |
| http_req_duration avg | 7.53ms |
| http_req_failed rate | 43.18% (4xx 비즈니스 에러, 5xx 없음) |
| checks passed | 100% (no server error) |
| 총 iterations | 35,286 |
| max VUs | 100 |

---

## Enrollment Rush (100→500 VU, 4분 10초)

| 지표 | 값 |
|------|-----|
| iterations/s | 1,310 |
| http_req_duration p90 | 198.53ms |
| http_req_duration p95 | 259.01ms |
| http_req_duration p99 | 366.29ms |
| http_req_duration avg | 79.32ms |
| enroll_success | 2,018건 (8.8/s) |
| enroll_failed | 296,778건 |
| enroll_capacity_exceeded | 290,040건 |
| http_req_failed rate | 99.32% (대부분 정원 초과 409) |
| checks passed | 100% (no server error, 5xx 0건) |
| 총 iterations | 298,796 |
| max VUs | 500 |

---

## 분석

- **서버 안정성**: 500 VU 동시 부하에서도 5xx 에러 0건. 비관적 락 기반 동시성 제어가 안정적으로 동작.
- **응답 시간**: Load test 기준 p95 11.78ms로 매우 양호. Rush 시나리오에서도 p95 259ms로 500ms 이내.
- **정원 초과 제어**: 인기 강좌(1~50번) 대상 50만 건 이상 요청에서 정원 초과가 정확히 감지됨.
- **개선 포인트**: HikariCP pool-size가 10으로 작아 대량 동시 요청 시 커넥션 대기 발생 가능. Phase 1에서 튜닝 예정.
