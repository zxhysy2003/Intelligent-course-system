# Intelligent Course System 操作手册

本项目是一个智能课程学习系统，当前仓库采用前后端与推荐服务同仓组织方式，包含 `backend`、`frontend` 和 `recommend-service` 三个服务。

完整系统的本地联调通常需要同时运行：

- `backend`：Spring Boot 后端服务，负责用户、课程、学习行为、学习分析、知识图谱和推荐结果融合，默认端口 `8080`
- `frontend`：Vue 3 + Vite 前端服务，负责用户端与管理端页面，默认开发端口 `5173`
- `recommend-service`：FastAPI 推荐服务，负责基于学习行为评分生成协同过滤候选课程，默认端口 `8000`

如果想用 Docker 学习完整部署流程，可以直接使用根目录 `docker-compose.local.yml`，它会同时启动前端 Nginx、后端、推荐服务、MySQL、Redis 和 Neo4j，默认入口为 `http://localhost:8088`。详细步骤见 [`docs/DOCKER_LOCAL_DEPLOY.md`](./DOCKER_LOCAL_DEPLOY.md)。

页面 URL 与 JSON API 是一次性破坏性升级，不提供旧路径兼容。部署环境必须同步发布前端、后端和 Nginx 配置；Nginx 转发 `/api/v1` 时必须保留完整 URI，静态视频继续使用 `/videos/**`。

演示主线建议使用普通学生账号：登录后先完成 `/onboarding` 三步引导，再进入 `/recommendations` 查看带来源核验的推荐卡片，也可以进入 `/assistant` 让学习助手基于学习画像、进度、能力雷达、最近课程和推荐结果生成只读学习建议。

相关开发参考见 [文档索引](README.md)。单独开发或联调某个服务时，可查阅 [后端](BACKEND_GUIDE.md)、[前端](FRONTEND_GUIDE.md) 和 [推荐服务](RECOMMEND_SERVICE_GUIDE.md) 手册。

## 1. 仓库结构

```text
Intelligent-course-system
├── backend
│   ├── src/main/java/com/sy/course_system
│   ├── src/main/resources
│   │   └── db/migration
│   ├── mvnw
│   └── pom.xml
├── frontend
│   ├── src
│   ├── public
│   ├── package.json
│   └── vite.config.js
├── recommend-service
│   ├── environment.yml
│   ├── main.py
│   ├── model.py
│   └── schemas.py
├── scripts
│   ├── docker-compose.yml
│   ├── dev.sh
│   ├── dev.bash
│   └── neo4j-backups
│       └── neo4j.dump
└── docs
    ├── OPERATION_MANUAL.md
    └── agent-module.md
```

MySQL 初始化以 `backend/src/main/resources/db/migration` 下的 Flyway 迁移为准。

## 2. 服务关系

```text
Browser
  |
  | http://localhost:5173
  v
frontend
  |
  | Vite proxy: /api, /videos
  v
backend
  |
  | RECOMMEND_SERVICE_URL + /recommend
  v
recommend-service

backend 同时依赖 MySQL、Redis、Neo4j、本地视频目录和可选的 OpenAI 兼容模型服务；recommend-service 会直连 MySQL 读取推荐评分快照。学习助手没有配置模型密钥或配置为 `mock` 时，会返回本地 mock 回答，便于本地演示。
```

推荐启动顺序：

1. 启动 MySQL、Redis、Neo4j 等基础依赖
2. 启动 `backend`，由 Flyway 完成 MySQL 迁移并生成 `flyway_schema_history`
3. 启动 `recommend-service`
4. 启动 `frontend`

## 3. 环境要求

### 3.1 后端

- JDK 17
- Maven 3.9+，或使用 `backend/mvnw`
- MySQL 8
- Redis 7
- Neo4j 5
- ffprobe

### 3.2 前端

- Node.js `^20.19.0` 或 `>=22.12.0`
- npm 9+

项目 `frontend/package.json` 中配置了 Volta Node 版本 `22.22.2`。如果本机使用 Volta，可以直接让 Volta 接管 Node 版本；否则使用满足 Vite 7 要求的版本。

### 3.3 推荐服务

- Conda 或兼容的 Python 环境管理工具
- Python 3.9

推荐服务依赖定义在 `recommend-service/environment.yml`，其中包含 FastAPI、Uvicorn、Pandas、SciPy、scikit-surprise 等包。

## 4. 启动基础依赖

基础依赖的 Docker Compose 文件位于 `scripts/docker-compose.yml`。

注意：`scripts/docker-compose.yml` 只启动 MySQL、Redis、Neo4j 这类基础依赖，适合配合本机直接运行前端、后端和推荐服务。若要一并容器化应用服务，请使用根目录的 `docker-compose.local.yml`。

```bash
cd scripts
docker compose up -d
docker compose ps
```

停止依赖服务：

```bash
cd scripts
docker compose down
```

如果需要同时删除本地数据卷：

```bash
cd scripts
docker compose down -v
```

### 4.1 Docker 默认账号

| 服务 | 地址 | 默认账号 | 默认密码 |
| --- | --- | --- | --- |
| MySQL | `127.0.0.1:3306` | `dev` | `dev123` |
| Redis | `127.0.0.1:6379` | 无用户名 | `redis123` |
| Neo4j HTTP | `http://127.0.0.1:7474` | `neo4j` | `neo4j123` |
| Neo4j Bolt | `bolt://127.0.0.1:7687` | `neo4j` | `neo4j123` |

### 4.2 初始化数据注意事项

`scripts/docker-compose.yml` 会在首次创建数据卷时准备基础依赖：

