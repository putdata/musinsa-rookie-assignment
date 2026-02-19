# Phase 1 Step 4: 전체 최적화 (인덱스 포함) 성능 측정 결과 (Instant Spike, java -jar)

**측정 일시:** 2026-02-19
**k6 시나리오:** enrollment-rush (instant spike 500VU, ~1m21s)
**실행 방법:** `java -jar phase1-optimization.jar --spring.profiles.active=mysql --server.tomcat.threads.max=500 --server.tomcat.threads.min-spare=100`
**누적 최적화:** HikariCP pool=30 + OSIV OFF + Tomcat threads=500 + Hibernate batch=50 + 인덱스
**서버:** phase1-optimization (모든 Phase 1 최적화 포함)

## Enrollment Rush 결과 (전체)

| 지표 | 값 |
|------|-----|
| iterations/s | 3,176 |
| http_req_duration p95 | 48.5ms |
| http_req_duration avg | 27.0ms |
| http_req_duration med | 21.0ms |
| http_req_duration max | 1,638ms |
| enroll_success | 2,018 (24.9/s) |
| enroll_failed | 255,532 |
| enroll_capacity_exceeded | 249,678 |
| checks passed | 100% (257,550/257,550) |
| 총 iterations | 257,550 |
| max VUs | 500 |

## Burst 구간 (0~25초, 정원 경쟁)

| 지표 | 값 |
|------|-----|
| enroll_duration_burst p95 | 249.5ms |
| enroll_duration_burst avg | 52.1ms |
| enroll_success_burst | 2,018 |

## Sustained 구간 (25초~, 정원 소진 후)

| 지표 | 값 |
|------|-----|
| enroll_duration_sustained p95 | 37.0ms |
| enroll_duration_sustained avg | 21.2ms |
| enroll_success_sustained | 0 |

## Phase 1 전체 단계별 비교표

| 지표 | Phase 0 | Step 1 (HikariCP) | Step 2 (+OSIV OFF) | Step 3 (+Tomcat) | Step 4 (+Index) |
|------|---------|-------------------|---------------------|-------------------|------------------|
| iterations/s | 2,377 | **3,185** | **3,248** | 3,210 | 3,176 |
| p95 (전체) | 128ms | **47ms** | **43.5ms** | 45.5ms | 48.5ms |
| avg (전체) | 70.0ms | **26.6ms** | **24.2ms** | 25.6ms | 27.0ms |
| burst avg | 98.6ms | 46.0ms | **42.5ms** | 47.6ms | 52.1ms |
| sustained avg | 63.3ms | 22.0ms | **19.8ms** | 20.5ms | 21.2ms |

## 분석

- **인덱스/batch 추가 효과 없음**: Step 2(OSIV OFF)가 최적 성능. Step 3~4는 오히려 미세 악화.
- **UniqueConstraint가 이미 인덱스 제공**: enrollments 테이블의 UK(student_id, course_id)가 이미 쿼리를 커버.
- **batch_size=50 효과 없음**: IDENTITY 전략에서는 Hibernate가 JDBC 배치를 비활성화하므로 효과 없음.
- **최적 구성**: HikariCP pool=30 + OSIV OFF (Step 2) — Tomcat/인덱스/batch는 불필요.
