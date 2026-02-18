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

# 원하는 Phase 디렉토리로 이동 후 실행
cd phase3-queue

# 빌드
./gradlew build

# 서버 실행 (MySQL + Redis 프로파일)
./gradlew bootRun --args='--spring.profiles.active=mysql,redis'

# 테스트
./gradlew test
```

## 부하 테스트 (k6)

```bash
# 수강신청 러시 (직접 수강신청, Phase 0~2)
k6 run k6/scenarios/enrollment-rush.js

# 대기열 수강신청 러시 (Phase 3)
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

> 테스트 공통 조건: MySQL 8.0 + 인기 강좌 50개 (정원 합계 2,018명), 학생 10,000명, 수강신청 러시 시나리오

### Phase 0 — Baseline

Spring Boot + MySQL + 비관적 락. 별도 최적화 없이 기본 설정 그대로 측정한 기준점입니다.

- **500 VU** 동시 접속, 직접 수강신청 방식
- p95 응답시간: 259ms / 수강 성공: 2,018건
- HTTP 에러율 99.3% (대부분 409 정원 초과 — 학생들이 같은 강좌에 수백 번 재시도)

### Phase 1 — 커넥션 & JPA 최적화

HikariCP 풀 확대, OSIV OFF, Hibernate 배치 처리, MySQL 인덱스를 적용했습니다.

| 단계 | 변경 | 효과 |
|------|------|------|
| Step 1 | HikariCP pool 10→30 | 단독 적용 시 오히려 타임아웃 발생 (커넥션 점유 시간이 병목) |
| Step 2 | OSIV OFF + Hibernate batch | 커넥션 점유 시간 단축 → pool 확대와 시너지 |
| Step 3 | 복합 인덱스 추가 | INSERT 부하로 미미한 효과 (-2.4%) |

- p95: 259ms → **205ms** (-20.8%)
- 핵심 교훈: 풀 크기보다 **커넥션 점유 시간 단축**이 먼저

### Phase 2 — Redis 원자 연산 + 캐싱

DB 비관적 락을 Redis DECR 원자 연산으로 대체하고, 강좌 정보를 Redis에 캐싱했습니다.

- DB 행 수준 락 제거 → Redis 단일 스레드 보장으로 동시성 제어
- 강좌 조회가 Redis에서 처리되어 DB 부하 대폭 감소
- p95: 205ms → **9.66ms** (-95.3%) / 처리량: 1,478 → **2,225 iter/s** (+50.6%)

### Phase 3 — Redis Sorted Set 대기열

수강신청 패러다임을 전환했습니다. 학생이 버튼을 누르면 대기열에 진입하고, 서버가 순서대로 자동 처리합니다.

```
학생: 수강신청 클릭 → 대기열 진입 → (서버가 자동 처리) → 결과 폴링으로 확인
```

- Redis Sorted Set (ZADD/ZPOPMIN)으로 순서 보장
- `@Scheduled` 100ms 간격, 50건씩 배치 처리
- 순번 기반 적응형 폴링 (뒤쪽 학생일수록 긴 간격)
- **1,000 VU** 동시 접속 (2배 증가), HTTP 에러율 **0.00%**

### Phase 0 → 3 전체 비교

| 지표 | Phase 0 | Phase 1 | Phase 2 | Phase 3 |
|------|---------|---------|---------|---------|
| p95 응답시간 | 259ms | 205ms | 9.66ms | **5.15ms** |
| HTTP 에러율 | 99.3% | 99.4% | 99.6% | **0.00%** |
| 총 요청 수 | 298,796 | 340,827 | 515,214 | **24,469** |
| 동시 접속 | 500 VU | 500 VU | 500 VU | **1,000 VU** |
| 수강 성공 | 2,018 | 2,018 | 2,018 | **2,018** |

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