- MySQL 只创建空数据库 `course_db`，表结构和初始化数据由后端 Flyway 迁移创建
- Neo4j 通过 `neo4j-init` 服务从 `scripts/neo4j-backups/neo4j.dump` 自动恢复默认库 `neo4j`
- Neo4j 认证为 `neo4j/neo4j123`，与后端默认配置一致

如果需要重建本地 MySQL 和 Neo4j 数据，请删除数据卷后重新启动，再启动后端执行 Flyway 迁移：

```bash
cd scripts
docker compose down -v
docker compose up -d
```

## 5. 初始化数据库

### 5.1 创建数据库

如果使用 `scripts/docker-compose.yml` 启动 MySQL，数据库 `course_db` 会自动创建。若使用本机 MySQL，可以手动执行：

```sql
CREATE DATABASE course_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 5.2 使用 Flyway 初始化新数据库

后端集成 Flyway，迁移脚本位于：

```text
backend/src/main/resources/db/migration
```

空数据库场景下，启动后端即可自动执行 `V1__baseline_schema.sql` 以及后续迁移：

```bash
set -a
source .env.local
set +a
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

迁移完成后，MySQL 中会出现 `flyway_schema_history` 表。该表记录已执行的迁移版本，后续不要删除或手动修改。

### 5.3 已有数据库首次接入 Flyway

如果你的 `course_db` 已经在 Flyway 接入前存在，并且表结构就是当前项目基准状态，第一次启动后端时需要显式 baseline：

