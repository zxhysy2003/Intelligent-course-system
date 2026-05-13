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
- 通过 `/videos/**` 暴露本地课程视频静态资源

## 技术栈

- Java 17
- Spring Boot 3.5.14
- Spring MVC
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
│   ├── META-INF
│   └── mapper              # MyBatis XML
├── src/test                # 单元测试
├── mvnw
└── pom.xml

../scripts
├── course_db.sql           # MySQL 初始化脚本
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

## 快速启动

下面的快速启动命令默认从仓库根目录执行。

### 1. 启动基础依赖

`docker-compose.yml` 位于仓库根目录的 `scripts` 目录：

```bash
cd scripts
docker compose up -d
docker compose ps
```

默认依赖信息：

| 服务 | 地址 | 默认配置 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3306` | 数据库 `course_db`，用户 `dev`，密码 `dev123` |
| Redis | `127.0.0.1:6379` | 密码 `redis123` |
| Neo4j HTTP | `http://127.0.0.1:7474` | 用户 `neo4j`，密码 `neo4j123` |
| Neo4j Bolt | `bolt://127.0.0.1:7687` | 用户 `neo4j`，密码 `neo4j123` |

### 2. 初始化数据说明

`scripts/docker-compose.yml` 已挂载初始化数据：

- MySQL 首次创建 `mysql_data` 数据卷时，会自动导入 `scripts/course_db.sql`
- Neo4j 首次创建 `neo4j_data` 数据卷时，会由 `neo4j-init` 服务自动从 `scripts/neo4j-backups/neo4j.dump` 恢复知识图谱数据
- Neo4j 认证已配置为 `neo4j/neo4j123`，与后端 `application.yaml` 默认值一致

如果需要重新导入 MySQL 和 Neo4j 初始化数据，可以删除 Compose 数据卷后重建：

```bash
cd scripts
docker compose down -v
docker compose up -d
```

### 3. 初始化数据库

首次使用 `scripts/docker-compose.yml` 启动 MySQL 时通常无需手动导入。若数据库已经存在，或使用本机 MySQL，可以从仓库根目录手动执行：

```bash
mysql -u dev -p course_db < scripts/course_db.sql
```

如果使用 Docker MySQL：

```bash
docker compose -f scripts/docker-compose.yml exec -T mysql mysql -udev -pdev123 course_db < scripts/course_db.sql
```

### 4. 启动后端并生成评分快照

从仓库根目录执行：

```bash
cd backend
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 ./mvnw spring-boot:run
```

也可以显式指定常用本地环境变量：

```bash
cd backend
DB_HOST=127.0.0.1 \
REDIS_HOST=127.0.0.1 \
NEO4J_URI=bolt://127.0.0.1:7687 \
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 \
VIDEO_DIR=/data/course_videos \
VIDEO_BASE_URL=http://127.0.0.1:8080 \
./mvnw spring-boot:run
```

健康检查：

```bash
curl "http://127.0.0.1:8080/actuator/health"
```

后端启动时默认会全量重建 `recommend_user_course_score`，之后在 `STUDY`、`VIEW`、`FAVORITE`、`UNFAVORITE`、`FINISH` 等行为变化后增量刷新对应用户课程评分。

### 5. 启动推荐服务

后端默认调用：

```text
POST http://127.0.0.1:8000/recommend
```

推荐服务代码在 `../recommend-service`，常用启动方式：

```bash
cd recommend-service
conda env create -f environment.yml
conda activate lab_autumn
DB_HOST=127.0.0.1 DB_PORT=3306 DB_NAME=course_db DB_USERNAME=dev DB_PASSWORD=dev123 \
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

推荐服务启动时会读取后端维护的评分快照表并训练内存模型。后端刚产生或更新大量快照后，也可以手动执行：

```bash
curl -X POST "http://127.0.0.1:8000/model/reload"
```

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
java -jar target/course-system-0.0.1-SNAPSHOT.jar
```

当前测试主要覆盖推荐、冷启动、新课推荐、学习行为、学习分析、用户、课程和用户选课等 service 层逻辑，以及推荐控制器。

## 配置说明

主配置文件为 [`src/main/resources/application.yaml`](src/main/resources/application.yaml)。

### 基础配置

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` | MySQL 地址 | `localhost` |
| `DB_PORT` | MySQL 端口 | `3306` |
| `DB_NAME` | 数据库名 | `course_db` |
| `DB_USERNAME` | MySQL 用户名 | `dev` |
| `DB_PASSWORD` | MySQL 密码 | `dev123` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | `redis123` |
| `NEO4J_URI` | Neo4j Bolt 地址 | `bolt://localhost:7687` |
| `NEO4J_USERNAME` | Neo4j 用户名 | `neo4j` |
| `NEO4J_PASSWORD` | Neo4j 密码 | `neo4j123` |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 前端跨域来源 | `http://localhost:5173,http://127.0.0.1:5173,http://192.168.*:5173` |

