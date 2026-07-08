#!/usr/bin/env bash
# ============================================================
# Ink Speaker 一键部署脚本(前后端整合版)
# ============================================================
# 功能:
#   1. 拉取后端 + 前端两个仓库的最新代码
#   2. 用 Docker 多阶段构建打两个镜像(后端 jar + 前端 nginx)
#   3. 滚动重启 ink-speaker + nginx 容器(不动 postgres/redis)
#   4. 健康检查,失败则回滚到上一版本镜像
#
# 用法:
#   ./deploy.sh                  # 默认从 origin/main 拉取前后端
#   ./deploy.sh dev              # 拉取 dev 分支
#   ./deploy.sh --no-pull        # 不拉 git,只重新构建镜像
#   ./deploy.sh --rollback       # 回滚到上一版本镜像
#   ./deploy.sh --backend-only  # 只部署后端(前端不动)
#   ./deploy.sh --frontend-only # 只部署前端(后端不动)
#
# 前置条件:
#   - 已安装 docker / docker compose
#   - 当前用户在 docker 组或用 sudo
#   - 已准备 .env.prod 文件(由 .env.prod.example 拷贝并修改)
#   - 后端仓库根目录有 Dockerfile,前端仓库 ink-speaker-web 也有 Dockerfile
#
# 目录布局:
#   /opt/ink-speaker/              ← 后端仓库(deploy.sh 所在目录)
#     ├── Dockerfile
#     ├── docker-compose.yml
#     ├── .env.prod
#     └── deploy.sh
#   /opt/ink-speaker-web/          ← 前端仓库(由本脚本自动 clone)
#     ├── Dockerfile
#     └── nginx.conf
# ============================================================
set -euo pipefail

# ------------------------------------------------------------
# 配置区
# ------------------------------------------------------------
APP_NAME="ink-speaker"
WEB_NAME="ink-speaker-web"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
WEB_DIR="${APP_DIR}-web"                          # /opt/ink-speaker-web

ENV_FILE="${APP_DIR}/.env.prod"
LOG_DIR="${APP_DIR}/deploy-logs"

IMAGE_APP_LATEST="${APP_NAME}:latest"
IMAGE_APP_BACKUP="${APP_NAME}:prev"
IMAGE_WEB_LATEST="${WEB_NAME}:latest"
IMAGE_WEB_BACKUP="${WEB_NAME}:prev"

HEALTH_URL="http://localhost:9688/actuator/health"
HEALTH_TIMEOUT=120                  # 健康检查最长等待秒数

# 前端 git 仓库地址(默认走 origin,可在 .env.prod 用 WEB_GIT_URL 覆盖)
WEB_GIT_URL="${WEB_GIT_URL:-git@github.com:Mox-lab/Ink-Speaker-Web.git}"

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

    # 加载 .env.prod(用于读取 WEB_GIT_URL 等自定义配置)
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
    WEB_GIT_URL="${WEB_GIT_URL:-git@github.com:Mox-lab/Ink-Speaker-Web.git}"

    info "前置检查通过"
    info "  后端目录:${APP_DIR}"
    info "  前端目录:${WEB_DIR}"
    info "  前端仓库:${WEB_GIT_URL}"
}

# ------------------------------------------------------------
# 拉取最新代码(后端 + 前端)
# ------------------------------------------------------------
pull_latest() {
    local branch="${1:-main}"
    info "拉取最新代码 (分支:${branch})..."

    # ---------- 后端 ----------
    cd "${APP_DIR}"
    if ! git diff --quiet || ! git diff --cached --quiet; then
        warn "后端检测到本地改动,执行 stash"
        git stash push -m "auto-stash before deploy $(date +%s)" || true
    fi
    git fetch origin --prune
    git checkout "${branch}"
    git reset --hard "origin/${branch}"
    info "  后端已更新到提交:$(git rev-parse --short HEAD)"

    # ---------- 前端 ----------
    if [[ ! -d "${WEB_DIR}/.git" ]]; then
        info "  前端目录不存在,首次 clone..."
        mkdir -p "$(dirname "${WEB_DIR}")"
        git clone "${WEB_GIT_URL}" "${WEB_DIR}"
        cd "${WEB_DIR}"
        git checkout "${branch}" || warn "  前端无 ${branch} 分支,留在默认分支"
    else
        cd "${WEB_DIR}"
        if ! git diff --quiet || ! git diff --cached --quiet; then
            warn "  前端检测到本地改动,执行 stash"
            git stash push -m "auto-stash before deploy $(date +%s)" || true
        fi
        git fetch origin --prune
        git checkout "${branch}" 2>/dev/null || true
        git reset --hard "origin/${branch}" 2>/dev/null || \
            warn "  前端无 ${branch} 分支,保持当前提交"
    fi
    info "  前端已更新到提交:$(git rev-parse --short HEAD)"
}

# ------------------------------------------------------------
# 备份当前镜像(用于回滚)
# ------------------------------------------------------------
backup_image() {
    info "备份当前镜像..."

    if [[ "${DEPLOY_FRONTEND:-yes}" == "yes" ]]; then
        if docker image inspect "${IMAGE_WEB_LATEST}" >/dev/null 2>&1; then
            docker tag "${IMAGE_WEB_LATEST}" "${IMAGE_WEB_BACKUP}"
            info "  已备份 ${IMAGE_WEB_LATEST} → ${IMAGE_WEB_BACKUP}"
        else
            warn "  未找到 ${IMAGE_WEB_LATEST},跳过备份(首次部署)"
        fi
    fi

    if [[ "${DEPLOY_BACKEND:-yes}" == "yes" ]]; then
        if docker image inspect "${IMAGE_APP_LATEST}" >/dev/null 2>&1; then
            docker tag "${IMAGE_APP_LATEST}" "${IMAGE_APP_BACKUP}"
            info "  已备份 ${IMAGE_APP_LATEST} → ${IMAGE_APP_BACKUP}"
        else
            warn "  未找到 ${IMAGE_APP_LATEST},跳过备份(首次部署)"
        fi
    fi
}

