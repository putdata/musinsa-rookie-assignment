# 대학교 수강신청 시스템

## 프로젝트 개요
동시성 제어가 핵심인 대학교 수강신청 REST API 서버입니다.
정원이 1명 남은 강좌에 100명이 동시에 신청해도, 정확히 1명만 성공합니다.

## 기술 스택

| 항목 | 기술 | 버전 |
|------|------|------|
| 언어 | Java | 21 |
| 프레임워크 | Spring Boot | 3.5.10 |
| 빌드 도구 | Gradle | 8.12 |
| 데이터베이스 | H2 (내장) | - |
| API 문서 | SpringDoc OpenAPI | 2.8.15 |

## 사전 요구사항
- Java 21+

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# 서버 실행
./gradlew bootRun

# 테스트
./gradlew test
```

## 서버 접속 정보

| 항목 | URL |
|------|-----|
| API 서버 | http://localhost:8080 |
| Health Check | http://localhost:8080/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| H2 Console | http://localhost:8080/h2-console |

> H2 Console 접속 시 JDBC URL: `jdbc:h2:mem:musinsadb`, 사용자명: `sa`, 비밀번호: (없음)

## 프로젝트 구조

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

## 문서

| 문서 | 설명 |
|------|------|
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | 요구사항 분석 및 설계 결정 |
| [docs/api/endpoints.md](docs/api/endpoints.md) | REST API 상세 명세 |
| [docs/decisions/](docs/decisions/) | Architecture Decision Records |

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
