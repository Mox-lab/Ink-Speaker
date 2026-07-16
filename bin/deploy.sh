#!/usr/bin/env bash
# ============================================================
# Ink Realm 一键部署脚本(纯镜像拉取模式)
# ============================================================
# 特点:
#   - 不需要源代码(服务器无 src/ / pom.xml / package.json)
#   - 不需要 git pull(部署文件由 scp 上传,或从 release 下载)
#   - 直接从阿里云 ACR 拉取已构建镜像,30 秒完成部署
#
# 前置条件:
#   - 已安装 docker / docker compose
#   - 当前目录有 docker-compose.yml + .env.prod
#
# 用法:
#   ./deploy.sh                # 拉取 latest 镜像并重启
#   ./deploy.sh --rollback     # 回滚到上一版本
#   ./deploy.sh --backend-only  # 只更新后端
#   ./deploy.sh --frontend-only # 只更新前端
# ============================================================
set -euo pipefail

# ------------------------------------------------------------
# 配置区
# ------------------------------------------------------------
APP_NAME="ink-realm"
WEB_NAME="ink-realm-web"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${APP_DIR}/.env.prod"
LOG_DIR="${APP_DIR}/deploy-logs"

mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/deploy-$(date +%Y%m%d-%H%M%S).log"

HEALTH_PORT="${MANAGEMENT_SERVER_PORT:-9689}"
HEALTH_URL="http://localhost:${HEALTH_PORT}/actuator/health"
HEALTH_TIMEOUT=180

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
# 前置检查 + 加载环境变量
# ------------------------------------------------------------
check_prerequisites() {
    info "前置检查..."
    command -v docker >/dev/null || fatal "未安装 docker"
    docker compose version >/dev/null 2>&1 || fatal "未安装 docker compose(v2)"

    if [[ ! -f "${ENV_FILE}" ]]; then
        fatal "缺少环境变量文件:${ENV_FILE}
        请执行:cp .env.prod.example .env.prod 并填入真实密钥"
    fi

    if [[ ! -f "${APP_DIR}/docker-compose.yml" ]]; then
        fatal "缺少 docker-compose.yml 文件,请确认部署文件齐全"
    fi

    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a

    # 校验必填项
    : "${DATA_DIR:?在 .env.prod 中设置 DATA_DIR}"
    : "${ACR_REGISTRY:?在 .env.prod 中设置 ACR_REGISTRY}"
    : "${ACR_NAMESPACE:?在 .env.prod 中设置 ACR_NAMESPACE}"
    : "${ACR_USERNAME:?在 .env.prod 中设置 ACR_USERNAME}"
    : "${ACR_PASSWORD:?在 .env.prod 中设置 ACR_PASSWORD}"

    APP_IMAGE="${ACR_REGISTRY}/${ACR_NAMESPACE}/${APP_NAME}:latest"
    WEB_IMAGE="${ACR_REGISTRY}/${ACR_NAMESPACE}/${WEB_NAME}:latest"

    info "前置检查通过"
    info "  后端镜像:${APP_IMAGE}"
    info "  前端镜像:${WEB_IMAGE}"
    info "  数据目录:${DATA_DIR}"
}

# ------------------------------------------------------------
# 创建数据目录(确保 bind mount 不会因为宿主机目录不存在而用 Docker 自动创建的 root 权限目录)
# ------------------------------------------------------------
ensure_data_dirs() {
    info "创建数据目录..."
    mkdir -p "${DATA_DIR}/postgres"
    mkdir -p "${DATA_DIR}/logs"
    mkdir -p "${DATA_DIR}/knowledge-base"
    chmod 755 "${DATA_DIR}"
    info "  ${DATA_DIR}/postgres"
    info "  ${DATA_DIR}/logs"
    info "  ${DATA_DIR}/knowledge-base"
}

# ------------------------------------------------------------
# 登录 ACR
# ------------------------------------------------------------
login_acr() {
    info "登录阿里云 ACR..."
    echo "${ACR_PASSWORD}" | docker login -u "${ACR_USERNAME}" --password-stdin "${ACR_REGISTRY}" \
        >/dev/null 2>&1
    info "  ACR 登录成功"
}

