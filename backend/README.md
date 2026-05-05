# Course System

智能课程学习系统后端项目，基于 Spring Boot 3 开发，提供课程管理、学习行为记录、学习分析、知识图谱展示和个性化推荐等能力。

该仓库当前主要包含后端服务代码，不包含完整前端工程。推荐功能依赖独立的 Python 推荐服务，后端通过 HTTP 调用该服务获取推荐结果。

## 系统组成

当前完整系统由三个仓库共同组成：

- 后端服务（当前仓库）：`https://github.com/zxhysy2003/course-system`
- 前端项目：`https://github.com/zxhysy2003/course-system-frontend`
- Python 推荐服务：`https://github.com/zxhysy2003/recommend_service`

其中，本仓库负责课程、用户、学习行为、学习分析、知识图谱和推荐结果融合等后端业务逻辑；前端仓库负责页面展示与交互；推荐服务仓库负责基于 Python 的推荐算法计算。

## 项目特点

- 支持用户注册、登录与 JWT 鉴权
- 支持课程查询、选课、视频学习与断点续学
- 支持后台课程管理与视频上传
- 支持学习行为记录、学习进度分析和能力雷达图
- 支持基于 Neo4j 的知识图谱展示与学习路径辅助
- 支持基于 `surprise` 库 `SVD` 算法的个性化推荐

## 技术栈

- Java 17
- Spring Boot 3.2.5
- Spring MVC
- MyBatis-Plus
- MySQL 8
- Redis 7
- Neo4j 5
- JWT
- MapStruct
- Maven

## 项目结构

```text
course-system
├── src/main/java/com/sy/course_system
│   ├── common
│   ├── config
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── graph
│   ├── mapper
│   ├── repository
│   ├── service
│   └── vo
├── src/main/resources
│   ├── application.yaml
│   ├── application-dev.yaml
│   ├── application-prod.yaml
│   └── mapper
├── course_db.sql
├── docker-compose.yml
└── docs
```

## 快速开始

如果你只打算运行当前后端仓库，可以直接按下面步骤完成环境准备和后端启动。如果你要联调完整系统，建议同时准备前端仓库和 Python 推荐服务仓库。

### 1. 准备依赖环境

请先准备以下环境：

- JDK 17
- MySQL 8
- Redis 7
- Neo4j 5
- ffprobe

也可以直接使用仓库中的 Docker Compose 启动依赖服务：

```bash
docker compose up -d
```

### 2. 初始化数据库

```bash
mysql -u dev -p course_db < course_db.sql
```

### 3. 配置环境变量

常用配置包括：

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `NEO4J_URI`
- `NEO4J_USERNAME`
- `NEO4J_PASSWORD`
- `RECOMMEND_SERVICE_URL`
- `VIDEO_DIR`
- `VIDEO_BASE_URL`
- `FFPROBE_PATH`

### 4. 启动项目

```bash
./mvnw spring-boot:run
```

或先打包再运行：

```bash
./mvnw clean package
java -jar target/course-system-0.0.1-SNAPSHOT.jar
```

服务默认运行在 `http://127.0.0.1:8080`。

### 5. 联调整个系统

如果你希望运行完整系统，建议按下面顺序启动：

1. 启动 MySQL、Redis、Neo4j 等基础依赖
2. 启动推荐服务仓库：`https://github.com/zxhysy2003/recommend_service`
3. 启动当前后端仓库
4. 启动前端仓库：`https://github.com/zxhysy2003/course-system-frontend`

前端仓库和推荐服务仓库的具体安装方式、依赖说明和运行命令，请以它们各自仓库中的 README 为准。

## 重要说明

### 1. 推荐服务是外部依赖

本项目的推荐模块依赖一个独立的 Python 服务。推荐算法使用 `surprise` 库中的 `SVD` 算法，默认通过 `POST /recommend` 提供服务。该服务仓库地址为：

- `https://github.com/zxhysy2003/recommend_service`

### 2. Neo4j 配置需要自行确认

当前 [docker-compose.yml](./docker-compose.yml) 中 Neo4j 使用的是无认证配置，而后端默认按用户名密码连接。实际使用前，请先统一这两边的配置，否则后端可能无法连接 Neo4j。

### 3. 视频目录需要自行指定

请务必通过 `VIDEO_DIR` 指定一个可读写的视频存储目录，否则视频上传与播放功能无法正常工作。

## 常用接口

大部分接口都需要在请求头中携带 JWT：

```text
Authorization: Bearer <your_token>
```

- `POST /user/register` 用户注册
- `POST /user/login` 用户登录
- `GET /user/profile` 获取当前用户信息
- `POST /course/list` 获取课程列表
- `GET /course/attend/{courseId}` 选课
- `POST /behavior/record` 记录学习行为
- `GET /recommend/hybrid` 获取融合推荐结果
- `GET /analysis/progress` 获取学习进度分析
- `GET /analysis/ability-radar` 获取能力雷达图
- `GET /analysis/knowledge-graph` 获取知识图谱

## 文档

- 详细操作手册见 [docs/OPERATION_MANUAL.md](./docs/OPERATION_MANUAL.md)
- 前端仓库：`https://github.com/zxhysy2003/course-system-frontend`
- 推荐服务仓库：`https://github.com/zxhysy2003/recommend_service`

## License

本项目采用 MIT License 开源，具体内容请见仓库根目录下的 [LICENSE](./LICENSE) 文件。

## 声明

本项目为本人毕业设计项目，主要用于学习、课程设计展示与技术交流。

项目中如涉及第三方框架、依赖库、图片、图标、数据集或其他资源，其版权归原作者或原版权方所有。相关内容的使用须遵循各自对应的许可证或使用条款。
