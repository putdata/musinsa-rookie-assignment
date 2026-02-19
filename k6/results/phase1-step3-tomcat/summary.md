# Phase 1 Step 3: Tomcat 쓰레드풀 확대 성능 측정 결과 (Instant Spike, java -jar)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**실행 방법:** `java -jar phase0-baseline.jar --spring.profiles.active=mysql --spring.datasource.hikari.maximum-pool-size=30 --spring.datasource.hikari.minimum-idle=10 --spring.jpa.open-in-view=false --server.tomcat.threads.max=500 --server.tomcat.threads.min-spare=100`
**누적 변경:** HikariCP pool=30 + OSIV OFF + Tomcat threads.max=500, min-spare=100
**베이스:** phase0-baseline (인덱스 없음)

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 3,210 |
| http_req_duration p95 | 45.5ms |
| http_req_duration avg | 25.6ms |
| http_req_duration med | 20.0ms |
| http_req_duration max | 1,377ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 258,319 |
| enroll_capacity_exceeded | 252,486 |
| checks passed | 100% (260,337/260,337) |
| 총 iterations | 260,337 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 212.0ms |
| enroll_duration_burst avg | 47.6ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 36.5ms |
| enroll_duration_sustained avg | 20.5ms |
| enroll_success_sustained | 0 |

## Step 2 vs Step 3 비교

| 지표 | Step 2 (threads=200) | Step 3 (threads=500) | 변화 |
|------|----------------------|----------------------|------|
| iterations/s | 3,248 | 3,210 | -1.2% |
| p95 (전체) | 43.5ms | 45.5ms | +4.6% |
| avg (전체) | 24.2ms | 25.6ms | +5.8% |
| burst p95 | 164.0ms | 212.0ms | +29.3% |
| burst avg | 42.5ms | 47.6ms | +12.0% |

## 분석

- **효과 없음 (오히려 미세 악화)**: Tomcat 쓰레드 200→500 확대가 성능 개선에 기여하지 않음.
- **원인**: HikariCP pool=30이 병목이므로, Tomcat 쓰레드를 500으로 늘려도 30개 커넥션을 두고 경쟁하는 쓰레드만 증가.
- **컨텍스트 스위칭 오버헤드**: 500개 쓰레드 간 컨텍스트 스위칭이 오히려 burst 구간 성능을 악화시킴.
- **결론**: 커넥션 풀보다 큰 쓰레드풀은 불필요. threads=200이 pool=30에 더 적합.