```bash
set -a
source .env.local
set +a
cd backend
FLYWAY_BASELINE_ON_MIGRATE=true SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

确认 `flyway_schema_history` 已生成后，后续启动不要再携带 `FLYWAY_BASELINE_ON_MIGRATE=true`。

### 5.4 后续数据库变更

不要修改已经执行过的迁移文件。新增表、字段、索引或基础数据时，新建更高版本迁移，例如：

```text
backend/src/main/resources/db/migration/V2__add_course_source.sql
```

多人协作时建议使用时间戳版本降低冲突概率，例如：

```text
V202605201430__add_course_source.sql
```

当前初始化流程以 Flyway 迁移为准，基线脚本为 `backend/src/main/resources/db/migration/V1__baseline_schema.sql`。

## 6. 配置说明

### 6.1 后端环境变量

后端主配置文件位于 `backend/src/main/resources/application.yaml`。

本机开发首次运行时，在仓库根目录创建已被 Git 忽略的 `.env.local`。分别执行两次
`openssl rand -base64 32`，再把两个不同的输出保存为：

```dotenv
JWT_SECRET_BASE64=生成的Base64值
PLAYBACK_TOKEN_SECRET_BASE64=另一个生成的Base64值
```

建议执行 `chmod 600 .env.local`。`scripts/dev.sh` 和 `scripts/dev.bash` 会自动加载该文件；手动启动后端前使用 `set -a; source .env.local; set +a` 加载。不要在每次启动时重新生成密钥；两类密钥不能相同。

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` | MySQL 地址 | `localhost` |
| `DB_PORT` | MySQL 端口 | `3306` |
| `DB_NAME` | 数据库名 | `course_db` |
| `DB_USERNAME` | MySQL 用户名 | `dev` |
| `DB_PASSWORD` | MySQL 密码 | `dev123` |
| `FLYWAY_BASELINE_ON_MIGRATE` | 旧库首次接入 Flyway 时标记当前库为基线 | `false` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | `redis123` |
| `REDIS_CONNECT_TIMEOUT` | Lettuce 建立 Redis 连接的超时 | `500ms` |
| `REDIS_COMMAND_TIMEOUT` | Lettuce 等待 Redis 命令完成的超时 | `500ms` |
| `NEO4J_URI` | Neo4j Bolt 地址 | `bolt://localhost:7687` |
| `NEO4J_USERNAME` | Neo4j 用户名 | `neo4j` |
| `NEO4J_PASSWORD` | Neo4j 密码 | `neo4j123` |
| `RECOMMEND_SERVICE_URL` | 推荐服务基础地址 | `http://localhost:8000` |
| `RECOMMEND_CF_CONNECT_TIMEOUT_MS` | 推荐服务连接超时 | `5000` |
| `RECOMMEND_CF_READ_TIMEOUT_MS` | 推荐服务读取超时 | `30000` |
| `RECOMMEND_CF_REQUEST_TOP_N` | CF 服务候选返回数量 | `100` |
| `RECOMMEND_COLD_START_LIMIT` | 用户冷启动推荐数量 | `10` |
| `RECOMMEND_CANDIDATE_POOL_SIZE` | 常规推荐候选池大小 | `20` |
| `RECOMMEND_CF_WEIGHT` | CF 分数融合权重 | `0.70` |
| `RECOMMEND_COLD_START_CACHE_TTL_MINUTES` | 冷启动推荐缓存时间，单位分钟 | `10` |
| `RECOMMEND_CACHE_TTL_MINUTES` | 常规推荐缓存时间，单位分钟 | `30` |
| `RECOMMEND_CACHE_BUILD_LOCK_TTL_SECONDS` | 推荐缓存构建锁时间，单位秒 | `20` |
| `RECOMMEND_CACHE_WAIT_RETRY_TIMES` | 推荐缓存等待重试次数 | `3` |
| `RECOMMEND_CACHE_WAIT_MILLIS` | 推荐缓存每次等待时间，单位毫秒 | `80` |
| `RECOMMEND_SCORE_SNAPSHOT_REBUILD_ON_STARTUP` | 启动时是否全量重建 `recommend_user_course_score` 评分快照表 | `true` |
| `RECOMMEND_SCORE_SNAPSHOT_BATCH_SIZE` | 全量重建快照的批量写入大小 | `500` |
| `RECOMMEND_SCORE_SNAPSHOT_RAW_SCORE_SCALE` | 隐式评分归一化到 0-10 区间的缩放系数 | `20.0` |
| `RECOMMEND_SCORE_SNAPSHOT_MIN_SCORE` | 快照保留的最低归一化分 | `0.1` |
| `RECOMMEND_SCORE_BASE` | 推荐展示分基础值 | `60` |
| `RECOMMEND_SCORE_SPAN` | 推荐展示分跨度 | `35` |
| `RECOMMEND_COLD_START_USER_SCORE_SCALE` | 用户冷启动展示分缩放系数 | `10.0` |
| `RECOMMEND_HOT_FALLBACK_SCORE_BASE` | 热门兜底展示分基础值 | `0.70` |
| `RECOMMEND_HOT_FALLBACK_SCORE_STEP` | 热门兜底展示分递减步长 | `0.03` |
| `RECOMMEND_HOT_FALLBACK_SCORE_MIN` | 热门兜底展示分下限 | `0.55` |
| `RECOMMEND_HOT_FALLBACK_LIMIT` | 热门兜底推荐数量 | `10` |
| `RECOMMEND_HOT_FALLBACK_MAX_SCAN_COUNT` | 热门兜底最大扫描数量 | `100` |
| `RECOMMEND_GRAPH_PREREQUISITE_THRESHOLD` | 图谱先修掌握阈值 | `0.7` |
| `RECOMMEND_GRAPH_LEARNING_PATH_LIMIT_PER_COURSE` | 单课程学习路径数量 | `5` |
| `RECOMMEND_ASYNC_ENABLED` | 混合推荐异步执行开关 | `true` |
| `RECOMMEND_ASYNC_CORE_SIZE` | 推荐线程池核心线程数 | `2` |
| `RECOMMEND_ASYNC_MAX_SIZE` | 推荐线程池最大线程数 | `4` |
| `RECOMMEND_ASYNC_QUEUE_CAPACITY` | 推荐线程池队列容量 | `100` |
| `RECOMMEND_HOT_SYNC_ENABLED` | 课程热度同步开关 | `true` |
| `RECOMMEND_HOT_SYNC_FIXED_DELAY_MS` | 课程热度同步间隔 | `300000` |
| `RECOMMEND_HOT_SYNC_BATCH_SIZE` | 课程热度同步批量大小 | `500` |
| `RECOMMEND_NEW_COURSE_ENABLED` | 常规推荐中新课曝光开关 | `true` |
| `RECOMMEND_NEW_COURSE_WINDOW_DAYS` | 新课时间窗 | `14` |
| `RECOMMEND_NEW_COURSE_CANDIDATE_LIMIT` | 新课候选池大小 | `80` |
| `RECOMMEND_NEW_COURSE_REGULAR_CANDIDATE_LIMIT` | 常规推荐新课候选数量 | `30` |
| `RECOMMEND_NEW_COURSE_FALLBACK_LIMIT` | CF 为空时新课兜底数量 | `10` |
| `RECOMMEND_NEW_COURSE_MAX_LEARNERS` | 新课学习人数上限 | `20` |
| `RECOMMEND_NEW_COURSE_INJECT_LIMIT` | 单次最多注入新课数量 | `3` |
| `RECOMMEND_NEW_COURSE_MAX_EXPOSURE_RATIO` | 新课最大曝光占比 | `0.30` |
| `RECOMMEND_NEW_COURSE_INJECT_SLOTS` | 新课注入槽位 | `2,7,12` |
| `RECOMMEND_NEW_COURSE_MIN_TAG_COUNT` | 新课标签数量下限 | `1` |
| `RECOMMEND_NEW_COURSE_MIN_KP_COUNT` | 新课知识点数量下限 | `1` |
| `RECOMMEND_NEW_COURSE_MIN_DURATION_SECONDS` | 新课时长下限，单位秒 | `300` |
| `RECOMMEND_NEW_COURSE_MIN_LIMIT` | 新课推荐最小返回数量 | `1` |
| `RECOMMEND_NEW_COURSE_MAX_LIMIT` | 新课推荐最大返回数量 | `50` |
| `RECOMMEND_NEW_COURSE_DEFAULT_LIMIT` | 新课推荐默认返回数量 | `10` |
| `RECOMMEND_NEW_COURSE_TAG_WEIGHT` | 新课标签匹配权重 | `0.45` |
| `RECOMMEND_NEW_COURSE_FRESHNESS_WEIGHT` | 新课新鲜度权重 | `0.30` |
| `RECOMMEND_NEW_COURSE_QUALITY_WEIGHT` | 新课质量权重 | `0.20` |
| `RECOMMEND_NEW_COURSE_READINESS_WEIGHT` | 新课先修掌握度权重 | `0.05` |
| `RECOMMEND_NEW_COURSE_READINESS_THRESHOLD` | 新课先修掌握阈值 | `0.7` |
| `RECOMMEND_NEW_COURSE_LEARNING_GOAL_BONUS` | 学习目标匹配加分 | `0.05` |
| `RECOMMEND_NEW_COURSE_QUALITY_KP_FULL_SCORE_COUNT` | 新课质量知识点满分数量 | `4.0` |
| `RECOMMEND_NEW_COURSE_QUALITY_DURATION_FULL_SCORE_SECONDS` | 新课质量时长满分秒数 | `1800.0` |
| `RECOMMEND_NEW_COURSE_QUALITY_KP_WEIGHT` | 新课质量知识点权重 | `0.5` |
| `VIDEO_DIR` | 视频文件存储目录 | 本机开发绝对路径 |
| `FFPROBE_PATH` | ffprobe 可执行文件路径 | `/opt/homebrew/bin/ffprobe` |
| `JWT_SECRET_BASE64` | 必填的 Base64 JWT 签名密钥，解码后至少 32 字节 | 无默认值 |
| `PLAYBACK_TOKEN_SECRET_BASE64` | 必填的独立播放凭证签名密钥，解码后至少 32 字节 | 无默认值 |
| `AGENT_ENABLED` | 学习助手开关 | `true` |
| `AGENT_LLM_PROVIDER` | 学习助手模型提供方；`mock` 或空密钥时走本地 mock | `openai-compatible` |
| `AGENT_LLM_BASE_URL` | OpenAI 兼容接口基础地址 | `https://api.openai.com/v1` |
| `AGENT_LLM_API_KEY` | 学习助手模型密钥 | 空 |
| `AGENT_LLM_MODEL` | 学习助手模型名称 | `gpt-4o-mini` |
| `AGENT_LLM_CONNECT_TIMEOUT_MS` | 学习助手模型连接超时，单位毫秒 | `5000` |
| `AGENT_LLM_READ_TIMEOUT_MS` | 学习助手模型读取超时，单位毫秒 | `45000` |
| `AGENT_MAX_HISTORY_MESSAGES` | 构造对话上下文时最多读取的历史消息数 | `12` |
| `AGENT_MAX_CONTEXT_COURSES` | 学习助手上下文中最多使用的课程数量 | `5` |
| `AGENT_CONTEXT_RECOMMEND_TIMEOUT_MS` | 学习助手读取推荐上下文的等待时间，单位毫秒 | `5000` |
| `AGENT_INCOMPLETE_RECOVERY_AFTER_MS` | USER 已落库但 ASSISTANT 未落库时允许重试接管的窗口，单位毫秒 | `90000` |
| `AGENT_LLM_MAX_OUTPUT_TOKENS` | 学习助手模型最大输出 token | `800` |
| `AGENT_LLM_TEMPERATURE` | 学习助手模型温度 | `0.3` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 允许跨域来源 | `http://localhost:5173,http://127.0.0.1:5173,http://192.168.*:5173` |

