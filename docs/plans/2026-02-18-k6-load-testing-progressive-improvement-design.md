# k6 부하 테스트 기반 점진적 성능 개선 설계

## 개요

k6를 활용한 수강신청 시스템 부하 테스트 환경을 구축하고, Phase별로 새로운 기술을 도입하면서 성능 개선 과정을 측정/비교한다.

## 접근법

**단계별 기술 스택 적층 방식**: 미리 정의된 Phase를 따라가며 하나의 기술을 도입하고, 동일한 k6 시나리오로 전후 비교를 측정한다.

## k6 테스트 구조

### 디렉토리

```
k6/
├── scripts/
│   ├── smoke-test.js          # 1-2 VU, 기본 동작 확인
│   ├── load-test.js           # 50-100 VU, 정상 부하
│   ├── stress-test.js         # 200-500 VU, 한계 확인
│   └── spike-test.js          # 갑작스러운 트래픽 폭증
├── scenarios/
│   ├── enrollment-rush.js     # 수강신청 오픈 시나리오 (핵심)
│   └── mixed-workload.js      # 조회+신청+취소 혼합
├── helpers/
│   ├── config.js              # 서버 URL, 임계값 설정
│   └── data.js                # 학생ID/강좌ID 범위 등 데이터
└── results/                   # Phase별 결과 저장
    ├── phase0-baseline/
    ├── phase1-app-optimization/
    ├── phase2-redis-cache/
    └── phase3-waiting-queue/
```

### 핵심 시나리오: 수강신청 러시 (enrollment-rush.js)

| 구간 | VU 수 | 시간 | 설명 |
|------|-------|------|------|
| Ramp-up | 0 → 100 | 30초 | 점진적 부하 증가 |
| Steady | 100 | 2분 | 정상 부하 유지 |
| Spike | 100 → 500 | 10초 | 수강신청 오픈 순간 시뮬레이션 |
| Spike hold | 500 | 1분 | 피크 부하 유지 |
| Ramp-down | 500 → 0 | 30초 | 부하 감소 |

### 혼합 워크로드 비율

- 강좌 목록 조회 (`GET /api/courses`): 40%
- 수강신청 (`POST /api/enrollments`): 40%
- 시간표 조회 (`GET /api/enrollments?studentId=`): 15%
- 수강취소 (`DELETE /api/enrollments/{id}`): 5%

### 측정 지표 및 임계값

| 지표 | 목표 |
|------|------|
| http_req_duration (p95) | < 500ms |
| http_req_duration (p99) | < 1000ms |
| http_req_failed | < 5% (비즈니스 에러 제외) |
| iterations/s | Phase별 비교 |
| 정원 초과 발생 | 0건 (데이터 정합성) |

## Phase별 기술 도입 로드맵

### Phase 0: MySQL 전환 + Baseline 측정

**도입 기술**: MySQL 8.0, Docker Compose

**작업 내용**:
- Docker Compose로 MySQL 8 구성
- H2 → MySQL DataSource 전환
- `application-mysql.yml` 프로파일 추가
- `show-sql: false` 설정
- 기본 인덱스 설정 (PK, FK)
- `build.gradle`에 `mysql-connector-j` 추가
- k6 스크립트 작성 및 baseline 성능 측정

**Docker Compose**:
```yaml
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: musinsadb
    volumes:
      - mysql-data:/var/lib/mysql
```

### Phase 1: 애플리케이션 레벨 최적화

**도입 기술**: HikariCP 튜닝, JPA 쿼리 최적화, 인덱스 최적화

**작업 내용**:
- HikariCP 커넥션 풀 파라미터 튜닝 (maximumPoolSize, minimumIdle, connectionTimeout)
- JPA 쿼리 최적화 (N+1 문제 점검, fetch join 적용)
- MySQL 인덱스 최적화 (enrollment 테이블의 student_id + course_id 복합 인덱스 등)
- 비관적 락 범위 최소화 또는 낙관적 락 비교 실험
- k6로 Phase 0 대비 성능 비교

### Phase 2: 캐시 레이어 (Redis)

**도입 기술**: Redis 7, Spring Data Redis

