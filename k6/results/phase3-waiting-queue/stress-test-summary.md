# Phase 3 스트레스 테스트: 순차 → 병렬 처리 개선 + 점진적 VU 증가

**측정 일시:** 2026-02-19
**실행 방법:** `java -jar phase3-queue.jar --spring.profiles.active=mysql,redis`
**k6 시나리오:** queue-enrollment-rush (1초 instant spike → 1m 지속 → 5s 쿨다운)
**테스트 환경:** Windows PowerShell (서버 + k6), Docker (MySQL + Redis), 동일 머신

---

## 1차 테스트: 순차 처리 (WORKER_THREADS=1)

`processQueue()`에서 50건을 for-loop 순차 처리하는 기존 방식.

### 결과

| 지표 | 1,000 VU | 2,000 VU | 3,000 VU | 5,000 VU |
|------|----------|----------|----------|----------|
| http_req p95 | 2.49ms | 12.49ms | 4.79ms | 7ms |
| queue_submit p95 | 4.50ms | 386.85ms | 697.38ms | 987.50ms |
| queue_wait avg | 4,196ms | 9,205ms | 13,817ms | 22,200ms |
| queue_wait p95 | 9,004ms | 17,506ms | 24,007ms | 33,007ms |
| iterations/s | 182.9 | 179.7 | 181.0 | 184.8 |
| enroll_success | 2,018 | 2,018 | 2,018 | 2,018 |
| http_req_failed | 0.00% | 0.00% | 0.00% | 0.00% |
| checks_failed | 0.00% | 0.00% | 0.00% | 0.00% |

### 분석
- **queue_wait가 VU에 선형 비례**: VU 1,000당 ~4.5초 증가
- **병목**: for-loop 순차 처리 → 실질 처리량 ~59건/s (이론 500건/s의 12%)
- **안정성**: 5,000 VU까지 에러 0%, 정합성 완벽

---

## 개선: 배치 내 병렬 처리 (WORKER_THREADS=20)

`processQueue()`에서 50건을 `ExecutorService(20 threads)`로 병렬 처리하도록 변경.

### 변경 내용

```java
// Before: 순차 처리
for (item : items) {
    processEnrollment(token);
}

// After: 20스레드 병렬 처리
ExecutorService workerPool = Executors.newFixedThreadPool(20);
for (item : items) {
    futures.add(workerPool.submit(() -> processEnrollment(token)));
}
for (future : futures) {
    future.get(30, TimeUnit.SECONDS);  // 배치 완료 대기
}
```

### 결과

| 지표 | 3,000 VU (병렬) | 5,000 VU (병렬) |
|------|----------------|----------------|
| http_req p95 | 10.49ms | 5ms |
| queue_submit p95 | 569.37ms | 12.00ms |
| queue_wait avg | 5,880ms | 9,872ms |
| queue_wait p95 | 8,004ms | 11,505ms |
| iterations/s | 402.1 | 451.1 |
| enroll_success | **2,018** | **2,018** |
| http_req_failed | 0.00% | 3.66% |
| checks_failed | 0.00% | 11.20% (3,920건) |

### 순차 vs 병렬 비교 (3,000 VU 기준)

| 지표 | 순차 | 병렬 (20스레드) | 변화 |
|------|------|----------------|------|
| **queue_wait avg** | 13,817ms | **5,880ms** | **-57.4%** |
| **queue_wait p95** | 24,007ms | **8,004ms** | **-66.7%** |
| **iterations/s** | 181 | **402** | **+122.1%** |
| **총 iterations** | 13,806 | **28,646** | **+107.5%** |
| enroll_success | 2,018 | 2,018 | 정합성 유지 |
| http_req_failed | 0.00% | 0.00% | 안정 |

---

## 5,000 VU에서의 submit 실패 분석

5,000 VU에서 checks_failed **11.20% (3,920건)** 발생.

### 원인

| 근거 | 설명 |
|------|------|
| `http_req_duration min=0s` | 서버 도달 전 연결 수립 자체가 실패 |
| 서버 로그에 에러 없음 | 애플리케이션은 정상 처리 |
| enroll_success = 2,018 | 도달한 요청은 100% 정확히 처리 |

**결론:** 단일 머신(Windows)에서 5,000개 TCP 연결이 1초에 몰리면서 OS 수준의 TCP 연결 한계에 도달.
서버 + k6 + Docker(MySQL, Redis)가 동일 머신에서 실행되어 ephemeral port, 소켓 버퍼 등이 포화.
**애플리케이션 문제가 아닌 테스트 환경의 물리적 한계.**

### Tomcat 튜닝 효과 (5,000 VU)

| 지표 | threads=1000 | threads=5000 | 변화 |
|------|-------------|-------------|------|
| queue_submit p95 | 248.50ms | **12.00ms** | **-95.2%** |
| enroll_success | 2,017 | **2,018** | 정합성 복구 |
| checks_failed | 9.84% | 11.20% | 환경 한계로 미해결 |

Tomcat 쓰레드 확대로 submit 응답 속도는 95% 개선되었으나, TCP 연결 실패는 해소되지 않음.

---

## 전체 개선 효과 요약

### 병렬 처리가 해결한 것
- **대기 시간 57% 감소** (3,000 VU: 13.8s → 5.9s)
- **처리량 2배 증가** (181 → 402 iter/s)
- for-loop 순차 처리의 비효율을 ExecutorService 20스레드로 해소
- DB 커넥션 풀(30개)을 실제로 활용하게 됨

### Tomcat 튜닝이 해결한 것
- **submit 응답 속도 95% 개선** (5,000 VU p95: 248ms → 12ms)
- Phase 3의 submit/polling은 Redis만 사용하므로 쓰레드 확대가 순수 이점
- (Phase 1에서는 DB 커넥션 경합으로 Tomcat 확대가 역효과였음)

### 남은 한계
| 병목 | 현재 상태 | 해결 방안 |
|------|----------|----------|
| 단일 머신 TCP 한계 | 5,000 VU에서 11% 연결 실패 | 서버/k6 분리, 별도 머신 테스트 |
| 대기열 처리 속도 | ~500건/s (50건 × 20스레드 병렬) | BATCH_SIZE 확대, fixedDelay 감소 |
| Redis ZADD 경합 | 5,000 VU에서 max 1.1s | Redis Pipeline, Lua Script 배치 |

---

## 안정 동작 범위

| VU | 정합성 | 에러율 | 판정 |
|----|--------|--------|------|
| 1,000 | 2,018 정확 | 0.00% | **안정** |
| 2,000 | 2,018 정확 | 0.00% | **안정** |
| 3,000 | 2,018 정확 | 0.00~0.16% | **안정** |
| 5,000 | 2,018 정확 | 3.66% (환경 한계) | **앱 안정, 환경 한계** |

**결론:** Phase 3 대기열 시스템은 병렬 처리 개선 후 **3,000 VU까지 무장애 안정 동작**하며, 5,000 VU에서도 애플리케이션 자체는 정상이나 단일 머신 테스트 환경의 TCP 한계로 일부 연결 실패가 발생한다.
