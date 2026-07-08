# Ink Speaker 部署文档(腾讯云 4GB 服务器 / Debian 13.2)

> 本文档覆盖从空服务器到 Ink Speaker 上线的全部流程,所有步骤均可复制粘贴执行。
>
> **部署模式**:GitHub Actions 构建镜像 → 推阿里云 ACR → 服务器 docker pull 拉取部署。
> 服务器**不存放源代码**,只保留部署脚本(docker-compose.yml + deploy.sh + .env.prod)。

---

## 一、配置评估结论

### 服务器配置

| 项目 | 规格 |
|------|------|
| CPU | 4 核 |
| 内存 | 4 GB |
| 系统盘 | 40 GB SSD |
| 带宽 | 3 Mbps |
| 流量包 | 300 GB/月 |
| 操作系统 | Debian 13.2 64bit |

### 结论:**够用**

Ink Speaker 是单机部署的中小型应用,核心负载是 LLM API 调用(瓶颈在网络 IO,不在本机 CPU),PostgreSQL 仅做元数据 + pgvector 向量存储,Redis 仅做 L2 缓存 + ShedLock。4 核 / 4GB / 40GB SSD 完全满足生产运行 + 适度并发(预估 50~100 QPS)。

### 资源分配表(4GB 总量)

| 组件 | 内存上限 | 说明 |
|------|----------|------|
| PostgreSQL 16 + pgvector | 512 MB | shared_buffers=128MB, max_connections=100 |
| Redis 7 | 96 MB | maxmemory=64mb, 关闭 RDB/AOF |
| Ink Speaker 后端 | 2048 MB | -Xmx ≈ 1.4GB(-XX:MaxRAMPercentage=70), 堆外/元空间预留 |
| Nginx 前端 + 反代 | 64 MB | 托管静态 dist + 反代 /api 到后端 |
| 系统 + Docker daemon | ~500 MB | 含 OS 内核 + Docker 进程 |
| 预留缓冲 | ~800 MB | 避免 OOM kill, 突发流量兜底 |

**磁盘占用预估**:
- Docker 镜像:~1.5 GB(jdk-jre 350MB + postgres 400MB + redis 50MB + 后端 350MB + nginx+dist 80MB)
- Postgres 数据:预估 5~10 GB(根据向量数据增长)
- 应用日志:预估 1~2 GB
- 知识库:预估 1~5 GB
- **总计**:首日 ~3.5 GB,长期增长 < 20 GB,**40 GB SSD 足够**

---

## 二、部署架构

```
┌─────────────────────────────────────────────────────┐
│                腾讯云 175.24.206.254                 │
│                Debian 13.2 64bit                    │
│                                                     │
│  /opt/ink-speaker/         (无源代码,只有部署文件)│
│  ├── docker-compose.yml    ← 编排                   │
│  ├── deploy.sh             ← 一键部署              │
│  ├── .env.prod             ← 环境变量(不进 git)  │
│  └── data/                 ← 持久化目录(bind mount)│
│      ├── postgres/         (pg 数据)              │
│      ├── logs/             (应用日志)             │
│      └── knowledge-base/   (知识库)               │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │  Docker Engine + Compose v2                 │   │
│  │                                             │   │
│  │  ┌──────────────┐  ┌──────────────┐         │   │
│  │  │  postgres    │  │   redis      │         │   │
│  │  │  512MB       │  │   96MB       │         │   │
│  │  │  127.0.0.1   │  │  127.0.0.1   │         │   │
│  │  │   :5432      │  │    :6379     │         │   │
│  │  └──────┬───────┘  └──────┬───────┘         │   │
│  │         │                 │                 │   │
│  │         └────────┬────────┘                 │   │
│  │                  │                          │   │
│  │           ┌──────┴───────┐                  │   │
│  │           │ ink-speaker  │  127.0.0.1       │   │
│  │           │   2048MB     │  :9688 :9689    │   │
│  │           └──────┬───────┘                  │   │
│  │                  │                          │   │
│  │           ┌──────┴───────┐                  │   │
│  │           │    nginx     │  :80  ←──── 用户│   │
│  │           │   64MB       │  (对外)         │   │
│  │           └──────────────┘                  │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
              │                  │
              │ SSH              │ HTTP 80
              ▼                  ▼
        运维访问            用户访问(http://175.24.206.254/)
```

**端口说明**:

