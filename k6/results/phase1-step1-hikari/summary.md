# Phase 1 Step 1: HikariCP pool=30 성능 측정 결과 (Instant Spike)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**변경 사항:** HikariCP maximum-pool-size=30, minimum-idle=10 (CLI 오버라이드)
**베이스:** phase0-baseline (인덱스 없음, OSIV ON, Tomcat threads=200)

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 1,905.5 |
| http_req_duration p95 | 143.5ms |
| http_req_duration avg | 112.2ms |
| http_req_duration med | 116.5ms |
| http_req_duration max | 487.0ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 152,471 |
| enroll_capacity_exceeded | 149,017 |
| checks passed | 100% (154,489/154,489) |
| 총 iterations | 154,489 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 205.0ms |
| enroll_duration_burst avg | 124.6ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 137.5ms |
| enroll_duration_sustained avg | 108.9ms |
| enroll_success_sustained | 0 |

## Phase 0 vs Step 1 비교

| 지표 | Phase 0 (pool=10) | Step 1 (pool=30) | 변화 |
|------|-------------------|-------------------|------|
| iterations/s | 1,910.7 | 1,905.5 | -0.3% |
| p95 (전체) | 144.5ms | 143.5ms | -0.7% |
| avg (전체) | 111.6ms | 112.2ms | +0.5% |
| burst p95 | 204.0ms | 205.0ms | +0.5% |
| burst avg | 124.1ms | 124.6ms | +0.4% |

## 분석

- **거의 차이 없음**: pool=10→30 변경이 instant spike 시나리오에서는 측정 오차 범위 내의 차이만 보임.
- **이전 점진적 ramp-up에서는 35% 개선**이 있었으나, instant spike에서는 병목 지점이 커넥션 풀이 아닌 비관적 락 경합으로 이동.
- **해석**: 500VU가 동시에 몰릴 때 DB 커넥션을 얻어도 행 수준 락 대기가 지배적이므로 풀 크기 확대 효과가 미미함.
