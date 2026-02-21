# 대학교 수강신청 시스템

## 프로젝트 개요
동시성 제어가 핵심인 대학교 수강신청 REST API 서버입니다.
정원이 1명 남은 강좌에 100명이 동시에 신청해도, 정확히 1명만 성공합니다.

단계별 성능 최적화를 진행하며, 각 Phase를 독립 프로젝트로 분리하여 변화 과정을 비교할 수 있습니다.

## 기술 스택

| 항목 | 기술 | 버전 |
|------|------|------|
| 언어 | Java | 25 |
| 프레임워크 | Spring Boot | 3.5.10 |
| 빌드 도구 | Gradle | 9.1.0 |
| 데이터베이스 | MySQL | 8.0 (Docker) |
| 캐시/큐 | Redis | 7 (Docker, Phase 2+) |
| 부하 테스트 | k6 | - |
| API 문서 | SpringDoc OpenAPI | 2.8.15 |

## 사전 요구사항
- Java 25+
- Docker & Docker Compose (MySQL, Redis)

## 프로젝트 구조

```
├── phase0-baseline/        # Phase 0: Spring Boot + MySQL + 비관적 락
├── phase1-optimization/    # Phase 1: HikariCP + OSIV OFF + 인덱스
├── phase2-redis/           # Phase 2: Redis read-only 체크 + 캐싱
├── phase3-step1/           # Phase 3 Step 1: 순차 처리 (1 스레드)
├── phase3-step2/           # Phase 3 Step 2: 병렬 처리 (20 스레드)
├── phase3-step3/           # Phase 3 Step 3: Lua Script 원자 연산
├── k6/                     # 부하 테스트 시나리오 및 결과
│   ├── scenarios/          # k6 테스트 스크립트
│   └── results/            # Phase별 측정 결과
├── docker-compose.yml      # MySQL 8.0 + Redis 7
└── docs/                   # 문서, ADR, 계획서
```

각 Phase는 독립된 Spring Boot 프로젝트이며, 이전 Phase의 최적화를 누적합니다.

### 애플리케이션 패키지 구조 (공통)

```
src/main/java/com/musinsa/
├── ApiApplication.java          # Spring Boot 진입점
├── api/                         # 프레젠테이션 계층 (Controller, DTO)
│   ├── HealthController.java
│   ├── student/
│   ├── course/
│   ├── professor/
│   └── enrollment/
├── service/                     # 비즈니스 계층 (Service)
│   ├── student/
│   ├── course/
│   ├── professor/
│   └── enrollment/
└── domain/                      # 도메인 계층 (Entity, Repository)
    ├── student/
    ├── course/
    ├── professor/
    ├── enrollment/
    └── department/
```

아키텍처: **레이어드 아키텍처** (`api → service → domain` 단방향 의존)

## 빌드 및 실행

```bash
# 인프라 실행 (MySQL + Redis)
docker-compose up -d

# 원하는 Phase 디렉토리로 이동 후 빌드
cd phase2-redis
./gradlew build

# 서버 실행 (java -jar 권장)
java -jar build/libs/phase2-redis-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql,redis
```

> **java -jar vs bootRun:** 성능 측정 시 반드시 `java -jar`로 실행해야 합니다.
> Gradle `bootRun`은 60~137% 성능 오버헤드가 발생하며, Windows 환경에서 `--args`의 CLI 인자가 제대로 전달되지 않는 문제가 있습니다.

### Phase별 실행 예시

```bash
# Phase 0: 기본 설정
java -jar phase0-baseline/build/libs/phase0-baseline-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=mysql

# Phase 1: CLI 오버라이드로 최적화 적용
java -jar phase0-baseline/build/libs/phase0-baseline-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=mysql \
  --spring.datasource.hikari.maximum-pool-size=30 \
  --spring.datasource.hikari.minimum-idle=10 \
  --spring.jpa.open-in-view=false

# Phase 2: Redis 프로파일 포함
java -jar phase2-redis/build/libs/phase2-redis-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=mysql,redis

# Phase 3: 대기열 시스템 (Step 3 기준)
java -jar phase3-step3/build/libs/phase3-step3-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=mysql,redis
```

