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
# 2초만에 1000명 동시 접속, 순차 수강신청
k6 run k6/scenarios/queue-enrollment-rush.js

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

> **테스트 환경:** 로컬 WSL2에서 Spring Boot 서버(`java -jar`), Docker MySQL/Redis를 동일 머신에서 실행. 독립된 서버 환경이 아니므로 측정 간 10~15% 편차가 발생할 수 있으며, 절대값보다 Phase 간 상대적 추이에 의미가 있습니다.
>
> **공통 조건:** MySQL 8.0 + 인기 강좌 50개 (정원 합계 2,018명), 학생 10,000명, instant spike 시나리오, `java -jar` 실행
> - Phase 0~2: enrollment-rush (1s만에 500 VU, 1m 지속)
> - Phase 3: queue-enrollment-rush (1s만에 1,000 VU, 1m 지속)

### Phase 0 — Baseline

Spring Boot + MySQL + 비관적 락. 별도 최적화 없이 기본 설정 그대로 측정한 기준점입니다.

- **500 VU** instant spike, `java -jar` 직접 실행
- p95 **128ms** / avg **70ms** / 처리량 **2,377 iter/s**
- 수강 성공: 2,018건 / 5xx 에러: 0건

### Phase 1 — 커넥션 & JPA 최적화

HikariCP 풀 확대부터 인덱스까지 4단계를 개별 테스트하여 각 최적화의 실제 효과를 검증했습니다.

| 단계 | 변경 | 효과 |
|------|------|------|
| Step 1 | HikariCP pool 10→30 | 처리량 **+34%**, avg **-62%** (커넥션 대기 병목 해소) |
| Step 2 | + OSIV OFF | avg **-9%** 추가 (커넥션 조기 반환) |
| Step 3 | + Tomcat threads 200→500 | 효과 없음 (미세 악화, 컨텍스트 스위칭 오버헤드) |
| Step 4 | + 인덱스 + batch_size=50 | 효과 없음 (UniqueConstraint가 이미 인덱스 제공, IDENTITY 전략에서 batch 비활성) |

- **최적 구성: Step 2 (pool=30 + OSIV OFF)**
- p95: 128ms → **43.5ms** (-66.0%) / avg: 70ms → **24.2ms** (-65.4%)
- 핵심 교훈: pool=30 하나로 전체 개선의 90% 이상. OSIV OFF로 커넥션 회전율을 추가 확보.

### Phase 2 — Redis 원자 연산 + 캐싱

Redis INCR/DECR 원자 연산으로 정원 초과를 사전 차단하고, 강좌 정보를 Redis에 캐싱했습니다.

- Redis로 정원 초과 요청을 DB 도달 전에 즉시 거절 (fast rejection)
- 강좌 조회가 Redis에서 처리되어 DB 부하 대폭 감소
- DB 비관적 락은 최종 방어선으로 유지 (중복 신청, 학점 초과 등 검증)
- p95: 43.5ms → **10.5ms** (-75.9%) / avg: 24.2ms → **6.2ms** (-74.4%)
- 처리량: 3,248 → **3,788 iter/s** (+16.6%)
- **Phase 0 대비: p95 -91.8%, avg -91.1%**

### Phase 3 — Redis Sorted Set 대기열

수강신청 패러다임을 전환했습니다. 학생이 버튼을 누르면 대기열에 진입하고, 서버가 순서대로 자동 처리합니다.

```
학생: 수강신청 클릭 → 대기열 진입 → (서버가 자동 처리) → 결과 폴링으로 확인
```

- Redis Sorted Set (ZADD/ZPOPMIN)으로 순서 보장
- `@Scheduled` 100ms 간격, 50건씩 배치 + **20스레드 병렬 처리**
- **Lua Script** enqueue (Redis 6회 왕복 → 1회)
- 순번 기반 적응형 폴링 (뒤쪽 학생일수록 긴 간격)
- **3,000 VU** (3회 측정) 안정 동작, enroll_success **3회 모두 2,018건**

#### Phase 3 내부 최적화 효과 (3,000 VU)

| 지표 | 순차 처리 (기존) | 최종 (병렬+Lua) | 변화 |
|------|----------------|----------------|------|
| queue_wait avg | 13.8s | **5.9s** | **-57.4%** |
| queue_wait p95 | 24.0s | **8.0s** | **-66.7%** |
| iterations/s | 181 | **394** | **+117.7%** |
| enroll_success | 2,018 | 2,018 | 정합성 유지 |

### Phase 0 → 2 성능 비교 (enrollment-rush, 500 VU)

| 지표 | Phase 0 | Phase 1 (Step 2) | Phase 2 (Redis) |
|------|---------|-------------------|-----------------|
| p95 | 128ms | **43.5ms** | **10.5ms** |
| avg | 70.0ms | **24.2ms** | **6.2ms** |
| med | 67.5ms | 19.5ms | **3.0ms** |
| iterations/s | 2,377 | **3,248** | **3,788** |
| 총 iterations | 192,698 | 263,242 | **307,215** |
| enroll_success | 2,018 | 2,018 | 2,018 |
| 5xx 에러 | 0 | 0 | 0 |

