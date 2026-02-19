import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL } from '../helpers/config.js';
import { STUDENT_COUNT } from '../helpers/data.js';

const queueSubmitDuration = new Trend('queue_submit_duration');
const queueWaitDuration = new Trend('queue_wait_duration');
const enrollSuccess = new Counter('enroll_success');
const enrollFailed = new Counter('enroll_failed');

// 환경변수로 VU 수 지정: k6 run --env VUS=2000 queue-enrollment-rush.js
const VUS = parseInt(__ENV.VUS || '1000');

export const options = {
  scenarios: {
    enrollment_open: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1s', target: VUS },   // 수강신청 오픈! 1초만에 instant spike
        { duration: '1m', target: VUS },   // 1분간 수강신청 진행
        { duration: '5s', target: 0 },     // 종료
      ],
    },
  },
};

// 대기 순번에 따른 폴링 간격 (뒤쪽일수록 느리게)
function pollInterval(position) {
  if (position <= 100) return 0.5;    // 곧 처리될 순번: 0.5초
  if (position <= 500) return 1.5;    // 중간 순번: 1.5초
  if (position <= 1500) return 3;     // 뒤쪽 순번: 3초
  return 5;                           // 한참 뒤: 5초
}

export default function () {
  // 1 VU = 1 학생 (고정)
  const studentId = (__VU % STUDENT_COUNT) + 1;

  // 인기 강좌(1~50) 중 하나를 선택하여 수강신청
  const courseId = Math.floor(Math.random() * 50) + 1;

  // 1. 수강신청 요청 → 대기열 적재
  const payload = JSON.stringify({ studentId, courseId });
  const submitRes = http.post(`${BASE_URL}/api/queue/enroll`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'queue_enroll' },
  });
  check(submitRes, { 'queue enroll: 202': (r) => r.status === 202 });
  queueSubmitDuration.add(submitRes.timings.duration);

  if (submitRes.status !== 202) return;

  const body = JSON.parse(submitRes.body);
  const token = body.token;
  let position = body.position;

  // 2. 결과 폴링 (순번 기반 간격 조절)
  const waitStart = Date.now();
  for (let i = 0; i < 120; i++) {
    sleep(pollInterval(position));

    const resultRes = http.get(`${BASE_URL}/api/queue/result/${token}`, {
      tags: { name: 'queue_result' },
    });

    if (resultRes.status !== 200) continue;

    const result = JSON.parse(resultRes.body);
    if (result.status === 'SUCCESS') {
      enrollSuccess.add(1);
      break;
    } else if (result.status === 'FAILED') {
      enrollFailed.add(1);
      break;
    }

    // WAITING 상태: 현재 대기 순번 업데이트해서 폴링 간격 재조정
    if (result.status === 'WAITING' && result.message) {
      const match = result.message.match(/(\d+)/);
      if (match) position = parseInt(match[1]);
    }
  }
  queueWaitDuration.add(Date.now() - waitStart);

  // 3. 결과 확인 후 다음 강좌 신청 전 생각 시간
  sleep(1);
}