## 부하 테스트 (k6)

```bash
# 수강신청 러시 — instant spike (Phase 0~2)
# 10s 워밍업 → 1s만에 500VU 스파이크 → 1m 지속 → 10s 쿨다운 (총 ~1m21s)
k6 run k6/scenarios/enrollment-rush.js

# 대기열 수강신청 러시 (Phase 3)
# 1초만에 3000명 동시 접속 (VUS 환경변수로 조절)
k6 run --env VUS=3000 k6/scenarios/queue-enrollment-rush.js

# 혼합 워크로드 (조회 + 수강신청)
k6 run k6/scenarios/mixed-workload.js
```

## 서버 접속 정보

| 항목 | URL / 접속 정보 |
|------|-----------------|
| API 서버 | http://localhost:8080 |
| Health Check | http://localhost:8080/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MySQL | localhost:3306 (musinsa / musinsa) |
| Redis | localhost:6379 |

## 성능 최적화 여정

수강신청 시스템의 동시성 처리 성능을 단계적으로 개선한 과정입니다.
각 Phase는 이전 Phase의 최적화를 누적하며, k6 부하 테스트로 효과를 측정했습니다.

> **테스트 환경:** Windows에서 Spring Boot 서버(`java -jar`) + k6, Docker로 MySQL/Redis를 동일 머신에서 실행.
> 독립된 서버 환경이 아니므로 절대값보다 **Phase 간 상대적 추이**에 의미가 있습니다.
>
> **Phase 0~2 조건:** 인기 강좌 50개 (정원 합계 2,018명), 학생 10,000명, 500 VU, 1초 instant spike
> **Phase 3 조건:** 인기 강좌 50개 (정원 합계 ~3,957명), 학생 10,000명, 3000 VU, Burst + Sustained 패턴
> **공통:** `java -jar` 실행

### Phase 0 — Baseline (500 VU)

Spring Boot + MySQL + 비관적 락. 최적화 없이 기본 설정 그대로 측정한 기준점입니다.

| p95 | avg | 처리량 | 수강 성공 |
|-----|-----|--------|----------|
| 128ms | 70ms | 2,377 iter/s | 2,018건 |

### Phase 1 — 커넥션 & JPA 최적화 (500 VU)

4단계를 개별 테스트하여 각 최적화의 실제 효과를 검증했습니다.

| 단계 | 변경 | 효과 |
|------|------|------|
| Step 1 | HikariCP pool 10→30 | avg **-62%**, 처리량 **+34%** |
| Step 2 | + OSIV OFF | avg **-9%** 추가 |
| Step 3 | + Tomcat threads 200→500 | 효과 없음 (컨텍스트 스위칭 오버헤드) |
| Step 4 | + 인덱스 + batch_size=50 | 효과 없음 (UniqueConstraint 자동 인덱스, IDENTITY 배치 비활성) |

**최적 구성: Step 2** — p95 128→**43.5ms** (-66%) / avg 70→**24.2ms** (-65%)

> pool=30 하나로 전체 개선의 90% 이상. OSIV OFF로 커넥션 회전율을 추가 확보.

### Phase 2 — Redis Fast Rejection + 캐싱 (500 VU)

Redis 읽기 전용 정원 확인으로 정원 초과를 DB 도달 전에 즉시 거절(fast rejection)합니다.

- `isFull()` 읽기 전용 체크 → DB 비관적 락으로 수강신청 → 성공 후 카운터 증가
- 강좌 정적 정보(이름, 학점, 시간표 등)를 Redis에 캐싱 → DB 조회 제거
- DB 비관적 락은 최종 방어선으로 유지 (중복 신청, 학점 초과 등)
- p95 43.5→**11.3ms** (-74%) / avg 24.2→**6.3ms** (-74%)
- **Phase 0 대비: p95 -91%, avg -91%**

