# k6 Instant Spike 전체 Phase 비교표

**측정 일시:** 2026-02-19
**실행 방법:** java -jar (Gradle bootRun이 아닌 직접 JAR 실행)
**k6 시나리오:**
- Phase 0~2: enrollment-rush (1s 500VU 스파이크 → 1m 지속 → 10s 쿨다운, 총 ~1m21s)
- Phase 3: queue-enrollment-rush (1s 1000VU 스파이크 → 1m 지속 → 5s 쿨다운, 총 ~1m6s)

## 전체 비교

| 지표 | Phase 0 | P1-Step1 (HikariCP) | P1-Step2 (+OSIV OFF) | P1-Step3 (+Tomcat) | P1-Step4 (+Index) | Phase 2 (Redis) |
|------|---------|---------------------|----------------------|--------------------|--------------------| ----------------|
| iterations/s | 2,377 | 3,185 | **3,248** | 3,210 | 3,176 | **3,788** |
| p95 | 128ms | 47ms | **43.5ms** | 45.5ms | 48.5ms | **10.5ms** |
| avg | 70.0ms | 26.6ms | **24.2ms** | 25.6ms | 27.0ms | **6.2ms** |
| med | 67.5ms | 21.5ms | 19.5ms | 20.0ms | 21.0ms | **3.0ms** |
| enroll_success | 2,018 | 2,018 | 2,018 | 2,018 | 2,018 | 2,018 |
| 총 iterations | 192,698 | 258,214 | 263,242 | 260,337 | 257,550 | **307,215** |
| 5xx 에러 | 0 | 0 | 0 | 0 | 0 | 0 |

## Burst 구간 비교 (0~25초, 정원 경쟁)

| 지표 | Phase 0 | P1-Step1 | P1-Step2 | P1-Step3 | P1-Step4 | Phase 2 |
|------|---------|----------|----------|----------|----------|---------|
| burst p95 | 236.5ms | 169.8ms | **164.0ms** | 212.0ms | 249.5ms | **43.5ms** |
| burst avg | 98.6ms | 46.0ms | **42.5ms** | 47.6ms | 52.1ms | **15.7ms** |

## Sustained 구간 비교 (25초~, 정원 소진 후)

| 지표 | Phase 0 | P1-Step1 | P1-Step2 | P1-Step3 | P1-Step4 | Phase 2 |
|------|---------|----------|----------|----------|----------|---------|
| sustained p95 | 86.5ms | 39.5ms | **34.5ms** | 36.5ms | 37.0ms | **9.0ms** |
| sustained avg | 63.3ms | 22.0ms | **19.8ms** | 20.5ms | 21.2ms | **3.7ms** |

## Phase 0 대비 개선율

| 지표 | P1-Step1 | P1-Step2 | P1-Step3 | P1-Step4 | Phase 2 |
|------|----------|----------|----------|----------|---------|
| iterations/s | +34.0% | +36.6% | +35.0% | +33.6% | **+59.4%** |
| p95 (전체) | -63.3% | -66.0% | -64.5% | -62.1% | **-91.8%** |
| avg (전체) | -62.0% | -65.4% | -63.4% | -61.4% | **-91.1%** |
| burst avg | -53.3% | -56.9% | -51.7% | -47.2% | **-84.1%** |
| sustained avg | -65.2% | -68.7% | -67.6% | -66.5% | **-94.2%** |

## 핵심 발견사항

### 1. HikariCP pool=30이 지배적 개선 요인
- pool=10→30 변경 하나로 avg **62% 감소**, 처리량 **34% 증가**.
- 500 VU 동시 요청에서 10개 커넥션은 극심한 대기 발생. 30개로 확대 시 병목 해소.
- Phase 1의 다른 모든 최적화를 합쳐도 pool 확대 효과를 넘지 못함.

### 2. OSIV OFF는 소폭 추가 개선
- pool=30 위에 OSIV OFF 추가 시 avg **9% 추가 감소** (26.6ms → 24.2ms).
- 트랜잭션 종료 즉시 커넥션 반환 → 풀 활용률 향상.

