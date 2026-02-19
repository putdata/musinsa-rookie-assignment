# ADR-007: getResult() NOT_FOUND 레이스 컨디션 제거

## 상태
Accepted

## 맥락
Phase 3 대기열 시스템에서 `getResult()` 호출 시 처리 중인 요청이 `NOT_FOUND`로 오인되는 레이스 컨디션이 존재했다.

### 문제 흐름
1. `processQueue()`의 `popMin`이 ZSET에서 토큰을 제거
2. 워커 풀에 비동기 제출 → `processEnrollment()` 실행 대기
3. **이 구간에서 `getResult()` 호출 시**: result 없음 + ZSET 없음 → `NOT_FOUND` 반환
4. 클라이언트가 토큰 유실로 오판하고 폴링 중단

### 키 생존 구간
| 단계 | ZSET | request:{token} | result:{token} |
|------|------|-----------------|----------------|
| enqueue 후 | O | O (TTL 30분) | X |
| 큐 대기 중 | O | O | X |
| popMin 후 (처리 중) | **X** | **O** | X |
| saveResult 후 | X | O | O |
| finally delete 후 | X | X | O |

핵심: `request:{token}`은 `processEnrollment()`의 `finally` 블록에서 삭제되므로, popMin~saveResult 구간에서도 존재한다.

## 선택지
1. **Write-side: PROCESSING 상태 선저장** — `processQueue()`에서 popMin 직후, 워커 제출 전에 `saveResult(token, "PROCESSING", ...)`을 호출. 배치당 `BATCH_SIZE × 3`(HSET 2 + EXPIRE 1) = 150회 추가 Redis 쓰기 발생 (매 100ms).
2. **Read-side: getResult() 판단 순서 변경** — `request:{token}` 존재 여부를 ZSET보다 먼저 확인. 추가 쓰기 없이 기존 데이터 활용. NOT_FOUND 경로에서만 읽기 1회 추가 (O(1)).

## 결정
**옵션 2 (Read-side)** 채택. `getResult()` 판단 순서를 다음과 같이 변경:

```
1. result:{token}  → 결과 반환           (정상 완료)
2. request:{token} → WAITING             (대기 중 또는 처리 중, seq로 순번 계산)
3. ZSET score      → WAITING fallback    (request TTL 30분 만료 엣지케이스)
4. 전부 없음       → NOT_FOUND
```

### ZSET fallback을 남긴 이유
`request:{token}`에는 `EXPIRE 1800`(30분)이 걸려 있지만 ZSET에는 TTL이 없다. 큐 체류 시간이 30분을 초과하는 극단적 상황에서 request 해시가 먼저 만료될 수 있으므로, ZSET을 fallback으로 유지한다.

### 선택 근거
- 추가 Redis 쓰기 없음 (옵션 1 대비 배치당 150회 쓰기 절약)
- `request:{token}`은 이미 존재하는 데이터를 활용하므로 별도 상태 관리 불필요
- HGET은 O(1)이며, 추가 호출은 NOT_FOUND 직전에만 발생

## 결과
- popMin~saveResult 구간의 NOT_FOUND 오인 제거
- 클라이언트 폴링 중단 방지
- 성능 영향 무시할 수준 (NOT_FOUND 경로에서만 O(1) 읽기 1회 추가)
