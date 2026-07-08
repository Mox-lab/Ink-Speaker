# Ink Speaker 部署文档(腾讯云 4GB 服务器 / Debian 13.2)

> 本文档覆盖从空服务器到 Ink Speaker 上线的全部流程,所有步骤均可复制粘贴执行。
>
> **部署模式**:GitHub Actions 构建镜像 → 推阿里云 ACR → 服务器 docker pull 拉取部署。
> 服务器**不构建镜像**,只拉取已构建好的镜像启动,资源占用低、部署快(< 30 秒)。

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
- Postgres 数据卷:预估 5~10 GB(根据向量数据增长)
- 应用日志卷:预估 1~2 GB(log-driver 限制 50m × 3 文件)
- 知识库卷:预估 1~5 GB(根据 markdown 文档数量)
- **总计**:首日 ~3.5 GB,长期增长 < 20 GB,**40 GB SSD 足够**

**带宽评估**:
- 3 Mbps ≈ 384 KB/s,LLM API 调用走外网出站(请求体小、响应流式),用户侧 SSE 流式输出峰值 < 100 KB/s
- 单用户 SSE 长连接占用极少带宽,**50 并发用户无压力**

---

## 二、部署架构

```
┌─────────────────────────────────────────────────────┐
│                腾讯云 175.24.206.254                 │
│                Debian 13.2 64bit                    │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │  Docker Engine + Compose v2                 │   │
│  │                                             │   │
│  │  ┌──────────────┐  ┌──────────────┐         │   │
│  │  │  postgres    │  │   redis      │         │   │
│  │  │  pgvector    │  │   7-alpine   │         │   │
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
│  │                  │  (内网反代)              │   │
│  │           ┌──────┴───────┐                  │   │
│  │           │    nginx     │  :80  ←──── 用户│   │
│  │           │   64MB       │  (对外)         │   │
│  │           │  静态+反代   │                  │   │
│  │           └──────────────┘                  │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  防火墙(ufw):仅放行 22/tcp, 80/tcp              │
└─────────────────────────────────────────────────────┘
              │                  │
              │ SSH              │ HTTP 80
              ▼                  ▼
        运维访问            用户访问入口(http://175.24.206.254/)
```

**端口说明**:

| 端口 | 用途 | 是否对外 |
|------|------|----------|
| 22 | SSH | 对外(安全组放行) |
| 80 | Nginx 用户访问入口 | 对外 |
| 9688 | 后端业务端口 | **仅本机**(由 nginx 反代) |
| 9689 | Actuator 监控端口 | **仅本机**(运维 SSH 隧道访问) |
| 5432 | PostgreSQL | **仅本机** |
| 6379 | Redis | **仅本机** |

**目录布局**(服务器):

```
/opt/
├── ink-speaker/              ← 后端仓库(deploy.sh 所在目录,只用来放部署脚本)
│   ├── docker-compose.yml    ← 编排文件(从 ACR 拉镜像)
│   ├── .env.prod             ← 环境变量(含 ACR 账号密码)
│   ├── deploy.sh             ← 一键部署脚本(拉镜像 + 重启)
│   └── server-init.sh        ← 服务器初始化(只跑一次)
└── (前端仓库不在服务器上,在 GitHub Actions 上构建)
```

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
阿里云 ACR(registry.cn-shenzhen.aliyuncs.com/mox-lab/)
  ├─ ink-speaker:latest
  └─ ink-speaker-web:latest
        │
        │  服务器 SSH 执行 ./deploy.sh
        ▼
腾讯云服务器 docker pull 拉镜像 → docker compose up -d 重启容器
```

**访问入口**:用户浏览器访问 `http://175.24.206.254/`,nginx 托管前端 + `/api/*` 反代到后端。

---

## 三、完整部署流程(7 步)

### Step 1:首次登录服务器

用 MobaXterm / Termius / 任意 SSH 客户端连接:

```bash
ssh root@175.24.206.254
# 输入腾讯云控制台设置的密码
```

确认系统版本:

```bash
cat /etc/os-release
# PRETTY_NAME 应为 "Debian GNU/Linux 13.2 (trixie)"
```

### Step 2:上传项目脚本

将本地仓库的 `server-init.sh` 上传到服务器(用 MobaXterm 的 SFTP 拖拽,或 scp):

