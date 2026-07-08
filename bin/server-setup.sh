#!/usr/bin/env bash
# ============================================================
# Ink Speaker 服务器初始化脚本(纯部署模式,无源代码)
# ============================================================
# 功能:
#   1. 安装 Docker + Docker Compose v2 + curl(如未装)
#   2. 创建 /opt/ink-speaker 部署目录
#   3. 下载部署文件(docker-compose.yml / deploy.sh / .env.prod.example)
#   4. 提示用户编辑 .env.prod 后执行 deploy.sh
#
# 用法:
#   curl -fsSL <脚本URL> | sudo bash
#   或:
#   sudo bash server-setup.sh
#
# 部署文件来源(二选一):
#   1. 从 GitHub 仓库下载(默认,需要仓库 public)
#   2. 从本地 scp 上传(脚本会检测文件是否已存在)
# ============================================================
set -euo pipefail

# 必须用 root
if [[ $EUID -ne 0 ]]; then
    echo "请用 root 或 sudo 执行:sudo bash $0" >&2
    exit 1
fi

# ------------------------------------------------------------
# 配置区
# ------------------------------------------------------------
APP_DIR="/opt/ink-speaker"
DATA_DIR="${APP_DIR}/data"
DEPLOY_FILES=(docker-compose.yml deploy.sh .env.prod.example)

# 部署文件下载源(GitHub raw,public 仓库)
# 默认指向 Mox-lab/Ink-Speaker main 分支
GITHUB_RAW_BASE="https://raw.githubusercontent.com/Mox-lab/Ink-Speaker/main"

echo "================================================"
echo "  Ink Speaker 服务器初始化(无源代码模式)"
echo "================================================"

# ------------------------------------------------------------
# 1. 安装 Docker(如未装)
# ------------------------------------------------------------
echo "[1/4] 检查 Docker..."
if ! command -v docker >/dev/null 2>&1; then
    echo "  Docker 未安装,开始安装..."
    apt-get update -y
    apt-get install -y ca-certificates curl gnupg

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/debian/gpg | \
        gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://mirrors.aliyun.com/docker-ce/linux/debian \
        $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
        tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io \
        docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
    echo "  Docker 安装完成"
else
    echo "  Docker 已安装,$(docker --version)"
fi

# ------------------------------------------------------------
# 2. 配置 Docker 镜像加速(国内必备,如未配)
# ------------------------------------------------------------
echo "[2/4] 配置 Docker 镜像加速..."
if [[ ! -f /etc/docker/daemon.json ]] || ! grep -q "registry-mirrors" /etc/docker/daemon.json 2>/dev/null; then
    mkdir -p /etc/docker
    cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://docker.nju.edu.cn",
    "https://docker.mirrors.ustc.edu.cn"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "50m",
    "max-file": "3"
  }
}
EOF
    systemctl daemon-reload
    systemctl restart docker
    echo "  镜像加速已配置"
else
    echo "  镜像加速已存在,跳过"
fi

# ------------------------------------------------------------
# 3. 创建目录 + 下载部署文件
# ------------------------------------------------------------
echo "[3/4] 创建部署目录 + 下载部署文件..."
mkdir -p "${APP_DIR}"
mkdir -p "${DATA_DIR}/postgres"
mkdir -p "${DATA_DIR}/logs"
mkdir -p "${DATA_DIR}/knowledge-base"
chmod 755 "${DATA_DIR}"

cd "${APP_DIR}"

for file in "${DEPLOY_FILES[@]}"; do
    if [[ -f "${file}" ]]; then
        echo "  ${file} 已存在,跳过下载(如需更新请先删除)"
    else
        echo "  下载 ${file}..."
        if curl -fsSL "${GITHUB_RAW_BASE}/${file}" -o "${file}"; then
            echo "    ✓ ${file}"
        else
            echo "    ✗ 下载 ${file} 失败"
            echo "    请手动从 GitHub 仓库下载:${GITHUB_RAW_BASE}/${file}"
            echo "    或在本地用 scp 上传到 ${APP_DIR}/"
        fi
    fi
done

# 给 deploy.sh 加执行权限
[[ -f deploy.sh ]] && chmod +x deploy.sh

echo ""
echo "  部署目录结构:"
ls -la "${APP_DIR}"

# ------------------------------------------------------------
# 4. 配置防火墙
# ------------------------------------------------------------
echo "[4/4] 配置防火墙..."
if command -v ufw >/dev/null 2>&1; then
    ufw allow 22/tcp   comment 'SSH' || true
    ufw allow 80/tcp   comment 'Ink Speaker HTTP' || true
    ufw --force enable || true
    echo "  ufw 已启用,仅放行 22/80"
else
    echo "  未安装 ufw,建议手动配置腾讯云安全组:
    - 放行入站:22(SSH)、80(HTTP)
    - 拒绝入站:9688(后端)、9689(Actuator)、5432(PG)、6379(Redis)"
fi

# ------------------------------------------------------------
# 验证 + 下一步提示
# ------------------------------------------------------------
echo ""
echo "================================================"
echo "  初始化完成!"
echo "================================================"
echo ""
echo "当前状态:"
echo "  Docker:       $(docker --version 2>&1)"
echo "  Compose:      $(docker compose version 2>&1 | head -1)"
echo "  部署目录:     ${APP_DIR}"
echo "  数据目录:     ${DATA_DIR}"
echo ""
echo "下一步操作:"
echo "  cd ${APP_DIR}"
echo "  cp .env.prod.example .env.prod"
echo "  vim .env.prod       # 填入真实密钥(8 项必填)"
echo "  chmod 600 .env.prod"
echo "  ./deploy.sh"
echo ""
echo "必填项(.env.prod):"
echo "  DATA_DIR         (默认 /opt/ink-speaker/data,可保留)"
echo "  DB_PASSWORD      (openssl rand -base64 24)"
echo "  OPENAI_API_KEY   (LLM API Key)"
echo "  JWT_SECRET       (openssl rand -base64 48)"
echo "  JASYPT_KEY       (openssl rand -base64 32)"
echo "  ACR_REGISTRY     (阿里云 ACR 地址)"
echo "  ACR_NAMESPACE    (ACR 命名空间)"
echo "  ACR_USERNAME     (ACR 账号)"
echo "  ACR_PASSWORD     (ACR 固定密码)"
echo ""
echo "腾讯云安全组(在控制台配置):"
echo "  - 入站放行:22/tcp、80/tcp"
echo "  - 入站拒绝:9688/9689/5432/6379"