常用本地配置示例：

```bash
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=course_db
export DB_USERNAME=dev
export DB_PASSWORD=dev123

export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD=redis123

export NEO4J_URI=bolt://127.0.0.1:7687
export NEO4J_USERNAME=neo4j
export NEO4J_PASSWORD=neo4j123

export RECOMMEND_SERVICE_URL=http://127.0.0.1:8000
export VIDEO_DIR=/data/course_videos
export FFPROBE_PATH=/usr/bin/ffprobe
export AGENT_LLM_PROVIDER=mock
```

### 6.2 前端接口配置

前端 Axios 默认以 `/api/v1` 作为接口前缀，Vite 代理保留完整路径，配置位于 `frontend/vite.config.js`：

```text
/api/v1 -> http://localhost:8080/api/v1
/videos -> http://localhost:8080
```

如果后端端口或地址发生变化，需要同步修改 `frontend/vite.config.js` 中的代理目标。

### 6.3 推荐服务配置

推荐服务默认监听 `127.0.0.1:8000`，后端会调用：

```text
POST ${RECOMMEND_SERVICE_URL}/recommend
```

因此只要后端的 `RECOMMEND_SERVICE_URL` 与 FastAPI 实际监听地址一致即可。

第二阶段重构后，推荐服务不再接收后端传来的全量评分矩阵，而是直连 MySQL 读取后端维护的 `recommend_user_course_score` 快照表。推荐服务复用以下数据库环境变量：

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` | MySQL 地址 | `localhost` |
| `DB_PORT` | MySQL 端口 | `3306` |
| `DB_NAME` | 数据库名 | `course_db` |
| `DB_USERNAME` | MySQL 用户名 | `dev` |
| `DB_PASSWORD` | MySQL 密码 | `dev123` |

后端启动后默认会重建评分快照表；如果推荐服务已经提前启动，后端重建完成后可调用 `POST /model/reload` 让推荐服务重新加载快照并训练内存模型。

## 7. 启动 backend

进入后端目录：

```bash
set -a
source .env.local
set +a
cd backend
```

编译检查：

```bash
./mvnw -q -DskipTests compile
```

启动后端服务：

```bash
SPRING_PROFILES_ACTIVE=dev RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 \
./mvnw spring-boot:run
```

`dev` profile 会开启 MyBatis SQL 调试日志；默认配置不打印 SQL，适合生产或演示环境。后端启动阶段会先执行 Flyway 迁移，再启动业务服务。

如果需要显式指定本地依赖地址：

```bash
DB_HOST=127.0.0.1 \
REDIS_HOST=127.0.0.1 \
NEO4J_URI=bolt://127.0.0.1:7687 \
SPRING_PROFILES_ACTIVE=dev \
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 \
VIDEO_DIR=/data/course_videos \
./mvnw spring-boot:run
```

打包运行：

```bash
./mvnw clean package
java -jar target/course-system-0.0.1-SNAPSHOT.jar
```

服务默认访问地址：

```text
http://127.0.0.1:8080
```

Actuator 健康检查：

```bash
curl "http://127.0.0.1:8080/actuator/health"
```

## 8. 启动 recommend-service

进入推荐服务目录：

```bash
cd recommend-service
```

创建 Conda 环境：

```bash
conda env create -f environment.yml
```

激活环境：

```bash
conda activate lab_autumn
```

启动 FastAPI 服务：

```bash
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

