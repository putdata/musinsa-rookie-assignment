# Phase 2: Redis 원자 연산 기반 수강 인원 관리 + 캐싱 성능 측정 결과

**측정 일시:** 2026-02-19
**변경 사항:** Redis 원자 연산(INCR/DECR) 기반 수강 인원 관리 + 강좌 정보 캐싱
**누적 최적화:** HikariCP pool=30 + OSIV OFF + Hibernate batch + 인덱스 + Redis 원자 연산/캐싱
**프로파일:** mysql,redis

---

## Enrollment Rush 결과 (100→500 VU, 4m10s)

| 지표 | 값 |
|------|-----|
| iterations/s | 2,224.9 |
| http_req_duration p90 | 7.02ms |
| http_req_duration p95 | 9.66ms |
| http_req_duration p99 | 17.54ms |
| http_req_duration avg | 4.31ms |
| enroll_success | 2,018건 (8.71/s) |
| enroll_failed | 513,196건 |
| enroll_capacity_exceeded | 513,179건 |
| http_req_failed rate | 99.60% (대부분 정원 초과 409) |
| checks passed | 100% (no server error, 5xx 0건) |
| 총 iterations | 515,214 |
| max VUs | 500 |

---

## Mixed Workload 결과 (100 VU, 4분)

| 지표 | 값 |
|------|-----|
| iterations/s | 175.4 |
| http_req_duration p90 | 17.3ms |
| http_req_duration p95 | 20.03ms |
| http_req_duration p99 | 25.17ms |
| http_req_duration avg | 7.27ms |
| http_req_failed rate | 6.20% (수강신청 비즈니스 에러) |
| checks passed | 100% (no server error) |
| 총 iterations | 41,395 |
| max VUs | 100 |

---

## Phase 0~2 비교표 (Enrollment Rush 기준)

| 지표 | Phase 0 (baseline) | Phase 1 (최적화) | Phase 2 (Redis) | Phase 0→2 개선율 |
|------|-------------------|-----------------|----------------|-----------------|
| iterations/s | 1,310 | 1,477.6 | **2,224.9** | +69.8% |
| p90 | 198.53ms | 171.1ms | **7.02ms** | -96.5% |
| p95 | 259.01ms | 204.99ms | **9.66ms** | -96.3% |
| p99 | 366.29ms | - | **17.54ms** | -95.2% |
| avg | 79.32ms | 57.44ms | **4.31ms** | -94.6% |
| 총 iterations | 298,796 | 340,827 | **515,214** | +72.4% |
| enroll_success | 2,018 | 2,018 | 2,018 | 동일 (정합성 유지) |
| 5xx 에러 | 0 | 0 | 0 | 안정적 |

---

## 분석

### Redis 원자 연산의 극적인 성능 개선
- **처리량**: 1,310 → 2,224.9 iter/s (Phase 0 대비 **+69.8%**)
- **p95 응답시간**: 259.01ms → 9.66ms (Phase 0 대비 **-96.3%**)
- **평균 응답시간**: 79.32ms → 4.31ms (Phase 0 대비 **-94.6%**)

### 핵심 개선 원인
1. **MySQL 비관적 락 제거**: 수강 인원 관리를 Redis INCR/DECR 원자 연산으로 전환하여 DB 행 수준 락 대기 제거
2. **강좌 정보 캐싱**: 강좌 조회 시 Redis 캐시 히트로 DB 부하 감소
3. **DB 커넥션 경합 감소**: Redis로 읽기/카운트 연산을 오프로드하여 MySQL 커넥션 풀 압박 완화

### 정합성 확인
- enroll_success가 모든 Phase에서 **정확히 2,018건**으로 동일
- 인기 강좌(1~50번) × 정원(30~50명) 조합의 정확한 정원 제어 유지
- Redis 원자 연산의 동시성 제어가 MySQL 비관적 락과 동등한 정합성 보장

### Mixed Workload 인사이트
- 40% 조회 + 40% 수강신청 + 15% 시간표 + 5% 취소 혼합 부하에서 p95 20.03ms
- http_req_failed 6.20%는 수강신청의 비즈니스 에러(정원 초과/중복 등)이며, 5xx 서버 에러는 0건
