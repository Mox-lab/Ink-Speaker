#!/usr/bin/env bash
# ============================================================
# 腾讯云服务器(Debian 13.2)一键初始化脚本
# ============================================================
# 功能:
#   1. 更新系统包
#   2. 安装 Docker + Docker Compose v2 + git + curl
#   3. 配置 Docker 镜像加速(国内必备)
#   4. 配置 Docker 开机自启
#   5. 创建 swap(4GB 内存机器强烈建议)
#   6. 系统参数调优(文件句柄 / TCP)
#
# 用法:
#   sudo bash server-init.sh
# ============================================================
set -euo pipefail

# 必须用 root
if [[ $EUID -ne 0 ]]; then
    echo "请用 root 或 sudo 执行:sudo bash $0" >&2
    exit 1
fi

echo "================================================"
echo "  Ink Speaker 服务器初始化(Debian 13.2)"
echo "================================================"

# ------------------------------------------------------------
# 1. 更新系统
# ------------------------------------------------------------
echo "[1/8] 更新系统包..."
apt-get update -y
apt-get upgrade -y
# 注意:
#   - software-properties-common 是 Ubuntu 包,Debian 13 trixie 仓库无此包,不需要
#     (本脚本添加 Docker 源用的是手动 tee 写 sources.list,不依赖 add-apt-repository)
#   - apt-transport-https 在 Debian 11+ 已合并进 apt 主包,无需单独安装
apt-get install -y ca-certificates curl gnupg lsb-release \
    git vim htop iotop iftop \
    postgresql-client redis-tools

# ------------------------------------------------------------
# 2. 安装 Docker(阿里云镜像源,国内访问快)
# ------------------------------------------------------------
echo "[2/8] 安装 Docker..."
if ! command -v docker >/dev/null 2>&1; then
    install -m 0755 -d /etc/apt/keyrings
    # 阿里云 Docker CE 镜像(替代官方 download.docker.com)
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
else
    echo "  Docker 已安装,跳过"
fi

# ------------------------------------------------------------
# 3. Docker 镜像加速(国内必备,否则拉镜像超时)
# ------------------------------------------------------------
echo "[3/8] 配置 Docker 镜像加速..."
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
  },
  "default-address-pools": [
    {"base": "172.20.0.0/16", "size": 24}
  ]
}
EOF
systemctl daemon-reload
systemctl enable docker
systemctl restart docker

# ------------------------------------------------------------
# 4. 创建 swap(4GB 内存机器必备,避免 OOM kill)
# ------------------------------------------------------------
echo "[4/8] 配置 swap..."
if [[ ! -f /swapfile ]]; then
    fallocate -l 4G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    echo 'vm.swappiness=10' >> /etc/sysctl.conf
    echo "  已创建 4GB swap"
else
    echo "  swap 已存在,跳过"
fi

# ------------------------------------------------------------
# 5. 系统参数调优
# ------------------------------------------------------------
echo "[5/8] 系统参数调优..."
cat > /etc/sysctl.d/99-ink-speaker.conf <<'EOF'
# 文件句柄上限(应用 + postgres 都需要)
fs.file-max = 655350

# TCP 连接相关
net.core.somaxconn = 4096
net.ipv4.tcp_max_syn_backlog = 4096
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_keepalive_time = 600

# 内存分配策略(避免 OOM 时直接 kill 进程)
vm.overcommit_memory = 1
EOF
sysctl -p /etc/sysctl.d/99-ink-speaker.conf

# 提升 docker 用户的文件句柄限制
cat > /etc/security/limits.d/99-ink-speaker.conf <<'EOF'
*       soft    nofile  655350
*       hard    nofile  655350
root    soft    nofile  655350
root    hard    nofile  655350
EOF

# ------------------------------------------------------------
# 6. 创建应用目录
# ------------------------------------------------------------
echo "[6/8] 创建应用目录..."
APP_DIR="/opt/ink-speaker"
mkdir -p "${APP_DIR}"
echo "  应用目录:${APP_DIR}"

# ------------------------------------------------------------
# 7. 配置防火墙(仅放行业务端口)
# ------------------------------------------------------------
echo "[7/8] 配置防火墙..."
if command -v ufw >/dev/null 2>&1; then
    ufw allow 22/tcp       comment 'SSH' || true
    ufw allow 9688/tcp     comment 'Ink Speaker 业务端口' || true
    # 9689 Actuator 端口不对外,仅本机访问
    ufw --force enable || true
    echo "  ufw 已启用,仅放行 22/9688"
else
    # Debian 默认无 ufw,用 iptables 简单规则
    echo "  未安装 ufw,建议手动配置腾讯云安全组:
        - 放行入站:22(SSH)、9688(HTTP)
        - 拒绝入站:9689(Actuator)、5432(PG)、6379(Redis)"
fi

# ------------------------------------------------------------
# 8. 验证
# ------------------------------------------------------------
echo "[8/8] 验证安装..."
echo "--- Docker 版本 ---"
docker --version
docker compose version
echo "--- 系统资源 ---"
free -h
df -h /
echo "--- Docker 服务状态 ---"
systemctl is-active docker

echo ""
echo "================================================"
echo "  初始化完成!"
echo "================================================"
echo ""
echo "下一步:"
echo "  1. cd /opt/ink-speaker"
echo "  2. git clone <你的仓库地址> ."
echo "  3. cp .env.prod.example .env.prod && vim .env.prod"
echo "  4. bash deploy.sh"
echo ""
echo "腾讯云安全组配置(在控制台 → 安全组):"
echo "  - 入站规则:放行 22/tcp、9688/tcp"
echo "  - 入站规则:拒绝 9689/tcp(Actuator)、5432/tcp(PG)、6379/tcp(Redis)"
