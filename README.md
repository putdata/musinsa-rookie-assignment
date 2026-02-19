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
├── phase2-redis/           # Phase 2: Redis 원자 연산 + 캐싱
├── phase3-queue/           # Phase 3: Redis Sorted Set 대기열
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

# 테스트
./gradlew test
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

# Phase 3: 대기열 시스템
java -jar phase3-queue/build/libs/phase3-queue-0.0.1-SNAPSHOT.jar \
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
> **공통 조건:** 인기 강좌 50개 (정원 합계 2,018명), 학생 10,000명, 1초 instant spike, `java -jar` 실행

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

### Phase 2 — Redis 원자 연산 + 캐싱 (500 VU)

Redis INCR/DECR 원자 연산으로 정원 초과를 DB 도달 전에 즉시 거절(fast rejection)합니다.

- 정원 소진 후 Redis에서 즉시 거절 → DB 락 경합 근본 제거
- 강좌 정적 정보(이름, 학점, 시간표 등)를 Redis에 캐싱 → DB 조회 제거
- DB 비관적 락은 최종 방어선으로 유지 (중복 신청, 학점 초과 등)
- p95 43.5→**10.5ms** (-76%) / avg 24.2→**6.2ms** (-74%)
- **Phase 0 대비: p95 -92%, avg -91%**

> **캐싱 메모리 실측 ([ADR-006](docs/decisions/006-phase2-redis-cache-memory-analysis.md)):**
> 수강신청 중 변하지 않는 정적 정보만 캐싱하고, 수강 인원은 Redis 카운터로 별도 관리합니다.
> 강좌 500개 실측 결과 — 카운터 1,000키 64KB + 캐시 11키 93KB = **총 ~153KB** (순증분 97KB).
> Redis 기본 메모리 대비 무시할 수 있는 수준이며, 키 수와 TTL(1시간)이 고정되어 장기 누적 위험도 없습니다.

### Phase 3 — Redis Sorted Set 대기열 (3,000 VU)

수강신청 패러다임을 전환했습니다. 클릭 → 대기열 진입 → 서버 자동 처리 → 결과 폴링.

```
학생: 수강신청 클릭 → 대기열 진입 → (서버가 자동 처리) → 결과 폴링으로 확인
```

**핵심 구현:**
- Redis Sorted Set (ZADD/ZPOPMIN) 순서 보장
- Lua Script enqueue (Redis 6회 왕복 → 1회 원자 실행)
- INCR 카운터 근사 순번 (ZRANK O(log N) → INCR O(1))
- 20스레드 병렬 배치 처리 (`@Scheduled` 100ms, 50건/배치)
- 순번 기반 적응형 폴링 (뒤쪽 학생일수록 긴 간격)

**Phase 3 내부 최적화 효과:**

| 지표 | 순차 처리 (기존) | 최종 (병렬+Lua+INCR) | 변화 |
|------|----------------|---------------------|------|
| queue_wait avg | 13.8s | **5.9s** | **-57%** |
| queue_wait p95 | 24.0s | **8.0s** | **-67%** |
| queue_submit p95 | 697ms | **547ms** | **-22%** |
| iterations/s | 181 | **402** | **+122%** |

3회 측정 모두 enroll_success **정확히 2,018건** (정합성 100%).

---

### 전체 성능 비교

#### Phase 0~2 (enrollment-rush, 500 VU 직접 수강신청)

| 지표 | Phase 0 | Phase 1 | Phase 2 |
|------|---------|---------|---------|
| p95 | 128ms | 43.5ms | **10.5ms** |
| avg | 70ms | 24.2ms | **6.2ms** |
| 처리량 | 2,377/s | 3,248/s | **3,788/s** |
| 수강 성공 | 2,018 | 2,018 | 2,018 |

#### Phase 3 — 패러다임 전환 (queue-enrollment-rush, 3,000 VU 대기열)

> Phase 0~2와 시나리오가 다르므로 직접 수치 비교는 제한적입니다.
> 핵심은 아키텍처 전환에 따른 근본적 변화입니다.

| 항목 | Phase 0~2 (직접 수강신청) | Phase 3 (대기열) |
|------|--------------------------|-----------------|
| 수강신청 방식 | 클릭 → 즉시 성공/실패 | 클릭 → 대기 → 자동 처리 → 결과 통보 |
| 재시도 패턴 | 버튼 연타 (같은 강좌 수백~수천 회) | 결과 받고 다른 강좌 신청 (~1.6회) |
| 총 수강신청 시도 | 192,698건 | **28,603건** (-85%) |
| 불필요한 실패 요청 | 190,680건 | **26,572건** (-86%) |
| HTTP 에러율 | ~99% (409 정원 초과) | **~0.01%** (결과를 데이터로 전달) |
| 동시 접속 | 500 VU | **3,000 VU** (6배) |
| 대기열 처리 시간 | - | avg 5.9초, p95 8.0초 |

---

### 핵심 발견사항

**1. HikariCP pool 확대가 지배적 개선 요인**
pool=10→30 하나로 avg 62% 감소, 처리량 34% 증가. Phase 1의 다른 모든 최적화를 합쳐도 이 효과를 넘지 못함.

**2. "당연히 좋을 것"이라고 생각한 최적화가 효과 없음**
Tomcat 쓰레드 확대, 인덱스 추가, JDBC 배치 모두 이 워크로드에서는 효과 없음.
UniqueConstraint가 이미 인덱스를 제공하고, IDENTITY 전략은 배치를 비활성화함.

**3. Redis로 DB 경합을 근본 제거**
Phase 0 대비 avg 91% 감소. 정원 소진 후에는 Redis INCR로 즉시 거절하여 DB에 도달하지 않음.

**4. 대기열은 성능 최적화가 아닌 패러다임 전환**
응답 속도를 줄이는 것이 아니라, 불필요한 트래픽 자체를 86% 제거. 동시 접속 6배 확대.

**5. Gradle bootRun vs java -jar 성능 차이**
bootRun은 60~137% 오버헤드 발생. Windows에서 `--args` CLI 인자가 제대로 전달되지 않아 최적화 효과가 "없는 것"으로 잘못 관측되는 원인이 됨.

### 최적화 경로 요약

```
Phase 0 (기본)        avg 70.0ms
    │ HikariCP pool=30 + OSIV OFF: avg -65%
Phase 1 (최적)        avg 24.2ms
    │ Redis 원자 연산 + 캐싱: avg -74%
Phase 2 (Redis)       avg  6.2ms
    │ 대기열 패러다임 전환
Phase 3 (대기열)      3,000 VU 안정
                      queue_wait avg 5.9s, p95 8.0s
                      불필요한 트래픽 -86%, 동시접속 6배
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
| [004](docs/decisions/004-redis-atomic-enrollment-counter.md) | Redis 원자 연산 기반 수강 인원 관리 | Accepted |
| [005](docs/decisions/005-phase1-optimization-analysis.md) | Phase 1 최적화 개별 효과 검증 및 인덱스/배치 불필요 결정 | Accepted |
| [006](docs/decisions/006-phase2-redis-cache-memory-analysis.md) | Phase 2 강좌 캐시 Redis 메모리 적재 가능성 분석 | Accepted |
