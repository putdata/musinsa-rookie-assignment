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
