# Phase 1 Step 2: JPA 레벨 최적화 성능 측정 결과

**측정 일시:** 2026-02-19
**변경 사항:** spring.jpa.open-in-view=false, Hibernate batch_size=50, order_inserts/updates=true
**테스트 시나리오:** enrollment-rush (100→500 VU, 4m10s)

---

## Enrollment Rush 결과

| 지표 | 값 |
|------|-----|
| iterations/s | 1,513.9 |
| http_req_duration p90 | 160.55ms |
| http_req_duration p95 | 185.86ms |
| http_req_duration p99 | 282.27ms |
| http_req_duration avg | 52.83ms |
| enroll_success | 2,018건 (8.7/s) |
| enroll_failed | 349,042건 |
| enroll_capacity_exceeded | 341,130건 |
| http_req_failed rate | 99.42% (대부분 정원 초과 409) |
| checks passed | 100% (no server error, 5xx 0건) |
| 총 iterations | 351,060 |
| max VUs | 500 |
| request timeout | 0건 |
| 총 소요 시간 | ~4m10s (정상) |

---

## Phase 0 → Step 1 → Step 2 비교

| 지표 | Phase 0 (baseline) | Step 1 (HikariCP) | Step 2 (JPA opt) | Phase 0 대비 변화 |
|------|-------------------|-------------------|-------------------|-------------------|
| iterations/s | 1,310 | 283.5 | **1,513.9** | +15.6% |
| p90 | 198.53ms | 101.84ms | **160.55ms** | -19.1% |
| p95 | 259.01ms | 178.3ms | **185.86ms** | -28.2% |
| p99 | 366.29ms | 406.57ms | **282.27ms** | -22.9% |
| avg | 79.32ms | 99.56ms | **52.83ms** | -33.4% |
| 총 iterations | 298,796 | 173,279 | **351,060** | +17.5% |
| request timeout | 0 | 다수 | **0** | - |
| enroll_success | 2,018 | 2,018 | **2,018** | 동일 |

---

## 분석

- **OSIV 비활성화 효과가 핵심**: `open-in-view=false`로 트랜잭션 종료 후 커넥션을 즉시 반환하게 되어 커넥션 풀 효율이 크게 향상되었다.
- **Step 1 문제 해결**: HikariCP pool=30 + OSIV=false 조합으로, Step 1에서 발생했던 request timeout이 완전히 해소되었다.
- **Phase 0 대비 전면 개선**: 처리량 15.6% 증가, 평균 응답시간 33.4% 감소, p99 22.9% 감소.
- **핵심 인사이트**: 커넥션 풀 크기를 늘리는 것만으로는 부족하고, 커넥션 보유 시간을 줄이는 최적화(OSIV off)와 병행해야 효과가 있다.
