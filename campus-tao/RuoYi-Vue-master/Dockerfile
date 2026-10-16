# ============================================================================
# 校园淘 · 后端镜像（多阶段构建）
#
# 构建：  docker build -t campus-tao-backend:1.0.17 .
# 运行：  docker run -p 8080:8080 \
#           -e DB_URL='jdbc:mysql://host.docker.internal:3306/campus_tao?...' \
#           -e DB_USERNAME=root -e DB_PASSWORD=123456 \
#           -e JWT_SECRET=<至少32位随机串> \
#           -e DRUID_LOGIN_USERNAME=ruoyi -e DRUID_LOGIN_PASSWORD=<随机串> \
#           -e EMBEDDED_REDIS_ENABLED=false -e REDIS_HOST=redis \
#           campus-tao-backend:1.0.17
#
# 说明：
#   - 构建阶段跑 mvn package（跳过测试，测试由 CI 的 backend job 独立执行）；
#   - 运行阶段只带 JRE + jar，镜像不含 Maven/JDK/源码；
#   - 以非 root 用户运行；
#   - 启动时 Flyway 自动迁移数据库（见 db/migration），无需手工执行 SQL。
# ============================================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# 先只拷 pom，利用 Docker 层缓存把依赖下载与源码变更解耦
COPY pom.xml ./
COPY ruoyi-common/pom.xml        ruoyi-common/
COPY ruoyi-system/pom.xml        ruoyi-system/
COPY ruoyi-framework/pom.xml     ruoyi-framework/
COPY ruoyi-quartz/pom.xml        ruoyi-quartz/
COPY ruoyi-generator/pom.xml     ruoyi-generator/
COPY ruoyi-admin/pom.xml         ruoyi-admin/
RUN mvn -B -q -DskipTests dependency:go-offline

COPY ruoyi-common/src        ruoyi-common/src
COPY ruoyi-system/src        ruoyi-system/src
COPY ruoyi-framework/src     ruoyi-framework/src
COPY ruoyi-quartz/src        ruoyi-quartz/src
COPY ruoyi-generator/src     ruoyi-generator/src
COPY ruoyi-admin/src         ruoyi-admin/src
RUN mvn -B -DskipTests package \
 && cp ruoyi-admin/target/ruoyi-admin.jar /src/app.jar

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre-jammy

# 时区与语言：避免容器内日志时间/中文乱码
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
 && apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r campus && useradd -r -g campus -d /app -s /usr/sbin/nologin campus

WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar

# 上传目录（ruoyi.profile=./uploadPath）与日志目录，挂卷持久化
RUN mkdir -p /app/uploadPath /app/logs && chown -R campus:campus /app
VOLUME ["/app/uploadPath", "/app/logs"]

USER campus
EXPOSE 8080

# 探活：Spring Boot Actuator（security 已放行 /actuator/health）
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD ["sh", "-c", "curl -fsS http://127.0.0.1:8080/actuator/health || exit 1"]

# exec 形式 + sh -c：让 $JAVA_OPTS 生效且 PID 1 能被正确转发信号
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
