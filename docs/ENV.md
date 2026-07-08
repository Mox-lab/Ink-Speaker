# 环境变量说明

> 项目所有环境变量的清单、用途、生成方式、默认值与生产建议。

---

## 1. 变量总览

| 变量名 | 必填 | 默认值 | 用途 | 生产建议 |
|--------|------|--------|------|----------|
| `OPENAI_API_KEY` | ✅ | 内置 dev key | OpenAI 兼容 LLM 接口鉴权 | 替换为付费 key |
| `DB_PASSWORD` | ✅ | (空) | PostgreSQL 密码 | 强密码 + 仅内网访问 |
| `REDIS_HOST` | ❌ | `localhost` | Redis 主机 | 内网地址 |
| `REDIS_PORT` | ❌ | `6379` | Redis 端口 | 保持默认 |
| `REDIS_PASSWORD` | ❌ | (空) | Redis 密码 | 强烈建议设置 |
| `JWT_SECRET` | ✅ | 内置 dev key | JWT HMAC-SHA256 签名密钥 | 32+ 字节强随机 |
| `JASYPT_KEY` | ✅ | `ink-speaker-dev-key` | 解密 `ENC(...)` 配置项 | K8s Secret 注入 |
| `SPRING_PROFILES_ACTIVE` | ❌ | `dev` | Spring Profile | 生产用 `prod` |
| `SERVER_PORT` | ❌ | `9688` | 业务端口 | 反代后保持内网 |
| `MANAGEMENT_SERVER_PORT` | ❌ | `9689` | Actuator 端口 | 仅运维网段可见 |

---

## 2. 各变量详解

### 2.1 `OPENAI_API_KEY`

- **用途**:调用 OpenAI 兼容接口的 API Key
- **支持的服务**(改 `application.yml` 的 `langchain4j.open-ai.chat-model.base-url`):
  - DeepSeek:`https://api.deepseek.com/v1`
  - 通义千问:`https://dashscope.aliyuncs.com/compatible-mode/v1`
  - Moonshot:`https://api.moonshot.cn/v1`
  - 智谱 GLM:`https://open.bigmodel.cn/api/paas/v4`
  - 当前默认:`https://aihub.bielcrystal.com/v1`(glm-5.2)
- **获取**:对应平台的控制台 → API Keys 页面
- **生产**:K8s Secret 注入,不进 Git

### 2.2 `DB_PASSWORD`

- **用途**:PostgreSQL `ink_speaker` 数据库密码
- **配置点**:`spring.datasource.password`
- **生成**:`openssl rand -base64 24`
- **生产**:数据库专用户 + 最小权限(仅 `ink_speaker` 库的 DML/DDL)

### 2.3 `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`

- **用途**:Redis 连接(L2 缓存 + ShedLock 锁后端)
- **配置点**:`spring.data.redis.host/port/password`
- **生产**:
  - 独立 Redis 实例(不与应用同 Pod)
  - 开启 AOF 持久化(ShedLock 锁数据不能丢)
  - 设置 maxmemory-policy 为 `allkeys-lru`

### 2.4 `JWT_SECRET`

- **用途**:JWT HMAC-SHA256 签名密钥
- **算法要求**:HS256 要求字节长度 ≥ 32(256 bits)
- **生成**:`openssl rand -base64 48`(48 字节随机数据,Base64 编码后约 64 字符)
- **轮换**:建议每 6 个月轮换;轮换会导致所有现有 Token 失效,用户需重新登录
- **生产**:
  - 不写入 Git
  - 不写入日志
  - 多实例必须共享同一密钥(否则 A 实例签的 Token 在 B 实例验签失败)

### 2.5 `JASYPT_KEY`

