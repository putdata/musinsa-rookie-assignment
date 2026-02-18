# k6 부하 테스트 기반 점진적 성능 개선 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** k6 부하 테스트 환경을 구축하고, Phase별 독립 프로젝트 디렉토리에서 기술을 도입하면서 수강신청 시스템의 성능을 점진적으로 개선한다.

**Architecture:** 기존 루트 소스코드를 phase0-baseline/으로 이동하고, 각 Phase를 독립 Spring Boot 프로젝트로 분리한다. k6 스크립트와 Docker Compose는 루트에서 공유한다. Phase N은 Phase N-1을 복사한 뒤 변경사항만 적용한다.

**Tech Stack:** k6, Docker Compose, MySQL 8.0, Redis 7, Spring Boot 3.5, Spring Data Redis

**설계 문서:** `docs/plans/2026-02-18-k6-load-testing-progressive-improvement-design.md`

**프로젝트 구조:**
```
/
├── phase0-baseline/            # Phase 0 - MySQL 전환 (기존 코드 이동)
│   ├── build.gradle
│   ├── gradlew / gradlew.bat
│   ├── settings.gradle
│   ├── gradle/
│   └── src/
├── phase1-optimization/        # Phase 1 - HikariCP, 쿼리/인덱스 최적화
├── phase2-redis/               # Phase 2 - Redis 캐시
├── phase3-queue/               # Phase 3 - Redis Sorted Set 대기열
├── k6/                         # 공유 - k6 테스트 스크립트
│   ├── scripts/
│   ├── scenarios/
│   ├── helpers/
│   └── results/
├── docker-compose.yml          # 공유 - MySQL + Redis 인프라
├── docs/                       # 공유 - 설계/결과 문서
├── prompts/
└── CLAUDE.md
```

---

## Phase 0: 프로젝트 구조 재편 + MySQL 전환 + Baseline k6 측정

### Task 1: Docker Compose로 MySQL + Redis 인프라 구성

**Files:**
- Create: `docker-compose.yml` (루트)

**Step 1: docker-compose.yml 작성**

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: musinsa-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: musinsadb
      MYSQL_USER: musinsa
      MYSQL_PASSWORD: musinsa
      TZ: Asia/Seoul
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    volumes:
      - mysql-data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    container_name: musinsa-redis
    ports:
      - "6379:6379"

volumes:
  mysql-data:
```

**Step 2: 컨테이너 시작 및 연결 확인**

Run: `docker compose up -d && sleep 5 && docker compose exec mysql mysql -umusinsa -pmusinsa -e "SELECT 1"`
Expected: 연결 성공

**Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "chore: Docker Compose MySQL + Redis 인프라 구성"
```

---

### Task 2: 기존 소스코드를 phase0-baseline/으로 이동

**Step 1: phase0-baseline 디렉토리로 프로젝트 파일 복사**

```bash
mkdir phase0-baseline
cp -r src/ phase0-baseline/
cp build.gradle settings.gradle gradlew gradlew.bat phase0-baseline/
cp -r gradle/ phase0-baseline/
```

**Step 2: phase0-baseline/settings.gradle 수정**

```groovy
rootProject.name = 'phase0-baseline'
```

**Step 3: 루트의 소스 파일 정리**

루트에서 src/, build.gradle, settings.gradle, gradlew, gradlew.bat, gradle/ 삭제.
docs/, prompts/, CLAUDE.md, docker-compose.yml, .gitignore는 루트에 유지.

```bash
rm -rf src/ gradle/
rm -f build.gradle settings.gradle gradlew gradlew.bat
```

**Step 4: phase0-baseline에서 기존 테스트 통과 확인**

Run: `cd phase0-baseline && ./gradlew test`
Expected: 기존 테스트 전부 PASS

**Step 5: Commit**

```bash
git add -A
git commit -m "refactor: 기존 소스코드를 phase0-baseline/ 디렉토리로 이동"
```

---

### Task 3: phase0-baseline MySQL 전환

**Files:**
- Modify: `phase0-baseline/build.gradle`
- Create: `phase0-baseline/src/main/resources/application-mysql.yml`

**Step 1: build.gradle에 MySQL 드라이버 추가**