```bash
# 在本地机器执行
scp "D:/ProjectCode/Ink Speaker/ink-speaker/server-init.sh" root@175.24.206.254:/root/
```

### Step 3:执行服务器初始化

```bash
# 在服务器执行
cd /root
chmod +x server-init.sh
sudo bash server-init.sh
```

脚本会自动完成 8 件事:

1. 更新 apt 包索引 + 安装基础工具(git / vim / htop / postgresql-client / redis-tools)
2. 安装 Docker CE + Docker Compose v2 + buildx 插件(阿里云镜像源)
3. 配置 Docker 镜像加速(daocloud / dockerproxy / nju / ustc 四源)
4. 创建 4 GB swap 文件(避免 OOM kill)
5. 系统参数调优(`fs.file-max=655350` / `somaxconn=4096` / `overcommit_memory=1`)
6. 创建 `/opt/ink-speaker` 应用目录
7. 配置 ufw 防火墙(放行 22 / 80)
8. 验证 Docker 服务状态

**预计耗时**:3~5 分钟(取决于网络)。

**验证**:

```bash
docker --version
docker compose version
free -h           # 应看到 Swap 总量 4.0Gi
systemctl is-active docker    # 应输出 active
```

### Step 4:腾讯云安全组配置(在控制台操作)

**这一步必须在腾讯云控制台做,脚本无法替代。**

登录腾讯云控制台 → 云服务器 CVM → 安全组 → 配置规则:

**入站规则**:

| 类型 | 协议端口 | 来源 | 策略 | 备注 |
|------|----------|------|------|------|
| 自定义 | TCP:22 | 0.0.0.0/0 | 允许 | SSH(建议改为你的固定 IP) |
| 自定义 | TCP:80 | 0.0.0.0/0 | 允许 | Nginx 用户访问入口 |
| 自定义 | TCP:9688 | 0.0.0.0/0 | **拒绝** | 后端业务,仅由 nginx 内网反代 |
| 自定义 | TCP:9689 | 0.0.0.0/0 | **拒绝** | Actuator 监控,禁止外网访问 |
| 自定义 | TCP:5432 | 0.0.0.0/0 | **拒绝** | PostgreSQL,禁止外网访问 |
| 自定义 | TCP:6379 | 0.0.0.0/0 | **拒绝** | Redis,禁止外网访问 |

**出站规则**:保持默认(全部允许)。

### Step 5:克隆后端代码 + 配置 .env.prod

**只需要手动 clone 后端仓库(只用来获取 deploy.sh / docker-compose.yml / .env.prod.example 这几个部署文件)。镜像构建在 GitHub Actions 上完成,服务器不构建。**

```bash
cd /opt/ink-speaker

# 替换为你的实际仓库地址
git clone https://github.com/Mox-lab/Ink-Speaker.git .

# 拷贝环境变量模板
cp .env.prod.example .env.prod

# 用 vim 或 nano 编辑(必填项不能留默认值)
vim .env.prod
```

**`.env.prod` 必填项**(其余保持默认即可):

```bash
# 1. 数据库密码(至少 16 字节,建议 openssl rand -base64 24)
DB_PASSWORD=替换为强密码_xxxxxxxxxxxxxxxx

# 2. LLM API Key
OPENAI_API_KEY=sk-替换为你的真实 API Key

# 3. JWT 密钥(至少 32 字节,执行:openssl rand -base64 48)
JWT_SECRET=替换为强随机字符串_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 4. Jasypt 加密主密钥(至少 32 字节,执行:openssl rand -base64 32)
JASYPT_KEY=替换为强随机字符串_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 5. 阿里云 ACR(镜像仓库)
ACR_REGISTRY=registry.cn-shenzhen.aliyuncs.com
ACR_NAMESPACE=mox-lab
ACR_USERNAME=你的阿里云账号
ACR_PASSWORD=ACR访问凭证固定密码
```

**生成强随机密钥的命令**(在服务器执行):

```bash
openssl rand -base64 48    # 用于 JWT_SECRET
openssl rand -base64 32    # 用于 JASYPT_KEY
openssl rand -base64 24    # 用于 DB_PASSWORD
```

**关键安全提醒**:
- `.env.prod` 已在 `.gitignore` 中,不会提交到 git
- 权限建议收紧:`chmod 600 .env.prod`
- 不要把 `.env.prod` 通过任何聊天工具/截图外发

