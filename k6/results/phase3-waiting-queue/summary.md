# Phase 3: Redis Sorted Set 수강신청 대기열 시스템 성능 측정 결과 (Instant Spike, java -jar)

**측정 일시:** 2026-02-19
**실행 방법:** `java -jar phase3-queue.jar --spring.profiles.active=mysql,redis`
**변경 사항:** Redis Sorted Set 기반 수강신청 대기열 시스템 (서버 자동 처리, BATCH_SIZE=50, 순번 기반 폴링)
**누적 최적화:** HikariCP pool=30 + OSIV OFF + Hibernate batch + 인덱스 + Redis 원자 연산/캐싱 + 대기열 + Tomcat 1000스레드
**프로파일:** mysql,redis
**테스트 시나리오:** queue-enrollment-rush (1 VU = 1 학생, 1초만에 1000명 instant spike, 1m 지속, 5s 쿨다운, 총 ~1m6s)

---

## 대기열 동작 방식

```
학생(클라이언트)                         서버
  │                                      │
  ├─ POST /api/queue/enroll ──────────►  │ Redis Sorted Set에 요청 적재
  │  {studentId, courseId}               │ → token + 대기순번 반환
  │                                      │
  │                                      │ @Scheduled(fixedDelay=100ms)
  │                                      │ ZPOPMIN으로 50건씩 꺼내서
  │                                      │ EnrollmentService.enroll() 자동 호출
  │                                      │ → 결과를 Redis Hash에 저장
  │                                      │
  ├─ GET /api/queue/result/{token} ──►   │ 결과 조회
  │  (순번 기반 폴링 간격)               │ → WAITING / SUCCESS / FAILED
  │                                      │
  │  결과 확인 후 1초 대기               │
  │                                      │
  ├─ POST /api/queue/enroll ──────────►  │ 다음 강좌 수강신청
  │  {studentId, 다른 courseId}          │
  └──────────────────────────────────    └─
```

### 순번 기반 폴링 간격 (서버 부하 제어)
| 대기 순번 | 폴링 간격 | 이유 |
|-----------|-----------|------|
| 1~100 | 0.5초 | 곧 처리될 순번 |
| 101~500 | 1.5초 | 중간 순번 |
| 501~1500 | 3초 | 뒤쪽 순번 |
| 1500+ | 5초 | 한참 뒤 순번 |

---

## Queue Enrollment Rush 결과 (1초만에 1000명 instant spike, ~1m8s)

| 지표 | 값 |
|------|-----|
| iterations/s | 182.9 |
| http_reqs/s | 540.0 |
| http_req_duration avg | 5.01ms |
| http_req_duration p90 | 2.0ms |
| http_req_duration p95 | **2.49ms** |
| http_req_duration med | 0.71ms |
| http_req_duration max | 585.5ms |
| queue_submit_duration avg | 13.55ms |
| queue_submit_duration p95 | 4.50ms |
| queue_wait_duration avg | 4,196ms |
| queue_wait_duration p95 | 9,004ms |
| **enroll_success** | **2,018건** |
| **enroll_failed** | **10,434건** |
| enroll_success + enroll_failed | **12,452건** (= 총 iterations) |
| http_req_failed rate | **0.00%** |
| checks passed | 100% (12,452/12,452) |
| iteration_duration avg | 5.2s |
| iteration_duration p95 | 10s |
| 총 iterations | 12,452 |
| 총 http_reqs | 36,765 |
| interrupted iterations | **0건** (전원 정상 완료) |
| max VUs | **1,000** |

---

## Phase 0~3 전체 비교표

| 지표 | Phase 0 | Phase 1 (Step 2) | Phase 2 (Redis) | Phase 3 (대기열) |
|------|---------|-------------------|-----------------|-----------------|
| http_req_duration p95 | 128ms | 43.5ms | 10.5ms | **2.49ms** |
| http_req_duration avg | 70.0ms | 24.2ms | 6.2ms | **5.01ms** |
| enroll_success | 2,018 | 2,018 | 2,018 | **2,018** |
| 총 수강신청 시도 | 192,698 | 263,242 | 307,215 | **12,452** |
| 불필요한 실패 요청 | 190,680 | 261,224 | 305,197 | **10,434** |
| 5xx 에러 | 0 | 0 | 0 | **0** |
| checks passed | 100% | 100% | 100% | **100%** |
| max VUs | 500 | 500 | 500 | **1,000** |

