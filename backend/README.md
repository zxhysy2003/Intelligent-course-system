# Course System Backend

`backend` 是智能课程学习系统的 Spring Boot 后端服务，负责用户认证、课程管理、学习行为记录、学习分析、知识图谱查询、视频文件访问，以及融合推荐结果生成。

当前项目采用同仓多服务结构：

- 后端服务：`backend`
- 前端服务：[`../frontend`](../frontend)
- FastAPI 推荐服务：[`../recommend-service`](../recommend-service)
- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)

后端默认运行在 `http://127.0.0.1:8080`。前端通过 Vite 代理访问后端，后端通过 `RECOMMEND_SERVICE_URL` 调用推荐服务。

## 功能概览

- 用户注册、登录、JWT 鉴权与当前用户信息获取
- 前台课程分页、分类、详情、选课、视频学习和学习进度更新
- 后台课程新增、编辑、上下线、删除和视频上传
- 后台用户分页、详情、角色、状态和基础信息管理
- 学习行为记录、课程热度同步、学习进度和能力雷达图分析
- Neo4j 知识图谱展示与课程知识点关系查询
- 个性化推荐、用户冷启动推荐、评分快照维护、新课曝光和混合推荐结果融合
- 学生端学习助手 Agent，会话持久化并基于学习画像、进度、能力雷达、最近课程和推荐结果生成只读建议
- 通过 `/videos/**` 暴露本地课程视频静态资源

## 技术栈

- Java 17
- Spring Boot 3.5.14
- Spring MVC
- Spring Security
- Spring Boot Actuator
- MyBatis-Plus 3.5.5
- MySQL 8
- Redis 7
- Neo4j 5
- JWT
- MapStruct 1.5.5
- Maven Wrapper
- ffprobe

## 目录结构

```text
backend
├── src/main/java/com/sy/course_system
│   ├── common              # 通用返回体、上下文、工具类
│   ├── config              # JWT、CORS、Redis、Neo4j、视频、推荐异步配置
│   ├── controller
│   │   ├── client          # 用户端接口
│   │   └── server          # 管理端接口
│   ├── converter           # MapStruct 转换器
│   ├── dto                 # 请求与内部传输对象
│   ├── entity              # MySQL 实体
│   ├── enums               # 业务枚举
│   ├── graph               # Neo4j 节点模型
│   ├── mapper              # MyBatis Mapper
│   ├── recommend/support   # 推荐辅助规则
│   ├── repository          # Neo4j Repository
│   ├── service             # 业务接口与实现
│   └── vo                  # 响应视图对象
├── src/main/resources
│   ├── application.yaml
│   ├── db/migration        # Flyway MySQL 迁移脚本
│   ├── META-INF
│   └── mapper              # MyBatis XML
├── src/test                # 单元测试
├── mvnw
└── pom.xml

../scripts
├── docker-compose.yml      # MySQL、Redis、Neo4j 本地依赖
└── neo4j-backups
    └── neo4j.dump          # Neo4j 知识图谱备份
```

## 环境要求

- JDK 17
- Docker 和 Docker Compose，或自行准备 MySQL、Redis、Neo4j
- ffprobe
- 可访问的推荐服务，默认地址为 `http://127.0.0.1:8000`

如果需要完整联调，还需要启动：

- `../recommend-service`：FastAPI 推荐服务
- `../frontend`：Vue 3 + Vite 前端

## 本地运行

