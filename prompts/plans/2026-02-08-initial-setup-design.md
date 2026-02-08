# 초기 프로젝트 세팅 설계

## 개요
PROBLEM.md 요구사항에 맞춰 프로젝트 초기 구조와 문서를 세팅한다.

## 결정 사항

### 1. 프로젝트 구조
- 루트 단일 프로젝트 (backend/ 하위 분리 없음)
- 레이어드 아키텍처: api → service → domain (단방향 의존)
- ApiApplication.java는 com.musinsa 최상위 패키지에 위치
- 각 레이어 내 서브도메인별 패키지 분리

### 2. 패키지 구조
```
src/main/java/com/musinsa/
├── ApiApplication.java
├── api/
│   ├── HealthController.java
│   ├── student/
│   │   ├── StudentController.java
│   │   └── dtos/
│   │       └── StudentDtos.java
│   ├── course/
│   │   ├── CourseController.java
│   │   └── dtos/
│   │       └── CourseDtos.java
│   ├── professor/
│   │   ├── ProfessorController.java
│   │   └── dtos/
│   │       └── ProfessorDtos.java
│   └── enrollment/
│       ├── EnrollmentController.java
│       └── dtos/
│           └── EnrollmentDtos.java
├── service/
│   ├── student/StudentService.java
│   ├── course/CourseService.java
│   ├── professor/ProfessorService.java
│   └── enrollment/EnrollmentService.java
└── domain/
    ├── student/
    │   ├── Student.java
    │   └── StudentRepository.java
    ├── course/
    │   ├── Course.java
    │   └── CourseRepository.java
    ├── professor/
    │   ├── Professor.java
    │   └── ProfessorRepository.java
    ├── enrollment/
    │   ├── Enrollment.java
    │   └── EnrollmentRepository.java
    └── department/
        ├── Department.java
        └── DepartmentRepository.java
```

### 3. DTO 컨벤션
- 도메인별 *Dtos.java 파일에 Request, Response record를 함께 정의
- api/{도메인}/dtos/ 폴더에 위치

### 4. 문서화
- README.md: 빌드/실행 가이드 (루트 단일 프로젝트)
- CLAUDE.md: 상세 AI 에이전트 지침서
- docs/REQUIREMENTS.md: 요구사항 분석 & 설계 결정 템플릿
- docs/api/endpoints.md: API 명세 템플릿
- docs/decisions/: ADR 지속 관리

### 5. Health 엔드포인트
- GET /health (PROBLEM.md 필수 요구사항)
- /api/health → /health 로 경로 변경

## 수행 작업
1. HealthController 경로 수정
2. ApiApplication.java 최상위 이동 + @ComponentScan 제거
3. 레이어드 패키지 스켈레톤 생성
4. application.yml 설정
5. CLAUDE.md 재작성
6. README.md 재작성
7. docs/REQUIREMENTS.md 템플릿
8. docs/api/endpoints.md 템플릿
9. ADR-001 업데이트
10. core/Main.java 제거