| 端口 | 用途 | 是否对外 |
|------|------|----------|
| 22 | SSH | 对外(安全组放行) |
| 80 | Nginx 用户访问入口 | 对外 |
| 9688 | 后端业务端口 | **仅本机**(由 nginx 反代) |
| 9689 | Actuator 监控端口 | **仅本机** |
| 5432 | PostgreSQL | **仅本机** |
| 6379 | Redis | **仅本机** |

**构建流程**(GitHub → ACR → 服务器):

```
开发者 push 代码到 GitHub
        │
        ▼
GitHub Actions 自动触发(前后端各自 workflow)
        │
        ├─ 后端:mvn package → docker build → docker push 到 ACR
        └─ 前端:pnpm build  → docker build → docker push 到 ACR
        │
        ▼
阿里云 ACR
  ├─ ink-speaker:latest
  └─ ink-speaker-web:latest
        │
        │  服务器 SSH 执行 ./deploy.sh
        ▼
腾讯云服务器 docker pull 拉镜像 → docker compose up -d 重启容器
```

---

## 三、完整部署流程

### Step 1:本地准备 — 推送代码到 GitHub(首次)

确保后端和前端仓库的最新代码已 push 到 GitHub main 分支:

```bash
# 后端
cd "D:/ProjectCode/Ink Speaker/ink-speaker"
git add .
git commit -m "refactor: 部署模式改为镜像拉取,服务器无源代码"
git push origin main

# 前端
cd "D:/ProjectCode/Ink Speaker/ink-speaker-web"
git add .
git commit -m "ci: 完善构建流程"
git push origin main
```

push 后 GitHub Actions 会自动构建镜像推到 ACR(约 5 分钟)。

### Step 2:配置 GitHub Secrets

到 `github.com/Mox-lab/Ink-Speaker` 和 `github.com/Mox-lab/Ink-Speaker-Web` 两个仓库,各自:
- Settings → Secrets and variables → Actions → New repository secret
- 添加 4 个 Secrets:

| Name | Secret |
|------|--------|
| `ACR_REGISTRY` | `crpi-2t2oc7kie6ke8uce.cn-hangzhou.personal.cr.aliyuncs.com` |
| `ACR_NAMESPACE` | `mox-labb` |
| `ACR_USERNAME` | 你的阿里云账号 |
| `ACR_PASSWORD` | ACR 固定密码 |

### Step 3:腾讯云安全组配置

登录腾讯云控制台 → 云服务器 → 安全组 → 配置规则:

**入站规则**:

| 协议端口 | 策略 | 备注 |
|----------|------|------|
| TCP:22 | 允许 | SSH |
| TCP:80 | 允许 | Nginx 用户访问 |
| TCP:9688 | 拒绝 | 后端仅内网 |
| TCP:9689 | 拒绝 | Actuator |
| TCP:5432 | 拒绝 | PostgreSQL |
| TCP:6379 | 拒绝 | Redis |

### Step 4:服务器初始化(一键脚本)

SSH 登录服务器,执行初始化脚本:

```bash
ssh admin@175.24.206.254

# 下载并执行初始化脚本(自动装 Docker + 创建目录 + 下载部署文件)
curl -fsSL https://raw.githubusercontent.com/Mox-lab/Ink-Speaker/main/server-setup.sh | sudo bash
```

或手动方式:

```bash
# 如果用 scp 上传 server-setup.sh
scp "D:/ProjectCode/Ink Speaker/ink-speaker/server-setup.sh" admin@175.24.206.254:/tmp/
ssh admin@175.24.206.254
sudo bash /tmp/server-setup.sh
```

脚本会自动完成:
1. 安装 Docker + Docker Compose(阿里云源)
2. 配置 Docker 镜像加速(daocloud / dockerproxy / nju / ustc)
3. 创建 `/opt/ink-speaker` 和 `/opt/ink-speaker/data/{postgres,logs,knowledge-base}` 目录
4. 从 GitHub raw 下载 `docker-compose.yml` / `deploy.sh` / `.env.prod.example`
5. 配置 ufw 防火墙(放行 22 / 80)

### Step 5:配置 .env.prod

```bash
cd /opt/ink-speaker
cp .env.prod.example .env.prod

# 生成强随机密钥(记下来)
echo "DB_PASSWORD=$(openssl rand -base64 24)"
echo "JWT_SECRET=$(openssl rand -base64 48)"
echo "JASYPT_KEY=$(openssl rand -base64 32)"

vim .env.prod
chmod 600 .env.prod
```

**`.env.prod` 必填项**(9 项):

