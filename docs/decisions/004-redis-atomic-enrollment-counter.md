# ADR-004: Redis 원자 연산 기반 수강 인원 관리

## 상태
채택됨

## 맥락

수강신청 시스템에서 강좌 목록 조회(`GET /api/courses`)는 가장 빈번한 요청이다. 학생들은 수강신청 기간에 강좌 목록을 반복적으로 새로고침하며 잔여 인원을 확인한다.

Phase 1에서는 매 요청마다 DB를 조회했다. Redis 캐시를 도입하면 DB 부하를 줄일 수 있지만, **수강 인원(enrolled)은 수강신청/취소 시마다 변하는 동적 데이터**라는 문제가 있다.

## 선택지

### 1. @Cacheable + @CacheEvict (Spring Cache 기본 패턴)
```
GET /api/courses → @Cacheable → 캐시 히트 시 DB 안 감
POST /api/enrollments → @CacheEvict → 캐시 전체 무효화
```

- **장점**: 구현이 단순
- **단점**: 수강신청이 발생할 때마다 캐시가 무효화됨. 러시 시간에 초당 수백 건의 신청이 들어오면 캐시 히트율이 0%에 수렴하여 캐시 효과가 사라짐

### 2. TTL 기반 자연 만료 (캐시 무효화 없음)
```
GET /api/courses → @Cacheable(TTL=1분) → 1분간 캐시
POST /api/enrollments → 캐시 무효화 안 함
```

- **장점**: 구현이 단순, 캐시 히트율 높음
- **단점**: 최대 TTL만큼 수강 인원이 오래된 값으로 표시됨. 잔여 1자리인 인기 강좌에서 학생이 잘못된 정보를 보게 됨

### 3. 정적/동적 데이터 분리 + Redis 원자 연산
```
정적 데이터 (강좌명, 학점, 시간표, 교수, 학과) → Redis 캐시 (긴 TTL)
동적 데이터 (수강 인원) → Redis INCR/DECR 원자 연산
```

- **장점**: 캐시 무효화 불필요, 수강 인원 항상 실시간, DB 부하 최소화
- **단점**: 구현 복잡도 증가, Redis-DB 간 동기화 관리 필요

## 결정

**선택지 3을 채택한다.**

### 아키텍처

```
GET /api/courses 요청
  ├─ 강좌 정보 → Redis Cache (TTL 1시간, 변하지 않는 데이터)
  ├─ 수강 인원 → Redis MGET (course:enrolled:{id})
  └─ Controller에서 조합하여 응답 (DB 히트 없음)

POST /api/enrollments 요청
  ├─ 1단계: Redis Lua 스크립트 (원자적 INCR + 정원 검사)
  │    └─ 정원 초과 시 자동 DECR + 즉시 거절 (DB까지 안 감)
  ├─ 2단계: DB 비관적 락 + 비즈니스 검증 (최종 방어)
  │    └─ 실패 시 Redis DECR 보상
  └─ 결과: DB 부하 대폭 감소

DELETE /api/enrollments/{id} 요청
  ├─ DB 비관적 락 + 삭제
  └─ Redis DECR
```

### Lua 스크립트를 사용하는 이유

수강 인원 증가와 정원 검사는 반드시 **하나의 원자적 연산**이어야 한다.

**Lua 스크립트 없이 개별 명령을 사용할 경우:**
```
시간 →
Thread A: INCR course:enrolled:1  → 30    ← 통과
Thread B: INCR course:enrolled:1  → 31    ← 초과 감지, DECR 필요
Thread C: INCR course:enrolled:1  → 32    ← 초과 감지, DECR 필요
Thread B:                         DECR → 31
Thread C:                         DECR → 30
```
INCR과 비교 사이에 다른 요청이 끼어들어 카운터가 일시적으로 정원을 초과한다.
DB 비관적 락이 최종 방어선이므로 실제 정원 초과 수강은 발생하지 않지만,
강좌 목록 조회 시 순간적으로 **잘못된 수강 인원이 노출**된다.

**Lua 스크립트 사용 시:**
```lua
local enrolled = redis.call('INCR', KEYS[1])        -- 수강 인원 증가
local capacity = tonumber(redis.call('GET', KEYS[2])) -- 정원 조회
if capacity and enrolled > capacity then
  redis.call('DECR', KEYS[1])                        -- 자동 롤백
  return -1                                          -- 정원 초과
end
return enrolled                                      -- 성공
```
Redis는 Lua 스크립트를 **단일 명령처럼 원자적으로 실행**한다.
INCR → 비교 → 조건부 DECR 전체가 중간에 끼어들 수 없이 한 번에 처리되므로
카운터가 정원을 초과하는 순간이 존재하지 않는다.

### Redis-DB 동기화

- **서버 시작 시**: `DataInitializer`에서 DB의 강좌 데이터를 읽어 Redis 카운터 초기화
- **운영 중**: Redis INCR/DECR과 DB `course.enroll()`/`course.cancel()`이 동일 트랜잭션 내에서 동기화
- **장애 복구**: 서버 재시작 시 DB(source of truth)에서 Redis 재초기화
- **Redis 장애 시**: `ObjectProvider`를 통한 graceful degradation → DB 직접 조회 (Phase 1 동작)

### 레이어 책임 분리

```
api (Controller)
  └─ CacheManager로 정적 강좌 정보 캐시 관리
  └─ CourseCounterService에서 수강 인원 조회
  └─ CachedCourse + enrolled 카운터를 조합하여 Response DTO 생성

service
  └─ CourseCounterService: Redis 원자 연산 캡슐화 (INCR/DECR/MGET)
  └─ EnrollmentService: fast rejection + DB 검증 + 보상 로직
  └─ CourseService: 도메인 객체 반환 (변경 없음)

domain
  └─ Course.enroll()/cancel(): DB 레벨 enrolled 관리 (변경 없음)
```

Service 레이어는 도메인 객체만 반환한다. DTO 변환과 캐시 조합은 Controller의 책임이다.

## 결과

- **강좌 목록 조회**: DB 히트 0회 (캐시 + Redis 카운터)
- **정원 초과 요청**: Redis 단에서 즉시 거절, DB 부하 없음
- **수강 인원 정확도**: 실시간 (캐시 무효화 지연 없음)
- **캐시 무효화 문제 없음**: 정적/동적 데이터가 분리되어 있으므로
