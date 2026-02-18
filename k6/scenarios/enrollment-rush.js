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