```bash
DATA_DIR=/opt/ink-speaker/data
DB_PASSWORD=<openssl rand -base64 24>
OPENAI_API_KEY=sk-<你的真实 API Key>
JWT_SECRET=<openssl rand -base64 48>
JASYPT_KEY=<openssl rand -base64 32>
ACR_REGISTRY=crpi-2t2oc7kie6ke8uce.cn-hangzhou.personal.cr.aliyuncs.com
ACR_NAMESPACE=mox-labb
ACR_USERNAME=虹叶早参子
ACR_PASSWORD=<ACR固定密码>
```

### Step 6:首次部署

```bash
cd /opt/ink-speaker
./deploy.sh
```

`deploy.sh` 会自动:
1. 前置检查 + 加载 .env.prod
2. 创建数据目录(bind mount 准备)
3. 登录 ACR
4. 备份当前镜像(首次跳过)
5. 拉取最新镜像(docker pull)
6. 重启容器(docker compose up -d --force-recreate)
7. 健康检查(后端 180s + nginx 30s)
8. 清理 dangling 镜像

**预计耗时**:30~60 秒(纯拉镜像 + 启动)。

### Step 7:验证部署

```bash
# 1. 四个容器都 Up / healthy
docker compose ps

# 2. 后端健康检查(本机)
curl http://localhost:9688/actuator/health
# 期望:{"status":"UP"}

# 3. 前端首页(本机)
curl -I http://localhost/
# 期望:HTTP/1.1 200 OK

# 4. 浏览器访问
# http://175.24.206.254/
```

---

## 四、日常运维

### 日常更新部署

**开发者侧**(本地):
```bash
git push origin main
# 等 GitHub Actions 构建完成(约 5 分钟)
```

**服务器侧**:
```bash
ssh admin@175.24.206.254
cd /opt/ink-speaker
./deploy.sh              # 30 秒拉镜像 + 重启
```

### 部署变体

```bash
./deploy.sh --backend-only     # 只更新后端
./deploy.sh --frontend-only    # 只更新前端
./deploy.sh --rollback         # 回滚到上一版本
```

### 数据目录结构

```
/opt/ink-speaker/data/
├── postgres/          ← PostgreSQL 数据(容器删除不丢)
├── logs/              ← 应用日志(含 heapdump)
└── knowledge-base/    ← 知识库 markdown 文档
```

**备份**:

```bash
# 备份数据库
docker compose exec postgres pg_dump -U postgres ink_speaker > \
  /opt/ink-speaker/backup/ink_speaker-$(date +%Y%m%d).sql

# 备份数据目录(完整冷备份)
docker compose down
tar -czf /opt/ink-speaker/backup/data-$(date +%Y%m%d).tar.gz data/
docker compose up -d
```

### 容器管理

```bash
cd /opt/ink-speaker

# 查看实时状态
docker compose ps

# 启动所有服务
docker compose --env-file .env.prod up -d

# 停止所有服务(数据保留)
docker compose down

# 重启单个服务
docker compose restart ink-speaker

# 查看实时日志
docker compose logs -f ink-speaker
docker compose logs -f nginx

# 进入容器排查
docker compose exec ink-speaker sh
docker compose exec postgres psql -U postgres -d ink_speaker
docker compose exec redis redis-cli
```

### 资源监控

```bash
# 实时资源占用
docker stats

# 磁盘占用
docker system df

# 清理无用镜像/容器/网络
docker system prune -f
```

---

## 五、故障排查

### Q1:`deploy.sh` 健康检查超时

```bash
# 看后端日志定位原因
docker compose logs --tail=200 ink-speaker
# 常见原因:
#   - .env.prod 必填项还是占位符
#   - DB_PASSWORD 不对
#   - Flyway 迁移失败
#   - 依赖服务(postgres/redis)未启动

# 回滚
./deploy.sh --rollback
```

### Q2:镜像拉取失败

```bash
# 1. 检查 ACR 登录
docker login crpi-2t2oc7kie6ke8uce.cn-hangzhou.personal.cr.aliyuncs.com

# 2. 检查镜像是否存在
docker pull crpi-2t2oc7kie6ke8uce.cn-hangzhou.personal.cr.aliyuncs.com/mox-labb/ink-speaker:latest

# 3. 如果镜像不存在,确认 GitHub Actions 是否构建成功
```

### Q3:磁盘满