完整系统联调和依赖启动步骤见 [`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)。只启动后端时，从仓库根目录执行：

```bash
set -a
source .env.local
set +a
cd backend
SPRING_PROFILES_ACTIVE=dev \
  RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 \
  ./mvnw spring-boot:run
```

`.env.local` 位于仓库根目录且已被 Git 忽略。`JWT_SECRET_BASE64` 和
`PLAYBACK_TOKEN_SECRET_BASE64` 都是必填的独立签名密钥，解码后必须至少为 32 字节，且不能相同。
分别执行两次 `openssl rand -base64 32` 生成并保存；同一环境的所有后端实例必须使用相同的一组值。
轮换登录密钥会使登录 Token 失效，轮换播放密钥会使尚未到期的播放 URL 失效。

`dev` profile 会开启 MyBatis SQL 调试日志。健康检查：

```bash
curl "http://127.0.0.1:8080/actuator/health"
```

后端启动时默认会全量重建 `recommend_user_course_score`，之后在 `STUDY`、`VIEW`、`FAVORITE`、`UNFAVORITE`、`FINISH` 等行为变化后增量刷新对应用户课程评分。

## 数据库迁移

MySQL 由 Flyway 管理，迁移文件位于 `src/main/resources/db/migration`。空库启动后端会自动执行迁移；已有 `course_db` 首次接入 Flyway 的 baseline 操作见操作手册。

后续数据库变更必须新增迁移文件，不要修改已执行过的历史迁移；命名规则和多人协作建议以操作手册为准。

## 构建与测试

以下命令在 `backend` 目录执行。

编译检查：

```bash
./mvnw -q -DskipTests compile
```

运行测试：

```bash
./mvnw test
```

打包：

```bash
./mvnw clean package
```

运行打包产物：

```bash
set -a
source ../.env.local
set +a
java -jar target/course-system-0.0.1-SNAPSHOT.jar
```

当前测试主要覆盖推荐、冷启动、新课推荐、学习行为、学习分析、学习助手 Agent、用户、课程和用户选课等 service 层逻辑，以及推荐控制器。

## 配置说明

主配置文件为 [`src/main/resources/application.yaml`](src/main/resources/application.yaml)。

关键配置均通过环境变量覆盖，完整清单见 [`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)。开发时最常改的是：

- `DB_*`、`REDIS_*`、`NEO4J_*`：基础依赖连接。
- `JWT_SECRET_BASE64`：必填的 Base64 JWT 签名密钥，可用 `openssl rand -base64 32` 生成。
- `PLAYBACK_TOKEN_SECRET_BASE64`：必填且必须与登录密钥不同，用于签发视频播放 URL。
- `RECOMMEND_SERVICE_URL`：FastAPI 推荐服务地址。
- `VIDEO_DIR`、`FFPROBE_PATH`：视频上传、静态文件定位与时长解析。
- `AGENT_LLM_*`：学习助手模型接入。

学习助手接口统一在 `/api/v1/assistant/**`，由 Spring Security JWT 过滤链保护。Agent 只读分析学习数据，不执行选课、收藏、删除或进度更新。`POST /api/v1/assistant/messages` 需要传入 `clientMessageId`，用于发送失败重试时防重复落库和重复调用模型；`AGENT_INCOMPLETE_RECOVERY_AFTER_MS` 控制半成品发送的恢复窗口，默认 90 秒。

## 接口分组

大部分业务接口需要携带 JWT：

```text
Authorization: Bearer <your_token>
```

### 用户端

- `POST /api/v1/auth/register`、`POST /api/v1/auth/login`：注册和登录
- `GET /api/v1/users/me`：当前用户信息
- `POST /api/v1/courses/search`、`GET /api/v1/courses/categories`：上线课程分页和分类
- `GET /api/v1/courses/{courseId}`：课程详情
- `GET /api/v1/knowledge-points/{knowledgePointId}/courses`：知识点关联课程
- `GET /api/v1/courses/{courseId}/knowledge-points`：课程知识点
- `POST /api/v1/courses/{courseId}/playback`：签发当前课程的限时播放地址（响应禁止缓存）
- `POST /api/v1/courses/{courseId}/enrollment`：选课
- `GET /api/v1/courses/{courseId}/enrollment`：当前用户选课关系
- `PATCH /api/v1/courses/{courseId}/enrollment/progress`：更新学习进度
- `POST /api/v1/learning-behaviors`：记录学习行为
- `GET /api/v1/recommendations`：融合推荐
- `GET /api/v1/onboarding/options`、`GET /api/v1/onboarding/status`：引导选项和状态
- `PUT /api/v1/onboarding/profile`：保存引导画像
- `GET /api/v1/learning-analytics/progress`：学习进度
- `GET /api/v1/learning-analytics/ability-radar`：能力雷达图
- `GET /api/v1/learning-analytics/knowledge-graph`：知识图谱
- `GET|POST /api/v1/assistant/sessions`：学习助手会话列表和创建
- `PATCH|DELETE /api/v1/assistant/sessions/{sessionId}`：重命名或删除会话
- `GET /api/v1/assistant/sessions/{sessionId}/messages`：学习助手消息列表
- `POST /api/v1/assistant/messages`：发送学习助手消息，需携带 `clientMessageId`

