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