启动成功后可访问：

- `http://127.0.0.1:8000/docs`
- `http://127.0.0.1:8000/openapi.json`
- `http://127.0.0.1:8000/model/status`

查看模型状态：

```bash
curl "http://127.0.0.1:8000/model/status"
```

手动重新加载快照并训练模型：

```bash
curl -X POST "http://127.0.0.1:8000/model/reload"
```

推荐接口测试：

```bash
curl -X POST "http://127.0.0.1:8000/recommend" \
  -H "Content-Type: application/json" \
  -d '{
    "targetUserId": 1,
    "topN": 3
  }'
```

模型可用时会返回类似结构：

```json
{
  "userId": 1,
  "items": [
    {
      "courseId": 3,
      "score": 8.72
    }
  ]
}
```

实际 `score` 会随模型训练结果变化；如果模型尚未训练、快照表为空或目标用户不在训练集中，`items` 会返回空数组，后端混合推荐会继续走新课和热门课程兜底。

## 9. 启动 frontend

进入前端目录：

```bash
cd frontend
```

安装依赖：

```bash
npm install
```

启动开发服务器：

```bash
npm run dev
```

默认访问地址：

```text
http://127.0.0.1:5173
```

构建生产包：

```bash
npm run build
```

前端生产包会按路由拆分页面代码，并把 Vue、Element Plus、ECharts 和其他第三方依赖拆成独立 vendor chunk。学习进度、知识图谱和个人中心页面使用 `src/utils/echarts.js` 中按需注册的 ECharts 能力。

本地预览构建结果：

```bash
npm run preview
```

## 10. 完整联调检查

三个服务都启动后，建议按下面顺序检查：

1. 浏览器打开 `http://127.0.0.1:5173`
2. 前端登录或注册是否正常
3. 普通学生首次进入学生端是否自动跳到 `/onboarding`
4. 完成学习基础、学习目标、兴趣方向后是否跳转到 `/recommendations`
5. 后端 `http://127.0.0.1:8080/actuator/health` 是否可访问
6. 推荐服务 `http://127.0.0.1:8000/docs` 是否可访问
7. 前端课程列表、课程详情、选课、视频学习是否正常
8. 推荐页是否能返回推荐课程，并显示 `CF`、`COLD_START_USER`、`COLD_START_COURSE` 或 `HOT_FALLBACK` 来源标签
9. 学习进度、能力雷达图、知识图谱页面是否正常渲染
10. 学习助手 `/assistant` 是否能创建会话、发送消息；刷新后半成品 USER 消息是否显示“回答未完成，可重试”
11. 管理端课程管理、用户管理、视频上传流程是否正常

## 11. 常用接口示例

多数后端业务接口需要登录后访问。登录成功后，将 JWT 放入请求头：

```text
Authorization: Bearer <your_token>
```

### 11.1 注册

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_user",
    "password": "123456",
    "email": "test@example.com",
    "phone": "13800000000"
  }'
```

### 11.2 登录

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "123456"
  }'
```

### 11.3 获取当前用户信息

```bash
curl "http://127.0.0.1:8080/api/v1/users/me" \
  -H "Authorization: Bearer <your_token>"
```

### 11.4 课程分页查询

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/courses/search" \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "page": 1,
    "pageSize": 9
  }'
```

### 11.5 选课

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/courses/1/enrollment" \
  -H "Authorization: Bearer <your_token>"
```

### 11.6 记录学习行为

```bash
curl -X POST "http://127.0.0.1:8080/api/v1/learning-behaviors" \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{"eventId":"study-550e8400-e29b-41d4-a716-446655440000","courseId":1,"behaviorType":"STUDY","duration":300}'
```

同一段 STUDY 行为重试时必须复用 `eventId`；新的学习事件应生成新的 ID。首次处理返回 `data.replayed=false`，重复回放返回 `data.replayed=true`，两者的业务码均为 `200`。

### 11.7 新用户引导

普通用户访问学生端页面时，前端会先查引导状态：

```bash
curl "http://127.0.0.1:8080/api/v1/onboarding/status" \
  -H "Authorization: Bearer <your_token>"
```

未完成时返回的核心字段为：

```json
{
  "completed": false
}
```

引导选项：

```bash
curl "http://127.0.0.1:8080/api/v1/onboarding/options" \
  -H "Authorization: Bearer <your_token>"
```

返回包含：

| 字段 | 说明 |
| --- | --- |
| `levels` | `1` 零基础、`2` 入门、`3` 有基础 |
| `learningGoals` | `JOB` 找工作、`PROJECT` 做项目、`FOUNDATION` 打基础、`EXAM` 备考 |
| `tags` | 可用于初始化兴趣画像的启用标签 |

提交引导信息：

```bash
curl -X PUT "http://127.0.0.1:8080/api/v1/onboarding/profile" \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "currentLevel": 2,
    "learningGoal": "PROJECT",
    "tagIds": [1, 2]
  }'
```

约束：`currentLevel` 必填且只能为 `1`、`2`、`3`；`learningGoal` 可空；`tagIds` 至少一个，且必须来自启用的引导标签。提交成功后后端会保存 `user_onboarding_profile`，用 `INIT` 来源重写用户兴趣标签，并清理该用户推荐缓存。

### 11.8 获取融合推荐结果

```bash
curl "http://127.0.0.1:8080/api/v1/recommendations" \
  -H "Authorization: Bearer <your_token>"
```

响应的推荐项只暴露前端稳定展示字段，不再直接暴露内部 `cfScore`、`finalScore`：

