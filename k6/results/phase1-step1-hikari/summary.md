# Phase 1 Step 1: HikariCP 커넥션 풀 튜닝 성능 측정 결과

**측정 일시:** 2026-02-19
**변경 사항:** HikariCP maximum-pool-size 10 → 30, minimum-idle 5 → 10 (그 외 설정 변경 없음, OSIV ON 유지)
**테스트 시나리오:** enrollment-rush (100→500 VU, 4m10s)
**측정 방법:** phase0-baseline 서버를 커맨드라인 인자로 pool-size만 오버라이드하여 실행

---

## Enrollment Rush 결과

| 지표 | 값 |
|------|-----|
| iterations/s | 1,474 |
| http_req_duration p90 | 138ms |
| http_req_duration p95 | 167.03ms |
| http_req_duration avg | 46.02ms |
| http_req_duration max | 972.95ms |
| enroll_success | 2,018건 (8.07/s) |
| enroll_failed | 366,389건 |
| enroll_capacity_exceeded | 358,114건 |
| http_req_failed rate | 99.45% (대부분 정원 초과 409) |
| checks passed | 100% (no server error, 5xx 0건) |
| 총 iterations | 368,407 |
| max VUs | 500 |
| request timeout | 0건 |
| 총 소요 시간 | ~4m10s |

---

## Phase 0 vs Phase 1 Step 1 비교

| 지표 | Phase 0 (pool=10) | Step 1 (pool=30) | 변화 |
|------|-------------------|-------------------|------|
| iterations/s | 1,310 | 1,474 | +12.5% (개선) |
| p90 | 198.53ms | 138ms | -30.5% (개선) |
| p95 | 259.01ms | 167.03ms | -35.5% (개선) |
| avg | 79.32ms | 46.02ms | -42.0% (개선) |
| enroll_success | 2,018 | 2,018 | 동일 |
| 총 iterations | 298,796 | 368,407 | +23.3% (개선) |
| request timeout | 0건 | 0건 | 동일 |
| 총 소요 시간 | ~4m10s | ~4m10s | 동일 |

---

## 분석

- **처리량 개선**: pool-size를 10→30으로 늘린 결과, iterations/s가 1,310→1,474로 12.5% 증가했다. 커넥션 대기 병목이 해소되면서 전체 처리량이 개선되었다.
- **응답 시간 대폭 개선**: 평균 응답 시간이 79.32ms→46.02ms로 42% 감소, p95도 259.01ms→167.03ms로 35.5% 개선되었다.
- **안정성 유지**: 500 VU에서도 5xx 에러 0건, request timeout 0건으로 안정적으로 동작했다.
- **결론**: 비관적 락 기반 시스템에서도 커넥션 풀 확대만으로 의미 있는 성능 개선(처리량 +12.5%, 응답시간 -42%)이 가능하다.