- **用途**:解密 `application.yml` 中 `ENC(...)` 包裹的字段
- **算法**:`PBEWITHHMACSHA512ANDAES_256`(AES-256 + HMAC-SHA512)
- **生成**:`openssl rand -base64 32`
- **使用流程**:
  1. 设置 `JASYPT_KEY=你的主密钥`
  2. 用 Jasypt CLI 加密敏感字段:
     ```bash
     java -cp jasypt-1.9.3.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI \
       input="真实的数据库密码" \
       password=$JASYPT_KEY \
       algorithm=PBEWITHHMACSHA512ANDAES_256 \
       ivGeneratorClassName=org.jasypt.iv.RandomIvGenerator
     ```
  3. 把输出的密文写入 `application.yml`:`password: ENC(密文)`
- **生产**:从 K8s Secret / HashiCorp Vault 注入,不入 Git

### 2.6 `SPRING_PROFILES_ACTIVE`

- **可选值**:`dev` | `prod`
- **影响**:
  - `dev`:加载 `application-dev.yml`(Swagger 开放、SQL 日志、弱密钥)
  - `prod`:加载 `application-prod.yml`(Swagger 关闭、强制环境变量、生产级别日志)
- **默认**:`application.yml` 中 `spring.profiles.active: dev`

---

## 3. 注入方式

### 3.1 本地开发(IDEA)

Run Configuration → Environment Variables:
```
OPENAI_API_KEY=sk-xxx;DB_PASSWORD=xxx;JWT_SECRET=xxx;JASYPT_KEY=xxx
```

或用 EnvFile 插件加载 `.env` 文件。

### 3.2 本地开发(命令行)

Linux/Mac:
```bash
export $(cat .env | xargs)
mvn spring-boot:run
```

Windows PowerShell:
```powershell
Get-Content .env | ForEach-Object {
  $kv = $_ -split '=', 2
  Set-Item -Path "Env:$($kv[0])" -Value $kv[1]
}
mvn spring-boot:run
```

### 3.3 Docker

```bash
docker run --env-file .env ink-speaker:latest
```

### 3.4 Kubernetes

```yaml
# Secret
apiVersion: v1
kind: Secret
metadata:
  name: ink-speaker-secrets
type: Opaque
stringData:
  OPENAI_API_KEY: sk-xxx
  DB_PASSWORD: xxx
  JWT_SECRET: xxx
  JASYPT_KEY: xxx
---
# Deployment 引用
spec:
  containers:
    - name: app
      envFrom:
        - secretRef:
            name: ink-speaker-secrets
```

---

## 4. 安全检查清单

- [ ] `.env` 已在 `.gitignore` 中
- [ ] 日志配置不输出 `OPENAI_API_KEY` / `DB_PASSWORD` / `JWT_SECRET` / `JASYPT_KEY`
- [ ] `application.yml` 中无明文密钥
- [ ] 数据库用户仅能访问 `ink_speaker` 库
- [ ] Redis 已设置密码
- [ ] K8s Secret 已加密(etcd 加密)
- [ ] CI/CD 流水线日志中无密钥泄露
- [ ] JWT Secret 长度 ≥ 32 字节
- [ ] 生产环境 `SPRING_PROFILES_ACTIVE=prod`
- [ ] `management.server.port` 仅对内网开放

---

## 5. 故障排查

### 5.1 启动报 "Encryption raised an exception"

**原因**:`JASYPT_KEY` 与加密时的密钥不一致。

**解决**:确认环境变量与加密时使用的密钥完全相同(注意首尾空格)。

### 5.2 启动报 "Failed to bind properties under 'spring.datasource.password'"

**原因**:`DB_PASSWORD` 未设置或包含特殊字符。

**解决**:特殊字符需转义或用引号包裹;确认环境变量已注入。

### 5.3 JWT 验签失败

**原因**:
1. `JWT_SECRET` 在多实例间不一致
2. Secret 长度 < 32 字节
3. Token 已过期

**解决**:`openssl rand -base64 48` 重新生成,所有实例同步更新。

### 5.4 Redis 连接失败

**原因**:`REDIS_HOST` / `REDIS_PASSWORD` 错误。

**解决**:
```bash
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD ping
# 期望返回 PONG
```
