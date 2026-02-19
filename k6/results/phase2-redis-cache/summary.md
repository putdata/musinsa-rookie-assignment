# Phase 2: Redis 원자 연산 + 캐싱 성능 측정 결과 (Instant Spike, java -jar)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**실행 방법:** `java -jar phase2-redis.jar --spring.profiles.active=mysql,redis --server.tomcat.threads.max=500 --server.tomcat.threads.min-spare=100`
**누적 최적화:** Phase 1 전체 + Redis INCR/DECR 원자 연산 + 강좌 캐싱
**프로파일:** mysql,redis

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 3,788 |
| http_req_duration p95 | 10.5ms |
| http_req_duration avg | 6.2ms |
| http_req_duration med | 3.0ms |
| http_req_duration max | 1,316ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 305,197 |
| enroll_capacity_exceeded | 305,172 |
| checks passed | 100% (307,215/307,215) |
| 총 iterations | 307,215 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 43.5ms |
| enroll_duration_burst avg | 15.7ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 9.0ms |
| enroll_duration_sustained avg | 3.7ms |
| enroll_success_sustained | 0 |

## Phase 1 Step 2 (최적) vs Phase 2 비교

| 지표 | Phase 1 Step 2 | Phase 2 (Redis) | 변화 |
|------|---------------|-----------------|------|
| iterations/s | 3,248 | 3,788 | +16.6% |
| p95 (전체) | 43.5ms | 10.5ms | **-75.9%** |
| avg (전체) | 24.2ms | 6.2ms | **-74.4%** |
| burst avg | 42.5ms | 15.7ms | **-63.1%** |
| sustained avg | 19.8ms | 3.7ms | **-81.3%** |

## 분석

- **Redis 효과 극대화**: 정원 소진 후 Redis INCR로 즉시 거절 → DB 접근 없이 3.7ms avg 응답.
- **burst 구간에서도 개선**: burst avg 42.5ms → 15.7ms. Redis 원자 연산이 DB 락 경합을 대폭 감소.
- **med 3.0ms**: 대부분의 요청이 Redis에서 즉시 처리됨 (정원 초과 거절).