`phase0-baseline/build.gradle`의 dependencies 블록에 추가:
```groovy
runtimeOnly 'com.mysql:mysql-connector-j'
```

**Step 2: application-mysql.yml 프로파일 작성**

`phase0-baseline/src/main/resources/application-mysql.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/musinsadb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: musinsa
    password: musinsa
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

  h2:
    console:
      enabled: false

  jpa:
    hibernate:
      ddl-auto: create
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        dialect: org.hibernate.dialect.MySQLDialect
```

**Step 3: MySQL 프로파일로 서버 시작 확인**

Run: `cd phase0-baseline && ./gradlew bootRun --args='--spring.profiles.active=mysql'`
Expected: 서버 시작 성공, DataInitializer가 10,000명 학생/500개 강좌 생성

**Step 4: Health Check 및 API 동작 확인**

Run: `curl -s http://localhost:8080/health`
Expected: `{"status":"ok"}`

Run: `curl -s http://localhost:8080/api/courses | python3 -m json.tool | head -20`
Expected: 강좌 목록 JSON 배열 반환

**Step 5: 기존 테스트 통과 확인 (H2)**

Run: `cd phase0-baseline && ./gradlew test`
Expected: 기존 테스트 전부 PASS (테스트는 H2 인메모리 사용)

**Step 6: Commit**

```bash
git add phase0-baseline/
git commit -m "feat(phase0): MySQL DataSource 프로파일 추가"
```

---

### Task 4: k6 helpers 및 설정 파일 작성

**Files:**
- Create: `k6/helpers/config.js`
- Create: `k6/helpers/data.js`

**Step 1: config.js 작성**

```javascript
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const THRESHOLDS = {
  http_req_duration: ['p(95)<500', 'p(99)<1000'],
  http_req_failed: ['rate<0.05'],
};
```

**Step 2: data.js 작성**

```javascript
export const STUDENT_COUNT = 10000;
export const COURSE_COUNT = 500;

export function randomStudentId() {
  return Math.floor(Math.random() * STUDENT_COUNT) + 1;
}

export function randomCourseId() {
  return Math.floor(Math.random() * COURSE_COUNT) + 1;
}

// 인기 강좌 (1~50번) - 동시성 경합 테스트용
export function popularCourseId() {
  return Math.floor(Math.random() * 50) + 1;
}
```

**Step 3: Commit**

```bash
git add k6/
git commit -m "chore(k6): 테스트 설정 및 헬퍼 파일 작성"
```

---

### Task 5: k6 smoke test 작성

**Files:**
- Create: `k6/scripts/smoke-test.js`

