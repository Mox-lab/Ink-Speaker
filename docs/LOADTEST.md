# ============================================================
# Ink Realm - 压测方案(第 9 阶段)
# ============================================================
#
# 目标:
#   1. 验证 L1+L2 多级缓存命中率(目标:同 prompt 重复 3 次后,L2 命中率 > 90%)
#   2. 测出 SSE 流式章节生成的并发上限(目标:8 并发不超时)
#   3. 测出 LLM 调用 P99 延迟(目标:< 60s,避免上游 nginx 504)
#   4. 暴露 GC 调优效果(目标:G1GC 下 P99 < 200ms)
#
# 工具:k6(Go,单二进制,比 JMeter 更轻量)
#   安装:choco install k6 (Windows) / brew install k6 (Mac)
#
# 运行:
#   k6 run --vus 8 --duration 5m scripts/loadtest-concept.js
#   k6 run --vus 4 --duration 10m scripts/loadtest-chapter-sse.js
#
# 报告:执行后控制台输出,关键指标:
#   - http_req_duration: P99 < 60s
#   - http_req_failed: < 5%
#   - iterations: 总迭代数(越高越好)
#   - vus_max: 峰值并发数
# ============================================================

# ------------------------------------------------------------
# 场景 1:构思接口压测(验证 L1+L2 缓存命中)
# ------------------------------------------------------------

# 压测脚本: scripts/loadtest-concept.js
# 模拟 8 个并发用户,5 分钟内反复用相同的 inspiration 调用 /api/concept
# 期望:第一次全 miss,后续 7 次都命中 L1 或 L2
# 监控指标:
#   - llm_cache_hit_level{level="l1"} 增长曲线(本机命中)
#   - llm_cache_hit_level{level="l2"} 增长曲线(跨实例命中)
#   - llm_cache_hit_level{level="miss"} 增长曲线(回源)

# ------------------------------------------------------------
# 场景 2:章节生成 SSE 压测(验证线程池隔离)
# ------------------------------------------------------------

# 压测脚本: scripts/loadtest-chapter-sse.js
# 模拟 4 个并发用户同时生成章节,每章约 2000 字(60s)
# 期望:
#   - sseStreamExecutor(最大 16)不饱和
#   - novelAsyncExecutor(最大 16)不阻塞
#   - resilience4j bulkhead 不触发
# 监控指标:
#   - http_server_requests_seconds{uri="/api/chat/stream"} P99
#   - resilience4j_bulkhead_available_concurrent_calls
#   - ink-async-* / sse-stream-* 线程池活跃数

# ------------------------------------------------------------
# 场景 3:RAG 检索压测(验证工具缓存)
# ------------------------------------------------------------

# 压测脚本: scripts/loadtest-lore-search.js
# 模拟 16 个并发用户用相同 query 检索知识库
# 期望:第一次走向量库,5min TTL 内重复命中 toolLore 缓存
# 监控指标:
#   - llm_cache_size{cache="toolLore"} 增长
#   - DB QPS 应保持平稳(不随并发数线性增长)

# ------------------------------------------------------------
# 场景 4:稳态压测(模拟真实生产流量)
# ------------------------------------------------------------

# 压测脚本: scripts/loadtest-mixed.js
# 混合比例:
#   - 60% /api/concept (高缓存命中)
#   - 20% /api/chapter (无缓存,LLM 长耗时)
#   - 15% /api/lore/search (工具缓存)
#   - 5%  /api/chat/stream (SSE 流式)
# 期望:整体 QPS > 2,P99 < 60s,GC P99 < 200ms
