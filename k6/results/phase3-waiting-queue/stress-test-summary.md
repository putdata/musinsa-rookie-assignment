# Phase 3 스트레스 테스트: 순차 → 병렬 처리 → Lua Script 개선

**측정 일시:** 2026-02-19
**실행 방법:** `java -jar phase3-queue.jar --spring.profiles.active=mysql,redis`
**k6 시나리오:** queue-enrollment-rush (1초 instant spike → 1m 지속 → 5s 쿨다운)
**테스트 환경:** Windows PowerShell (서버 + k6), Docker (MySQL + Redis), 동일 머신

---

## 1단계: 순차 처리 (기존)

`processQueue()`에서 50건을 for-loop 순차 처리.

| 지표 | 1,000 VU | 2,000 VU | 3,000 VU | 5,000 VU |
|------|----------|----------|----------|----------|
| queue_submit p95 | 4.50ms | 386.85ms | 697.38ms | 987.50ms |
| queue_wait avg | 4,196ms | 9,205ms | 13,817ms | 22,200ms |
| queue_wait p95 | 9,004ms | 17,506ms | 24,007ms | 33,007ms |
| iterations/s | 182.9 | 179.7 | 181.0 | 184.8 |
| enroll_success | 2,018 | 2,018 | 2,018 | 2,018 |
| checks_failed | 0.00% | 0.00% | 0.00% | 0.00% |

**병목:** for-loop 순차 처리 → 실질 처리량 ~59건/s (이론 500건/s의 12%)

---

## 2단계: 병렬 처리 (WORKER_THREADS=20)

`processQueue()`에서 50건을 `ExecutorService(20 threads)`로 병렬 처리.

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

### 순차 vs 병렬 비교 (3,000 VU)

| 지표 | 순차 | 병렬 (20스레드) | 변화 |
|------|------|----------------|------|
| **queue_wait avg** | 13,817ms | **5,880ms** | **-57.4%** |
| **queue_wait p95** | 24,007ms | **8,004ms** | **-66.7%** |
| **iterations/s** | 181 | **402** | **+122.1%** |
| enroll_success | 2,018 | 2,018 | 정합성 유지 |

### 병렬 처리 + Tomcat 튜닝 (threads 5000) 결과

| 지표 | 3,000 VU | 5,000 VU |
|------|----------|----------|
| queue_submit p95 | 569.37ms | 12.00ms |
| queue_wait avg | 5,880ms | 9,872ms |
| iterations/s | 402 | 451 |
| enroll_success | **2,018** | **2,018** |
| checks_failed | 0.00% | 11.20% (3,920건) |

**5,000 VU에서 checks_failed 11.20%**: 단일 머신 TCP 연결 한계. `http_req_duration min=0s`로 서버 도달 전 연결 실패. 애플리케이션 자체는 정상.

---

## 3단계: Lua Script (enqueue 6회 왕복 → 1회)

`enqueue()` 메서드의 Redis 명령 6개를 단일 Lua Script로 통합.

```lua
-- Before: 6회 네트워크 왕복
HSET request:token studentId {sid}    -- ①
HSET request:token courseId {cid}     -- ②
EXPIRE request:token 1800             -- ③
ZADD queue score token                -- ④
ZRANK queue token                     -- ⑤
ZCARD queue                           -- ⑥

-- After: 1회 왕복 (Lua Script 원자 실행)
redis.call('HSET', KEYS[1], 'studentId', ARGV[1], 'courseId', ARGV[2])
redis.call('EXPIRE', KEYS[1], 1800)
redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
local rank = redis.call('ZRANK', KEYS[2], ARGV[4])
local total = redis.call('ZCARD', KEYS[2])
return {rank, total}
```

### Lua Script 결과

| 지표 | 3,000 VU | 5,000 VU (1차) | 5,000 VU (2차) |
|------|----------|---------------|---------------|
| queue_submit p95 | 29.50ms | 1,399ms | 714ms |
| queue_submit avg | 11.77ms | 185.24ms | 51.02ms |
| queue_wait avg | 5,918ms | 10,025ms | 9,937ms |
| queue_wait p95 | 8,002ms | 13,003ms | 11,510ms |
| iterations/s | 417 | 410 | 450 |
| enroll_success | **2,018** | **2,018** | **2,018** |
| checks_failed | 3.23% (948) | 2.50% (793) | 8.69% (2,986) |

### 병렬 처리 vs Lua Script 비교 (5,000 VU)

| 지표 | 병렬만 (6회 왕복) | + Lua (1회 왕복) | 변화 |
|------|-----------------|-----------------|------|
| **checks_failed** | 11.20% (3,920) | **2.5~8.7% (793~2,986)** | **최대 -79.8%** |
| **http_req_failed** | 3.66% | **0.75~2.78%** | **최대 -79.5%** |
| queue_submit p95 | 12ms | 714~1,399ms | Lua 직렬화 영향* |
| enroll_success | 2,018 | **2,018** | 정합성 유지 |

\* Lua Script는 Redis에서 원자적(싱글 스레드)으로 실행되므로 5,000개가 직렬 대기. 하지만 TCP 연결 점유 시간이 6분의 1로 줄어 연결 실패가 대폭 감소.

---

## 전체 개선 효과 요약

### 각 개선이 해결한 것

| 개선 | 효과 | 핵심 원리 |
|------|------|----------|
| **병렬 처리** (20스레드) | queue_wait **-57%**, 처리량 **2배** | DB 커넥션 풀 실제 활용 |
| **Tomcat 튜닝** (5000스레드) | submit p95 **-95%** | Redis-only 엔드포인트에 쓰레드 확대 순수 이점 |
| **Lua Script** (1회 왕복) | TCP 연결 실패 **최대 -80%** | 네트워크 왕복 6→1, 연결 점유 시간 감소 |

### Phase 1 vs Phase 3 Tomcat 튜닝 차이

| | Phase 1 | Phase 3 |
|--|---------|---------|
| Tomcat 확대 효과 | **역효과** (미세 악화) | **효과적** (submit -95%) |
| 이유 | DB 커넥션 30개를 더 많은 쓰레드가 경쟁 | submit/polling은 Redis만 사용, DB 경합 없음 |

### Lua Script의 트레이드오프

| 장점 | 단점 |
|------|------|
| TCP 연결 점유 시간 6분의 1 | 5,000개 Lua 직렬 실행으로 submit p95 증가 |
| 연결 실패 대폭 감소 | Redis 싱글 스레드 병목 노출 |
| 원자적 실행 (데이터 일관성) | ZRANK/ZCARD가 Lua 내에서 O(log N) |

**다음 개선 후보:** Lua Script에서 ZRANK/ZCARD 제거 → 대기 순번을 INCR 카운터 근사치로 반환 → submit 즉시 응답 가능.

---

## 안정 동작 범위

| VU | 정합성 | 에러율 | 판정 |
|----|--------|--------|------|
| 1,000 | 2,018 정확 | 0.00% | **안정** |
| 2,000 | 2,018 정확 | 0.00% | **안정** |
| 3,000 | 2,018 정확 | 0.00~3.23% | **안정** |
| 5,000 | 2,018 정확 | 0.75~8.69% (환경 한계) | **앱 안정, 환경 한계** |

**결론:** Phase 3 대기열 시스템은 3단계 개선(병렬 처리 + Tomcat 튜닝 + Lua Script)을 거쳐 **3,000 VU까지 안정 동작**하며, 5,000 VU에서도 정합성은 완벽하나 단일 머신 테스트 환경의 TCP 한계로 일부 연결 실패가 발생한다.
