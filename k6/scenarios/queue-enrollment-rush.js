import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL } from '../helpers/config.js';
import { randomStudentId, popularCourseId } from '../helpers/data.js';

const queueEnterDuration = new Trend('queue_enter_duration');
const queueWaitDuration = new Trend('queue_wait_duration');
const enrollAfterQueue = new Counter('enroll_after_queue');

export const options = {
  scenarios: {
    queue_rush: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '10s', target: 500 },
        { duration: '1m', target: 500 },
        { duration: '30s', target: 0 },
      ],
    },
  },
};

export default function () {
  const studentId = randomStudentId();

  const enterRes = http.post(`${BASE_URL}/api/queue/enter?studentId=${studentId}`);
  check(enterRes, { 'queue enter: 202': (r) => r.status === 202 });
  queueEnterDuration.add(enterRes.timings.duration);

  if (enterRes.status !== 202) return;

  const { token } = JSON.parse(enterRes.body);

  let allowed = false;
  const waitStart = Date.now();
  for (let i = 0; i < 60; i++) {
    const statusRes = http.get(`${BASE_URL}/api/queue/status/${token}`);
    if (statusRes.status === 200) {
      const status = JSON.parse(statusRes.body);
      if (status.allowed) {
        allowed = true;
        break;
      }
    }
    sleep(0.5);
  }
  queueWaitDuration.add(Date.now() - waitStart);

  if (!allowed) return;

  const payload = JSON.stringify({
    studentId: studentId,
    courseId: popularCourseId(),
  });
  const enrollRes = http.post(`${BASE_URL}/api/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(enrollRes, { 'enroll: no 5xx': (r) => r.status < 500 });
  enrollAfterQueue.add(1);
}
