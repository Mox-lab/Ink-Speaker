#!/usr/bin/env bash
# ============================================================
# Ink Realm 一键移除脚本(清空全部 + 从零重建准备)
# ============================================================
# 功能:
#   1. 停止并移除 Ink Realm 全部容器、网络
#   2. 删除本项目的后端/前端镜像(latest + prev 备份)
#   3. 清理悬空的容器 / 网络 / 镜像
#   4. 删除数据目录(/opt/ink-realm/data:PostgreSQL + 日志 + 知识库)
#   5. 删除 .env.prod(密钥将丢失,由 setup-deploy.sh 重新生成)
#
# ⚠️ 不可逆:第 4/5 步会永久删除数据与密钥,执行前确认无需保留。
#
# 用法:
#   sudo bash teardown.sh                  # 交互确认后执行
#   sudo bash teardown.sh --yes            # 跳过确认(自动化用)
#   sudo bash teardown.sh -d /opt/ink-realm  # 指定部署目录
# ============================================================
set -uo pipefail

APP_DIR="/opt/ink-realm"
SKIP_CONFIRM=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes|-y) SKIP_CONFIRM=1; shift ;;
    -d|--dir) APP_DIR="$2"; shift 2 ;;
    *) echo "未知参数:$1" >&2; exit 1 ;;
  esac
done

echo "================================================"
echo "  Ink Realm 一键移除"
echo "  目标目录: ${APP_DIR}"
echo "================================================"

if [[ ${SKIP_CONFIRM} -ne 1 ]]; then
  read -r -p "确认要彻底删除容器/镜像/数据/密钥? 此操作不可逆 [y/N] " ans
  case "${ans}" in
    y|Y|yes|YES) ;;
    *) echo "已取消。"; exit 0 ;;
  esac
fi

# 进入部署目录(不存在则跳过目录相关操作,但镜像清理仍执行)
if ! cd "${APP_DIR}" 2>/dev/null; then
  echo "目录不存在: ${APP_DIR},跳过目录相关操作"
fi

# 1) 停止并移除容器 + 网络
echo "[1/5] 停止并移除容器与网络..."
if [[ -f docker-compose.yml && -f .env.prod ]]; then
  docker compose --env-file .env.prod down --remove-orphans 2>/dev/null \
    || docker compose down --remove-orphans 2>/dev/null \
    || true
else
  docker compose down --remove-orphans 2>/dev/null || true
fi

# 2) 删除本项目镜像(latest + prev 备份)
echo "[2/5] 删除镜像..."
if [[ -f .env.prod ]]; then
  set -a; source .env.prod; set +a
fi
ACR_REGISTRY="${ACR_REGISTRY:-crpi-2t2oc7kie6ke8uce.cn-hangzhou.personal.cr.aliyuncs.com}"
ACR_NAMESPACE="${ACR_NAMESPACE:-mox-labb}"
for img in ink-realm ink-realm-web; do
  docker rmi -f "${ACR_REGISTRY}/${ACR_NAMESPACE}/${img}:latest" 2>/dev/null || true
  docker rmi -f "${ACR_REGISTRY}/${ACR_NAMESPACE}/${img}:prev" 2>/dev/null || true
done
# 兜底:删除任何名称含 ink-realm 的镜像(防止变量不匹配导致残留)
docker rmi -f $(docker images --filter=reference='*/*ink-realm*' -q) 2>/dev/null || true

# 3) 清理悬空资源
echo "[3/5] 清理悬空容器/网络/镜像..."
docker container prune -f 2>/dev/null || true
docker network prune -f 2>/dev/null || true
docker image prune -af 2>/dev/null || true

# 4) 删除数据目录(数据库 + 日志 + 知识库)
echo "[4/5] 删除数据目录(数据库 + 日志 + 知识库)..."
rm -rf "${APP_DIR}/data"

# 5) 删除 .env.prod(密钥)
echo "[5/5] 删除 .env.prod(密钥将丢失)..."
rm -f "${APP_DIR}/.env.prod"

echo ""
echo "================================================"
echo "  移除完成! 下一步请运行:"
echo "    sudo bash setup-deploy.sh"
echo "================================================"
