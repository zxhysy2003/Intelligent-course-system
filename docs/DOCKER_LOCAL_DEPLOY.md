# 本机 Docker 部署说明

这套配置用于学习部署流程：在本机用 Docker Compose 同时运行前端 Nginx、Spring Boot 后端、FastAPI 推荐服务、MySQL、Redis 和 Neo4j。

## 文件说明

- `docker-compose.local.yml`：本机完整服务编排。
- `backend/Dockerfile`：构建后端 jar，并在运行镜像中安装 `ffprobe`。
- `frontend/Dockerfile`：构建前端静态资源，并用 Nginx 托管。
- `recommend-service/Dockerfile`：基于 `environment.yml` 创建 Conda 环境并启动 Uvicorn。
- `deploy/nginx.conf`：前端静态资源、`/api` 和 `/videos` 反向代理配置。
- `deploy/local.env.example`：本机端口和密码示例。

## 部署结构

```text
Browser
  -> http://localhost:8088
  -> frontend nginx
     -> /api    -> backend:8080
     -> /videos -> backend:8080

backend
  -> mysql:3306
  -> redis:6379
  -> bolt://neo4j:7687
  -> http://recommend-service:8000
```

## 启动

在仓库根目录执行：

```bash
docker compose -f docker-compose.local.yml up --build
```

首次构建需要下载基础镜像、Maven 依赖、npm 依赖和 Conda 依赖，耗时会比较久。

启动完成后访问：

```text
http://localhost:8088
```

默认暴露的本机端口：

| 服务 | 地址 |
| --- | --- |
| 前端入口 | `http://localhost:8088` |
| 后端 | `http://localhost:8080` |
| 推荐服务 | `http://localhost:8000` |
| MySQL | `127.0.0.1:3306` |
| Redis | `127.0.0.1:6379` |
| Neo4j HTTP | `http://localhost:7474` |

这些端口默认都只绑定到 `127.0.0.1`，用于本机学习和调试，不会向局域网其他机器暴露 MySQL、Redis、Neo4j 等服务。

## 视频目录

后端容器内的视频目录固定为：

```text
/app/videos
```

Compose 默认把本机目录挂载到这个容器目录：

```text
/home/shiyang/code_space/course_videos -> /app/videos
```

如果你的视频不在这个目录，复制 `.env` 后修改：

```bash
cp deploy/local.env.example .env
```

```text
VIDEO_HOST_DIR=/你的/本机/课程视频目录
```

数据库中保存的视频相对路径需要和这个目录下的文件结构一致。上传新视频时，文件也会写入 `VIDEO_HOST_DIR` 指向的本机目录。

## 自定义端口或密码

如果本机端口冲突，复制示例环境文件：

```bash
cp deploy/local.env.example .env
```

然后编辑 `.env`，例如把前端端口改成：

```text
FRONTEND_PORT=18088
APP_BASE_URL=http://localhost:18088
CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:18088,http://127.0.0.1:18088
```

再次启动：

```bash
docker compose -f docker-compose.local.yml up --build
```

如果修改了 `NEO4J_PASSWORD`，首次初始化后的已有 Neo4j 数据卷不会自动改旧密码。学习环境下最简单的处理方式是删除数据卷后重建：

```bash
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up -d --build
```

## 推荐模型刷新

后端首次启动会通过 Flyway 初始化 MySQL，并重建 `recommend_user_course_score` 快照。推荐服务如果在快照生成前已经启动，手动刷新一次模型：

```bash
curl -X POST http://localhost:8000/model/reload
curl http://localhost:8000/model/status
```

如果返回 `available=false` 且提示评分表为空，说明还没有足够学习行为数据，后端推荐仍会走新课或热门课程兜底。

## 常用命令

后台启动：

```bash
docker compose -f docker-compose.local.yml up -d --build
```

查看状态：

```bash
docker compose -f docker-compose.local.yml ps
```

验证入口：

```bash
curl http://127.0.0.1:8080/actuator/health
curl http://127.0.0.1:8000/model/status
curl -I http://127.0.0.1:8088
```

查看日志：

```bash
docker compose -f docker-compose.local.yml logs -f backend
docker compose -f docker-compose.local.yml logs -f recommend-service
docker compose -f docker-compose.local.yml logs -f frontend
docker compose -f docker-compose.local.yml logs -f neo4j
```

停止但保留数据卷：

```bash
docker compose -f docker-compose.local.yml down
```

停止并删除 MySQL、Redis、Neo4j 和视频数据卷：

```bash
docker compose -f docker-compose.local.yml down -v
```

当前视频目录使用本机 bind mount，`down -v` 不会删除 `VIDEO_HOST_DIR` 指向的本机视频文件；但会删除 MySQL、Redis 和 Neo4j 的 Docker 数据卷。

## 容器间访问规则

容器内部不能用 `localhost` 访问其他容器，必须使用 Compose 服务名：

```text
backend -> mysql:3306
backend -> redis:6379
backend -> bolt://neo4j:7687
backend -> http://recommend-service:8000
frontend nginx -> http://backend:8080
```

浏览器仍然通过本机端口访问：

```text
http://localhost:8088
```

后端容器默认设置了 `TZ=Asia/Shanghai` 和 `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Shanghai`，用于保持 Java `LocalDateTime.now()` 与 MySQL 容器时区一致。

## Neo4j 注意事项

`neo4j` 服务只通过 `NEO4J_AUTH=neo4j/<密码>` 设置密码。不要额外给 Neo4j 容器传入 `NEO4J_PASSWORD` 这类 `NEO4J_` 前缀环境变量；Neo4j 官方镜像会把 `NEO4J_` 前缀变量解析成数据库配置项，未知配置会导致容器启动失败。

如果遇到：

```text
dependency failed to start: container intelligent-course-system-local-neo4j-1 is unhealthy
```

先查看日志：

```bash
docker compose -f docker-compose.local.yml logs --tail=160 neo4j neo4j-init
```

如果日志中出现 `Unrecognized setting. No declared setting with name: PASSWORD`，说明 Neo4j 容器收到了不该传入的 `NEO4J_PASSWORD` 配置。检查 `.env` 和 `docker-compose.local.yml`，确保 `NEO4J_PASSWORD` 只作为 Compose 插值使用，不出现在 `neo4j` 服务的 `environment` 中。
