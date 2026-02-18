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
