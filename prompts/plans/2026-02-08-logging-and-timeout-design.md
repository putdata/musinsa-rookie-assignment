# 로깅 및 타임아웃 설정 설계

## 배경

현재 수강신청 시스템에 다음 항목이 빠져 있어 추가가 필요하다:
- 로깅 설정 (logback)
- 트랜잭션 타임아웃
- 비관적 락 타임아웃
- HikariCP 커넥션 풀 명시적 설정

## 설계

### 1. 로깅

개발 관점의 단일 환경 설정. 프로파일 분리 없음.

#### logback-spring.xml (신규 생성)

`src/main/resources/logback-spring.xml`에 생성한다.

**로그 패턴:**
```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

**로그 레벨:**
| Logger | Level | 용도 |
|--------|-------|------|
| ROOT | INFO | 기본 |
| `com.musinsa` | DEBUG | 비즈니스 로직 흐름 추적 |
| `org.hibernate.SQL` | DEBUG | 실행 SQL 확인 |
| `org.hibernate.orm.jdbc.bind` | TRACE | 바인드 파라미터 확인 |

콘솔 출력만 사용한다.

#### 비즈니스 로깅 위치

`EnrollmentService`에 핵심 로그를 추가한다:

| 이벤트 | 레벨 | 예시 |
|--------|------|------|
| 수강신청 시도 | INFO | `수강신청 시도 - studentId={}, courseId={}` |
| 수강신청 성공 | INFO | `수강신청 성공 - enrollmentId={}, studentId={}, courseId={}` |
| 수강취소 시도 | INFO | `수강취소 시도 - enrollmentId={}` |
| 수강취소 성공 | INFO | `수강취소 성공 - enrollmentId={}` |
| 비즈니스 규칙 위반 | WARN | `정원 초과 - courseId={}, 현재/최대={}/{}` |
| 락/타임아웃 실패 | WARN | 예외 처리기에서 기록 |

#### application.yml 변경

기존 `show-sql: true`, `format_sql: true` 설정은 logback으로 대체하므로 제거한다.

### 2. 트랜잭션 타임아웃

`EnrollmentService`의 수강신청/취소 메서드에 5초 타임아웃을 설정한다.

```java
@Transactional(timeout = 5)
```

적용 대상:
- `enroll()` 메서드
- `cancel()` 메서드

조회성 API에는 적용하지 않는다.

### 3. 락 타임아웃

비관적 락을 사용하는 모든 Repository 메서드에 3초(3000ms) 타임아웃을 설정한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
```

적용 대상:
- `CourseRepository.findByIdWithLock()`
- `StudentRepository.findByIdWithLock()`
- `EnrollmentRepository.findByIdWithLock()`

### 4. HikariCP 커넥션 풀

`application.yml`에 기본값을 명시적으로 선언한다.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 5. 타임아웃 예외 처리

기존 글로벌 예외 처리기에 두 가지 핸들러를 추가한다.

| 예외 | HTTP 상태 | 로그 레벨 | 메시지 |
|------|-----------|-----------|--------|
| `PessimisticLockingFailureException` | 409 Conflict | WARN | 다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요. |
| `TransactionTimedOutException` | 503 Service Unavailable | WARN | 서버가 일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요. |

## 변경 대상 파일

| 파일 | 변경 유형 |
|------|----------|
| `src/main/resources/logback-spring.xml` | 신규 생성 |
| `src/main/resources/application.yml` | 수정 (HikariCP 추가, show-sql 제거) |
| `EnrollmentService.java` | 수정 (`@Transactional(timeout=5)`, 로깅 추가) |
| `CourseRepository.java` | 수정 (`@QueryHints` 추가) |
| `StudentRepository.java` | 수정 (`@QueryHints` 추가) |
| `EnrollmentRepository.java` | 수정 (`@QueryHints` 추가) |
| 글로벌 예외 처리기 | 수정 (타임아웃 예외 핸들러 추가) |
