#!/usr/bin/env bash
# ============================================================
# Ink Speaker 一键部署脚本
# ============================================================
# 功能:
#   1. 拉取 git 最新代码
#   2. 用 Docker 多阶段构建打镜像
#   3. 滚动重启 ink-speaker 容器(无残留旧容器)
#   4. 健康检查,失败则回滚到上一版本
#
# 用法:
#   ./deploy.sh              # 默认从 origin/main 拉取
#   ./deploy.sh dev          # 拉取 dev 分支
#   ./deploy.sh --no-pull    # 不拉 git,只重新构建镜像
#   ./deploy.sh --rollback   # 回滚到上一版本镜像
#
# 前置条件:
#   - 已安装 docker / docker compose
#   - 当前用户在 docker 组或用 sudo
#   - 已准备 .env.prod 文件(由 .env.prod.example 拷贝并修改)
# ============================================================
set -euo pipefail

# ------------------------------------------------------------
# 配置区
# ------------------------------------------------------------
APP_NAME="ink-speaker"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${APP_DIR}/.env.prod"
LOG_DIR="${APP_DIR}/deploy-logs"
IMAGE_LATEST="${APP_NAME}:latest"
IMAGE_BACKUP="${APP_NAME}:prev"
HEALTH_URL="http://localhost:9688/actuator/health"
HEALTH_TIMEOUT=120                  # 健康检查最长等待秒数

mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/deploy-$(date +%Y%m%d-%H%M%S).log"

# ------------------------------------------------------------
# 日志函数
# ------------------------------------------------------------
log() {
    local level=$1; shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [${level}] $*" | tee -a "${LOG_FILE}"
}

info()  { log "INFO"  "$@"; }
warn()  { log "WARN"  "$@"; }
error() { log "ERROR" "$@" >&2; }
fatal() { error "$@"; exit 1; }

trap 'error "部署失败,详见日志:${LOG_FILE}"' ERR

# ------------------------------------------------------------
# 前置检查
# ------------------------------------------------------------
check_prerequisites() {
    info "前置检查..."
    command -v git >/dev/null || fatal "未安装 git"
    command -v docker >/dev/null || fatal "未安装 docker"
    docker compose version >/dev/null 2>&1 || fatal "未安装 docker compose(v2)"

    if [[ ! -f "${ENV_FILE}" ]]; then
        fatal "缺少环境变量文件:${ENV_FILE}
        请执行:cp .env.prod.example .env.prod 并填入真实密钥"
    fi

    info "前置检查通过"
}

# ------------------------------------------------------------
# 拉取最新代码
# ------------------------------------------------------------
pull_latest() {
    local branch="${1:-main}"
    info "拉取最新代码 (分支:${branch})..."

    cd "${APP_DIR}"

    # 暂存本地未提交的改动(避免 pull 失败)
    if ! git diff --quiet || ! git diff --cached --quiet; then
        warn "检测到本地改动,执行 stash"
        git stash push -m "auto-stash before deploy $(date +%s)" || true
    fi

    git fetch origin --prune
    git checkout "${branch}"
    git reset --hard "origin/${branch}"

    local commit
    commit=$(git rev-parse --short HEAD)
    info "已更新到提交:${commit}"
}

# ------------------------------------------------------------
# 备份当前镜像(用于回滚)
# ------------------------------------------------------------
backup_image() {
    info "备份当前镜像..."
    if docker image inspect "${IMAGE_LATEST}" >/dev/null 2>&1; then
        docker tag "${IMAGE_LATEST}" "${IMAGE_BACKUP}"
        info "已备份 ${IMAGE_LATEST} → ${IMAGE_BACKUP}"
    else
        warn "未找到 ${IMAGE_LATEST},跳过备份(首次部署)"
    fi
}

# ------------------------------------------------------------
# 构建新镜像
# ------------------------------------------------------------
build_image() {
    info "构建 Docker 镜像..."
    cd "${APP_DIR}"
    docker build -t "${IMAGE_LATEST}" . 2>&1 | tee -a "${LOG_FILE}"
    info "镜像构建完成"
}

# ------------------------------------------------------------
# 滚动重启
# ------------------------------------------------------------
restart_services() {
    info "重启服务..."
    cd "${APP_DIR}"

    # 仅重启应用容器,不动 postgres/redis(避免数据中断)
    docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate "${APP_NAME}"

    info "等待健康检查(最长 ${HEALTH_TIMEOUT}s)..."
    local elapsed=0
    while [[ ${elapsed} -lt ${HEALTH_TIMEOUT} ]]; do
        local status
        status=$(curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "")
        if [[ "${status}" == "UP" ]]; then
            info "健康检查通过(用时 ${elapsed}s)"
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
        info "等待中... ${elapsed}s elapsed"
    done

    fatal "健康检查超时,启动失败"
}

# ------------------------------------------------------------
# 回滚
# ------------------------------------------------------------
rollback() {
    info "执行回滚..."
    if ! docker image inspect "${IMAGE_BACKUP}" >/dev/null 2>&1; then
        fatal "未找到备份镜像 ${IMAGE_BACKUP},无法回滚"
    fi

    docker tag "${IMAGE_BACKUP}" "${IMAGE_LATEST}"
    cd "${APP_DIR}"
    docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate "${APP_NAME}"

    info "回滚完成,等待健康检查..."
    sleep 30
    local status
    status=$(curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "")
    if [[ "${status}" == "UP" ]]; then
        info "回滚成功,服务已恢复"
    else
        error "回滚后健康检查仍未通过,请人工介入"
    fi
}

# ------------------------------------------------------------
# 清理旧镜像(保留最新 + 备份)
# ------------------------------------------------------------
cleanup() {
    info "清理 dangling 镜像..."
    docker image prune -f --filter "dangling=true" || true
}

# ------------------------------------------------------------
# 主流程
# ------------------------------------------------------------
main() {
    info "================ 启动部署 ================"
    info "应用:${APP_NAME}  目录:${APP_DIR}"

    check_prerequisites

    if [[ "${1:-}" == "--rollback" ]]; then
        rollback
        exit 0
    fi

    if [[ "${1:-}" != "--no-pull" ]]; then
        pull_latest "${1:-main}"
    fi

    backup_image
    build_image
    restart_services
    cleanup

    info "================ 部署成功 ================"
    info "日志:${LOG_FILE}"
    info "健康检查:${HEALTH_URL}"
}

main "$@"