**Step 1: smoke-test.js 작성**

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../helpers/config.js';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const healthRes = http.get(`${BASE_URL}/health`);
  check(healthRes, {
    'health: status 200': (r) => r.status === 200,
  });

  const coursesRes = http.get(`${BASE_URL}/api/courses`);
  check(coursesRes, {
    'courses: status 200': (r) => r.status === 200,
    'courses: has data': (r) => JSON.parse(r.body).length > 0,
  });

  const enrollPayload = JSON.stringify({
    studentId: 1,
    courseId: Math.floor(Math.random() * 500) + 1,
  });
  const enrollRes = http.post(`${BASE_URL}/api/enrollments`, enrollPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(enrollRes, {
    'enroll: status 201 or 4xx': (r) => r.status === 201 || (r.status >= 400 && r.status < 500),
  });

  const scheduleRes = http.get(`${BASE_URL}/api/enrollments?studentId=1`);
  check(scheduleRes, {
    'schedule: status 200': (r) => r.status === 200,
  });

  sleep(1);
}
```

**Step 2: smoke test 실행 (phase0-baseline 서버 실행 상태에서)**

Run: `k6 run k6/scripts/smoke-test.js`
Expected: 모든 체크 통과

**Step 3: Commit**

```bash
git add k6/scripts/smoke-test.js
git commit -m "test(k6): smoke test 작성"
```

---

### Task 6: k6 수강신청 러시 시나리오 작성

**Files:**
- Create: `k6/scenarios/enrollment-rush.js`

**Step 1: enrollment-rush.js 작성**

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, THRESHOLDS } from '../helpers/config.js';
import { randomStudentId, popularCourseId } from '../helpers/data.js';

const enrollSuccess = new Counter('enroll_success');
const enrollFailed = new Counter('enroll_failed');
const enrollCapacityExceeded = new Counter('enroll_capacity_exceeded');
const enrollDuration = new Trend('enroll_duration');

export const options = {
  scenarios: {
    enrollment_rush: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '10s', target: 500 },
        { duration: '1m', target: 500 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: THRESHOLDS,
};

export default function () {
  const studentId = randomStudentId();
  const courseId = popularCourseId();

  const payload = JSON.stringify({ studentId, courseId });
  const res = http.post(`${BASE_URL}/api/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  enrollDuration.add(res.timings.duration);

  if (res.status === 201) {
    enrollSuccess.add(1);
  } else if (res.status === 409) {
    const body = JSON.parse(res.body);
    if (body.error === 'CAPACITY_EXCEEDED') {
      enrollCapacityExceeded.add(1);
    }
    enrollFailed.add(1);
  } else if (res.status >= 400) {
    enrollFailed.add(1);
  }

  check(res, {
    'enroll: no server error': (r) => r.status < 500,
  });

  sleep(0.1);
}
```

**Step 2: Commit**

```bash
git add k6/scenarios/enrollment-rush.js
git commit -m "test(k6): 수강신청 러시 시나리오 작성"
```

---

### Task 7: k6 혼합 워크로드 + load/stress/spike test 작성

**Files:**
- Create: `k6/scenarios/mixed-workload.js`
- Create: `k6/scripts/load-test.js`
- Create: `k6/scripts/stress-test.js`
- Create: `k6/scripts/spike-test.js`

**Step 1: mixed-workload.js 작성**

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, THRESHOLDS } from '../helpers/config.js';
import { randomStudentId, randomCourseId } from '../helpers/data.js';

const actionCounter = new Counter('actions');

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: THRESHOLDS,
};

export default function () {
  const rand = Math.random();

  if (rand < 0.40) {
    const res = http.get(`${BASE_URL}/api/courses`);
    check(res, { 'courses: 200': (r) => r.status === 200 });
    actionCounter.add(1, { action: 'list_courses' });
  } else if (rand < 0.80) {
    const payload = JSON.stringify({
      studentId: randomStudentId(),
      courseId: randomCourseId(),
    });
    const res = http.post(`${BASE_URL}/api/enrollments`, payload, {
      headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'enroll: no 5xx': (r) => r.status < 500 });
    actionCounter.add(1, { action: 'enroll' });
  } else if (rand < 0.95) {
    const res = http.get(`${BASE_URL}/api/enrollments?studentId=${randomStudentId()}`);
    check(res, { 'schedule: 200': (r) => r.status === 200 });
    actionCounter.add(1, { action: 'view_schedule' });
  } else {
    const enrollmentId = Math.floor(Math.random() * 1000) + 1;
    const res = http.del(`${BASE_URL}/api/enrollments/${enrollmentId}`);
    check(res, { 'cancel: no 5xx': (r) => r.status < 500 });
    actionCounter.add(1, { action: 'cancel' });
  }

  sleep(0.5);
}
```

**Step 2: load-test.js 작성**

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, THRESHOLDS } from '../helpers/config.js';
import { randomStudentId, randomCourseId } from '../helpers/data.js';

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 100 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: THRESHOLDS,
};

export default function () {
  const payload = JSON.stringify({
    studentId: randomStudentId(),
    courseId: randomCourseId(),
  });
  const res = http.post(`${BASE_URL}/api/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'no server error': (r) => r.status < 500 });
  sleep(0.5);
}
```

**Step 3: stress-test.js 작성**

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../helpers/config.js';
import { randomStudentId, randomCourseId } from '../helpers/data.js';

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 100 },
        { duration: '2m', target: 300 },
        { duration: '2m', target: 500 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.10'],
  },
};

export default function () {
  const payload = JSON.stringify({
    studentId: randomStudentId(),
    courseId: randomCourseId(),
  });
  const res = http.post(`${BASE_URL}/api/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'no server error': (r) => r.status < 500 });
  sleep(0.3);
}
```

**Step 4: spike-test.js 작성**

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../helpers/config.js';
import { randomStudentId, popularCourseId } from '../helpers/data.js';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '10s', target: 500 },
        { duration: '1m', target: 500 },
        { duration: '30s', target: 10 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.15'],
  },
};

