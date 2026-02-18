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
