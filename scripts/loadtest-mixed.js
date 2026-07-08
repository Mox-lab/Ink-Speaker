// ============================================================
// Ink Speaker - 场景 4:稳态混合流量(模拟真实生产)
// ============================================================
// 用法:
//   k6 run --vus 16 --duration 10m scripts/loadtest-mixed.js
//
// 比例:
//   - 60% /api/concept      (高缓存命中)
//   - 20% /api/chapter       (无缓存,LLM 长耗时)
//   - 15% /api/lore/search   (工具缓存)
//   -  5% /api/chat/stream   (SSE 流式)
//
// 期望:整体 QPS > 2,P99 < 60s,GC P99 < 200ms
// ============================================================

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9688';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'dev-only-token';
const NOVEL_ID = __ENV.NOVEL_ID || '1';

export const options = {
    scenarios: {
        mixed: {
            executor: 'constant-vus',
            vus: 16,
            duration: '10m',
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<60000'],
        http_req_failed: ['rate<0.05'],
    },
};

const CONCEPTS = [
    { inspiration: '少年在码头捡到带血长剑', genre: '武侠' },
    { inspiration: '雨夜酒馆不说话的剑客', genre: '武侠' },
    { inspiration: '末世荒原上听见花开的少女', genre: '末世' },
    { inspiration: '宫廷舞会替嫁公主遇真王子', genre: '宫斗' },
];

const QUERIES = ['云陵城', '听潮阁', '武学品阶', '林晚剑法'];

const CHAPTERS = [
    '主角出场雨夜码头偶遇剑客',
    '客栈老板娘认得那把剑',
    '主角第一次出手被剑意反噬',
];

function rand(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${AUTH_TOKEN}`,
        'X-Novel-Id': NOVEL_ID,
    };
}

export default function () {
    const r = Math.random();
    let ok = false;

    if (r < 0.60) {
        // 60% concept(高缓存命中)
        const res = http.post(
            `${BASE_URL}/api/concept`,
            JSON.stringify(rand(CONCEPTS)),
            { headers: authHeaders(), timeout: '90s' }
        );
        ok = check(res, { 'concept 200': (r) => r.status === 200 });
    } else if (r < 0.80) {
        // 20% chapter(无缓存,长耗时)
        const res = http.post(
            `${BASE_URL}/api/chapter`,
            JSON.stringify({ chapterNo: 1, hint: rand(CHAPTERS) }),
            { headers: authHeaders(), timeout: '90s' }
        );
        ok = check(res, { 'chapter 200': (r) => r.status === 200 });
    } else if (r < 0.95) {
        // 15% lore/search(工具缓存)
        const res = http.post(
            `${BASE_URL}/api/lore/search`,
            JSON.stringify({ query: rand(QUERIES), topK: 5 }),
            { headers: authHeaders(), timeout: '10s' }
        );
        ok = check(res, { 'lore 200': (r) => r.status === 200 });
    } else {
        // 5% chat/stream(SSE 流式)
        const res = http.post(
            `${BASE_URL}/api/chat/stream`,
            JSON.stringify({ message: rand(CHAPTERS) }),
            {
                headers: { ...authHeaders(), Accept: 'text/event-stream' },
                timeout: '120s',
            }
        );
        ok = check(res, { 'stream 200': (r) => r.status === 200 });
    }

    if (!ok) {
        console.error(`[mixed] VU=${__VU} iter=${__ITER} bucket=${r}`);
    }

    sleep(1 + Math.random() * 2);
}