```json
{
  "items": [
    {
      "courseId": 3,
      "title": "示例课程",
      "difficulty": 2,
      "recommendScore": 88,
      "reason": "根据你的学习行为推荐；当前可直接学习",
      "readiness": 0.86,
      "recommendSource": "CF",
      "isNewCourse": false,
      "knowledgePoints": [],
      "missingPrerequisitesMastery": [],
      "learningPaths": []
    }
  ]
}
```

`recommendSource` 用于演示和验收推荐链路：

| 值 | 来源 |
| --- | --- |
| `CF` | 协同过滤候选，经课程状态、已选过滤和图谱准备度加权 |
| `COLD_START_USER` | 行为不足的新用户，基于引导画像和兴趣标签生成 |
| `COLD_START_COURSE` | 常规推荐链路中的新课注入候选 |
| `HOT_FALLBACK` | CF 和新课候选都不可用时的热门课程兜底 |

### 11.9 获取学习分析数据

```bash
curl "http://127.0.0.1:8080/api/v1/learning-analytics/progress?days=30" \
  -H "Authorization: Bearer <your_token>"
```

```bash
curl "http://127.0.0.1:8080/api/v1/learning-analytics/ability-radar" \
  -H "Authorization: Bearer <your_token>"
```

```bash
curl "http://127.0.0.1:8080/api/v1/learning-analytics/knowledge-graph?courseId=1&depth=3" \
  -H "Authorization: Bearer <your_token>"
```

## 12. 视频功能说明

系统支持后台上传课程视频，并通过受保护的 `/videos/**` 静态路径读取文件。

课程详情页先携带登录 JWT 调用 `POST /api/v1/courses/{courseId}/playback`，后端返回只绑定该视频路径的限时签名 URL。原生 `<video>` 直接使用该 URL，Range 子请求继续携带查询参数，不需要认证 Cookie。播放凭证默认至少有效 2 小时；视频更长时，有效期为视频时长加 30 分钟。首次加载失败时前端自动续签一次。

使用视频功能前请确认：

- `VIDEO_DIR` 指向真实存在或可创建的目录
- 当前运行后端服务的系统用户对该目录拥有读写权限
- 本机已安装 `ffprobe`
- `FFPROBE_PATH` 指向正确的 ffprobe 可执行文件
- `PLAYBACK_TOKEN_SECRET_BASE64` 已配置，且不同于登录 JWT 密钥

上传成功后，后端会读取视频时长并回写到数据库。

## 13. 常见问题

### 13.1 后端连接不上 MySQL

请检查：

- MySQL 是否已经启动
- 数据库名是否为 `course_db`
- 用户名和密码是否与配置一致
- 新库是否已通过后端 Flyway 迁移完成
- 旧库首次接入 Flyway 时是否已使用 `FLYWAY_BASELINE_ON_MIGRATE=true` 生成 `flyway_schema_history`
- 本机端口 `3306` 是否被其他 MySQL 实例占用

### 13.2 Redis 连接失败

请检查：

- Redis 是否已经启动
- `REDIS_PASSWORD` 是否为 `redis123` 或与你的实际配置一致
- 本机端口 `6379` 是否被占用

### 13.3 Neo4j 连接失败

请检查：

- `NEO4J_URI` 是否为 `bolt://127.0.0.1:7687` 或你的实际地址
- Neo4j 容器是否已经启动
- 后端的 `NEO4J_USERNAME`、`NEO4J_PASSWORD` 是否仍为 `neo4j`、`neo4j123`
- 如果需要重新恢复 `scripts/neo4j-backups/neo4j.dump`，是否已经删除旧 Neo4j 数据卷并重新启动 Compose

### 13.4 推荐接口报错

通常是以下原因：

- `recommend-service` 未启动
- 后端 `RECOMMEND_SERVICE_URL` 配置错误
- FastAPI 实际端口不是 `8000`
- 推荐服务依赖未完整安装，尤其是 `scikit-surprise`
- 后端调用的 `/recommend` 请求结构与推荐服务模型不一致

### 13.5 前端页面请求接口失败

请检查：

- `backend` 是否已经正常启动
- `frontend/vite.config.js` 的代理目标是否仍指向 `http://localhost:8080`
- 后端 CORS 是否允许当前前端地址
- 浏览器开发者工具 Network 面板中请求是否返回 401、404、500 或跨域错误

### 13.6 视频上传成功但无法播放

请检查：

- `VIDEO_DIR` 是否配置正确
- 视频文件是否真实存在于对应目录
- 后端是否能访问 `/videos/**`
- 上传目录权限是否正确
- `POST /api/v1/courses/{courseId}/playback` 是否成功返回带 `token` 的 URL
- `/videos/**` 请求是返回 401（凭证问题）还是 404（文件路径问题）

### 13.7 Conda 创建推荐服务环境失败

请检查：

- Conda 是否可用
- 当前网络是否能够访问 `environment.yml` 中配置的镜像源
- `scikit-surprise` 编译依赖是否安装完整
- 如果本机 Python 环境复杂，优先使用新的 Conda 环境重新安装

## 14. 开发与验证命令汇总

### 14.1 后端

```bash
set -a
source .env.local
set +a
cd backend
./mvnw -q -DskipTests compile
./mvnw test
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

### 14.2 前端

```bash
cd frontend
npm install
npm run dev
npm run build
```

### 14.3 推荐服务

```bash
cd recommend-service
conda env create -f environment.yml
conda activate lab_autumn
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

## 15. 部署提示

正式部署时建议至少完成以下调整：

