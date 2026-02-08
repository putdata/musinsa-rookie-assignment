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

## 의사결정 기록

| ADR | 제목 | 상태 |
|-----|------|------|
| [001](docs/decisions/001-project-structure.md) | 프로젝트 구조 및 아키텍처 | Accepted |
| [002](docs/decisions/002-tech-stack.md) | 기술 스택 선택 | Accepted |
