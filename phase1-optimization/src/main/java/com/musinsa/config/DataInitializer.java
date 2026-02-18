package com.musinsa.config;

import com.musinsa.domain.course.Course;
import com.musinsa.domain.course.CourseRepository;
import com.musinsa.domain.department.Department;
import com.musinsa.domain.department.DepartmentRepository;
import com.musinsa.domain.professor.Professor;
import com.musinsa.domain.professor.ProfessorRepository;
import com.musinsa.domain.student.Student;
import com.musinsa.domain.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;
    private final ProfessorRepository professorRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;

    private static final List<String> DEPARTMENT_NAMES = List.of(
            "컴퓨터공학과", "경영학과", "전자공학과", "기계공학과", "화학공학과",
            "수학과", "물리학과", "영어영문학과", "심리학과", "경제학과"
    );

    private static final String[] LAST_NAMES = {
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍"
    };

    private static final String[] FIRST_NAMES = {
            "민준", "서연", "예준", "서윤", "도윤", "지우", "시우", "하은",
            "주원", "하윤", "지호", "소율", "지한", "다은", "준서", "수아",
            "현우", "지아", "건우", "채원", "우진", "지윤", "선우", "은서",
            "민재", "유나", "정우", "하린", "승현", "소은", "유준", "예은",
            "태윤", "민서", "시윤", "지민", "지원", "윤서", "재윤", "채은"
    };

    private static final String[] DAYS = {"MON", "TUE", "WED", "THU", "FRI"};

    private static final Map<String, List<String>> COURSE_NAMES_BY_DEPT = new LinkedHashMap<>();

    static {
        COURSE_NAMES_BY_DEPT.put("컴퓨터공학과", List.of(
                "자료구조", "알고리즘", "운영체제", "컴퓨터네트워크", "데이터베이스",
                "소프트웨어공학", "인공지능", "머신러닝", "컴퓨터구조", "웹프로그래밍",
                "모바일프로그래밍", "클라우드컴퓨팅", "정보보안", "컴파일러", "분산시스템",
                "디지털논리회로", "프로그래밍언어론", "컴퓨터그래픽스", "멀티미디어", "임베디드시스템"
        ));
        COURSE_NAMES_BY_DEPT.put("경영학과", List.of(
                "경영학원론", "마케팅관리", "재무관리", "인적자원관리", "경영전략",
                "회계원리", "조직행동론", "국제경영", "경영정보시스템", "생산운영관리",
                "소비자행동", "브랜드관리", "재무회계", "관리회계", "투자론",
                "기업윤리", "창업경영", "서비스경영", "디지털마케팅", "경영통계"
        ));
        COURSE_NAMES_BY_DEPT.put("전자공학과", List.of(
                "회로이론", "전자회로", "디지털시스템", "신호처리", "통신공학",
                "전자기학", "반도체공학", "제어공학", "전력전자", "마이크로프로세서",
                "VLSI설계", "센서공학", "광전자공학", "RF공학", "영상처리",
                "로봇공학", "자동제어", "전자측정", "아날로그회로", "디지털통신"
        ));
        COURSE_NAMES_BY_DEPT.put("기계공학과", List.of(
                "열역학", "유체역학", "재료역학", "동역학", "기계설계",
                "기계제도", "자동차공학", "항공공학", "열전달", "진동학",
                "기계가공학", "메카트로닉스", "CAD/CAM", "유한요소해석", "로봇역학",
                "에너지공학", "냉동공조", "기계요소설계", "생산자동화", "나노공학"
        ));
        COURSE_NAMES_BY_DEPT.put("화학공학과", List.of(
                "화학공학개론", "반응공학", "분리공정", "공정제어", "촉매공학",
                "고분자공학", "생물화학공학", "환경화학공학", "전기화학", "열역학",
                "이동현상", "공정설계", "화학공학실험", "나노화학", "에너지화학",
                "석유화학", "식품공학", "화장품공학", "제약공학", "플라즈마화학"
        ));
        COURSE_NAMES_BY_DEPT.put("수학과", List.of(
                "미적분학", "선형대수학", "해석학", "대수학", "위상수학",
                "확률론", "통계학", "미분방정식", "수치해석", "이산수학",
                "복소해석", "정수론", "조합론", "편미분방정식", "함수해석학",
                "수리통계학", "금융수학", "암호학", "기하학", "수학교육론"
        ));
        COURSE_NAMES_BY_DEPT.put("물리학과", List.of(
                "일반물리학", "역학", "전자기학", "양자역학", "열통계물리학",
                "광학", "현대물리학", "고체물리학", "핵물리학", "입자물리학",
                "천체물리학", "유체물리학", "물리수학", "전산물리학", "반도체물리",
                "레이저물리학", "플라즈마물리학", "생물물리학", "나노물리학", "상대성이론"
        ));
        COURSE_NAMES_BY_DEPT.put("영어영문학과", List.of(
                "영어학개론", "영문학개론", "영어음성학", "영어문법론", "영미소설",
                "영미시", "영미드라마", "번역실습", "영어회화", "영작문",
                "영어교육론", "응용언어학", "영미문화", "비교문학", "세계문학",
                "영어의미론", "영어화용론", "코퍼스언어학", "미국문학사", "영국문학사"
        ));
        COURSE_NAMES_BY_DEPT.put("심리학과", List.of(
                "심리학개론", "발달심리학", "사회심리학", "인지심리학", "이상심리학",
                "상담심리학", "성격심리학", "실험심리학", "생리심리학", "학습심리학",
                "산업심리학", "범죄심리학", "건강심리학", "동기심리학", "정서심리학",
                "심리측정", "임상심리학", "교육심리학", "소비자심리학", "긍정심리학"
        ));
        COURSE_NAMES_BY_DEPT.put("경제학과", List.of(
                "경제학원론", "미시경제학", "거시경제학", "국제경제학", "노동경제학",
                "재정학", "화폐금융론", "계량경제학", "산업조직론", "경제사",
                "게임이론", "공공경제학", "환경경제학", "도시경제학", "경제수학",
                "한국경제론", "경제발전론", "금융경제학", "행동경제학", "디지털경제학"
        ));
    }

    @Override
    @Transactional
    public void run(String... args) {
        long startTime = System.currentTimeMillis();
        log.info("초기 데이터 생성 시작...");

        List<Department> departments = createDepartments();
        List<Professor> professors = createProfessors(departments);
        createCourses(departments, professors);
        createStudents(departments);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("초기 데이터 생성 완료 ({}ms)", elapsed);
    }

    private List<Department> createDepartments() {
        List<Department> departments = DEPARTMENT_NAMES.stream()
                .map(name -> Department.builder().name(name).build())
                .toList();
        return departmentRepository.saveAll(departments);
    }

    private List<Professor> createProfessors(List<Department> departments) {
        Random random = new Random(42);
        List<Professor> professors = new ArrayList<>();

        for (Department dept : departments) {
            for (int i = 0; i < 10; i++) {
                String name = LAST_NAMES[random.nextInt(LAST_NAMES.length)]
                        + FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
                professors.add(Professor.builder()
                        .name(name)
                        .department(dept)
                        .build());
            }
        }
        return professorRepository.saveAll(professors);
    }

    private void createCourses(List<Department> departments, List<Professor> professors) {
        Random random = new Random(123);
        List<Course> courses = new ArrayList<>();

        for (int deptIdx = 0; deptIdx < departments.size(); deptIdx++) {
            Department dept = departments.get(deptIdx);
            String deptName = dept.getName();
            List<String> courseNames = COURSE_NAMES_BY_DEPT.get(deptName);
            List<Professor> deptProfessors = professors.subList(deptIdx * 10, (deptIdx + 1) * 10);

            // 교수별 사용된 슬롯 추적
            Map<Long, Set<String>> professorSlots = new HashMap<>();
            for (Professor p : deptProfessors) {
                professorSlots.put(p.getId(), new HashSet<>());
            }

            for (int i = 0; i < 50; i++) {
                // 학점 분포: 1학점(10%), 2학점(30%), 3학점(60%)
                int creditRoll = random.nextInt(100);
                int credits;
                if (creditRoll < 10) {
                    credits = 1;
                } else if (creditRoll < 40) {
                    credits = 2;
                } else {
                    credits = 3;
                }

                // 교수 배정 (라운드로빈 + 랜덤)
                Professor professor = deptProfessors.get(i % deptProfessors.size());
                Set<String> usedSlots = professorSlots.get(professor.getId());

                // schedule 생성 (교수 시간 충돌 없이)
                String schedule = generateSchedule(random, credits, usedSlots);
                for (String slot : schedule.split(",")) {
                    usedSlots.add(slot);
                }

                // 강좌명: 기본 목록에서 순환 + 넘버링
                String courseName;
                if (i < courseNames.size()) {
                    courseName = courseNames.get(i);
                } else {
                    courseName = courseNames.get(i % courseNames.size()) + " " + ((i / courseNames.size()) + 1);
                }

                int capacity = 30 + random.nextInt(21); // 30~50명

                courses.add(Course.builder()
                        .name(courseName)
                        .credits(credits)
                        .capacity(capacity)
                        .enrolled(0)
                        .schedule(schedule)
                        .department(dept)
                        .professor(professor)
                        .build());
            }
        }

        courseRepository.saveAll(courses);
    }

    private String generateSchedule(Random random, int credits, Set<String> usedSlots) {
        List<String> allSlots = new ArrayList<>();
        for (String day : DAYS) {
            for (int period = 1; period <= 9; period++) {
                allSlots.add(day + "_" + period);
            }
        }

        // 사용 가능한 슬롯만 필터링
        List<String> available = new ArrayList<>();
        for (String slot : allSlots) {
            if (!usedSlots.contains(slot)) {
                available.add(slot);
            }
        }

        // 연속 교시 선호: 같은 요일의 연속 슬롯 찾기
        if (credits >= 2) {
            List<String[]> consecutiveBlocks = findConsecutiveBlocks(available, credits);
            if (!consecutiveBlocks.isEmpty()) {
                String[] block = consecutiveBlocks.get(random.nextInt(consecutiveBlocks.size()));
                return String.join(",", block);
            }
        }

        // 연속 슬롯이 없으면 랜덤 선택
        Collections.shuffle(available, random);
        List<String> selected = available.subList(0, Math.min(credits, available.size()));
        selected.sort(Comparator.naturalOrder());
        return String.join(",", selected);
    }

    private List<String[]> findConsecutiveBlocks(List<String> available, int size) {
        Set<String> availableSet = new HashSet<>(available);
        List<String[]> blocks = new ArrayList<>();

        for (String day : DAYS) {
            for (int start = 1; start <= 9 - size + 1; start++) {
                String[] block = new String[size];
                boolean valid = true;
                for (int j = 0; j < size; j++) {
                    String slot = day + "_" + (start + j);
                    if (!availableSet.contains(slot)) {
                        valid = false;
                        break;
                    }
                    block[j] = slot;
                }
                if (valid) {
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    private void createStudents(List<Department> departments) {
        Random random = new Random(456);
        List<Student> students = new ArrayList<>(10000);

        int studentNumber = 1;
        for (int i = 0; i < 10000; i++) {
            Department dept = departments.get(i % departments.size());
            int grade = (i % 4) + 1;
            String name = LAST_NAMES[random.nextInt(LAST_NAMES.length)]
                    + FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];

            students.add(Student.builder()
                    .name(name)
                    .studentNumber(String.format("2026%04d", studentNumber++))
                    .department(dept)
                    .grade(grade)
                    .build());
        }

        studentRepository.saveAll(students);
    }
}