- 不要使用默认数据库、Redis、Neo4j 密码
- 使用独立的生产环境配置管理敏感信息
- 为 `JWT_SECRET_BASE64` 生成并持久保存生产专用密钥；同一环境的所有后端实例使用相同值，应用重启时不得重新生成
- 为 `PLAYBACK_TOKEN_SECRET_BASE64` 生成另一个生产专用密钥；不得与登录密钥复用，并确保所有后端实例一致
- 生产库首次接入 Flyway 前先备份数据库，并确认是否需要一次性 baseline
- 将 `VIDEO_DIR` 指向持久化存储目录
- 为后端、前端和推荐服务配置统一的反向代理
- 为 FastAPI 推荐服务增加进程守护，例如 systemd、Docker 或 Supervisor
- 为后端和推荐服务增加日志收集与监控
- 明确前端生产构建产物的部署路径和后端 API 地址


## 学习行为 Outbox 运行与恢复

### 行为与部署

学习接口返回成功表示 MySQL 中的行为、进度、首次完成状态与任务已经一起提交；Redis 热度、Neo4j 掌握关系、推荐快照和缓存失效由后台补齐。
后台消费不依赖消息中间件。缓存失效等待对应快照和掌握度任务完成，普通 STUDY 保留弱失效节流。
VIEW 十分钟冷却改为 MySQL `user_course_relation.last_view_recorded_at`，迁移从现有 VIEW 历史初始化；事务回滚不会消耗冷却机会。
Neo4j 掌握分数改为历史最高值，以现存值为初值，新的低分或重试不会降低分数；完课公式保持不变，不重建过去已被覆盖的历史分数。

上线时暂停学习写入，停止全部旧后端，备份数据库，启动新版让 Flyway 执行 V4，再恢复流量。
不可混合运行旧版同步处理与新版 Outbox；本次迁移只增加列和任务表，不补偿历史跨库不一致。
回退应用前必须暂停写入并处理或明确保留全部待办任务，不能简单删除任务表。

### 配置

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| LEARNING_OUTBOX_ENABLED | true | 消费开关；关闭仍记录任务，需要重启应用生效 |
| LEARNING_OUTBOX_SCAN_INTERVAL_MS | 1000 | 扫描间隔 |
| LEARNING_OUTBOX_PARALLELISM | 4 | 每实例最大并行任务数 |
| LEARNING_OUTBOX_LEASE_SECONDS | 60 | 执行租约 |
| LEARNING_OUTBOX_RENEW_SECONDS | 20 | 续租间隔，租约至少为其两倍 |
| LEARNING_OUTBOX_MAX_ATTEMPTS | 20 | 单任务最大执行尝试次数 |
| LEARNING_OUTBOX_RETRY_BASE_SECONDS | 5 | 首次重试基准延迟 |
| LEARNING_OUTBOX_RETRY_MAX_SECONDS | 300 | 指数退避含抖动的最大延迟 |

消费程序在独立的 READ COMMITTED 短事务中领取任务，避免状态索引间隙锁竞争；外部调用不持有领取锁。进程重启后过期租约可重新领取。
`next_attempt_at` 是 PENDING 任务允许再次领取的最早时间；失败后按退避策略推迟，并不保证到点立即执行。
PROCESSING 任务是否可回收由 `lease_until` 决定；每次领取都会增加尝试次数，过期且已达到上限的任务转为 DEAD。
状态更新携带租约令牌，旧执行者不能覆盖新执行者的任务状态；副作用本身也支持重复执行。
任务依赖为 DONE 或 SKIPPED 才可执行；依赖处于 DEAD 时，应优先修复该依赖。
MySQL 源用户被删除、课程或选课关系不存在时跳过任务；课程下线跳过热度任务。
MySQL 源数据存在而 Neo4j 用户或知识点缺失时视为故障，整批图写入回滚并重试，不静默丢失掌握关系。

### 停机与重启

Spring 容器正常销毁 `LearningOutboxProcessor` 时会自动调用其 `@PreDestroy close()`。
处理器先通知扫描停止领取，并关闭工作线程池的提交入口，再最多等待 30 秒让已提交任务收尾；等待期间续租线程池仍可维护租约。
等待超时或等待线程被中断时，会尝试中断工作线程，最后关闭续租线程池。

中断需要任务配合响应，因此该方法返回不保证所有工作线程已结束；强制杀进程也不能保证执行销毁回调。
重启后应检查遗留的 PROCESSING、PENDING 和 DEAD 任务，按租约和重试规则恢复，不要仅因进程已退出就重放仍有有效租约的任务。

### 检查与重放

使用管理员 JWT 访问 `GET /actuator/metrics/{指标名}` 查看以下指标：

| 指标 | 统计口径 |
| --- | --- |
| `learning.outbox.pending` | MySQL 中 PENDING 与 PROCESSING 的数量之和，包含等待重试和等待依赖的任务 |
| `learning.outbox.oldest.seconds` | 上述任务中最早的 created_at 距数据库当前时间的秒数；没有任务时为 0，重试不会重新开始计时 |
| `learning.outbox.dead` | MySQL 中 DEAD 任务数量 |
| `learning.outbox.completed` | 当前实例成功写入 DONE 或 SKIPPED 状态的次数，按 type 区分 |
| `learning.outbox.failures` | 当前实例执行过程中捕获异常的次数，按 type 区分；同一任务重试可重复计数 |
| `learning.outbox.duration` | 当前实例的任务处理耗时，按 type 区分，包含失败和提前退出的处理 |

