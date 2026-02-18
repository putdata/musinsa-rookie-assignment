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
