## 초기세팅

### Opus 4.6

/superpowers:brainstorm
우선 PROBLEM.md에 나와있는대로 초기 프로젝트 구성을 세팅하고싶어
구현 기능도 중요하지만, 문서화도 매우 중요한 평가요소야. 문서화를 적절하게 하기 위한 템플릿도 필요하고, README.md, CLAUDE.md 같은 문서도 중요해

백엔드는 레이어드 아키텍처로 설계할거야. api/ 폴더는 그대로 두고, domain/, service/ 폴더로 나누어서 구성하자. 각 폴더안에는
세부적인 도메인 폴더들로 나누어서 관리할거야(계층안에 각 도메인 별로), domain안의 서브도메인 폴더 안에는 entity와 repository가
함께 있는 구조야.

도메인의 Bounded Context는 우선 student, professor, enrollment, department, course야

dto같은 경우에는, 관리하기 쉽게 하나의 파일(*Dtos.java)안에 두개의 record로 구성하자
여러 dto들이 있을 수 있으니, dto만 모아두는 dtos/ 폴더를 만들어줘

빠른 개발을 위해, lombok을 사용하자. DDD에 가깝게 설계하고 싶으니까 Setter는 최대한 사용하지 말고, RequiredConstructor,
  AllArgsConstructor, NoArgsConstructor, Getter, Builder등만 사용하는거로 하자.

마지막으로 커밋 양식은 type(scope): 제목 에다가 내용도 세부적으로 작성할거야. 내용은 dash(-)로 구분해서 작성할 수 있게 하자

### Codex 5.3

현재 PROBLEM.md에 맞게 프로젝트 구성이 잘 되었는지 피드백해줘. 문서화 전략과, 백엔드 전략 모두