export default function () {
  const payload = JSON.stringify({
    studentId: randomStudentId(),
    courseId: popularCourseId(),
  });
  const res = http.post(`${BASE_URL}/api/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'no server error': (r) => r.status < 500 });
  sleep(0.1);
}
```

**Step 5: Commit**

```bash
git add k6/
git commit -m "test(k6): 혼합 워크로드 + load/stress/spike test 작성"
```

---

### Task 8: Phase 0 Baseline 성능 측정

**Step 1: phase0-baseline 서버 시작 (MySQL 프로파일)**

Run: `cd phase0-baseline && ./gradlew bootRun --args='--spring.profiles.active=mysql'`

**Step 2: smoke test로 기본 동작 확인**

Run: `k6 run k6/scripts/smoke-test.js`
Expected: 모든 체크 통과

**Step 3: load test 실행 및 결과 저장**

```bash
mkdir -p k6/results/phase0-baseline
k6 run --out json=k6/results/phase0-baseline/load-test.json k6/scripts/load-test.js
```

**Step 4: enrollment-rush 시나리오 실행 (서버 재시작 후)**

```bash
k6 run --out json=k6/results/phase0-baseline/enrollment-rush.json k6/scenarios/enrollment-rush.js
```

**Step 5: 결과 요약을 k6/results/phase0-baseline/summary.md에 기록**

주요 지표: http_req_duration p95/p99, iterations/s, enroll_success/failed, http_req_failed rate

**Step 6: Commit**

```bash
git add k6/results/phase0-baseline/
git commit -m "test(k6): Phase 0 baseline 측정 결과 기록"
```

---

## Phase 1: 애플리케이션 레벨 최적화

### Task 9: phase0를 복사하여 phase1-optimization 프로젝트 생성

**Step 1: phase0-baseline 전체를 phase1-optimization으로 복사**

```bash
cp -r phase0-baseline phase1-optimization
```

**Step 2: settings.gradle 수정**

`phase1-optimization/settings.gradle`:
```groovy
rootProject.name = 'phase1-optimization'
```

**Step 3: 테스트 통과 확인**

Run: `cd phase1-optimization && ./gradlew test`
Expected: 전부 PASS

**Step 4: Commit**

```bash
git add phase1-optimization/
git commit -m "chore(phase1): phase0 기반으로 phase1-optimization 프로젝트 생성"
```

---

### Task 10: HikariCP 커넥션 풀 튜닝

**Files:**
- Modify: `phase1-optimization/src/main/resources/application-mysql.yml`

**Step 1: HikariCP 파라미터 튜닝**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 3000
      idle-timeout: 60000
      max-lifetime: 1800000
```

**Step 2: Commit**

```bash
git add phase1-optimization/
git commit -m "perf(phase1): HikariCP 커넥션 풀 파라미터 튜닝"
```

---

### Task 11: JPA 쿼리 최적화 (N+1 해결)

**Files:**
- Modify: `phase1-optimization/src/main/java/com/musinsa/domain/course/CourseRepository.java`
- Modify: `phase1-optimization/src/main/java/com/musinsa/domain/enrollment/EnrollmentRepository.java`
- Modify: `phase1-optimization/src/main/java/com/musinsa/service/course/CourseService.java`
- Modify: `phase1-optimization/src/main/java/com/musinsa/service/enrollment/EnrollmentService.java`

**Step 1: CourseRepository에 fetch join 쿼리 추가**

```java
@Query("SELECT c FROM Course c JOIN FETCH c.department JOIN FETCH c.professor")
List<Course> findAllWithDetails();
```

**Step 2: EnrollmentRepository에 fetch join 쿼리 추가**

```java
@Query("SELECT e FROM Enrollment e JOIN FETCH e.course c JOIN FETCH c.department WHERE e.student.id = :studentId")
List<Enrollment> findByStudentIdWithCourse(@Param("studentId") Long studentId);
```

**Step 3: Service에서 새 메서드 사용하도록 수정**

**Step 4: 테스트 통과 확인**

Run: `cd phase1-optimization && ./gradlew test`
Expected: 전부 PASS

**Step 5: Commit**

```bash
git add phase1-optimization/
git commit -m "perf(phase1): JPA N+1 문제 해결 - fetch join 적용"
```

---

### Task 12: MySQL 인덱스 최적화

**Files:**
- Modify: `phase1-optimization/src/main/java/com/musinsa/domain/enrollment/Enrollment.java`
- Modify: `phase1-optimization/src/main/java/com/musinsa/domain/course/Course.java`

**Step 1: Enrollment 엔티티에 인덱스 추가**

```java
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id"}),
        indexes = {
            @Index(name = "idx_enrollment_student_id", columnList = "student_id"),
            @Index(name = "idx_enrollment_course_id", columnList = "course_id")
        })
```

**Step 2: Course 엔티티에 인덱스 추가**

```java
@Table(name = "courses",
        indexes = {
            @Index(name = "idx_course_department_id", columnList = "department_id")
        })
```

**Step 3: 테스트 통과 확인**

Run: `cd phase1-optimization && ./gradlew test`
Expected: 전부 PASS

**Step 4: Commit**

```bash
git add phase1-optimization/
git commit -m "perf(phase1): MySQL 인덱스 최적화"
```

---

### Task 13: Phase 1 성능 측정

**Step 1: phase1-optimization 서버 시작**

Run: `cd phase1-optimization && ./gradlew bootRun --args='--spring.profiles.active=mysql'`

**Step 2: enrollment-rush 시나리오 실행**

```bash
mkdir -p k6/results/phase1-app-optimization
k6 run --out json=k6/results/phase1-app-optimization/enrollment-rush.json k6/scenarios/enrollment-rush.js
```

**Step 3: Phase 0 vs Phase 1 비교표를 summary.md에 작성**

**Step 4: Commit**

```bash
git add k6/results/phase1-app-optimization/
git commit -m "test(k6): Phase 1 성능 측정 결과 기록"
```

---

## Phase 2: 캐시 레이어 (Redis)

### Task 14: phase1을 복사하여 phase2-redis 프로젝트 생성

**Step 1: 복사**

```bash
cp -r phase1-optimization phase2-redis
```

**Step 2: settings.gradle 수정**

```groovy
rootProject.name = 'phase2-redis'
```

**Step 3: Commit**

```bash
git add phase2-redis/
git commit -m "chore(phase2): phase1 기반으로 phase2-redis 프로젝트 생성"
```

---

### Task 15: Redis 캐시 설정

**Files:**
- Modify: `phase2-redis/build.gradle`
- Create: `phase2-redis/src/main/resources/application-redis.yml`
- Create: `phase2-redis/src/main/java/com/musinsa/config/RedisConfig.java`

**Step 1: build.gradle에 Redis 의존성 추가**

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

**Step 2: application-redis.yml 작성**

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  cache:
    type: redis
    redis:
      time-to-live: 60000
```

**Step 3: RedisConfig.java 작성**

```java
@Configuration
@EnableCaching
@Profile("redis")
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

**Step 4: Commit**

```bash
git add phase2-redis/
git commit -m "feat(phase2): Redis 캐시 설정 및 프로파일 추가"
```

---

### Task 16: 강좌 목록 캐시 적용 및 캐시 무효화

**Files:**
- Modify: `phase2-redis/src/main/java/com/musinsa/service/course/CourseService.java`
- Modify: `phase2-redis/src/main/java/com/musinsa/service/enrollment/EnrollmentService.java`

**Step 1: CourseService에 캐시 적용**

```java
@Cacheable(value = "courses", key = "'all'")
public List<Course> findAll() { ... }

@Cacheable(value = "courses", key = "#departmentName")
public List<Course> findByDepartmentName(String departmentName) { ... }
```

**Step 2: EnrollmentService에서 캐시 무효화**

```java
@CacheEvict(value = "courses", allEntries = true)
@Transactional
public Enrollment enroll(Long studentId, Long courseId) { ... }

@CacheEvict(value = "courses", allEntries = true)
@Transactional
public void cancel(Long enrollmentId) { ... }
```

**Step 3: 테스트 통과 확인**

Run: `cd phase2-redis && ./gradlew test`
Expected: 전부 PASS

**Step 4: Commit**

```bash
git add phase2-redis/
git commit -m "feat(phase2): 강좌 목록 Redis 캐시 적용 및 캐시 무효화"
```

---

### Task 17: Phase 2 성능 측정

**Step 1: phase2-redis 서버 시작**

Run: `cd phase2-redis && ./gradlew bootRun --args='--spring.profiles.active=mysql,redis'`

**Step 2: enrollment-rush + mixed-workload 시나리오 실행**

```bash
mkdir -p k6/results/phase2-redis-cache
k6 run --out json=k6/results/phase2-redis-cache/enrollment-rush.json k6/scenarios/enrollment-rush.js
# 서버 재시작 후
k6 run --out json=k6/results/phase2-redis-cache/mixed-workload.json k6/scenarios/mixed-workload.js
```

**Step 3: Phase 0~2 비교표 작성**

**Step 4: Commit**

```bash
git add k6/results/phase2-redis-cache/
git commit -m "test(k6): Phase 2 Redis 캐시 성능 측정 결과 기록"
```

---

## Phase 3: 대기열 시스템 (Redis Sorted Set)

### Task 18: phase2를 복사하여 phase3-queue 프로젝트 생성

**Step 1: 복사**

```bash
cp -r phase2-redis phase3-queue
```

**Step 2: settings.gradle 수정**

```groovy
rootProject.name = 'phase3-queue'
```

**Step 3: Commit**

```bash
git add phase3-queue/
git commit -m "chore(phase3): phase2 기반으로 phase3-queue 프로젝트 생성"
```

---

### Task 19: Redis Sorted Set 기반 대기열 구현

**Files:**
- Create: `phase3-queue/src/main/java/com/musinsa/api/queue/dtos/QueueDtos.java`
- Create: `phase3-queue/src/main/java/com/musinsa/service/queue/WaitingQueueService.java`
- Create: `phase3-queue/src/main/java/com/musinsa/api/queue/QueueController.java`

**Step 1: QueueDtos 작성**

```java
public class QueueDtos {
    public record EnterResponse(String token, long position, long totalWaiting) {}
    public record StatusResponse(String token, long position, long totalWaiting, boolean allowed) {}
}
```

**Step 2: WaitingQueueService 작성**

```java
@Service
@RequiredArgsConstructor
@Profile("redis")
public class WaitingQueueService {

    private static final String QUEUE_KEY = "enrollment:waiting-queue";
    private static final int MAX_CONCURRENT = 50;

    private final StringRedisTemplate redisTemplate;

    public QueueDtos.EnterResponse enter(Long studentId) {
        String token = studentId.toString();
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(QUEUE_KEY, token, score);

        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token);
        Long total = redisTemplate.opsForZSet().size(QUEUE_KEY);
        long position = (rank != null) ? rank + 1 : -1;
        return new QueueDtos.EnterResponse(token, position, total != null ? total : 0);
    }

    public QueueDtos.StatusResponse getStatus(String token) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, token);
        if (rank == null) {
            return new QueueDtos.StatusResponse(token, -1, 0, false);
        }

        Long total = redisTemplate.opsForZSet().size(QUEUE_KEY);
        long position = rank + 1;
        boolean allowed = position <= MAX_CONCURRENT;

        if (allowed) {
            redisTemplate.opsForZSet().remove(QUEUE_KEY, token);
        }
        return new QueueDtos.StatusResponse(token, position, total != null ? total : 0, allowed);
    }
}
```

**Step 3: QueueController 작성**

```java
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Profile("redis")
public class QueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping("/enter")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public QueueDtos.EnterResponse enter(@RequestParam Long studentId) {
        return waitingQueueService.enter(studentId);
    }

    @GetMapping("/status/{token}")
    public QueueDtos.StatusResponse getStatus(@PathVariable String token) {
        return waitingQueueService.getStatus(token);
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `cd phase3-queue && ./gradlew test`
Expected: 전부 PASS

**Step 5: Commit**

```bash
git add phase3-queue/
git commit -m "feat(phase3): Redis Sorted Set 기반 대기열 시스템 구현"
```

---

### Task 20: k6 대기열 시나리오 작성

**Files:**
- Create: `k6/scenarios/queue-enrollment-rush.js`

**Step 1: queue-enrollment-rush.js 작성**

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL } from '../helpers/config.js';
import { randomStudentId, popularCourseId } from '../helpers/data.js';

