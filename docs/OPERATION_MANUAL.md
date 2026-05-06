# Intelligent Course System 操作手册

本项目是一个智能课程学习系统，当前仓库采用前后端与推荐服务同仓组织方式，包含 `backend`、`frontend` 和 `recommend-service` 三个服务。

完整系统的本地联调通常需要同时运行：

- `backend`：Spring Boot 后端服务，负责用户、课程、学习行为、学习分析、知识图谱和推荐结果融合，默认端口 `8080`
- `frontend`：Vue 3 + Vite 前端服务，负责用户端与管理端页面，默认开发端口 `5173`
- `recommend-service`：FastAPI 推荐服务，负责基于学习行为评分生成协同过滤候选课程，默认端口 `8000`

## 1. 仓库结构

```text
Intelligent-course-system
├── backend
│   ├── src/main/java/com/sy/course_system
│   ├── src/main/resources
│   ├── course_db.sql
│   ├── docker-compose.yml
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
└── docs
    └── OPERATION_MANUAL.md
```

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

backend 同时依赖 MySQL、Redis、Neo4j 和本地视频目录。
```

推荐启动顺序：

1. 启动 MySQL、Redis、Neo4j 等基础依赖
2. 初始化 MySQL 数据库
3. 启动 `recommend-service`
4. 启动 `backend`
5. 启动 `frontend`

## 3. 环境要求

### 3.1 后端

- JDK 17
- Maven 3.9+，或使用 `backend/mvnw`
- MySQL 8
- Redis 7
- Neo4j 5
- ffprobe

### 3.2 前端

- Node.js 18+
- npm 9+

项目 `frontend/package.json` 中配置了 Volta Node 版本 `22.22.2`。如果本机使用 Volta，可以直接让 Volta 接管 Node 版本；否则使用 Node.js 18+ 即可。

### 3.3 推荐服务

- Conda 或兼容的 Python 环境管理工具
- Python 3.9

推荐服务依赖定义在 `recommend-service/environment.yml`，其中包含 FastAPI、Uvicorn、Pandas、SciPy、scikit-surprise 等包。

## 4. 启动基础依赖

基础依赖的 Docker Compose 文件位于 `backend/docker-compose.yml`。

```bash
cd backend
docker compose up -d
docker compose ps
```

停止依赖服务：

```bash
cd backend
docker compose down
```

如果需要同时删除本地数据卷：

```bash
cd backend
docker compose down -v
```

### 4.1 Docker 默认账号

| 服务 | 地址 | 默认账号 | 默认密码 |
| --- | --- | --- | --- |
| MySQL | `127.0.0.1:3306` | `dev` | `dev123` |
| Redis | `127.0.0.1:6379` | 无用户名 | `redis123` |
| Neo4j HTTP | `http://127.0.0.1:7474` | 见下方说明 | 见下方说明 |
| Neo4j Bolt | `bolt://127.0.0.1:7687` | 见下方说明 | 见下方说明 |

### 4.2 Neo4j 认证注意事项

当前 `backend/docker-compose.yml` 中 Neo4j 配置为：

```yaml
NEO4J_AUTH: none
```

但 `backend/src/main/resources/application.yaml` 默认按用户名密码连接：

```yaml
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=neo4j123
```

因此首次联调前需要统一 Neo4j 的认证方式。推荐做法是把 `backend/docker-compose.yml` 中的 Neo4j 环境变量改为：

```yaml
NEO4J_AUTH: neo4j/neo4j123
```

然后重新创建 Neo4j 容器和数据卷：

```bash
cd backend
docker compose down -v
docker compose up -d
```

如果你希望继续使用无认证 Neo4j，则需要同步调整后端 Neo4j 连接配置，保证后端配置与容器实际认证方式一致。

## 5. 初始化数据库

### 5.1 创建数据库

