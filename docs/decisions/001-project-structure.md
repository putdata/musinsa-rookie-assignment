# ADR-001: 프로젝트 구조 및 아키텍처

## 상태
Accepted (Updated)

## 맥락
대학교 수강신청 REST API 서버를 구축해야 한다. 프로젝트 구조와 아키텍처 패턴을 결정해야 했다.

## 선택지

### 프로젝트 구조
1. **루트 단일 프로젝트** — 간단한 구조, 빌드/실행이 직관적
2. **backend/frontend 분리** — 프론트엔드 확장 가능, 하지만 현재 서버만 요구

### 아키텍처 패턴
1. **레이어드 아키텍처 (api → service → domain)** — 관심사 분리 명확, 테스트 용이
2. **헥사고날 아키텍처** — 유연하지만 이 규모에서 과도
3. **단순 패키지 분리** — 빠르지만 확장 시 복잡도 증가

## 결정
- **루트 단일 프로젝트** 선택 (PROBLEM.md가 서버만 요구)
- **레이어드 아키텍처** 선택 (관심사 분리 + 적절한 복잡도)

### 패키지 구조
```
com.musinsa/
├── ApiApplication.java    # 최상위 (자동 컴포넌트 스캔)
├── api/                   # Controller, DTO
├── service/               # 비즈니스 로직
└── domain/                # Entity, Repository
```

- 각 레이어 내 서브도메인별 패키지 분리 (student, course, professor, enrollment, department)
- DTO는 `api/{도메인}/dtos/` 폴더에 `*Dtos.java` 파일로 관리 (Request/Response record 포함)

## 결과
- 단방향 의존으로 레이어 간 결합도 최소화
- `ApiApplication.java`가 최상위에 있어 `@ComponentScan` 불필요
- 평가자가 `./gradlew bootRun` 한 번으로 바로 실행 가능
