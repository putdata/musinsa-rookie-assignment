# Phase 1 Step 2b: OSIV 비활성화 순수 효과 측정 (pool=10 유지)

**측정 일시:** 2026-02-19
**변경 사항:** spring.jpa.open-in-view=false, Hibernate batch_size=50, order_inserts/updates=true
**HikariCP:** Phase 0과 동일 (maximum-pool-size=10, minimum-idle=5)
**목적:** HikariCP 변경 없이 OSIV OFF만의 순수 효과 측정
**테스트 시나리오:** enrollment-rush (100→500 VU, 4m10s)

---

## Enrollment Rush 결과

| 지표 | 값 |
|------|-----|
| iterations/s | 1,307.0 |
| http_req_duration p90 | 199.67ms |
| http_req_duration p95 | 252.59ms |
| http_req_duration p99 | - |
| http_req_duration avg | 77.86ms |
| enroll_success | 2,018건 (8.8/s) |
| enroll_failed | 299,265건 |
| enroll_capacity_exceeded | 292,468건 |
| http_req_failed rate | 99.33% (대부분 정원 초과 409) |
| checks passed | 100% (no server error, 5xx 0건) |
| 총 iterations | 301,283 |
| max VUs | 500 |
| request timeout | 0건 |
| 총 소요 시간 | ~3m50s (정상) |

---

## 변수 분리 비교표 (enrollment-rush 시나리오)

| 지표 | Phase 0 (pool=10, OSIV ON) | OSIV OFF only (pool=10, OSIV OFF) | HikariCP only (pool=30, OSIV ON) | 둘 다 적용 (pool=30, OSIV OFF) |
|------|---------------------------|-----------------------------------|----------------------------------|-------------------------------|
| iterations/s | 1,310 | **1,307** | 1,474 | **1,494** |
| p90 | 198.53ms | **199.67ms** | 138ms | **132.26ms** |
| p95 | 259.01ms | **252.59ms** | 167.03ms | **153.85ms** |
| avg | 79.32ms | **77.86ms** | 46.02ms | **44.12ms** |
| 총 iterations | 298,796 | **301,283** | 368,407 | **373,278** |
| request timeout | 0 | **0** | 0 | **0** |
| enroll_success | 2,018 | **2,018** | 2,018 | **2,018** |

---

## 분석

### OSIV OFF 단독 효과 (pool=10 기준)
- Phase 0과 거의 동일한 수치 (iterations/s: 1,310 → 1,307, avg: 79.32ms → 77.86ms)
- **pool=10에서는 OSIV OFF 효과가 미미하다.**
- 이유: pool=10이면 동시에 10개 커넥션만 사용 가능하고, 비관적 락 경합이 이미 병목이므로 커넥션 보유 시간을 줄여도 대기 중인 요청이 즉시 커넥션을 가져갈 수 없다.

### HikariCP 단독 효과 (OSIV ON 기준)
- pool=30으로 늘리면 처리량 12.5% 증가, avg 42% 감소. 커넥션 대기 병목이 해소되면서 전반적 개선.

### 두 최적화의 조합 (pool=30 + OSIV OFF)
- pool=30 + OSIV OFF = Phase 0 대비 처리량 14.0% 증가, avg 44.4% 감소.
- HikariCP 확대(+12.5%)에 OSIV OFF가 소폭 추가 개선(+1.4%p 처리량, -4.1% avg)을 제공.

### 핵심 인사이트
> - 커넥션 풀 확대가 주요 개선 요인 (+12.5%)
> - OSIV OFF는 pool=10에서는 효과 미미하지만, pool=30과 함께 적용 시 소폭 추가 개선 (+1.4% 처리량, -4.1% avg)
> - 두 최적화 모두 적용했을 때 가장 좋은 결과 (처리량 +14.0%, avg -44.4%)
