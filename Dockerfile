# ============================================================
# Ink Speaker Dockerfile(多阶段构建)
# ============================================================
# 镜像分层:
#   1. build 阶段:maven 编译,产出 fat jar
#   2. runtime 阶段:eclipse-temurin JRE,只拷贝 jar
#
# 体积优化:
#   - build 阶段用 maven 缓存依赖,加速重复构建
#   - runtime 阶段用 JRE 而非 JDK,省 ~200MB
#   - 最终镜像 ~350MB
# ============================================================

# ---------- 阶段 1:构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# 配置多 Maven 镜像(阿里云 + 中央仓库,阿里云偶发 502 时自动 fallback)
RUN mkdir -p /root/.m2 && cat > /root/.m2/settings.xml <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
    <mirror>
      <id>central</id>
      <mirrorOf>*,!aliyun</mirrorOf>
      <name>Maven Central</name>
      <url>https://repo1.maven.org/maven2</url>
    </mirror>
  </mirrors>
  <profiles>
    <profile>
      <id>multiple-repos</id>
      <repositories>
        <repository>
          <id>aliyun</id>
          <url>https://maven.aliyun.com/repository/public</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
        <repository>
          <id>central</id>
          <url>https://repo1.maven.org/maven2</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
        <repository>
          <id>jitpack</id>
          <url>https://jitpack.io</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>multiple-repos</activeProfile>
  </activeProfiles>
</settings>
EOF

# 先拷贝 pom.xml,利用 Docker 层缓存加速依赖下载(go-offline 可能因部分插件缺失失败,允许返回非零)
COPY pom.xml .
RUN mvn -B dependency:go-offline -q || true

# 拷贝源码并打包(跳过测试,测试在 CI 阶段跑)
COPY src ./src
RUN mvn -B clean package -DskipTests -Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8

# ---------- 阶段 2:运行 ----------
FROM eclipse-temurin:21-jre-jammy

# 时区与编码
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS=""

WORKDIR /app

# 拷贝 fat jar
COPY --from=build /workspace/target/ink-speaker-1.0.0-SNAPSHOT.jar app.jar

# 健康检查:Actuator health 端点
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:9688/actuator/health || exit 1

EXPOSE 9688 9689

# JVM 参数由 docker-compose 通过 JAVA_OPTS 注入,适配 4GB 服务器
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