const queueEnterDuration = new Trend('queue_enter_duration');
const queueWaitDuration = new Trend('queue_wait_duration');
const enrollAfterQueue = new Counter('enroll_after_queue');

export const options = {
  scenarios: {
    queue_rush: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '10s', target: 500 },
        { duration: '1m', target: 500 },
        { duration: '30s', target: 0 },
      ],
    },
  },
};

export default function () {
  const studentId = randomStudentId();

  const enterRes = http.post(`${BASE_URL}/api/queue/enter?studentId=${studentId}`);
  check(enterRes, { 'queue enter: 202': (r) => r.status === 202 });
  queueEnterDuration.add(enterRes.timings.duration);

  if (enterRes.status !== 202) return;

  const { token } = JSON.parse(enterRes.body);

  let allowed = false;
  const waitStart = Date.now();
  for (let i = 0; i < 60; i++) {
    const statusRes = http.get(`${BASE_URL}/api/queue/status/${token}`);
    if (statusRes.status === 200) {
      const status = JSON.parse(statusRes.body);
      if (status.allowed) {
        allowed = true;
        break;
      }
    }
    sleep(0.5);
  }
  queueWaitDuration.add(Date.now() - waitStart);

  if (!allowed) return;

  const payload = JSON.stringify({
    studentId: studentId,
    courseId: popularCourseId(),
  });
  const enrollRes = http.post(`${BASE_URL}/api/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(enrollRes, { 'enroll: no 5xx': (r) => r.status < 500 });
  enrollAfterQueue.add(1);
}
```

**Step 2: Commit**

```bash
git add k6/scenarios/queue-enrollment-rush.js
git commit -m "test(k6): 대기열 포함 수강신청 러시 시나리오 작성"
```

---

### Task 21: Phase 3 성능 측정 및 전체 비교

**Step 1: phase3-queue 서버 시작**

Run: `cd phase3-queue && ./gradlew bootRun --args='--spring.profiles.active=mysql,redis'`

**Step 2: 대기열 시나리오 실행**

```bash
mkdir -p k6/results/phase3-waiting-queue
k6 run --out json=k6/results/phase3-waiting-queue/queue-enrollment-rush.json k6/scenarios/queue-enrollment-rush.js
```

**Step 3: Phase 0~3 전체 비교표 작성**

| 항목 | Phase 0 | Phase 1 | Phase 2 | Phase 3 |
|------|---------|---------|---------|---------|
| TPS (수강신청) | - | - | - | - |
| p95 응답시간 | - | - | - | - |
| p99 응답시간 | - | - | - | - |
| 에러율 | - | - | - | - |
| 최대 동시 VU | - | - | - | - |
| 정원 초과 건수 | 0 | 0 | 0 | 0 |

**Step 4: Commit**

```bash
git add k6/results/phase3-waiting-queue/
git commit -m "test(k6): Phase 3 대기열 성능 측정 및 전체 Phase 비교 기록"
```

---

## 실행 가이드

각 Phase 서버 실행 방법:

```bash
# Phase 0 (MySQL baseline)
cd phase0-baseline && ./gradlew bootRun --args='--spring.profiles.active=mysql'

# Phase 1 (애플리케이션 최적화)
cd phase1-optimization && ./gradlew bootRun --args='--spring.profiles.active=mysql'

# Phase 2 (Redis 캐시)
cd phase2-redis && ./gradlew bootRun --args='--spring.profiles.active=mysql,redis'

# Phase 3 (대기열)
cd phase3-queue && ./gradlew bootRun --args='--spring.profiles.active=mysql,redis'
```

k6 테스트 실행 (모든 Phase 공통, 루트에서):

```bash
k6 run k6/scripts/smoke-test.js
k6 run k6/scenarios/enrollment-rush.js
k6 run k6/scenarios/mixed-workload.js
```
