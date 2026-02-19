# Phase 1 Step 3: Tomcat 쓰레드풀 확대 성능 측정 결과 (Instant Spike)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**누적 변경:** HikariCP pool=30 + OSIV OFF + Tomcat threads.max=500, min-spare=100 (CLI 오버라이드)
**베이스:** phase0-baseline (인덱스 없음)
**참고:** Spring Boot 기본 Tomcat threads.max=200 → 500으로 확대

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 1,923.0 |
| http_req_duration p95 | 141.0ms |
| http_req_duration avg | 110.3ms |
| http_req_duration med | 114.5ms |
| http_req_duration max | 481.0ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 153,830 |
| enroll_capacity_exceeded | 150,235 |
| checks passed | 100% (155,848/155,848) |
| 총 iterations | 155,848 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 202.0ms |
| enroll_duration_burst avg | 123.4ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 131.5ms |
| enroll_duration_sustained avg | 106.8ms |
| enroll_success_sustained | 0 |

## Step 2 vs Step 3 비교

| 지표 | Step 2 (threads=200) | Step 3 (threads=500) | 변화 |
|------|----------------------|----------------------|------|
| iterations/s | 1,905.1 | 1,923.0 | +0.9% |
| p95 (전체) | 142.0ms | 141.0ms | -0.7% |
| avg (전체) | 112.2ms | 110.3ms | -1.7% |
| burst p95 | 204.6ms | 202.0ms | -1.3% |
| burst avg | 126.4ms | 123.4ms | -2.4% |

## 분석

- **소폭 개선**: Tomcat 쓰레드 200→500 확대로 전체 avg 1.7% 감소, burst avg 2.4% 감소. 유의미하지만 큰 폭은 아님.
- **Tomcat 큐잉 병목은 제한적**: 200 쓰레드에서도 대부분의 요청이 빠르게 처리되고 있었음. 쓰레드 포화보다 DB 락 경합이 주요 병목.
- **처리량**: 1,923 req/s로 Phase 0(1,910.7) 대비 소폭 증가. 전 단계 누적 효과.