### 管理端

`/api/v1/admin/**` 除 JWT 登录外还要求令牌角色为 `ADMIN`，否则返回 HTTP 403。

- `POST /api/v1/admin/users/search`：用户分页
- `GET|PUT /api/v1/admin/users/{userId}`：用户详情和更新
- `PATCH /api/v1/admin/users/{userId}/role`：修改用户角色
- `PATCH /api/v1/admin/users/{userId}/status`：修改用户状态
- `DELETE /api/v1/admin/users`：批量删除用户
- `POST /api/v1/admin/courses/search`：管理课程分页
- `GET /api/v1/admin/courses/form-options`：课程表单选项
- `POST /api/v1/admin/courses`：新增课程
- `GET|PUT /api/v1/admin/courses/{courseId}`：后台课程详情和更新
- `DELETE /api/v1/admin/courses`：批量删除课程
- `PATCH /api/v1/admin/courses/{courseId}/status`：更新课程状态
- `POST /api/v1/admin/courses/{courseId}/video`：上传课程视频

### 静态资源

- `GET|HEAD /videos/**?token=...`：使用路径绑定的播放凭证读取视频，支持 Range 请求；登录 JWT 不可直接访问

## 联调说明

完整联调建议直接使用根目录 `scripts/dev.sh` 或 `scripts/dev.bash`。脚本会先启动后端并等待 Flyway 迁移完成，再启动推荐服务和前端。前端默认将 `/api` 和 `/videos` 代理到 `http://localhost:8080`。

## 常见问题

### 后端启动后无法连接 MySQL

检查 MySQL 容器是否启动、`course_db` 是否存在、账号密码是否与 `application.yaml` 一致，以及本机 `3306` 端口是否被其他服务占用。若是已有数据库首次接入 Flyway，请按操作手册执行 baseline。

### Redis 认证失败

Docker Compose 中 Redis 使用 `redis123` 作为密码。若使用本机 Redis，需要保证 `REDIS_PASSWORD` 与实际配置一致。

### Neo4j 连接失败

优先检查 Neo4j 容器是否启动，`NEO4J_URI` 是否指向 `bolt://127.0.0.1:7687`，以及后端 `NEO4J_USERNAME`、`NEO4J_PASSWORD` 是否仍为 `neo4j`、`neo4j123`。如果需要重新恢复 `scripts/neo4j-backups/neo4j.dump`，请在 `scripts` 目录执行 `docker compose down -v` 后重新启动。

### 推荐接口失败

确认 `../recommend-service` 已启动，并且 `RECOMMEND_SERVICE_URL` 指向它的实际地址。默认推荐接口为 `POST /recommend`。

### 推荐服务返回空 items

确认 `recommend_user_course_score` 表已经生成数据，并检查推荐服务 `GET /model/status`。如果后端刚重建快照，调用推荐服务 `POST /model/reload` 重新训练模型。即使 CF 为空，后端仍会尝试新课和热门课程兜底。

### 视频无法上传或播放

确认 `VIDEO_DIR` 存在且后端进程有读写权限，`FFPROBE_PATH` 指向可执行文件，并检查课程详情页能否成功调用播放凭证接口。凭证过期或网络中断时，前端会自动续签一次。

## 更多文档

- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)
- 本机 Docker 部署：[`../docs/DOCKER_LOCAL_DEPLOY.md`](../docs/DOCKER_LOCAL_DEPLOY.md)
- 学习助手模块说明：[`../docs/agent-module.md`](../docs/agent-module.md)
- 前端说明：[`../frontend/README.md`](../frontend/README.md)
- 推荐服务配置：[`../recommend-service/environment.yml`](../recommend-service/environment.yml)

## License

本后端服务采用 MIT License，详见根目录 [`../LICENSE`](../LICENSE)。
