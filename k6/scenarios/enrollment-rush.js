import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { BASE_URL } from '../helpers/config.js';
import { randomStudentId, popularCourseId } from '../helpers/data.js';

// 전체 구간 메트릭
const enrollSuccess = new Counter('enroll_success');
const enrollFailed = new Counter('enroll_failed');
const enrollCapacityExceeded = new Counter('enroll_capacity_exceeded');
const enrollDuration = new Trend('enroll_duration');

// burst 구간 메트릭 (spike 직후 15초: 실제 정원 경쟁 구간)
const enrollDurationBurst = new Trend('enroll_duration_burst');
const enrollSuccessBurst = new Counter('enroll_success_burst');

// sustained 구간 메트릭 (정원 소진 후)
const enrollDurationSustained = new Trend('enroll_duration_sustained');
const enrollSuccessSustained = new Counter('enroll_success_sustained');

// spike는 t=10s(워밍업 후)에 발생, t=11s~t=25s가 실제 정원 경쟁 구간
const BURST_END_MS = 25 * 1000;

export const options = {
  scenarios: {
    enrollment_rush: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 },    // 워밍업
        { duration: '1s', target: 500 },    // 즉시 500 VU 스파이크
        { duration: '1m', target: 500 },    // 1분간 지속 부하
        { duration: '10s', target: 0 },     // 쿨다운
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
  },
};

export default function () {
  const studentId = randomStudentId();
  const courseId = popularCourseId();

  const payload = JSON.stringify({ studentId, courseId });
  const res = http.post(`${BASE_URL}/api/enrollments`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  enrollDuration.add(res.timings.duration);

  // 구간별 메트릭 분리
  const elapsed = Date.now() - exec.scenario.startTime;
  const isBurst = elapsed < BURST_END_MS;

  if (res.status === 201) {
    enrollSuccess.add(1);
    if (isBurst) enrollSuccessBurst.add(1);
    else enrollSuccessSustained.add(1);
  } else if (res.status === 409) {
    const body = JSON.parse(res.body);
    if (body.error === 'CAPACITY_EXCEEDED') {
      enrollCapacityExceeded.add(1);
    }
    enrollFailed.add(1);
  } else if (res.status >= 400) {
    enrollFailed.add(1);
  }

  if (isBurst) {
    enrollDurationBurst.add(res.timings.duration);
  } else {
    enrollDurationSustained.add(res.timings.duration);
  }

  check(res, {
    'enroll: no server error': (r) => r.status < 500,
  });

  sleep(0.1);
}