### Phase 3 — Redis Sorted Set 대기열 (3000 VU)

수강신청 패러다임을 전환했습니다. 클릭 → 대기열 진입 → 서버 자동 처리 → 결과 폴링.

```
학생: 수강신청 클릭 → 대기열 진입 → (서버가 자동 처리) → 결과 폴링으로 확인
```

3단계에 걸쳐 최적화하며 각 변경의 실제 효과를 검증했습니다.

| 단계 | 변경 | iterations/s | Submit p95 | Wait p95 |
|------|------|:---:|:---:|:---:|
| Step 1 | 순차 처리 (1 스레드) | 122 | 62ms | 31.5s |
| Step 2 | 병렬 처리 (20 스레드) | **349** (+186%) | 101.8ms | **8.5s** (-73%) |
| Step 3 | + Lua Script 원자 연산 | 352 | **9.8ms** (-90%) | 8.5s |

**Step 2 (병렬화)** 가 처리량을 3배로 끌어올렸고, **Step 3 (Lua Script)** 는 Submit 지연시간을 90% 줄였습니다.

**최종 구현 (Step 3):**
- Redis Sorted Set (ZADD/ZPOPMIN) 순서 보장
- Lua Script 원자 연산 (enqueue, getResult, isFull — Redis 6회 왕복 → 1회)
- 20스레드 병렬 배치 처리 (`@Scheduled` 100ms, 50건/배치)
- 순번 기반 적응형 폴링 (뒤쪽 학생일수록 긴 간격)
- 모든 조건에서 에러율 0%, 정원 초과 0건

---

### 전체 성능 비교

#### Phase 0~2 (enrollment-rush, 500 VU 직접 수강신청)

| 지표 | Phase 0 | Phase 1 | Phase 2 |
|------|---------|---------|---------|
| iterations/s | 2,377 | 3,248 | **3,786** |
| avg | 70ms | 24.2ms | **6.3ms** |
| p95 | 128ms | 43.5ms | **11.3ms** |
| burst avg | 98.6ms | 42.5ms | **16.0ms** |
| burst p95 | 236.5ms | 164.0ms | **48.8ms** |
| sustained avg | 63.3ms | 19.8ms | **3.8ms** |
| enroll_success | 2,018 | 2,018 | 2,018 |

#### Phase 3 (queue-enrollment-rush, 3000 VU 대기열)

> Phase 0~2와 테스트 방식이 다릅니다 (직접 수강신청 vs 대기열 + 폴링).
> Submit = 큐 제출 응답 시간, Wait = 큐 대기~결과 수신 시간, iterations/s = 초당 완료된 수강신청 시도.

| 지표 | Step 1 (순차) | Step 2 (병렬 20t) | Step 3 (+ Lua Script) |
|------|:---:|:---:|:---:|
| **에러율** | 0% | 0% | 0% |
| **수강 성공** | ~3,957 | 3,957 | 3,957 |
| **Submit p95** | 62ms | 101.8ms | **9.8ms** |
| **Wait p95** | 31.5s | **8.5s** | **8.5s** |
| **iterations/s** | 122 | **349** | **352** |
| **스레드풀 민감도** | 높음 (2000t에서 45%↓) | 낮음 | **없음** |

---

### 핵심 발견사항

**1. HikariCP pool 확대가 지배적 개선 요인**
pool=10→30 하나로 avg 62% 감소, 처리량 34% 증가. Phase 1의 다른 모든 최적화를 합쳐도 이 효과를 넘지 못함.

**2. "당연히 좋을 것"이라고 생각한 최적화가 효과 없음**
Tomcat 쓰레드 확대, 인덱스 추가, JDBC 배치 모두 이 워크로드에서는 효과 없음.
UniqueConstraint가 이미 인덱스를 제공하고, IDENTITY 전략은 배치를 비활성화함.

**3. Redis로 DB 경합을 근본 제거**
Phase 0 대비 avg 91% 감소. 정원 소진 후에는 Redis `isFull()`로 즉시 거절하여 DB에 도달하지 않음.

