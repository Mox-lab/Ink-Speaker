// ============================================================
// Ink Realm - 场景 3:RAG 检索压测(验证工具缓存)
// ============================================================
// 用法:
//   k6 run --vus 16 --duration 3m scripts/loadtest-lore-search.js
//
// 期望:
//   - 16 个并发用户用相同 query 检索
//   - 第一次走向量库,5min TTL 内重复命中 toolLore 缓存
//   - DB QPS 平稳,不随并发数线性增长
// ============================================================

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9688';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'dev-only-token';
const NOVEL_ID = __ENV.NOVEL_ID || '1';

export const options = {
    scenarios: {
        lore_search: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 16 },
                { duration: '2m', target: 16 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        // RAG 检索应 < 5s(向量库 + 工具缓存)
        http_req_duration: ['p(99)<5000'],
        http_req_failed: ['rate<0.05'],
    },
};

// 4 个固定 query,确保缓存命中
const QUERIES = [
    '云陵城',
    '听潮阁',
    '武学品阶',
    '林晚的剑法来历',
];

export default function () {
    const idx = (__VU - 1) % QUERIES.length;
    const query = QUERIES[idx];

    const res = http.post(
        `${BASE_URL}/api/lore/search`,
        JSON.stringify({ query, topK: 5 }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${AUTH_TOKEN}`,
                'X-Novel-Id': NOVEL_ID,
            },
            timeout: '10s',
        }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'has hits': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && Array.isArray(body.data.hits);
            } catch (e) {
                return false;
            }
        },
    });

    // 短思考
    sleep(0.5 + Math.random() * 0.5);
}
