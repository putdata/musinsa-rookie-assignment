# Phase 0 Baseline 성능 측정 결과 (Instant Spike)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (10s 워밍업 → 1s 500VU 스파이크 → 1m 지속 → 10s 쿨다운)
**서버 설정:** phase0-baseline, HikariCP pool=10, OSIV ON, Tomcat threads=200(기본), 인덱스 없음

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 1,910.7 |
| http_req_duration p95 | 144.5ms |
| http_req_duration avg | 111.6ms |
| http_req_duration med | 116.5ms |
| http_req_duration max | 502.9ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 152,867 |
| enroll_capacity_exceeded | 149,398 |
| checks passed | 100% (154,885/154,885) |
| 총 iterations | 154,885 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 204.0ms |
| enroll_duration_burst avg | 124.1ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 137.0ms |
| enroll_duration_sustained avg | 108.3ms |
| enroll_success_sustained | 0 |

## 분석

- **서버 안정성**: 500 VU instant spike에서도 5xx 에러 0건. checks 100% 통과.
- **정원 소진**: burst 구간에서 2,018건 성공 후 sustained 구간에서는 성공 0건 → 정원이 burst 구간 내에 모두 소진됨.
- **burst vs sustained 응답시간**: burst p95(204ms) > sustained p95(137ms) → 정원 경쟁 구간에서 락 대기로 인한 지연 확인.
- **처리량**: 1,910.7 req/s로 기존 점진적 ramp-up(1,310 req/s) 대비 높지만, 시나리오 구조 차이(총 시간 단축)에 기인.
