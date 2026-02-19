# Phase 1 Step 2: HikariCP pool=30 + OSIV OFF 성능 측정 결과 (Instant Spike)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**변경 사항:** HikariCP pool=30 + spring.jpa.open-in-view=false (CLI 오버라이드)
**베이스:** phase0-baseline (인덱스 없음, Tomcat threads=200)

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 1,905.1 |
| http_req_duration p95 | 142.0ms |
| http_req_duration avg | 112.2ms |
| http_req_duration med | 117.5ms |
| http_req_duration max | 523.0ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 152,444 |
| enroll_capacity_exceeded | 148,943 |
| checks passed | 100% (154,462/154,462) |
| 총 iterations | 154,462 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 204.6ms |
| enroll_duration_burst avg | 126.4ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 132.5ms |
| enroll_duration_sustained avg | 108.5ms |
| enroll_success_sustained | 0 |

## Step 1 vs Step 2 비교

| 지표 | Step 1 (pool=30) | Step 2 (+ OSIV OFF) | 변화 |
|------|-------------------|----------------------|------|
| iterations/s | 1,905.5 | 1,905.1 | -0.0% |
| p95 (전체) | 143.5ms | 142.0ms | -1.0% |
| avg (전체) | 112.2ms | 112.2ms | 0.0% |
| burst p95 | 205.0ms | 204.6ms | -0.2% |
| burst avg | 124.6ms | 126.4ms | +1.4% |

## 분석

- **OSIV OFF 효과 없음**: Step 1 대비 측정 오차 범위 내 차이. instant spike에서는 OSIV 설정 변경이 성능에 영향을 주지 않음.
- **이유**: 비관적 락 경합이 지배적 병목이므로, 커넥션 보유 시간(OSIV)을 줄여도 락 대기 시간에 변화 없음.
- **이전 점진적 ramp-up에서는 소폭 개선**(+1.4% 처리량)이 있었으나, instant spike에서는 효과가 사라짐.
