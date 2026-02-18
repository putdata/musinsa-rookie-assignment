# Phase 1 Step 1: HikariCP 커넥션 풀 튜닝 성능 측정 결과

**측정 일시:** 2026-02-19
**변경 사항:** HikariCP maximum-pool-size 10 → 30, minimum-idle 5 → 10, connection-timeout 3000ms, idle-timeout 60000ms, max-lifetime 1800000ms
**테스트 시나리오:** enrollment-rush (100→500 VU, 4m10s)

---

## Enrollment Rush 결과

| 지표 | 값 |
|------|-----|
| iterations/s | 283.5 |
| http_req_duration p90 | 101.84ms |
| http_req_duration p95 | 178.3ms |
| http_req_duration p99 | 406.57ms |
| http_req_duration avg | 99.56ms |
| enroll_success | 2,018건 (3.3/s) |
| enroll_failed | 171,296건 |
| enroll_capacity_exceeded | 167,395건 |
| http_req_failed rate | 98.83% (대부분 정원 초과 409) |
| checks passed | 100% (no server error, 5xx 0건) |
| 총 iterations | 173,279 |
| max VUs | 500 |
| request timeout | 다수 발생 (graceful stop 10분 초과) |

---

## Phase 0 vs Phase 1 Step 1 비교

| 지표 | Phase 0 (pool=10) | Step 1 (pool=30) | 변화 |
|------|-------------------|-------------------|------|
| iterations/s | 1,310 | 283.5 | -78.4% (악화) |
| p90 | 198.53ms | 101.84ms | -48.7% (개선) |
| p95 | 259.01ms | 178.3ms | -31.2% (개선) |
| p99 | 366.29ms | 406.57ms | +11.0% (악화) |
| avg | 79.32ms | 99.56ms | +25.5% (악화) |
| enroll_success | 2,018 | 2,018 | 동일 |
| 총 iterations | 298,796 | 173,279 | -42.0% (악화) |
| request timeout | 0건 | 다수 발생 | 악화 |
| 총 소요 시간 | ~4m10s | ~10m | 악화 |

---

## 분석

- **처리량 급감**: 커넥션 풀을 30으로 늘리자 더 많은 커넥션이 동시에 MySQL 비관적 락을 경합하게 되어 lock wait가 증가하고 전체 처리량이 78% 급감했다.
- **p90/p95 개선**: 동시 접속 가능한 커넥션이 많아져 커넥션 대기 시간이 줄어 중간 구간 응답 시간은 개선되었다.
- **p99 악화 + timeout**: 동시 락 경합이 심해지면서 일부 요청이 극단적으로 느려졌고, 결국 request timeout이 발생하여 graceful stop이 10분까지 늘어났다.
- **CPU 100%**: 500 VU × 30 커넥션 풀 조합이 MySQL 락 경합 + JVM 스레드 경합을 동시에 유발하여 CPU가 100%를 기록했다.
- **결론**: 비관적 락 기반 시스템에서 커넥션 풀만 늘리는 것은 오히려 역효과. 쿼리 최적화와 인덱스 추가로 락 보유 시간을 줄여야 실질적 개선이 가능하다.
