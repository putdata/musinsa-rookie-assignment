# Phase 0 Baseline 성능 측정 결과 (Instant Spike, java -jar)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (10s 워밍업 → 1s 500VU 스파이크 → 1m 지속 → 10s 쿨다운)
**실행 방법:** `java -jar phase0-baseline.jar --spring.profiles.active=mysql`
**서버 설정:** HikariCP pool=10, OSIV ON, Tomcat threads=200(기본), 인덱스 없음

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 2,377 |
| http_req_duration p95 | 128ms |
| http_req_duration avg | 70.0ms |
| http_req_duration med | 67.5ms |
| http_req_duration max | 1,158.5ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 190,680 |
| enroll_capacity_exceeded | 186,204 |
| checks passed | 100% (192,698/192,698) |
| 총 iterations | 192,698 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 236.5ms |
| enroll_duration_burst avg | 98.6ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 86.5ms |
| enroll_duration_sustained avg | 63.3ms |
| enroll_success_sustained | 0 |

## 분석

- **서버 안정성**: 500 VU instant spike에서 5xx 에러 0건. checks 100% 통과.
- **정원 소진**: burst 구간에서 2,018건 성공 후 sustained 구간에서는 성공 0건.
- **burst vs sustained**: burst avg(98.6ms) > sustained avg(63.3ms) → 정원 경쟁 구간에서 락 대기로 인한 지연.
- **처리량**: 2,377 req/s, avg 70ms.