### Phase 3 대기열의 패러다임 전환

> Phase 3은 별도 k6 시나리오(queue-enrollment-rush, 3,000 VU)로 측정하여 Phase 0~2와 직접 수치 비교는 제한적입니다. 핵심은 아키텍처 전환에 따른 근본적 변화입니다.

#### 사용자 경험의 변화
| 항목 | Phase 0~2 (직접 수강신청) | Phase 3 (대기열) |
|------|--------------------------|-----------------|
| 수강신청 방식 | 버튼 클릭 → 즉시 성공/실패 | 버튼 클릭 → 대기 → 자동 처리 → 결과 통보 |
| 정원 찬 강좌 | 즉시 "정원 초과" 에러 | 대기열에서 처리 후 "정원 초과" 결과 |
| 재시도 패턴 | 미친듯이 버튼 연타 | 결과 받고 다른 강좌 신청 |
| 학생당 평균 시도 | 수백~수천 회 (같은 강좌 반복) | **~1.6회** (서로 다른 강좌 순차) |
| 동시 접속 한계 | 500 VU | **3,000 VU** (6배) |

#### 서버 부하 감소
- **총 수강신청 시도**: 192,698건 → **28,665건** (**-85.1%**)
- **불필요한 실패 요청**: 190,680건 → **26,592건** (**-86.1%**)
- 순번 기반 폴링 간격이 뒤쪽 학생의 불필요한 요청을 억제

#### HTTP 에러율
- **Phase 0~2**: 대부분의 요청이 409 정원 초과 (수강 실패율 ~99%)
- **Phase 3**: http_req_failed **~0.06%** — 결과가 HTTP 에러가 아닌 데이터(SUCCESS/FAILED)로 전달

#### 사용자 체감 대기 시간
- p95 응답시간: **5.5ms** (HTTP 응답 기준)
- 대기열 처리 대기: avg **5.9초**, p95 **8.0초**
- 3,000명이 1초만에 몰리는 극한 상황에서도 대부분 9초 이내 결과 확인

#### 데이터 정합성
- **enroll_success**: 3회 측정 모두 정확히 **2,018건**
- 인기 강좌(1~50번) 정원 합계와 100% 일치
- 20스레드 병렬 처리에서도 동시성 제어 정확히 동작

### 핵심 발견사항

#### 1. HikariCP pool=30이 지배적 개선 요인
- pool=10→30 변경 하나로 avg **62% 감소**, 처리량 **34% 증가**.
- 500 VU 동시 요청에서 10개 커넥션은 극심한 대기 발생. 30개로 확대 시 병목 해소.
- Phase 1의 다른 모든 최적화를 합쳐도 pool 확대 효과를 넘지 못함.

#### 2. OSIV OFF는 소폭 추가 개선
- pool=30 위에 OSIV OFF 추가 시 avg **9% 추가 감소** (26.6ms → 24.2ms).
- 트랜잭션 종료 즉시 커넥션 반환 → 풀 활용률 향상.

#### 3. Tomcat 쓰레드 확대/인덱스/batch는 효과 없음
- Tomcat threads 200→500: 오히려 미세 악화 (컨텍스트 스위칭 오버헤드).
- 인덱스: UniqueConstraint가 이미 복합 인덱스 제공, 추가 인덱스 불필요.
- batch_size=50: IDENTITY 전략에서 Hibernate가 JDBC 배치 비활성화.

#### 4. Redis가 압도적 개선
- Phase 0 대비 avg **91% 감소** (70ms → 6.2ms).
- 정원 소진 후 Redis INCR로 즉시 거절 → sustained avg **3.7ms**.
- DB 락 경합을 근본적으로 제거.

#### 5. Gradle bootRun vs java -jar 성능 차이 발견

측정 과정에서 Gradle `bootRun`과 `java -jar` 사이에 큰 성능 차이를 발견:

| 실행 방법 | Phase 0 avg | Phase 1 avg | 오버헤드 |
|-----------|------------|------------|---------:|
| Gradle bootRun | 112ms | 64ms | +60~137% |
| java -jar | 70ms | 27ms | (기준) |

**원인:** Windows 환경에서 Gradle `bootRun --args='...'`의 CLI 인자가 첫 번째 이후 제대로 전달되지 않음.
이로 인해 bootRun 기반 테스트에서 CLI 오버라이드(pool=30, OSIV OFF 등)가 적용되지 않아, 각 최적화의 효과가 "없는 것"으로 잘못 관측됨.
`java -jar`로 전환 후 CLI 인자가 정상 적용되어 각 최적화의 실제 효과를 확인할 수 있었음.

### 최적화 경로 요약

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
Phase 3 (대기열)      p95  5.5ms  ── HTTP 에러 ~0%, 동시접속 6배
                                     │ 병렬 20스레드 + Lua Script
                                     │ queue_wait -57%, 처리량 2배
Phase 3 (최종)        3,000 VU 안정 ── queue_wait avg 5.9s, p95 8.0s
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