> **비교 시 유의사항:**
> - Phase 0~2: enrollment-rush 시나리오 (500 VU, instant spike, 직접 수강신청). 즉시 응답 → 즉시 재시도.
> - Phase 3: queue-enrollment-rush 시나리오 (1000 VU, instant spike, 대기열). 1 VU = 1 학생 고정. 신청 → 서버 자동 처리 → 결과 폴링 → 다음 강좌.
> - 모든 Phase는 `java -jar`로 실행하여 측정.

---

## 핵심 변화: 대기열 도입의 패러다임 전환

### 1. 사용자 경험의 변화
| 항목 | Phase 0~2 (직접 수강신청) | Phase 3 (대기열) |
|------|--------------------------|-----------------|
| 수강신청 방식 | 버튼 클릭 → 즉시 성공/실패 | 버튼 클릭 → 대기 → 자동 처리 → 결과 통보 |
| 정원 찬 강좌 | 즉시 "정원 초과" 에러 | 대기열에서 처리 후 "정원 초과" 결과 |
| 재시도 패턴 | 미친듯이 버튼 연타 | 결과 받고 다른 강좌 신청 |
| 학생당 평균 시도 | 수백~수천 회 (같은 강좌 반복) | **~1.6회** (서로 다른 강좌 순차) |
| 동시 접속 한계 | 500 VU | **1,000 VU** (2배) |

### 2. 서버 부하의 극적 감소
- **총 수강신청 시도**: 192,698건 → **12,452건** (**-93.5%**)
- **불필요한 실패 요청**: 190,680건 → **10,434건** (**-94.5%**)
- 순번 기반 폴링 간격이 뒤쪽 학생의 불필요한 요청을 억제

### 3. HTTP 에러율
- **Phase 0~2**: 대부분의 요청이 409 정원 초과
- **Phase 3**: http_req_failed **0.00%**
- 수강신청 결과가 HTTP 에러가 아닌 결과 데이터(SUCCESS/FAILED)로 전달

### 4. 사용자 체감 대기 시간
- **queue_wait_duration avg**: 4,196ms (신청 후 결과까지 평균 ~4초)
- **queue_wait_duration p95**: 9,004ms (최대 ~9초)
- 1,000명이 1초만에 몰리는 극한 상황에서도 대부분 10초 이내 결과 확인

### 5. 데이터 정합성
- **enroll_success**: 2,018건 (모든 Phase에서 동일)
- 인기 강좌(1~50번) 정원 합계와 정확히 일치
- 대기열 순차 처리에서도 동시성 제어 정확히 동작

---

## Phase 0→3 전체 개선 요약

| 개선 항목 | Phase 0 → Phase 3 | 개선율 |
|-----------|-------------------|--------|
| HTTP 응답시간 p95 | 128ms → 2.49ms | **-98.1%** |
| 총 수강신청 시도 | 192,698건 → 12,452건 | **-93.5%** |
| 불필요한 실패 요청 | 190,680건 → 10,434건 | **-94.5%** |
| 동시 접속 한계 | 500 VU → 1,000 VU | **2배 증가** |
| 서버 에러 | 0건 → 0건 | 안정적 유지 |
| 수강 성공 | 2,018건 → 2,018건 | 정합성 유지 |

### 각 Phase별 핵심 기여
1. **Phase 1** (HikariCP pool=30 + OSIV OFF): 커넥션 관리 최적화 → p95 -66.0%
2. **Phase 2** (Redis 원자 연산): DB 락 제거 → p95 -75.9% (Phase 0 대비 -91.8%)
3. **Phase 3** (대기열): 서버 자동 처리 + 순번 기반 폴링 → p95 -76.3%, HTTP 에러율 0%, 동시 접속 2배, 불필요한 트래픽 94.5% 감소