**작업 내용**:
- Docker Compose에 Redis 추가
- `build.gradle`에 `spring-boot-starter-data-redis` 추가
- `application-redis.yml` 프로파일 추가
- 강좌 목록 조회 캐시 (`GET /api/courses` - 읽기 빈도 높음)
- 잔여 정원 정보 캐시 (실시간성과 정합성 트레이드오프 고려)
- 캐시 무효화: 수강신청/취소 시 해당 강좌 캐시 갱신
- k6로 Phase 1 대비 성능 비교

**Docker Compose 추가**:
```yaml
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
```

### Phase 3: 대기열 시스템 (Redis Sorted Set)

**도입 기술**: Redis Sorted Set 기반 대기열 (Phase 2에서 도입한 Redis 재활용)

**핵심 개념**: 수강신청 오픈 시 동시 접속자 수를 제한하는 대기열. 수강신청 자체는 동기 처리를 유지하여 즉시 응답(201/409)을 보장한다.

**Redis 명령 활용**:
```
ZADD queue <timestamp> <userId>    → 대기열 진입, O(log N)
ZRANK queue <userId>               → 내 순번 조회, O(log N)
ZCARD queue                        → 전체 대기 인원, O(1)
ZPOPMIN queue                      → 가장 앞 대기자 꺼내기, O(log N)
ZREM queue <userId>                → 대기열 이탈/만료, O(log N)
```

**요청 흐름**:
```
1. 클라이언트 → 대기열 진입 (POST /api/queue/enter)
2. Redis ZADD → 대기 순번 발급 (202 Accepted, position: N)
3. 클라이언트 → 대기 상태 폴링 (GET /api/queue/status/{token})
   - ZRANK로 현재 순번 반환
4. 동시 처리 가능 인원 이내 → 진입 허용 토큰 발급
5. 클라이언트 → 토큰과 함께 수강신청 (POST /api/enrollments, 기존 동기 처리)
```

**작업 내용**:
- 별도 인프라 추가 없이 기존 Redis 활용
- Redis Sorted Set으로 대기열 관리
- 동시 처리 가능 인원 제한 (예: 동시 50명)
- 대기 순번 조회 API (폴링 방식, ZRANK 활용)
- 진입 허용 토큰 발급 및 검증
- 토큰 만료 처리 (일정 시간 내 미사용 시 ZREM으로 제거, 다음 대기자에게 양보)
- 수강신청 API는 기존 동기 방식 유지 (데이터 정합성 보장)
- k6로 Phase 2 대비 성능 비교 (대기열 유무에 따른 처리량/응답시간 비교)

## 인프라 구성 (최종)

```yaml
# docker-compose.yml
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: musinsadb
    volumes:
      - mysql-data:/var/lib/mysql
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

volumes:
  mysql-data:
```

## Spring Boot 프로파일 전략

- `application.yml`: 공통 설정
- `application-mysql.yml`: MySQL DataSource 설정
- `application-redis.yml`: Redis 연결 설정
- Phase별로 프로파일을 추가 활성화: `--spring.profiles.active=mysql,redis`

## build.gradle 의존성 추가 (Phase별)

| Phase | 의존성 |
|-------|--------|
| Phase 0 | `runtimeOnly 'com.mysql:mysql-connector-j'` |
| Phase 2 | `implementation 'org.springframework.boot:spring-boot-starter-data-redis'` |
| Phase 3 | 추가 불필요 (spring-boot-starter-data-redis에 포함) |

## 성능 비교 대시보드

각 Phase 완료 후 동일한 k6 시나리오로 측정하여 비교표 작성:

| 항목 | Phase 0 | Phase 1 | Phase 2 | Phase 3 |
|------|---------|---------|---------|---------|
| TPS (수강신청) | - | - | - | - |
| p95 응답시간 | - | - | - | - |
| p99 응답시간 | - | - | - | - |
| 에러율 | - | - | - | - |
| 최대 동시 VU | - | - | - | - |
| 정원 초과 건수 | 0 | 0 | 0 | 0 |

## 결과 저장

- k6 JSON/CSV 출력: `k6/results/phase{N}/`
- Phase별 비교 요약: `docs/` 에 기록
