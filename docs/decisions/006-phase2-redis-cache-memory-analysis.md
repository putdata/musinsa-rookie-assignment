# ADR-006: Phase 2 강좌 캐시 Redis 메모리 적재 가능성 분석

## 상태
Accepted

## 맥락

Phase 2는 강좌 조회 성능을 위해 Redis 캐시(`courses::*`)와 Redis 카운터(`course:enrolled:*`, `course:capacity:*`)를 함께 사용한다.
성능 개선 효과는 확인되었지만, "강좌 캐시를 Redis에 올렸을 때 메모리 사용량이 운영상 안전한가"에 대한 정량 근거가 필요했다.

본 ADR은 **분석 방법(재현 가능한 측정 절차)** 과 **실측 결과(바이트 단위)** 를 기록한다.

## 분석 대상과 범위

- 대상 Phase: `phase2-redis`
- 데이터 스케일: 강좌 500개, 학과 10개 (초기화 코드 기준)
- Redis 키 범위:
  - 카운터: `course:enrolled:{id}`, `course:capacity:{id}`
  - 강좌 캐시: `courses::all`, `courses::{department}`
- 제외 범위:
  - Redis 프로세스 고정 시작 메모리(`used_memory_startup`)
  - OS 레벨 RSS 및 allocator fragment (데이터 크기 자체 판단에서 분리)

## 분석 방법

### 1) 코드 기반 구조 확인

아래 코드로 키 구조/개수 상한/TTL을 먼저 확정했다.

- 캐시 키 생성 위치: `phase2-redis/src/main/java/com/musinsa/api/course/CourseController.java`
  - 키: `all` 또는 `department` 문자열
- 캐시 DTO 필드: `phase2-redis/src/main/java/com/musinsa/api/course/dtos/CourseDtos.java`
  - 정적 필드(`id`, `name`, `credits`, `capacity`, `schedule`, `departmentName`, `professorName`)만 캐시
- 카운터 키 prefix: `phase2-redis/src/main/java/com/musinsa/service/course/CourseCounterService.java`
- 캐시 TTL: `phase2-redis/src/main/java/com/musinsa/config/RedisConfig.java`
  - `Duration.ofHours(1)`
- 데이터 스케일 근거: `phase2-redis/src/main/java/com/musinsa/config/DataInitializer.java`
  - 강좌 500개, 학과 10개

### 2) Redis 실측 절차

측정 일시: **2026-02-20**

1. `phase2-redis` 서버를 `mysql,redis` 프로파일로 실행
2. `/api/courses` 전체 + 학과별 10개를 호출해 캐시 생성
3. `redis-cli --scan`으로 키 개수/패턴 확인
4. `redis-cli MEMORY USAGE <key>`로 키별 실제 메모리 사용량 측정
5. `redis-cli INFO memory`로 전체 메모리와 델타 확인
6. `courses*` 키 삭제 전/후 `used_memory`를 비교해 캐시 순증분 검증

핵심 측정 명령:

```bash
redis-cli --scan --pattern 'course:enrolled:*' | xargs -n1 redis-cli MEMORY USAGE
redis-cli --scan --pattern 'course:capacity:*' | xargs -n1 redis-cli MEMORY USAGE
redis-cli --scan --pattern 'courses*' | xargs -n1 redis-cli MEMORY USAGE
redis-cli INFO memory
```

## 측정 결과

### 키 개수

- `course:enrolled:*`: 500개
- `course:capacity:*`: 500개
- `courses*`: 11개 (`all` 1 + 학과별 10)
- 전체: 1,011개

### MEMORY USAGE 합계

| 범주 | 키 수 | 합계 |
|------|------:|-----:|
| `course:enrolled:*` | 500 | 32,000B |
| `course:capacity:*` | 500 | 32,000B |
| `courses*` | 11 | 92,896B |
| **합계(위 3개 범주)** | 1,011 | **156,896B** |

추가 관찰:

- 카운터 키는 **키당 64B**로 균일
- `courses::all`: 41,016B (`STRLEN` 38,964B)
- 학과별 캐시 10개: 각 5,184~5,192B (`STRLEN` 약 4.2~4.5KB)

### used_memory 델타(강좌 캐시 순증분)

- `courses*` 삭제 전 `used_memory`: 1,964,792B
- `courses*` 삭제 후 `used_memory`: 1,865,400B
- **강좌 캐시 순증분**: **99,392B** (약 97KiB)

즉, `courses*` 도입으로 Redis 프로세스 총 메모리는 약 0.1MB 수준 증가했다.

## 해석

1. **메모리 부담이 작다**
   - 강좌 캐시 + 카운터 전체 키 데이터가 약 153KiB.
   - 운영 메모리 관점에서도 강좌 캐시 순증분이 약 97KiB.

2. **상한이 예측 가능하다**
   - 카운터는 강좌 수에 선형 비례(2 * courseCount).
   - 캐시는 현재 설계상 `all + 학과 수` 수준으로 작은 고정 집합.

3. **TTL로 장기 누적 가능성이 낮다**
   - `courses*`는 1시간 TTL.
   - 재요청이 없으면 자연 만료됨.

4. **주의점**
   - 현재 캐시 키가 `department` 원문 문자열이므로, 허용되지 않은 학과명이 유입되면 캐시 키 cardinality가 증가할 수 있다.
   - 필요 시 학과명 화이트리스트 검증 또는 canonical key 매핑을 적용한다.

## 결정

현 데이터 스케일(강좌 500, 학과 10)과 현재 키 구조 기준에서,
**Phase 2 강좌 캐시를 Redis에 적재하는 것은 메모리 관점에서 안전하다고 판단한다.**

## 결과

- Redis 캐시 도입에 대한 용량 우려를 정량 수치로 해소
- 향후 스케일 확장 시 다음 계산식으로 빠른 용량 추정 가능:
  - 카운터 메모리 ≈ `2 * courseCount * 64B`
  - 강좌 캐시 메모리 ≈ `courses::all + (departmentCount * per-department-cache-size)`
- 운영 가드레일:
  - `used_memory`, `dbsize`, `keys courses*` 모니터링
  - department 파라미터 검증으로 캐시 키 폭증 방지