### 视频配置

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `VIDEO_DIR` | 视频文件存储目录 | `/Users/shiyang/Desktop/graduate_design/course_videos` |
| `VIDEO_BASE_URL` | 视频访问基础地址 | `http://localhost:8080` |
| `FFPROBE_PATH` | ffprobe 可执行文件路径 | `/opt/homebrew/bin/ffprobe` |

### 推荐配置

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `RECOMMEND_SERVICE_URL` | FastAPI 推荐服务地址 | `http://localhost:8000` |
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
| `RECOMMEND_SCORE_SNAPSHOT_REBUILD_ON_STARTUP` | 启动时是否全量重建评分快照表 | `true` |
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

## 接口分组

大部分业务接口需要携带 JWT：

```text
Authorization: Bearer <your_token>
```

### 用户端

- `POST /user/register`：注册
- `POST /user/login`：登录
- `GET /user/profile`：当前用户信息
- `GET /course/{courseId}`：课程详情
- `POST /course/list`：课程分页
- `GET /course/categories`：课程分类
- `GET /course/attend/{courseId}`：选课
- `GET /course/video/{courseId}`：课程视频地址
- `POST /course/relation/updateProgressSeconds`：更新学习进度
- `POST /behavior/record`：记录学习行为
- `GET /recommend/hybrid`：混合推荐
- `GET /onboarding/options`：冷启动问卷选项
- `POST /onboarding/submit`：提交冷启动问卷
- `GET /onboarding/status`：冷启动状态
- `GET /analysis/progress`：学习进度
- `GET /analysis/ability-radar`：能力雷达图
- `GET /analysis/knowledge-graph`：知识图谱

### 管理端

- `POST /admin/user/list`：用户分页
- `GET /admin/user/detail/{userId}`：用户详情
- `PUT /admin/user/role/{userId}`：修改用户角色
- `PUT /admin/user/status/{userId}`：修改用户状态
- `PUT /admin/user/update`：更新用户信息
- `DELETE /admin/user/delete`：删除用户
- `GET /admin/course/register-options`：课程注册选项
- `POST /admin/course/register`：新增课程
- `PUT /admin/course/update`：更新课程
- `DELETE /admin/course/delete`：删除课程
- `POST /admin/course/{courseId}/video`：上传课程视频
- `GET /admin/course/detail/{courseId}`：后台课程详情
- `PUT /admin/course/status/{courseId}`：更新课程状态

### 静态资源

- `GET /videos/**`：访问课程视频文件

## 联调说明

完整系统建议按下面顺序启动：

1. `scripts/docker-compose.yml` 中的 MySQL、Redis、Neo4j
2. `../recommend-service` FastAPI 推荐服务
3. 当前 `backend` Spring Boot 服务
4. `../frontend` Vite 前端服务

前端默认将 `/api` 和 `/videos` 代理到 `http://localhost:8080`。如果后端端口发生变化，需要同步修改 `../frontend/vite.config.js`。

## 常见问题

### 后端启动后无法连接 MySQL

检查 MySQL 容器是否启动、`course_db` 是否已初始化、账号密码是否与 `application.yaml` 一致，以及本机 `3306` 端口是否被其他服务占用。

### Redis 认证失败

Docker Compose 中 Redis 使用 `redis123` 作为密码。若使用本机 Redis，需要保证 `REDIS_PASSWORD` 与实际配置一致。

### Neo4j 连接失败

优先检查 Neo4j 容器是否启动，`NEO4J_URI` 是否指向 `bolt://127.0.0.1:7687`，以及后端 `NEO4J_USERNAME`、`NEO4J_PASSWORD` 是否仍为 `neo4j`、`neo4j123`。如果需要重新恢复 `scripts/neo4j-backups/neo4j.dump`，请在 `scripts` 目录执行 `docker compose down -v` 后重新启动。

### 推荐接口失败

确认 `../recommend-service` 已启动，并且 `RECOMMEND_SERVICE_URL` 指向它的实际地址。默认推荐接口为 `POST /recommend`。

### 推荐服务返回空 items

确认 `recommend_user_course_score` 表已经生成数据，并检查推荐服务 `GET /model/status`。如果后端刚重建快照，调用推荐服务 `POST /model/reload` 重新训练模型。即使 CF 为空，后端仍会尝试新课和热门课程兜底。

### 视频无法上传或播放

确认 `VIDEO_DIR` 存在且后端进程有读写权限，`FFPROBE_PATH` 指向可执行文件，`VIDEO_BASE_URL` 与后端实际访问地址一致。

## 更多文档

- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)
- 前端说明：[`../frontend/README.md`](../frontend/README.md)
- 推荐服务配置：[`../recommend-service/environment.yml`](../recommend-service/environment.yml)

## License

本后端服务采用 MIT License，详见根目录 [`../LICENSE`](../LICENSE)。
