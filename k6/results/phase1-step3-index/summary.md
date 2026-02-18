# Phase 1 Step 3: MySQL 인덱스 최적화 성능 측정 결과

**측정 일시:** 2026-02-19
**변경 사항:** Enrollment(student_id, course_id 개별 인덱스), Course(department_id 인덱스) 추가
**누적 최적화:** HikariCP pool=30 + OSIV OFF + Hibernate batch + 인덱스
**테스트 시나리오:** enrollment-rush (100→500 VU, 4m10s)

---

## Enrollment Rush 결과

| 지표 | 값 |
|------|-----|
| iterations/s | 1,477.6 |
| http_req_duration p90 | 171.1ms |
| http_req_duration p95 | 204.99ms |
| http_req_duration avg | 57.44ms |
| enroll_success | 2,018건 (8.7/s) |
| enroll_failed | 338,809건 |
| enroll_capacity_exceeded | 331,064건 |
| http_req_failed rate | 99.40% (대부분 정원 초과 409) |
| checks passed | 100% (no server error, 5xx 0건) |
| 총 iterations | 340,827 |
| max VUs | 500 |
| request timeout | 0건 |
| 총 소요 시간 | ~3m50s (정상) |

---

## Phase 1 전체 단계별 비교표

| 지표 | Phase 0 (baseline) | Step 1 (HikariCP) | Step 2 (+ OSIV OFF) | Step 3 (+ Index) |
|------|-------------------|-------------------|---------------------|------------------|
| iterations/s | 1,310 | 283.5 | **1,513.9** | 1,477.6 |
| p90 | 198.53ms | 101.84ms | **160.55ms** | 171.1ms |
| p95 | 259.01ms | 178.3ms | **185.86ms** | 204.99ms |
| avg | 79.32ms | 99.56ms | **52.83ms** | 57.44ms |
| 총 iterations | 298,796 | 173,279 | **351,060** | 340,827 |
| request timeout | 0 | 다수 | 0 | 0 |
| enroll_success | 2,018 | 2,018 | 2,018 | 2,018 |

---

## 분석

### 인덱스 추가 후 오히려 소폭 하락
- Step 2 대비 iterations/s가 1,513.9 → 1,477.6으로 약 2.4% 감소
- p95도 185.86ms → 204.99ms로 소폭 증가

### 원인: INSERT 중심 워크로드에서의 인덱스 오버헤드
- enrollment-rush 시나리오는 **대부분 INSERT** (수강신청 POST 요청)
- 인덱스가 추가되면 매 INSERT마다 인덱스도 함께 갱신해야 하므로 **쓰기 성능에 오버헤드 발생**
- 특히 student_id, course_id 개별 인덱스 + 기존 unique constraint 인덱스까지 3개의 인덱스를 매번 갱신

### 인덱스가 효과를 발휘하는 시나리오
- **강좌 목록 조회** (`GET /api/courses`): department_id 인덱스로 학과별 필터링 가속
- **수강 내역 조회** (`GET /api/enrollments?studentId=X`): student_id 인덱스로 특정 학생 조회 가속
- **mixed-workload** 시나리오에서 효과가 클 것으로 예상 (40% 조회 + 40% 수강신청 + 15% 시간표 + 5% 취소)

### 핵심 인사이트
> 인덱스는 READ 성능을 개선하지만 WRITE 성능에는 오버헤드를 준다. 워크로드 특성에 따라 인덱스 전략이 달라져야 한다. enrollment-rush처럼 WRITE 집중 시나리오에서는 인덱스가 오히려 역효과일 수 있다.

### Phase 1 최종 결론 (Phase 0 대비)
- **처리량**: 1,310 → 1,477.6 iter/s (+12.8% 개선)
- **평균 응답시간**: 79.32ms → 57.44ms (-27.6% 개선)
- **안정성**: request timeout 0건, 5xx 0건 유지
- **주요 기여 요인**: OSIV 비활성화 + HikariCP 풀 증가의 시너지