# ------------------------------------------------------------
# 构建镜像
# ------------------------------------------------------------
build_image() {
    # ---------- 前端 ----------
    if [[ "${DEPLOY_FRONTEND:-yes}" == "yes" ]]; then
        info "构建前端镜像..."
        cd "${WEB_DIR}"
        docker build -t "${IMAGE_WEB_LATEST}" . 2>&1 | tee -a "${LOG_FILE}"
        info "  前端镜像构建完成"
    fi

    # ---------- 后端 ----------
    if [[ "${DEPLOY_BACKEND:-yes}" == "yes" ]]; then
        info "构建后端镜像..."
        cd "${APP_DIR}"
        docker build -t "${IMAGE_APP_LATEST}" . 2>&1 | tee -a "${LOG_FILE}"
        info "  后端镜像构建完成"
    fi
}

# ------------------------------------------------------------
# 滚动重启
# ------------------------------------------------------------
restart_services() {
    info "重启服务..."
    cd "${APP_DIR}"

    # 仅重启应用容器,不动 postgres/redis(避免数据中断)
    if [[ "${DEPLOY_BACKEND:-yes}" == "yes" ]]; then
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate "${APP_NAME}"
    fi
    if [[ "${DEPLOY_FRONTEND:-yes}" == "yes" ]]; then
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate nginx
    fi

    info "等待后端健康检查(最长 ${HEALTH_TIMEOUT}s)..."
    local elapsed=0
    while [[ ${elapsed} -lt ${HEALTH_TIMEOUT} ]]; do
        local status
        status=$(curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "")
        if [[ "${status}" == "UP" ]]; then
            info "  后端健康检查通过(用时 ${elapsed}s)"
            break
        fi
        sleep 5
        elapsed=$((elapsed + 5))
        info "  等待中... ${elapsed}s elapsed"
    done

    if [[ "${elapsed}" -ge "${HEALTH_TIMEOUT}" ]]; then
        fatal "后端健康检查超时,启动失败"
    fi

    # 前端 nginx 健康检查(快速,nginx 启动几秒就绪)
    if [[ "${DEPLOY_FRONTEND:-yes}" == "yes" ]]; then
        info "等待 nginx 健康检查..."
        local web_elapsed=0
        while [[ ${web_elapsed} -lt 30 ]]; do
            if curl -fsS http://localhost/ >/dev/null 2>&1; then
                info "  nginx 健康检查通过(用时 ${web_elapsed}s)"
                return 0
            fi
            sleep 2
            web_elapsed=$((web_elapsed + 2))
        done
        warn "  nginx 健康检查 30s 内未通过,但容器可能仍在启动,继续..."
    fi
}

# ------------------------------------------------------------
# 回滚
# ------------------------------------------------------------
rollback() {
    info "执行回滚..."

    if docker image inspect "${IMAGE_APP_BACKUP}" >/dev/null 2>&1; then
        docker tag "${IMAGE_APP_BACKUP}" "${IMAGE_APP_LATEST}"
        cd "${APP_DIR}"
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate "${APP_NAME}"
        info "  后端已回滚"
    else
        warn "  未找到 ${IMAGE_APP_BACKUP},后端跳过回滚"
    fi

    if docker image inspect "${IMAGE_WEB_BACKUP}" >/dev/null 2>&1; then
        docker tag "${IMAGE_WEB_BACKUP}" "${IMAGE_WEB_LATEST}"
        cd "${APP_DIR}"
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate nginx
        info "  前端已回滚"
    else
        warn "  未找到 ${IMAGE_WEB_BACKUP},前端跳过回滚"
    fi

    info "回滚完成,等待健康检查..."
    sleep 30
    local status
    status=$(curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || echo "")
    if [[ "${status}" == "UP" ]]; then
        info "  回滚成功,服务已恢复"
    else
        error "  回滚后健康检查仍未通过,请人工介入"
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
    info "================ 启动部署(前后端整合) ================"
    info "应用:后端 ${APP_NAME} + 前端 ${WEB_NAME}"
    info "目录:后端 ${APP_DIR} / 前端 ${WEB_DIR}"

    # 解析参数
    DEPLOY_BACKEND="yes"
    DEPLOY_FRONTEND="yes"

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --rollback)
                check_prerequisites
                rollback
                exit 0
                ;;
            --no-pull)
                NO_PULL="yes"
                shift
                ;;
            --backend-only)
                DEPLOY_FRONTEND="no"
                shift
                ;;
            --frontend-only)
                DEPLOY_BACKEND="no"
                shift
                ;;
            *)
                BRANCH="$1"
                shift
                ;;
        esac
    done

    check_prerequisites

    if [[ "${NO_PULL:-no}" != "yes" ]]; then
        pull_latest "${BRANCH:-main}"
    fi

    backup_image
    build_image
    restart_services
    cleanup

    info "================ 部署成功 ================"
    info "日志:${LOG_FILE}"
    info "后端健康检查:${HEALTH_URL}"
    info "前端访问入口:http://<服务器IP>/"
}

main "$@"
