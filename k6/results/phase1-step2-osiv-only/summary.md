# Phase 1 Step 2: HikariCP pool=30 + OSIV OFF 성능 측정 결과 (Instant Spike, java -jar)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**실행 방법:** `java -jar phase0-baseline.jar --spring.profiles.active=mysql --spring.datasource.hikari.maximum-pool-size=30 --spring.datasource.hikari.minimum-idle=10 --spring.jpa.open-in-view=false`
**변경 사항:** HikariCP pool=30 + spring.jpa.open-in-view=false
**베이스:** phase0-baseline (인덱스 없음, Tomcat threads=200)

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 3,248 |
| http_req_duration p95 | 43.5ms |
| http_req_duration avg | 24.2ms |
| http_req_duration med | 19.5ms |
| http_req_duration max | 1,074.5ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 261,224 |
| enroll_capacity_exceeded | 255,249 |
| checks passed | 100% (263,242/263,242) |
| 총 iterations | 263,242 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 164.0ms |
| enroll_duration_burst avg | 42.5ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 34.5ms |
| enroll_duration_sustained avg | 19.8ms |
| enroll_success_sustained | 0 |

## Step 1 vs Step 2 비교

| 지표 | Step 1 (pool=30) | Step 2 (+ OSIV OFF) | 변화 |
|------|-------------------|----------------------|------|
| iterations/s | 3,185 | 3,248 | +2.0% |
| p95 (전체) | 47ms | 43.5ms | -7.4% |
| avg (전체) | 26.6ms | 24.2ms | **-9.0%** |
| burst p95 | 169.8ms | 164.0ms | -3.4% |
| burst avg | 46.0ms | 42.5ms | -7.6% |

## 분석

- **OSIV OFF 효과 확인**: avg 9% 추가 감소. 트랜잭션 종료 시 즉시 커넥션 반환 → 풀 활용률 향상.
- **sustained avg 19.8ms**: Step 1(22.0ms) 대비 10% 개선. 커넥션 보유 시간 단축 효과.