### Step 5.5:配置 GitHub Secrets(在 GitHub 网页操作)

**这一步让 GitHub Actions 能推镜像到你的阿里云 ACR。**

到 GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret,添加 4 个:

| Secret 名 | 值 | 说明 |
|-----------|-----|------|
| `ACR_REGISTRY` | `registry.cn-shenzhen.aliyuncs.com` | 阿里云 ACR 地址(华南建议深圳) |
| `ACR_NAMESPACE` | `mox-lab` | ACR 命名空间(在 ACR 控制台创建) |
| `ACR_USERNAME` | 你的阿里云账号 | ACR 访问凭证账号 |
| `ACR_PASSWORD` | ACR 固定密码 | ACR 控制台 → 访问凭证 → 设置固定密码 |

**前后端两个仓库都要配**(因为各自有独立的 workflow)。

配置完后,只要 push 到 main 分支,GitHub Actions 会自动构建并推镜像到 ACR。可以在仓库的 Actions 标签页看构建进度。

### Step 6:首次部署

```bash
cd /opt/ink-speaker
chmod +x deploy.sh
./deploy.sh
```

`deploy.sh`(镜像拉取模式)会自动完成:

1. **前置检查**:确认 docker / docker compose 已安装,`.env.prod` 必填项齐全
2. **登录 ACR**:用 `.env.prod` 里的 ACR 账号密码登录
3. **备份当前镜像**:`docker tag ... :prev`(首次部署会跳过)
4. **拉取镜像**:`docker pull` 后端 + 前端 latest 镜像(从阿里云 ACR,国内速度极快)
5. **重启服务**:`docker compose up -d --no-deps --force-recreate` 重启 ink-speaker + nginx
6. **健康检查**:后端最长 180s 轮询 `/actuator/health`,nginx 最长 30s 轮询 80 端口
7. **清理 dangling 镜像**:`docker image prune -f`

**部署日志**:`/opt/ink-speaker/deploy-logs/deploy-YYYYMMDD-HHMMSS.log`

**验证部署成功**:

```bash
# 1. 看容器状态(四个容器都应是 Up / healthy)
docker compose ps

# 2. 看后端日志(确认无异常)
docker compose logs --tail=100 ink-speaker

# 3. 看前端 nginx 日志
docker compose logs --tail=50 nginx

# 4. 后端健康检查(本机)
curl http://localhost:9688/actuator/health
# 期望:{"status":"UP"}

# 5. 前端首页(本机)
curl -I http://localhost/
# 期望:HTTP/1.1 200 OK

# 6. 外网访问(在本地浏览器)
# 直接打开:http://175.24.206.254/
```

### Step 7:后续更新部署

代码推送到 git 后,服务器一键拉取前后端并重新部署:

```bash
cd /opt/ink-speaker
./deploy.sh              # 拉取最新镜像并重启(前后端都更新)
```

**只更新前端 / 后端**:

```bash
./deploy.sh --frontend-only    # 只拉前端镜像并重启 nginx
./deploy.sh --backend-only     # 只拉后端镜像并重启 ink-speaker
```

**回滚到上一版本**(健康检查失败时):

```bash
./deploy.sh --rollback
```

回滚逻辑:本地 `:prev` tag 重新打回 `:latest`,重启容器,等待 30 秒后再次健康检查。

> **注意**:回滚只能回到**上一次成功部署的版本**。如果连续跑了两次 `./deploy.sh`,第二次的 prev 是第一次的镜像,不是更早的版本。如需回滚到指定版本,可用 ACR 上的 SHA tag:
>
> ```bash
> # 拉指定版本
> docker pull registry.cn-shenzhen.aliyuncs.com/mox-lab/ink-speaker:abc1234
> docker tag registry.cn-shenzhen.aliyuncs.com/mox-lab/ink-speaker:abc1234 \
>              registry.cn-shenzhen.aliyuncs.com/mox-lab/ink-speaker:latest
> docker compose --env-file .env.prod up -d --no-deps --force-recreate ink-speaker
> ```

---

## 四、运维常用命令

### 容器管理