### 3. Tomcat 쓰레드 확대/인덱스/batch는 효과 없음
- Tomcat threads 200→500: 오히려 미세 악화 (컨텍스트 스위칭 오버헤드).
- 인덱스: UniqueConstraint가 이미 복합 인덱스 제공, 추가 인덱스 불필요.
- batch_size=50: IDENTITY 전략에서 Hibernate가 JDBC 배치 비활성화.
- **최적 Phase 1 구성: pool=30 + OSIV OFF (Step 2)**.

### 4. Redis가 최종 압도적 개선
- Phase 0 대비 avg **91% 감소** (70ms → 6.2ms).
- 정원 소진 후 Redis INCR로 즉시 거절 → sustained avg **3.7ms**.
- DB 락 경합을 근본적으로 제거.

### 5. Gradle bootRun vs java -jar 성능 차이 발견

측정 과정에서 Gradle `bootRun`과 `java -jar` 사이에 큰 성능 차이를 발견:

| 실행 방법 | Phase 0 avg | Phase 1 avg | 오버헤드 |
|-----------|------------|------------|---------|
| Gradle bootRun | 112ms | 64ms | +60~137% |
| java -jar | 70ms | 27ms | (기준) |

**원인:** Windows 환경에서 Gradle `bootRun --args='...'`의 CLI 인자가 첫 번째 이후 제대로 전달되지 않음.
이로 인해 bootRun 기반 테스트에서 CLI 오버라이드(pool=30, OSIV OFF 등)가 적용되지 않아, 각 최적화의 효과가 "없는 것"으로 잘못 관측됨.
`java -jar`로 전환 후 CLI 인자가 정상 적용되어 각 최적화의 실제 효과를 확인할 수 있었음.

## 최적화 경로 요약

```
Phase 0 (기본)        avg 70.0ms  ──┐
                                     │ HikariCP pool=30: -62%
Phase 1 Step 1        avg 26.6ms  ──┤
                                     │ OSIV OFF: -9%
Phase 1 Step 2 (최적) avg 24.2ms  ──┤
                                     │ Tomcat/Index/Batch: 효과 없음
Phase 1 Step 3~4      avg 25~27ms ──┘
                                     │ Redis: -74%
Phase 2 (Redis)       avg  6.2ms  ──
                                     │ 대기열: 패러다임 전환
Phase 3 (대기열)      p95  2.49ms ── HTTP 에러 0%, 동시접속 2배
```

## Phase 3 (대기열) 결과

> Phase 3은 별도 시나리오(queue-enrollment-rush, 1000 VU)이므로 Phase 0~2와 직접 수치 비교는 제한적.

| 지표 | Phase 2 (Redis) | Phase 3 (대기열) | 비고 |
|------|-----------------|-----------------|------|
| p95 | 10.5ms | **2.49ms** | HTTP 응답 기준 |
| avg | 6.2ms | **5.01ms** | |
| med | 3.0ms | **0.71ms** | |
| enroll_success | 2,018 | 2,018 | 동일 |
| 총 iterations | 307,215 | **12,452** | 재시도 없는 대기열 방식 |
| http_req_failed | ~99.3% | **0.00%** | 패러다임 전환 |
| max VUs | 500 | **1,000** | 2배 |
| queue_wait avg | - | 4,196ms | 대기열 처리 대기 |
| queue_wait p95 | - | 9,004ms | |

### Phase 0 → Phase 3 전체 개선

| 지표 | Phase 0 | Phase 3 | 개선율 |
|------|---------|---------|--------|
| p95 | 128ms | 2.49ms | **-98.1%** |
| 총 수강신청 시도 | 192,698 | 12,452 | **-93.5%** |
| 불필요한 실패 요청 | 190,680 | 10,434 | **-94.5%** |
| max VUs | 500 | 1,000 | **2배** |
