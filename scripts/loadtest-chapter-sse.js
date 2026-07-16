// ============================================================
// Ink Realm - 场景 2:章节生成 SSE 压测(验证线程池隔离)
// ============================================================
// 用法:
//   k6 run --vus 4 --duration 10m scripts/loadtest-chapter-sse.js
//
// 期望:
//   - 4 个并发 SSE 流,持续 10 分钟
//   - sseStreamExecutor(max 16) 不饱和
//   - 单次 SSE 耗时 < 60s,无 504
// ============================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import stream from 'k6/x/stream';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9688';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'dev-only-token';
const NOVEL_ID = __ENV.NOVEL_ID || '1';

export const options = {
    scenarios: {
        chapter_sse: {
            executor: 'constant-vus',
            vus: 4,
            duration: '10m',
        },
    },
    thresholds: {
        // SSE 单流 60s 内必须结束
        sse_duration: ['p(99)<60000'],
        http_req_failed: ['rate<0.05'],
    },
};

const CHAPTER_SEEDS = [
    { chapterNo: 1, hint: '主角出场,在雨夜码头偶遇受伤的剑客' },
    { chapterNo: 2, hint: '剑客留下半句遗言后消失,主角被卷入江湖恩怨' },
    { chapterNo: 3, hint: '主角来到镇上唯一的客栈,打听剑客来历' },
    { chapterNo: 4, hint: '客栈老板娘似乎认得那把剑,但不愿多说' },
];

export default function () {
    const idx = (__VU - 1) % CHAPTER_SEEDS.length;
    const payload = CHAPTER_SEEDS[idx];

    const url = `${BASE_URL}/api/chat/stream`;
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${AUTH_TOKEN}`,
            'X-Novel-Id': NOVEL_ID,
            'Accept': 'text/event-stream',
        },
        timeout: '120s',
    };

    const start = Date.now();
    let chunks = 0;
    let lastChunk = '';

    try {
        const resp = http.post(url, JSON.stringify({ message: payload.hint }), params);
        // k6 默认不解析 SSE 流,这里用 stream 扩展读取
        // 若未装 k6/x/stream,可降级为只校验首字节响应
        if (resp.status === 200) {
            // 模拟 SSE 接收:统计 data: 事件数
            const events = (resp.body.match(/^data:/gm) || []).length;
            chunks = events;
        }
        check(resp, {
            'status 200': (r) => r.status === 200,
            'has data events': (r) => (r.body.match(/^data:/gm) || []).length > 0,
        });
    } catch (e) {
        console.error(`[chat/stream] VU=${__VU} iter=${__ITER} err=${e.message}`);
    }

    const elapsed = Date.now() - start;
    console.log(`[chat/stream] VU=${__VU} iter=${__ITER} chunks=${chunks} elapsed=${elapsed}ms`);

    // 单次结束后短歇,避免连发压垮 LLM 限流
    sleep(2);
}