前三项在扫描时刷新，关闭消费开关后仍会查询；扫描查询失败时可能保留上次采样值。
它们反映共享数据库的队列状态，多实例监控时不要直接相加；后三项是各实例进程内的累计观测值。
重点关注最老待办持续增长和死任务；日志包含 taskId 和尝试次数。以下 SQL 由具有数据库运维权限的操作者执行，不向学生端开放。

```sql
SELECT status, task_type, COUNT(*) AS tasks, MIN(created_at) AS oldest
FROM learning_outbox_task GROUP BY status, task_type;

SELECT id, task_type, attempts, last_error, dependency_id, mastery_dependency_id
FROM learning_outbox_task WHERE status='DEAD' ORDER BY created_at;

-- 查找因失败依赖而等待的缓存任务。
SELECT t.id, d.id AS failed_dependency, d.last_error
FROM learning_outbox_task t JOIN learning_outbox_task d
  ON d.id=t.dependency_id OR d.id=t.mastery_dependency_id
WHERE t.status='PENDING' AND d.status='DEAD';
```

先检查指定任务的载荷和失败原因，恢复 Redis/Neo4j 或修复缺失图数据，再重放明确选定的 DEAD 任务。
不要修改任务 ID、来源事件 ID、载荷或依赖，不重放仍在执行的任务，不复制为一条新任务。

```sql
START TRANSACTION;
SELECT id, status, payload, last_error FROM learning_outbox_task
WHERE id='替换为已核验的任务ID' FOR UPDATE;
UPDATE learning_outbox_task
SET status='PENDING', attempts=0, next_attempt_at=NOW(6),
    lease_token=NULL, lease_until=NULL, completed_at=NULL, last_error=NULL
WHERE id='替换为已核验的任务ID' AND status='DEAD';
COMMIT;
```

如需要等待依赖恢复，可先关闭消费、修复后重新开启。暂时没有自动归档，已完成任务与
`learning:outbox:hot:<taskId>` 去重键均保留，需监控存储增长，不单独删除去重键。

### Redis 丢失与任务重试的区别

普通请求超时或进程崩溃后，任务重试沿用原 ID，已有去重标记可阻止重复加热度。
这依赖 Redis 热榜与去重记录仍然存在；应为相关数据配置合适的持久化、备份和不淘汰策略。
Redis 全量丢失、恢复旧备份或去重键被淘汰，不能靠 Outbox 声称精确恢复，也不能把全部历史增量任务直接重放。

发生数据丢失时，暂停消费并暂时关闭 `RECOMMEND_HOT_SYNC_ENABLED`，保留 MySQL 热度快照，
避免空 Redis 覆盖快照；按同一恢复时点恢复热榜及去重数据，核验后再恢复消费和热度同步。
如果没有一致的备份，需单独制定热度重建和待办对账方案，本次改造不提供自动重建工具。

### 故障恢复验证

`cd backend && ./mvnw test` 运行全部测试，需要 Docker；集成测试在隔离的 MySQL、Redis、Neo4j 容器中验证事务回滚、
重复请求、并发完课/浏览、租约回收、外部执行后崩溃、Neo4j 缺失节点、Redis 错误及 HTTP 学习请求后的恢复。
测试不会停止或清空开发环境的数据库。


## 一键开发启动与演示

完成依赖安装并启动基础服务后，推荐用一键脚本拉起前端、后端和推荐服务：

首次运行先生成两个不同的本机固定密钥，并写入已被 Git 忽略的 `.env.local`：

```bash
openssl rand -base64 32
openssl rand -base64 32
```

```dotenv
JWT_SECRET_BASE64=把上一步生成的值粘贴到这里
PLAYBACK_TOKEN_SECRET_BASE64=把第二个生成值粘贴到这里
```

开发脚本会自动加载 `.env.local`，后续重启继续使用同一密钥：

```bash
./scripts/dev.sh
```

如果当前环境没有 zsh，也可以使用 Bash 版本：

```bash
./scripts/dev.bash
```

默认地址：

- frontend: `http://127.0.0.1:5173`
- backend: `http://127.0.0.1:8080`
- recommend-service: `http://127.0.0.1:8000`

推荐服务会优先通过 Conda 环境 `lab_autumn` 启动；如需切换可设置 `RECOMMEND_CONDA_ENV`。脚本默认会一直等待后端启动完成；如需设置等待上限，可设置 `BACKEND_READY_TIMEOUT_SECONDS`。

常用覆盖项可直接在命令前设置，例如：

```bash
FRONTEND_PORT=5174 BACKEND_PORT=8081 RECOMMEND_PORT=8001 ./scripts/dev.sh
```

完整手动启动、Flyway 接管旧库、数据库重建和排查步骤见 [docs/OPERATION_MANUAL.md](OPERATION_MANUAL.md)。


### 演示流程

1. 注册或登录一个普通学生账号
2. 首次进入学生端会自动跳到 `/onboarding`
3. 依次选择当前基础、学习目标和至少一个兴趣标签
4. 完成后进入 `/recommendations`，推荐卡片会显示推荐分、原因、准备度和来源标签
5. 可继续进入课程详情、播放视频并回写学习行为，再刷新推荐观察结果变化

推荐来源字段由后端 `GET /api/v1/recommendations` 透出为 `recommendSource`：

| 值 | 含义 |
| --- | --- |
| `CF` | 协同过滤候选，经课程状态、已选过滤和图谱准备度加权 |
| `COLD_START_USER` | 新用户或行为不足时，基于引导画像和兴趣标签生成 |
| `COLD_START_COURSE` | 常规链路中的新课注入候选 |
| `HOT_FALLBACK` | CF 和新课候选不可用时的热门课程兜底 |
