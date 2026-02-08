# ADR-002: 기술 스택 선택

## 상태
Accepted (Updated)

## 맥락
대학교 수강신청 REST API 서버를 구축해야 한다. PROBLEM.md에서 서버 측만 요구하며, 평가 기준에 빌드/실행 가능한 프로젝트 구성과 문서화가 포함되어 있다.

## 선택지

### 언어
1. **Java 21** — LTS, 강력한 타입 시스템, Spring 생태계
2. **Kotlin** — 간결한 문법, Java 호환
3. **Python** — 빠른 프로토타이핑, 동시성 제어 불리
4. **JavaScript** — 비동기 기반, 타입 안정성 부족

### 프레임워크
1. **Spring Boot 3.5** — JPA, 트랜잭션, 동시성 제어, SpringDoc API 문서 자동 생성
2. **순수 Java** — 의존성 최소화, 하지만 DB/동시성 직접 구현 필요

### 데이터베이스
1. **H2 (내장)** — 별도 설치 불필요, 평가자가 바로 실행 가능
2. **MySQL/PostgreSQL** — 실환경에 가깝지만 외부 설치 필요

### 주요 라이브러리
1. **Lombok** — 보일러플레이트 제거, DDD 스타일 (@Setter 금지)
2. **SpringDoc OpenAPI** — Swagger UI 자동 생성
3. **Spring Data JPA** — Repository 추상화, 비관적 락 지원

## 결정
- 언어: **Java 21** (LTS, 동시성 제어에 유리)
- 프레임워크: **Spring Boot 3.5.10** (JPA 비관적 락, 트랜잭션 관리)
- 데이터베이스: **H2 내장 DB** (외부 의존성 제거, 평가자 편의)
- 라이브러리: **Lombok**, **SpringDoc OpenAPI 2.8.15**

## 결과
- 평가자가 Java 21만 설치하면 `./gradlew bootRun`으로 바로 실행 가능
- Swagger UI를 통한 API 문서 자동 제공
- JPA 비관적 락으로 동시성 제어 구현 용이
- Lombok으로 Entity/Service 보일러플레이트 최소화
