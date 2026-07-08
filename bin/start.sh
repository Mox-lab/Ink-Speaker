#!/usr/bin/env bash
# ============================================================
# Ink Speaker - JVM 启动脚本(第 7 阶段:GC 调优 + JVM 参数配置化)
# ============================================================
# 用法:
#   ./bin/start.sh              # 默认 dev profile
#   ./bin/start.sh prod         # 指定 prod profile
#
# 所有 JVM 参数通过环境变量覆盖,无硬编码默认值参见 .env.example
# ============================================================
set -euo pipefail

# ------------------------------------------------------------
# 1. 确定 Profile
# ------------------------------------------------------------
PROFILE="${1:-${SPRING_PROFILES_ACTIVE:-dev}}"
export SPRING_PROFILES_ACTIVE="${PROFILE}"

# ------------------------------------------------------------
# 2. 应用主 jar 定位
# ------------------------------------------------------------
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR_FILE="${APP_DIR}/target/ink-speaker-1.0.0-SNAPSHOT.jar"

if [[ ! -f "${JAR_FILE}" ]]; then
    echo "[ERROR] jar 不存在: ${JAR_FILE}" >&2
    echo "        请先执行 mvn package -DskipTests" >&2
    exit 1
fi

# ------------------------------------------------------------
# 3. JVM 参数配置化(全部可由环境变量覆盖)
# ------------------------------------------------------------
# 堆大小:容器化部署下用 -XX:+UseContainerSupport 让 JVM 自动识别 cgroup 内存上限
# -XX:InitialRAMPercentage / MaxRAMPercentage 比 -Xmx 更适合容器
JAVA_HEAP_INIT="${JAVA_HEAP_INIT:--XX:InitialRAMPercentage=25}"
JAVA_HEAP_MAX="${JAVA_HEAP_MAX:--XX:MaxRAMPercentage=75}"

# GC 选择:G1GC(JDK 21 默认),生产可换 ZGC(-XX:+UseZGC)降低 P99
JAVA_GC="${JAVA_GC:--XX:+UseG1GC}"

# GC 调优:G1 大堆(>16G)时建议增大 IHOP,避免过早 mixed GC
JAVA_GC_OPTS="${JAVA_GC_OPTS:--XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=4m -XX:InitiatingHeapOccupancyPercent=45}"

# 元空间:限制上限避免 Metaspace Leak 导致 OOM
JAVA_METASPACE="${JAVA_METASPACE:--XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m}"

# GC 日志:生产开启,便于事后分析;dev 可关闭
JAVA_GC_LOG="${JAVA_GC_LOG:--Xlog:gc*:file=logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m}"

# 故障诊断:OOM 时自动 dump,避免反复重启后丢失现场
JAVA_DIAGNOSTICS="${JAVA_DIAGNOSTICS:--XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError}"

# 时区与编码
JAVA_LOCALE="${JAVA_LOCALE:--Duser.timezone=GMT+8 -Dfile.encoding=UTF-8}"

# ------------------------------------------------------------
# 4. 合并 JVM_OPTS
# ------------------------------------------------------------
JVM_OPTS="${JAVA_HEAP_INIT} ${JAVA_HEAP_MAX} ${JAVA_GC} ${JAVA_GC_OPTS} ${JAVA_METASPACE} ${JAVA_GC_LOG} ${JAVA_DIAGNOSTICS} ${JAVA_LOCALE}"

# 允许外部追加额外 JVM 参数(如 -Ddebug、-agentlib:jdwp)
JVM_OPTS="${JVM_OPTS} ${EXTRA_JVM_OPTS:-}"

echo "[start] profile=${PROFILE}"
echo "[start] jvm=${JVM_OPTS}"
echo "[start] jar=${JAR_FILE}"

# ------------------------------------------------------------
# 5. 启动
# ------------------------------------------------------------
mkdir -p "${APP_DIR}/logs"

exec java ${JVM_OPTS} \
    -jar "${JAR_FILE}" \
    --spring.profiles.active="${PROFILE}"