如果使用 `backend/docker-compose.yml` 启动 MySQL，数据库 `course_db` 会自动创建。若使用本机 MySQL，可以手动执行：

```sql
CREATE DATABASE course_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 5.2 导入初始化脚本

从仓库根目录执行：

```bash
mysql -u dev -p course_db < backend/course_db.sql
```

使用 Docker MySQL 时，也可以从仓库根目录执行：

```bash
docker exec -i mysql8-dev mysql -udev -pdev123 course_db < backend/course_db.sql
```

`backend/course_db.sql` 包含表结构和部分初始化测试数据。

## 6. 配置说明

### 6.1 后端环境变量

后端主配置文件位于 `backend/src/main/resources/application.yaml`。

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
| `RECOMMEND_SERVICE_URL` | 推荐服务基础地址 | `http://localhost:8000` |
| `VIDEO_DIR` | 视频文件存储目录 | 本机开发绝对路径 |
| `VIDEO_BASE_URL` | 视频访问基础地址 | `http://localhost:8080` |
| `FFPROBE_PATH` | ffprobe 可执行文件路径 | `/opt/homebrew/bin/ffprobe` |
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
export VIDEO_BASE_URL=http://127.0.0.1:8080
export FFPROBE_PATH=/usr/bin/ffprobe
```

### 6.2 前端接口配置

前端 Axios 默认以 `/api` 作为接口前缀，Vite 代理配置位于 `frontend/vite.config.js`：

```text
/api    -> http://localhost:8080
/videos -> http://localhost:8080
```

如果后端端口或地址发生变化，需要同步修改 `frontend/vite.config.js` 中的代理目标。

### 6.3 推荐服务配置

推荐服务默认通过以下命令监听 `127.0.0.1:8000`：

```bash
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

后端会调用：

```text
POST ${RECOMMEND_SERVICE_URL}/recommend
```

因此只要后端的 `RECOMMEND_SERVICE_URL` 与 FastAPI 实际监听地址一致即可。

## 7. 启动 recommend-service

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

推荐接口测试：

```bash
curl -X POST "http://127.0.0.1:8000/recommend" \
  -H "Content-Type: application/json" \
  -d '{
    "targetUserId": 1,
    "topN": 3,
    "data": [
      { "userId": 1, "courseId": 1, "score": 9.0 },
      { "userId": 1, "courseId": 2, "score": 7.5 },
      { "userId": 2, "courseId": 2, "score": 8.0 },
      { "userId": 2, "courseId": 3, "score": 9.5 },
      { "userId": 3, "courseId": 3, "score": 8.5 },
      { "userId": 3, "courseId": 4, "score": 9.0 }
    ]
  }'
```

正常情况下会返回：

```json
{
  "userId": 1,
  "items": [
    {
      "courseId": 3,
      "score": 0.0
    }
  ]
}
```

实际 `score` 会随模型训练结果变化，只要响应结构包含 `userId` 和 `items` 即表示接口可用。

## 8. 启动 backend

进入后端目录：

```bash
cd backend
```

编译检查：

```bash
./mvnw -q -DskipTests compile
```

启动后端服务：

```bash
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 ./mvnw spring-boot:run
```

如果需要显式指定本地依赖地址：

```bash
DB_HOST=127.0.0.1 \
REDIS_HOST=127.0.0.1 \
NEO4J_URI=bolt://127.0.0.1:7687 \
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 \
VIDEO_DIR=/data/course_videos \
VIDEO_BASE_URL=http://127.0.0.1:8080 \
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

本地预览构建结果：

```bash
npm run preview
```

## 10. 完整联调检查

三个服务都启动后，建议按下面顺序检查：

1. 浏览器打开 `http://127.0.0.1:5173`
2. 前端登录或注册是否正常
3. 后端 `http://127.0.0.1:8080/actuator/health` 是否可访问
4. 推荐服务 `http://127.0.0.1:8000/docs` 是否可访问
5. 前端课程列表、课程详情、选课、视频学习是否正常
6. 推荐页是否能返回推荐课程
7. 学习进度、能力雷达图、知识图谱页面是否正常渲染
8. 管理端课程管理、用户管理、视频上传流程是否正常