```bash
cd /opt/ink-speaker

# 查看实时状态(应有 4 个容器:postgres / redis / ink-speaker / nginx)
docker compose ps

# 启动所有服务
docker compose --env-file .env.prod up -d

# 停止所有服务(不影响数据卷)
docker compose down

# 重启单个服务
docker compose restart ink-speaker
docker compose restart nginx

# 查看实时日志
docker compose logs -f ink-speaker
docker compose logs -f nginx
docker compose logs -f --tail=200 ink-speaker

# 进入容器排查
docker compose exec ink-speaker sh
docker compose exec nginx sh
docker compose exec postgres psql -U postgres -d ink_speaker
docker compose exec redis redis-cli
```

### 数据库备份

```bash
mkdir -p /opt/ink-speaker/backup

# 手动备份
docker compose exec postgres pg_dump -U postgres ink_speaker > \
  /opt/ink-speaker/backup/ink_speaker-$(date +%Y%m%d).sql

# 恢复
docker compose exec -T postgres psql -U postgres ink_speaker < \
  /opt/ink-speaker/backup/ink_speaker-20260708.sql
```

建议加入 crontab 每日自动备份:

```bash
crontab -e
# 加入下面这行(每天凌晨 3 点备份)
0 3 * * * cd /opt/ink-speaker && docker compose exec -T postgres pg_dump -U postgres ink_speaker > /opt/ink-speaker/backup/ink_speaker-$(date +\%Y\%m\%d).sql && find /opt/ink-speaker/backup -name "ink_speaker-*.sql" -mtime +14 -delete
```

### 资源监控

```bash
# 实时资源占用
docker stats

# 磁盘占用
docker system df

# 清理无用镜像/容器/网络(谨慎执行,会清掉所有 dangling 资源)
docker system prune -f
```

### JVM 诊断

```bash
# 查看 JVM 进程
docker compose exec ink-speaker jps -v

# 堆内存使用
docker compose exec ink-speaker jcmd 1 GC.heap_info

# 线程栈(卡死排查)
docker compose exec ink-speaker jcmd 1 Thread.print

# 堆 dump(排查内存泄漏)
docker compose exec ink-speaker jcmd 1 GC.heap_dump /app/logs/heapdump-$(date +%s).hprof
# 然后从容器拷出来
docker cp ink-speaker:/app/logs/heapdump-xxx.hprof ./
```

---

## 五、故障排查

### Q1:`deploy.sh` 健康检查超时

```bash
# 1. 看应用日志
docker compose logs --tail=200 ink-speaker

# 2. 常见原因
#    - .env.prod 必填项没改(API Key / JWT_SECRET 等还是占位符)
#    - 数据库连接失败(检查 DB_PASSWORD 是否和 compose 里一致)
#    - Flyway 迁移失败(看日志里的 SQL 错误)

# 3. 回滚
./deploy.sh --rollback
```

### Q2:Docker 拉镜像超时

```bash
# 镜像加速器没生效,检查
cat /etc/docker/daemon.json
# 应包含 registry-mirrors 配置

# 重启 Docker
systemctl restart docker

# 手动拉一下试试
docker pull pgvector/pgvector:pg16
```

### Q3:磁盘满

```bash
# 查看磁盘占用
df -h

# Docker 系统占用
docker system df

# 清理 dangling 镜像 + 停止的容器
docker system prune -f

# 清理应用日志(只保留最近 7 天)
find /opt/ink-speaker/deploy-logs -name "*.log" -mtime +7 -delete

# 清理 Postgres 备份(只保留最近 14 天)
find /opt/ink-speaker/backup -name "ink_speaker-*.sql" -mtime +14 -delete
```

### Q4:内存告警 / OOM kill

```bash
# 查看是否被 OOM kill 过
dmesg | grep -i "out of memory"
docker inspect ink-speaker | grep -A3 OOMKilled

# 当前内存占用
free -h
docker stats --no-stream

# 如果频繁 OOM
# 1. 检查 -XX:MaxRAMPercentage=70 是否生效(应约 1.4GB 堆)
# 2. 临时降低:改 docker-compose.yml 里的 JAVA_OPTS MaxRAMPercentage=60
# 3. 升级服务器内存到 8GB
```

### Q5:应用启动慢 / 健康检查首次失败

`start_period: 90s` 是健康检查宽限期,首次启动 Spring Boot + Flyway 迁移可能需要 60~90 秒。如果仍然超时:

