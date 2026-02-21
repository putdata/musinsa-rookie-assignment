# ADR-008: Redis 카운터를 read-only 체크 후 DB 성공 시 증가로 변경

## 상태
채택됨 (Supersedes [ADR-004](004-redis-atomic-enrollment-counter.md))

## 맥락

ADR-004에서는 수강신청 시 **Redis Lua Script로 INCR + 정원 검사를 원자적으로 실행**하고, DB 실패 시 DECR로 보상하는 방식을 채택했다.

```
[ADR-004 방식]
1. Redis Lua: INCR → 정원 비교 → 초과 시 DECR (원자적)
2. DB 비관적 락 + 비즈니스 검증
3. DB 실패 시 Redis DECR 보상
```

운영 중 다음 문제가 발견되었다:

### 문제 1: 보상 로직의 복잡성
DB 단계에서 실패할 수 있는 경우가 다양하다 (중복 신청, 학점 초과, 시간표 충돌). 모든 실패 경로에서 Redis DECR 보상이 누락되면 **카운터가 실제보다 높게 유지**되어 정원이 남았는데도 거절하는 문제가 발생한다.

### 문제 2: 불필요한 INCR-DECR 왕복
정원 30명인 강좌에 이미 30명이 찬 상태에서 수백 명이 신청하면, 매 요청마다 INCR → 초과 감지 → DECR이 실행된다. 읽기 전용 체크로 충분한 상황에서 **쓰기 연산 2회가 낭비**된다.

## 선택지

### 1. 현행 유지 (INCR-first + 보상 DECR)
- **장점**: 카운터가 실시간 정확 (성공 즉시 반영)
- **단점**: 보상 누락 위험, 정원 초과 후 불필요한 쓰기 연산

### 2. Read-only 체크 + DB 성공 후 INCR
```
1. Redis GET (read-only): 정원 초과면 즉시 거절
2. DB 비관적 락 + 비즈니스 검증 (최종 방어)
3. DB 성공 후에만 Redis INCR
```
- **장점**: 보상 로직 불필요, 정원 초과 시 쓰기 연산 없음
- **단점**: Redis 카운터가 DB보다 약간 느리게 반영 (DB 커밋 ~ INCR 사이의 미세 지연)

## 결정

**선택지 2를 채택한다.**

### 아키텍처

```
POST /api/enrollments 요청
  ├─ 1단계: Redis isFull() 읽기 전용 체크 (Lua Script)
  │    └─ GET enrolled + GET capacity → 비교 (카운터 변경 없음)
  │    └─ 정원 초과 시 즉시 거절 (DB까지 안 감)
  ├─ 2단계: DB 비관적 락 + 비즈니스 검증 (최종 방어)
  │    └─ 중복 신청, 학점 초과, 시간표 충돌, 정원 초과 검증
  ├─ 3단계: DB 성공 후 Redis INCR
  │    └─ 실패 시 보상 불필요 (INCR 자체가 안 일어남)
  └─ 결과: 보상 로직 제거, 코드 단순화

DELETE /api/enrollments/{id} 요청
  ├─ DB 비관적 락 + 삭제
  └─ Redis DECR
```

### Lua Script 역할 변경

ADR-004에서는 INCR + 정원 검사를 원자적으로 실행하는 Lua Script를 사용했다.
ADR-008에서는 **읽기 전용 정원 확인**을 위한 Lua Script로 변경한다.

```lua
-- ADR-004: INCR-first (쓰기)
local enrolled = redis.call('INCR', KEYS[1])
local capacity = tonumber(redis.call('GET', KEYS[2]))
if capacity and enrolled > capacity then
  redis.call('DECR', KEYS[1])
  return -1
end
return enrolled

-- ADR-008: read-only (읽기)
local enrolled = tonumber(redis.call('GET', KEYS[1]) or '0')
local capacity = tonumber(redis.call('GET', KEYS[2]) or '0')
if capacity > 0 and enrolled >= capacity then return 1 end
return 0
```

Lua Script를 사용하는 이유는 동일하다: GET 2회를 **원자적으로 실행**하여 읽기 사이에 다른 요청이 끼어들어 불일치가 발생하는 것을 방지한다.

### 카운터 미세 지연의 영향

DB 커밋과 Redis INCR 사이에 미세한 지연이 존재한다. 이 틈에 다른 요청이 `isFull()` 체크를 통과할 수 있지만:

- DB 비관적 락이 **최종 방어선**으로 정원 초과를 절대 허용하지 않음
- 영향은 "정원이 찼는데 한 건 더 DB까지 내려감" 수준이며, 정원 초과 수강은 발생하지 않음
- fast rejection의 목적은 **대부분의 불필요한 DB 접근을 사전 차단**하는 것이므로 100% 정확할 필요 없음

### ADR-004에서 유지되는 결정

- 정적/동적 데이터 분리 전략
- Redis 카운터 기반 수강 인원 관리
- Redis-DB 동기화 방식 (서버 시작 시 초기화, 장애 시 재초기화)
- `ObjectProvider`를 통한 graceful degradation
- 레이어 책임 분리 (Controller: 캐시 조합, Service: 비즈니스 로직)

## 결과

- **보상 로직 제거**: DECR 보상이 불필요하여 코드 단순화
- **정원 초과 시 쓰기 연산 제거**: read-only 체크로 Redis 부하 감소
- **정원 초과 0건 유지**: DB 비관적 락이 최종 방어선
- **Phase 2 성능**: Phase 0 대비 avg -91%, p95 -91% (ADR-004와 동일 수준)