```bash
df -h
docker system df

# 清理
docker system prune -f
find /opt/ink-speaker/deploy-logs -name "*.log" -mtime +7 -delete
find /opt/ink-speaker/backup -name "*.sql" -mtime +14 -delete
find /opt/ink-speaker/backup -name "*.tar.gz" -mtime +14 -delete
```

### Q4:数据迁移(换服务器)

```bash
# 旧服务器:停止服务 + 打包数据
docker compose down
tar -czf ink-speaker-data.tar.gz data/

# 传输到新服务器
scp ink-speaker-data.tar.gz admin@新服务器:/opt/ink-speaker/

# 新服务器:解压 + 启动
cd /opt/ink-speaker
tar -xzf ink-speaker-data.tar.gz
./deploy.sh
```

---

## 六、安全加固清单

- [x] `.env.prod` 加入 `.gitignore`,不会提交到 git
- [x] `.env.prod` 权限收紧:`chmod 600 .env.prod`
- [x] PostgreSQL / Redis 仅 `127.0.0.1` 监听
- [x] 后端 9688 / Actuator 9689 仅 `127.0.0.1`
- [x] 腾讯云安全组仅放行 22 / 80
- [x] ufw 防火墙双保险
- [x] Jasypt 加密敏感配置
- [x] JWT 鉴权,密钥 32 字节以上
- [x] Docker 镜像加速走国内 HTTPS 源
- [x] Nginx 安全头(X-Frame-Options / X-Content-Type-Options / Referrer-Policy)
- [x] Nginx gzip 压缩
- [x] Nginx SSE 长连接支持
- [x] 持久化数据用 bind mount,便于备份和迁移
- [ ] SSH 改用密钥登录(建议)
- [ ] 修改 SSH 默认端口(可选)
- [ ] 配置 fail2ban(可选)
- [ ] 加 HTTPS 证书(可选,有域名时)

---

## 七、文件清单

### 后端仓库(`ink-speaker/`,本地 + GitHub)

| 文件 | 用途 |
|------|------|
| `server-setup.sh` | 服务器初始化脚本(装 Docker + 下载部署文件) |
| `Dockerfile` | 后端多阶段构建(CI 用,服务器不用) |
| `docker-compose.yml` | 4 服务编排(postgres + redis + ink-speaker + nginx) |
| `.env.prod.example` | 环境变量模板 |
| `deploy.sh` | 一键部署脚本(拉镜像 + 重启) |
| `docs/DEPLOY.md` | 本文档 |
| `.github/workflows/build-backend.yml` | 后端 CI(构建推 ACR) |

### 前端仓库(`ink-speaker-web/`,本地 + GitHub)

| 文件 | 用途 |
|------|------|
| `Dockerfile` | 前端多阶段构建(CI 用) |
| `nginx.conf` | nginx 配置 |
| `.dockerignore` | 排除 node_modules / dist |
| `.github/workflows/build-frontend.yml` | 前端 CI(构建推 ACR) |

### 服务器(`/opt/ink-speaker/`,无源代码)

| 文件 | 用途 |
|------|------|
| `docker-compose.yml` | 编排文件 |
| `deploy.sh` | 部署脚本 |
| `.env.prod` | 环境变量(不进 git) |
| `deploy-logs/` | 部署日志(自动生成) |
| `data/` | 持久化数据(bind mount) |

---

## 八、首次部署 Checklist

- [ ] 1. 本地代码 push 到 GitHub main 分支
- [ ] 2. GitHub Secrets 配置(前后端各 4 个)
- [ ] 3. GitHub Actions 构建成功,ACR 上有 latest 镜像
- [ ] 4. 腾讯云安全组:放行 22/tcp、80/tcp,拒绝 9688/9689/5432/6379
- [ ] 5. SSH 登录服务器,执行 `curl -fsSL https://raw.githubusercontent.com/Mox-lab/Ink-Speaker/main/server-setup.sh | sudo bash`
- [ ] 6. `cd /opt/ink-speaker && cp .env.prod.example .env.prod && vim .env.prod` 填入真实密钥
- [ ] 7. `chmod 600 .env.prod`
- [ ] 8. `./deploy.sh` 首次部署
- [ ] 9. 验证 `docker compose ps` 四个容器都 Up / healthy
- [ ] 10. 验证 `curl http://localhost:9688/actuator/health` 返回 UP
- [ ] 11. 浏览器访问 `http://175.24.206.254/` 看到前端首页
- [ ] 12. (可选)配置 crontab 每日数据库备份

完成以上步骤后,Ink Speaker 已在生产环境稳定运行。