**4. Gradle bootRun vs java -jar 성능 차이**
bootRun은 60~137% 오버헤드 발생. Windows에서 `--args` CLI 인자가 제대로 전달되지 않아 최적화 효과가 "없는 것"으로 잘못 관측되는 원인이 됨.

### 최적화 경로 요약

```
Phase 0 (기본)        avg 70.0ms
    │ HikariCP pool=30 + OSIV OFF: avg -65%
Phase 1 (최적)        avg 24.2ms
    │ Redis fast rejection + 캐싱: avg -74%
Phase 2 (Redis)       avg  6.3ms
    │ 대기열 패러다임 전환 (3000 VU)
Phase 3 Step 1        122 iter/s, Wait 31.5s  (순차 처리)
    │ 20 스레드 병렬화: iterations/s +186%, Wait -73%
Phase 3 Step 2        349 iter/s, Wait 8.5s   (병렬 처리)
    │ Lua Script 원자 연산: Submit p95 -90%
Phase 3 Step 3        352 iter/s, Submit 9.8ms (최종)
```

> 상세 측정 결과: [k6/results/](k6/results/) 각 Phase별 summary.md 참조

---

## 문서

| 문서 | 설명 |
|------|------|
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | 요구사항 분석 및 설계 결정 |
| [docs/api/endpoints.md](docs/api/endpoints.md) | REST API 상세 명세 |
| [docs/decisions/](docs/decisions/) | Architecture Decision Records |
| [docs/plans/](docs/plans/) | 성능 최적화 계획서 |

## AI 활용 이력 (prompts/)

AI와의 협업 과정을 기록한 프롬프트 이력입니다.

| 파일 | 내용 |
|------|------|
| [1-초기설정.md](prompts/1-초기설정.md) | 프로젝트 초기 구조 세팅 |
| [2-ERD구성 및 구현 범위.md](prompts/2-ERD구성%20및%20구현%20범위.md) | ERD 설계, 구현 순서 결정 |
| [3-수강 동시 취소 멱등성.md](prompts/3-수강%20동시%20취소%20멱등성.md) | 동시 취소 버그 진단 및 멱등성 설계 |
| [4-로깅 및 타임아웃.md](prompts/4-로깅%20및%20타임아웃.md) | 로깅, 락/트랜잭션 타임아웃 설계 (미구현, 설계까지 완료) |

`prompts/plans/`에는 각 단계별 브레인스토밍에서 도출된 설계 문서가 포함되어 있습니다.

## 의사결정 기록

| ADR | 제목 | 상태 |
|-----|------|------|
| [001](docs/decisions/001-project-structure.md) | 프로젝트 구조 및 아키텍처 | Accepted |
| [002](docs/decisions/002-tech-stack.md) | 기술 스택 선택 | Accepted |
| [003](docs/decisions/003-enrollment-cancel-idempotency.md) | 수강취소 동시성 및 DELETE 멱등성 | Accepted |
| [003-osiv](docs/decisions/003-osiv-disable.md) | OSIV 비활성화 | Accepted |
| [004](docs/decisions/004-redis-atomic-enrollment-counter.md) | Redis 원자 연산 기반 수강 인원 관리 | Superseded by 008 |
| [005](docs/decisions/005-phase1-optimization-analysis.md) | Phase 1 최적화 개별 효과 검증 및 인덱스/배치 불필요 결정 | Accepted |
| [006](docs/decisions/006-phase2-redis-cache-memory-analysis.md) | Phase 2 강좌 캐시 Redis 메모리 적재 가능성 분석 | Accepted |
| [007](docs/decisions/007-getresult-race-condition-fix.md) | getResult() NOT_FOUND 레이스 컨디션 제거 | Accepted |
| [008](docs/decisions/008-redis-read-only-check-then-increment.md) | Redis read-only 체크 후 DB 성공 시 카운터 증가 | Accepted |