## 11. 常用接口示例

多数后端业务接口需要登录后访问。登录成功后，将 JWT 放入请求头：

```text
Authorization: Bearer <your_token>
```

### 11.1 注册

```bash
curl -X POST "http://127.0.0.1:8080/user/register" \
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
curl -X POST "http://127.0.0.1:8080/user/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "123456"
  }'
```

### 11.3 获取当前用户信息

```bash
curl "http://127.0.0.1:8080/user/profile" \
  -H "Authorization: Bearer <your_token>"
```

### 11.4 课程分页查询

```bash
curl -X POST "http://127.0.0.1:8080/course/list" \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "page": 1,
    "pageSize": 9
  }'
```

### 11.5 选课

```bash
curl "http://127.0.0.1:8080/course/attend/1" \
  -H "Authorization: Bearer <your_token>"
```

### 11.6 记录学习行为

```bash
curl -X POST "http://127.0.0.1:8080/behavior/record?courseId=1&behaviorType=STUDY&duration=300" \
  -H "Authorization: Bearer <your_token>"
```

### 11.7 获取融合推荐结果

```bash
curl "http://127.0.0.1:8080/recommend/hybrid" \
  -H "Authorization: Bearer <your_token>"
```

### 11.8 获取学习分析数据

```bash
curl "http://127.0.0.1:8080/analysis/progress?days=30" \
  -H "Authorization: Bearer <your_token>"
```

```bash
curl "http://127.0.0.1:8080/analysis/ability-radar" \
  -H "Authorization: Bearer <your_token>"
```

```bash
curl "http://127.0.0.1:8080/analysis/knowledge-graph?courseId=1&depth=3" \
  -H "Authorization: Bearer <your_token>"
```

## 12. 视频功能说明

系统支持后台上传课程视频，并通过 `/videos/**` 暴露静态访问路径。

使用视频功能前请确认：

- `VIDEO_DIR` 指向真实存在或可创建的目录
- 当前运行后端服务的系统用户对该目录拥有读写权限
- 本机已安装 `ffprobe`
- `FFPROBE_PATH` 指向正确的 ffprobe 可执行文件
- `VIDEO_BASE_URL` 与实际后端访问地址一致

上传成功后，后端会读取视频时长并回写到数据库。

## 13. 常见问题

### 13.1 后端连接不上 MySQL

请检查：

- MySQL 是否已经启动
- 数据库名是否为 `course_db`
- 用户名和密码是否与配置一致
- `backend/course_db.sql` 是否已经导入
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
- `NEO4J_AUTH` 与后端的 `NEO4J_USERNAME`、`NEO4J_PASSWORD` 是否一致
- 如果修改过认证方式，是否重新创建过 Neo4j 数据卷

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
- `VIDEO_BASE_URL` 是否与实际访问地址一致

### 13.7 Conda 创建推荐服务环境失败

请检查：

- Conda 是否可用
- 当前网络是否能够访问 `environment.yml` 中配置的镜像源
- `scikit-surprise` 编译依赖是否安装完整
- 如果本机 Python 环境复杂，优先使用新的 Conda 环境重新安装

## 14. 开发与验证命令汇总

### 14.1 后端

```bash
cd backend
./mvnw -q -DskipTests compile
./mvnw test
./mvnw spring-boot:run
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
- 将 `VIDEO_DIR` 指向持久化存储目录
- 为后端、前端和推荐服务配置统一的反向代理
- 为 FastAPI 推荐服务增加进程守护，例如 systemd、Docker 或 Supervisor
- 为后端和推荐服务增加日志收集与监控
- 明确前端生产构建产物的部署路径和后端 API 地址
