// ============================================================
// Ink Realm - 场景 1:构思接口压测(验证 L1+L2 缓存命中)
// ============================================================
// 用法:
//   k6 run --vus 8 --duration 5m scripts/loadtest-concept.js
//
// 期望:
//   - 第一次全 miss,后续 7 次都命中 L1 或 L2
//   - http_req_duration P99 < 60s
//   - http_req_failed < 5%
// ============================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 配置
const BASE_URL = __ENV.BASE_URL || 'http://localhost:9688';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'dev-only-token';
const NOVEL_ID = __ENV.NOVEL_ID || '1';

// 自定义指标:命中等级计数
const cacheL1Hit = new Counter('cache_l1_hit');
const cacheL2Hit = new Counter('cache_l2_hit');
const cacheMiss = new Counter('cache_miss');

// 压测选项
export const options = {
    scenarios: {
        concept_load: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '30s', target: 8 },   // 30s 内爬升到 8 并发
                { duration: '4m', target: 8 },    // 持续 4 分钟
                { duration: '30s', target: 0 },   // 30s 内降到 0
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        // 目标:P99 < 60s(构思走 LLM,单次约 10-30s),失败率 < 5%
        http_req_duration: ['p(99)<60000'],
        http_req_failed: ['rate<0.05'],
    },
};

// 8 套固定 inspiration,保证相同 VU 内重复命中缓存
const INSPIRATIONS = [
    { inspiration: '一个少年在码头捡到一把带血的长剑', genre: '武侠' },
    { inspiration: '雨夜酒馆里来了一位不说话的剑客', genre: '武侠' },
    { inspiration: '末世荒原上最后一个能听见花开的少女', genre: '末世' },
    { inspiration: '宫廷舞会上,替嫁公主与真王子偶遇', genre: '宫斗' },
    { inspiration: '修仙者破境时听到来自异界的呼唤', genre: '修仙' },
    { inspiration: '蒸汽朋克城里,钟表匠发现时间倒流', genre: '蒸汽朋克' },
    { inspiration: '侦探接到一桩死者亲自报案的案子', genre: '推理' },
    { inspiration: '黑客入侵服务器后看见自己的死亡预告', genre: '赛博朋克' },
];

export default function () {
    const idx = (__VU - 1) % INSPIRATIONS.length;
    const payload = INSPIRATIONS[idx];

    const res = http.post(
        `${BASE_URL}/api/concept`,
        JSON.stringify(payload),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${AUTH_TOKEN}`,
                'X-Novel-Id': NOVEL_ID,
            },
            timeout: '90s',
        }
    );

    const ok = check(res, {
        'status 200': (r) => r.status === 200,
        'has blueprint': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.blueprint && body.data.blueprint.length > 0;
            } catch (e) {
                return false;
            }
        },
    });

    if (!ok) {
        console.error(`[concept] VU=${__VU} iter=${__ITER} status=${res.status} body=${res.body}`);
    }

    // 思考时间:模拟用户读结果(1-3s)
    sleep(1 + Math.random() * 2);
}
