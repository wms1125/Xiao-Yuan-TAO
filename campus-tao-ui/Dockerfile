# ============================================================================
# 校园淘 · 前端镜像（多阶段构建：Node 构建 → Nginx 静态托管）
#
# 构建：  docker build -t campus-tao-ui:1.0.15 .
# 运行：  docker run -p 80:80 campus-tao-ui:1.0.15
#
# 说明：
#   - 构建阶段用 `npm ci` 严格按 package-lock.json 安装，保证可复现；
#   - 构建产物走 .env.production（接口前缀 /prod-api）；
#   - 运行阶段为 nginx:alpine，反代 /prod-api/ 到后端（上游名 backend:8080，
#     由 docker-compose 的服务名解析；单机 docker run 时请改用 host.docker.internal）。
# ============================================================================

# ---------- 阶段 1：构建 ----------
FROM node:22-alpine AS build
WORKDIR /src

# 先装依赖（利用层缓存）
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build:prod

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 容器内使用的 nginx 配置（HTTP 80，/prod-api 反代后端）
COPY nginx.docker.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD ["sh", "-c", "wget -q -O /dev/null http://127.0.0.1/ || exit 1"]

CMD ["nginx", "-g", "daemon off;"]
