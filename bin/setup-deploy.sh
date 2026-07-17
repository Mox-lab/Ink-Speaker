#!/usr/bin/env bash
# ============================================================
# Ink Realm 一键部署脚本(自动配密钥 + 拉镜像 + 启动)
# ============================================================
# 功能:
#   1. 确保部署目录与 docker-compose.yml / .env.prod.example 存在
#      (缺失则从 GitHub raw 下载)
#   2. 生成 .env.prod:
#      - 自动 openssl 随机生成 DB_PASSWORD / JWT_SECRET / JASYPT_KEY
#      - 交互式收集 ACR_REGISTRY / ACR_NAMESPACE / ACR_USERNAME /
#        ACR_PASSWORD / OPENAI_API_KEY(均提供默认值)
#   3. 登录阿里云 ACR
#   4. 拉取最新镜像(后端 + 前端)
#   5. 启动全部容器(按 depends_on 顺序)
#   6. 健康检查(后端 actuator + nginx)
#
# 用法:
#   sudo bash setup-deploy.sh                 # 自动生成密钥并部署
#   sudo bash setup-deploy.sh -d /opt/ink-realm  # 指定部署目录
#   sudo bash setup-deploy.sh --no-gen-keys   # 复用已有 .env.prod 的密钥
# ============================================================
set -euo pipefail

APP_DIR="/opt/ink-realm"
NO_GEN_KEYS=0
GITHUB_RAW_BASE="https://raw.githubusercontent.com/Mox-lab/ink-realm/main"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -d|--dir) APP_DIR="$2"; shift 2 ;;
    --no-gen-keys) NO_GEN_KEYS=1; shift ;;
    *) echo "未知参数:$1" >&2; exit 1 ;;
  esac
done

echo "================================================"
echo "  Ink Realm 一键部署"
echo "  部署目录: ${APP_DIR}"
echo "================================================"

# 前置:docker / compose
command -v docker >/dev/null 2>&1 || {
  echo "未安装 docker,请先运行 server-setup.sh 或 server-init.sh" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || {
  echo "未安装 docker compose v2" >&2; exit 1; }

mkdir -p "${APP_DIR}"
cd "${APP_DIR}"

# 1) 确保 docker-compose.yml 与 .env.prod.example 存在
echo "[1/6] 检查部署文件..."
if [[ ! -f docker-compose.yml ]]; then
  echo "  下载 docker-compose.yml ..."
  curl -fsSL "${GITHUB_RAW_BASE}/docker-compose.yml" -o docker-compose.yml
fi
if [[ ! -f .env.prod.example ]]; then
  echo "  下载 .env.prod.example ..."
  curl -fsSL "${GITHUB_RAW_BASE}/.env.prod.example" -o .env.prod.example
fi

# 2) 生成 / 复用 .env.prod
ENV_FILE="${APP_DIR}/.env.prod"
echo "[2/6] 配置 .env.prod ..."

if [[ -f "${ENV_FILE}" && ${NO_GEN_KEYS} -eq 1 ]]; then
  echo "  复用已有 .env.prod(不重新生成密钥)"
else
  # 交互收集(提供默认值,密码不回显)
  read -r -p "ACR 仓库地址 [crpi-2t2oc7kie6ke8uce.cn-hangzhou.personal.cr.aliyuncs.com]: " ACR_REGISTRY
  ACR_REGISTRY="${ACR_REGISTRY:-crpi-2t2oc7kie6ke8uce.cn-hangzhou.personal.cr.aliyuncs.com}"
  read -r -p "ACR 命名空间 [mox-labb]: " ACR_NAMESPACE
  ACR_NAMESPACE="${ACR_NAMESPACE:-mox-labb}"
  read -r -p "ACR 用户名: " ACR_USERNAME
  read -r -s -p "ACR 密码: " ACR_PASSWORD; echo
  read -r -p "OpenAI/LLM API Key (sk-... ,可留空稍后填): " OPENAI_API_KEY

  # 自动随机生成强密钥
  DB_PASSWORD="$(openssl rand -base64 24)"
  JWT_SECRET="$(openssl rand -base64 48)"
  JASYPT_KEY="$(openssl rand -base64 32)"

  cat > "${ENV_FILE}" <<EOF
# ============================================================
# Ink Realm 生产环境 .env(由 setup-deploy.sh 自动生成)
# ============================================================
DATA_DIR=${APP_DIR}/data
DB_NAME=ink_realm
DB_USERNAME=postgres
DB_PASSWORD=${DB_PASSWORD}
REDIS_PASSWORD=
OPENAI_API_KEY=${OPENAI_API_KEY}
JWT_SECRET=${JWT_SECRET}
JASYPT_KEY=${JASYPT_KEY}
SPRING_PROFILES_ACTIVE=prod
MANAGEMENT_SERVER_PORT=9689
ACR_REGISTRY=${ACR_REGISTRY}
ACR_NAMESPACE=${ACR_NAMESPACE}
ACR_USERNAME=${ACR_USERNAME}
ACR_PASSWORD=${ACR_PASSWORD}
EOF
  chmod 600 "${ENV_FILE}"
  echo "  .env.prod 已生成(权限 600)"
fi

# 加载环境变量供后续使用
set -a; source "${ENV_FILE}"; set +a

# 创建数据目录(确保 bind mount 目录存在,权限正确)
mkdir -p "${DATA_DIR}/postgres" "${DATA_DIR}/logs" "${DATA_DIR}/knowledge-base"
chmod 755 "${DATA_DIR}"

APP_IMAGE="${ACR_REGISTRY}/${ACR_NAMESPACE}/ink-realm:latest"
WEB_IMAGE="${ACR_REGISTRY}/${ACR_NAMESPACE}/ink-realm-web:latest"

# 3) 登录 ACR
echo "[3/6] 登录阿里云 ACR..."
echo "${ACR_PASSWORD}" | docker login -u "${ACR_USERNAME}" --password-stdin "${ACR_REGISTRY}"

# 4) 拉取镜像
echo "[4/6] 拉取镜像..."
docker pull "${APP_IMAGE}"
docker pull "${WEB_IMAGE}"

# 5) 启动容器(compose 按 depends_on 顺序等待 postgres/redis healthy)
echo "[5/6] 启动容器..."
docker compose --env-file "${ENV_FILE}" up -d

# 6) 健康检查
echo "[6/6] 健康检查..."
HEALTH_URL="http://127.0.0.1:${MANAGEMENT_SERVER_PORT:-9689}/actuator/health"
elapsed=0
while [[ ${elapsed} -lt 180 ]]; do
  status=$(curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || true)
  if [[ "${status}" == "UP" ]]; then
    echo "  后端健康检查通过(用时 ${elapsed}s)"
    break
  fi
  sleep 5; elapsed=$((elapsed + 5))
done
if [[ ${elapsed} -ge 180 ]]; then
  echo "  ⚠️ 后端健康检查超时,请排查:docker compose logs --tail=200 ink-realm" >&2
fi

if curl -fsS http://127.0.0.1/ >/dev/null 2>&1; then
  echo "  nginx 健康检查通过"
else
  echo "  ⚠️ nginx 健康检查未通过,请排查:docker compose logs --tail=200 nginx" >&2
fi

echo ""
echo "================================================"
echo "  部署完成!"
echo "  后端健康: ${HEALTH_URL}"
echo "  前端访问: http://<服务器IP>/"
echo "================================================"
