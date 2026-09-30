import http from 'k6/http';
import { check, sleep } from 'k6';

// docker/k6/load-test.js 와 같은 시나리오. 파일을 고치는 대신 환경변수로 받게만 바꿨다.
// run.ps1 -Mode offset -Page 999 처럼 넘기면 그대로 여기로 들어온다.
const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const MODE     = __ENV.MODE     || 'keyset';   // 'offset' | 'keyset'
const PAGE     = Number(__ENV.PAGE || 999);    // 측정 깊이: 0 / 19 / 999 / 9999
const VUS      = Number(__ENV.VUS  || 500);    // user1~user3000 이 DB에 존재
const SIZE     = Number(__ENV.SIZE || 10);

export const options = {
    stages: [
        { duration: '2m30s', target: Math.floor(VUS / 2) },
        { duration: '6m',    target: VUS },
        { duration: '1m30s', target: 0   },
    ],
    thresholds: {
        http_req_duration: ['p(95)<30000'],
        http_req_failed:   ['rate<0.15'],
    },
};

// setup에서 발생한 요청은 http_req_duration 통계에 잡히지 않는다.
export function setup() {
    const BATCH_SIZE = 10;
    const tokens = [];
    let failCount = 0;

    for (let batch = 0; batch < VUS / BATCH_SIZE; batch++) {
        const start = batch * BATCH_SIZE;
        const loginRequests = Array.from({ length: BATCH_SIZE }, (_, i) => [
            'POST',
            `${BASE_URL}/api/auth/signin`,
            JSON.stringify({ email: `user${start + i + 1}@gmail.com`, password: 'password123' }),
            { headers: { 'Content-Type': 'application/json' } },
        ]);

        const responses = http.batch(loginRequests);
        responses.forEach((r, i) => {
            const ok = check(r, { 'login 200': (res) => res.status === 200 });
            if (!ok) {
                console.error(`[setup] user${start + i + 1} login failed: status=${r.status}`);
                failCount++;
            }
            tokens.push(r.cookies['accessToken']?.[0]?.value);
        });
    }
    console.log(`[setup] login complete — success=${VUS - failCount}, fail=${failCount}`);

    // keyset 모드: 목표 깊이의 커서를 여기서 1회만 확보한다.
    // 커서는 순차적으로만 얻을 수 있어 깊이를 직접 지정할 수 없으므로,
    // offset으로 (PAGE-1)을 한 번 조회해 그 응답의 nextCursor를 받아 쓴다.
    let cursor = null;
    if (MODE === 'keyset' && PAGE > 0) {
        const seed = http.get(`${BASE_URL}/api/posts?page=${PAGE - 1}&size=${SIZE}`, {
            headers: { 'Cookie': `accessToken=${tokens[0]}` },
            timeout: '180s',   // 깊은 offset은 수십 초 걸릴 수 있음
        });
        if (seed.status !== 200) {
            throw new Error(`[setup] 커서 확보 실패: status=${seed.status}`);
        }
        cursor = seed.json('nextCursor');
        if (!cursor) throw new Error('[setup] nextCursor가 응답에 없음');
    }

    console.log(`[setup] MODE=${MODE}, PAGE=${PAGE}, VUS=${VUS}, cursor=${cursor ?? '(없음 - 첫 페이지)'}`);
    return { tokens, cursor };
}

export default function (data) {
    const accessToken = data.tokens[(__VU - 1) % VUS];

    const url = (MODE === 'keyset' && data.cursor)
        ? `${BASE_URL}/api/posts?cursor=${data.cursor}&size=${SIZE}`
        : `${BASE_URL}/api/posts?page=${PAGE}&size=${SIZE}`;

    const res = http.get(url, { headers: { 'Cookie': `accessToken=${accessToken}` } });

    check(res, { 'status 200': (r) => r.status === 200 });

    sleep(1);
}

// k6는 handleSummary가 있으면 기본 요약 출력을 이걸로 대체한다.
// 콘솔에 찍는 동시에 /out 에 파일로도 남긴다 — 컨테이너가 사라져도 결과는 남는다.
export function handleSummary(data) {
    const tag = `${MODE}-page${PAGE}-vus${VUS}`;
    const m = data.metrics || {};

    // 값이 없으면 null. 없는 지표를 NaN으로 만들지 않기 위해.
    const get = (metric, stat) => {
        const v = m[metric] && m[metric].values ? m[metric].values[stat] : undefined;
        return (typeof v === 'number' && isFinite(v)) ? v : null;
    };
    const ms  = (v) => v === null ? 'n/a' : v.toFixed(1) + ' ms';
    const pct = (v) => v === null ? 'n/a' : (v * 100).toFixed(2) + ' %';
    const num = (v) => v === null ? 'n/a' : Math.round(v).toLocaleString();

    const text = [
        '',
        '━━━━━━━━━ Deboard 페이지네이션 벤치 결과 ━━━━━━━━━',
        `  MODE=${MODE}  PAGE=${PAGE}  VUS=${VUS}  SIZE=${SIZE}`,
        '',
        `  요청 수          ${num(get('http_reqs', 'count'))}`,
        `  처리량           ${get('http_reqs','rate') === null ? 'n/a' : get('http_reqs','rate').toFixed(1) + ' req/s'}`,
        `  실패율           ${pct(get('http_req_failed', 'rate'))}`,
        '',
        `  응답시간 avg     ${ms(get('http_req_duration', 'avg'))}`,
        `  응답시간 p(90)   ${ms(get('http_req_duration', 'p(90)'))}`,
        `  응답시간 p(95)   ${ms(get('http_req_duration', 'p(95)'))}   ← 포트폴리오 표에 쓸 값`,
        `  응답시간 max     ${ms(get('http_req_duration', 'max'))}`,
        '',
        `  저장 위치  k6/out/summary-${tag}.txt`,
        `           k6/out/summary-${tag}.json  (전체 지표)`,
        `           k6/out/report.html          (그래프)`,
        '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━',
        '',
    ].join('\n');

    return {
        'stdout': text,
        [`/out/summary-${tag}.txt`]: text,
        [`/out/summary-${tag}.json`]: JSON.stringify(data, null, 2),
    };
}
