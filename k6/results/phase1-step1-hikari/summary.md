# Phase 1 Step 1: HikariCP pool=30 성능 측정 결과 (Instant Spike, java -jar)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**실행 방법:** `java -jar phase0-baseline.jar --spring.profiles.active=mysql --spring.datasource.hikari.maximum-pool-size=30 --spring.datasource.hikari.minimum-idle=10`
**변경 사항:** HikariCP maximum-pool-size=30, minimum-idle=10
**베이스:** phase0-baseline (인덱스 없음, OSIV ON, Tomcat threads=200)

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 3,185 |
| http_req_duration p95 | 47ms |
| http_req_duration avg | 26.6ms |
| http_req_duration med | 21.5ms |
| http_req_duration max | 1,578ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 256,196 |
| enroll_capacity_exceeded | 250,434 |
| checks passed | 100% (258,214/258,214) |
| 총 iterations | 258,214 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 169.8ms |
| enroll_duration_burst avg | 46.0ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 39.5ms |
| enroll_duration_sustained avg | 22.0ms |
| enroll_success_sustained | 0 |

## Phase 0 vs Step 1 비교

| 지표 | Phase 0 (pool=10) | Step 1 (pool=30) | 변화 |
|------|-------------------|-------------------|------|
| iterations/s | 2,377 | 3,185 | **+34.0%** |
| p95 (전체) | 128ms | 47ms | **-63.3%** |
| avg (전체) | 70.0ms | 26.6ms | **-62.0%** |
| burst p95 | 236.5ms | 169.8ms | **-28.2%** |
| burst avg | 98.6ms | 46.0ms | **-53.3%** |

## 분석

- **가장 큰 단일 최적화**: pool=10→30 변경만으로 avg 62% 감소, 처리량 34% 증가.
- **커넥션 대기 병목 해소**: 500 VU에서 10개 커넥션은 극심한 대기 발생. 30개로 확대 시 병목 해소.
- **sustained 구간**: avg 22ms로 Phase 0(63ms) 대비 65% 개선. 정원 소진 후에도 커넥션 풀 효과 지속.
