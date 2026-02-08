package com.musinsa.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musinsa.api.enrollment.dtos.EnrollmentDtos;
import com.musinsa.domain.course.Course;
import com.musinsa.domain.course.CourseRepository;
import com.musinsa.domain.department.Department;
import com.musinsa.domain.department.DepartmentRepository;
import com.musinsa.domain.professor.Professor;
import com.musinsa.domain.professor.ProfessorRepository;
import com.musinsa.domain.student.Student;
import com.musinsa.domain.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class EnrollmentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProfessorRepository professorRepository;

    private Student student;
    private Course course;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.save(
                Department.builder().name("테스트학과").build());

        Professor professor = professorRepository.save(
                Professor.builder().name("테스트교수").department(dept).build());

        student = studentRepository.save(
                Student.builder()
                        .name("테스트학생")
                        .studentNumber("TEST0001")
                        .department(dept)
                        .grade(1)
                        .build());

        course = courseRepository.save(
                Course.builder()
                        .name("자료구조")
                        .credits(3)
                        .capacity(30)
                        .enrolled(0)
                        .schedule("MON_1,MON_2,MON_3")
                        .department(dept)
                        .professor(professor)
                        .build());
    }

    @Test
    @DisplayName("GET /health - 헬스체크")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("POST /api/enrollments - 수강신청 성공")
    void enrollSuccess() throws Exception {
        EnrollmentDtos.Request request = new EnrollmentDtos.Request(student.getId(), course.getId());

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(student.getId()))
                .andExpect(jsonPath("$.courseId").value(course.getId()))
                .andExpect(jsonPath("$.courseName").value("자료구조"))
                .andExpect(jsonPath("$.credits").value(3))
                .andExpect(jsonPath("$.enrolledAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/enrollments - 정원 초과 시 409")
    void enrollCapacityExceeded() throws Exception {
        Course fullCourse = courseRepository.save(
                Course.builder()
                        .name("인기강좌")
                        .credits(3)
                        .capacity(1)
                        .enrolled(1)
                        .schedule("TUE_1,TUE_2,TUE_3")
                        .department(course.getDepartment())
                        .professor(course.getProfessor())
                        .build());

        EnrollmentDtos.Request request = new EnrollmentDtos.Request(student.getId(), fullCourse.getId());

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("DELETE /api/enrollments/{id} - 수강취소 성공 204")
    void cancelSuccess() throws Exception {
        EnrollmentDtos.Request request = new EnrollmentDtos.Request(student.getId(), course.getId());

        String response = mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long enrollmentId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/enrollments/" + enrollmentId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/enrollments/{id} - 존재하지 않는 수강신청 404")
    void cancelNotFound() throws Exception {
        mockMvc.perform(delete("/api/enrollments/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/enrollments?studentId={id} - 시간표 조회")
    void getEnrollments() throws Exception {
        EnrollmentDtos.Request request = new EnrollmentDtos.Request(student.getId(), course.getId());

        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/enrollments")
                        .param("studentId", student.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("자료구조"))
                .andExpect(jsonPath("$[0].credits").value(3))
                .andExpect(jsonPath("$[0].schedule").value("MON_1,MON_2,MON_3"));
    }
}