```bash
# 1. 看启动日志,定位卡在哪一步
docker compose logs ink-speaker | grep -E "(Started|Flyway|Tomcat)"

# 2. 临时延长宽限期
# 修改 docker-compose.yml 的 healthcheck.start_period: 180s
```

---

## 六、安全加固清单

- [x] `.env.prod` 加入 `.gitignore`,不会提交到 git
- [x] `.env.prod` 权限收紧:`chmod 600 .env.prod`
- [x] PostgreSQL / Redis 仅 `127.0.0.1` 监听,不对外暴露
- [x] 后端 9688 / Actuator 9689 仅 `127.0.0.1`,不对外(由 nginx 反代)
- [x] 腾讯云安全组仅放行 22 / 80,拒绝 9688/9689/5432/6379 入站
- [x] ufw 防火墙双保险(放行 22 / 80)
- [x] Jasypt 加密敏感配置
- [x] JWT 鉴权,密钥 32 字节以上
- [x] Docker 镜像加速走国内 HTTPS 源(阿里云)
- [x] Nginx 安全头(X-Frame-Options / X-Content-Type-Options / Referrer-Policy)
- [x] Nginx gzip 压缩(节省 3Mbps 带宽)
- [x] Nginx SSE 长连接支持(`proxy_buffering off` + 600s 超时)
- [ ] SSH 改用密钥登录(建议,禁用密码)
- [ ] 修改 SSH 默认端口 22 → 其他端口(可选)
- [ ] 配置 fail2ban 防暴力破解(可选)
- [ ] 加 HTTPS 证书(Let's Encrypt,如果有域名)

---

## 七、文件清单

### 后端仓库(`ink-speaker/`)

| 文件 | 用途 |
|------|------|
| `server-init.sh` | 服务器首次初始化脚本(Debian 13.2,跑一次) |
| `Dockerfile` | 后端多阶段构建(maven build + JRE runtime) |
| `docker-compose.yml` | postgres + redis + ink-speaker + nginx 编排 |
| `.env.prod.example` | 生产环境变量模板 |
| `.env.prod` | 实际环境变量(**不提交 git**,由 .env.prod.example 拷贝) |
| `deploy.sh` | 一键部署脚本(支持 `--rollback` / `--no-pull` / `--backend-only` / `--frontend-only` / 分支参数) |
| `deploy-logs/` | 部署日志目录(自动生成) |
| `docs/DEPLOY.md` | 本文档 |

### 前端仓库(`ink-speaker-web/`)

| 文件 | 用途 |
|------|------|
| `Dockerfile` | 前端多阶段构建(node + pnpm build → nginx runtime) |
| `nginx.conf` | nginx 配置(静态托管 + 反代 /api 到后端 + SSE 支持) |
| `.dockerignore` | 排除 node_modules / dist / .git,加速构建 |

---

## 八、首次部署 Checklist

按顺序勾选,确保每步都完成:

- [ ] 1. SSH 登录服务器,确认 Debian 13.2
- [ ] 2. 上传 `server-init.sh`,执行 `sudo bash server-init.sh`
- [ ] 3. 验证 Docker 已安装:`docker --version && docker compose version`
- [ ] 4. 腾讯云安全组:放行 22/tcp、80/tcp,拒绝 9688/9689/5432/6379
- [ ] 5. `cd /opt/ink-speaker && git clone https://github.com/Mox-lab/Ink-Speaker.git .`
- [ ] 6. `cp .env.prod.example .env.prod && vim .env.prod` 填入真实密钥
- [ ] 7. `chmod 600 .env.prod` 收紧权限
- [ ] 8. `./deploy.sh --no-pull` 首次部署(自动 clone 前端仓库)
- [ ] 9. 验证 `docker compose ps` 四个容器都 Up / healthy(postgres / redis / ink-speaker / nginx)
- [ ] 10. 验证 `curl http://localhost:9688/actuator/health` 返回 UP
- [ ] 11. 浏览器访问 `http://175.24.206.254/` 看到前端首页
- [ ] 12. (可选)配置 crontab 每日数据库备份
- [ ] 13. (可选)SSH 改密钥登录,禁用密码

完成以上步骤后,Ink Speaker 已在生产环境稳定运行。