# ------------------------------------------------------------
# 拉取最新镜像
# ------------------------------------------------------------
pull_images() {
    if [[ "${DEPLOY_BACKEND:-yes}" == "yes" ]]; then
        info "拉取后端镜像..."
        docker pull "${APP_IMAGE}" 2>&1 | tee -a "${LOG_FILE}"
        info "  后端镜像拉取完成"
    fi
    if [[ "${DEPLOY_FRONTEND:-yes}" == "yes" ]]; then
        info "拉取前端镜像..."
        docker pull "${WEB_IMAGE}" 2>&1 | tee -a "${LOG_FILE}"
        info "  前端镜像拉取完成"
    fi
}

# ------------------------------------------------------------
# 备份当前镜像(用 tag 方式,本地保留 prev 版本)
# ------------------------------------------------------------
backup_images() {
    info "备份当前镜像(用于回滚)..."

    if [[ "${DEPLOY_BACKEND:-yes}" == "yes" ]]; then
        if docker image inspect "${APP_IMAGE}" >/dev/null 2>&1; then
            docker tag "${APP_IMAGE}" "${APP_IMAGE%latest}prev"
            info "  后端已备份 → ${APP_IMAGE%latest}prev"
        else
            warn "  后端镜像不存在,跳过备份(首次部署)"
        fi
    fi

    if [[ "${DEPLOY_FRONTEND:-yes}" == "yes" ]]; then
        if docker image inspect "${WEB_IMAGE}" >/dev/null 2>&1; then
            docker tag "${WEB_IMAGE}" "${WEB_IMAGE%latest}prev"
            info "  前端已备份 → ${WEB_IMAGE%latest}prev"
        else
            warn "  前端镜像不存在,跳过备份(首次部署)"
        fi
    fi
}

# ------------------------------------------------------------
# 滚动重启
# ------------------------------------------------------------
restart_services() {
    info "重启服务..."
    cd "${APP_DIR}"

    if [[ "${DEPLOY_BACKEND:-yes}" == "yes" ]]; then
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate "${APP_NAME}"
    fi
    if [[ "${DEPLOY_FRONTEND:-yes}" == "yes" ]]; then
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate nginx
    fi

    info "等待后端健康检查(${HEALTH_URL},最长 ${HEALTH_TIMEOUT}s)..."
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
        fatal "后端健康检查超时,启动失败
        排查命令:docker compose logs --tail=200 ink-realm"
    fi

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
        warn "  nginx 健康检查 30s 内未通过,继续..."
    fi
}

# ------------------------------------------------------------
# 回滚到上一版本
# ------------------------------------------------------------
rollback() {
    info "执行回滚..."

    if docker image inspect "${APP_IMAGE%latest}prev" >/dev/null 2>&1; then
        docker tag "${APP_IMAGE%latest}prev" "${APP_IMAGE}"
        cd "${APP_DIR}"
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate "${APP_NAME}"
        info "  后端已回滚"
    else
        warn "  未找到后端备份镜像,跳过"
    fi

    if docker image inspect "${WEB_IMAGE%latest}prev" >/dev/null 2>&1; then
        docker tag "${WEB_IMAGE%latest}prev" "${WEB_IMAGE}"
        cd "${APP_DIR}"
        docker compose --env-file "${ENV_FILE}" up -d --no-deps --force-recreate nginx
        info "  前端已回滚"
    else
        warn "  未找到前端备份镜像,跳过"
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
# 清理 dangling 镜像
# ------------------------------------------------------------
cleanup() {
    info "清理 dangling 镜像..."
    docker image prune -f --filter "dangling=true" || true
}

# ------------------------------------------------------------
# 主流程
# ------------------------------------------------------------
main() {
    info "================ 启动部署(纯镜像拉取模式) ================"

    DEPLOY_BACKEND="yes"
    DEPLOY_FRONTEND="yes"

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --rollback)
                check_prerequisites
                rollback
                exit 0
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
                fatal "未知参数:$1
                用法:./deploy.sh [--rollback|--backend-only|--frontend-only]"
                ;;
        esac
    done

    check_prerequisites
    ensure_data_dirs
    login_acr
    backup_images
    pull_images
    restart_services
    cleanup

    info "================ 部署成功 ================"
    info "日志:${LOG_FILE}"
    info "后端健康检查:${HEALTH_URL}"
    info "前端访问:http://<服务器IP>/"
}

main "$@"
