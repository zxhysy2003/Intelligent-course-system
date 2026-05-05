# Course System 操作手册

智能课程学习系统后端项目，基于 Spring Boot 3 开发，提供课程管理、用户管理、学习行为记录、学习分析、知识图谱展示和课程推荐等能力。

本仓库当前主要包含后端服务代码，不包含完整前端工程。推荐系统依赖一个独立的 Python 推荐服务，后端通过 HTTP 调用该服务完成推荐结果获取。

## 0. 仓库关系说明

当前实际系统由三个仓库共同组成：

- 后端服务仓库（当前仓库）：`https://github.com/zxhysy2003/course-system`
- 前端仓库：`https://github.com/zxhysy2003/course-system-frontend`
- Python 推荐服务仓库：`https://github.com/zxhysy2003/recommend_service`

三者之间的职责划分如下：

- 当前仓库负责课程、用户、学习行为、学习分析、知识图谱和推荐结果融合等后端业务逻辑
- 前端仓库负责系统页面展示、交互逻辑和接口调用
- Python 推荐服务仓库负责推荐算法计算，并向后端提供推荐结果接口

因此，如果你只是想单独运行后端服务，可以只参考本手册；如果你要完整体验系统功能，则需要将三个仓库配合启动。

## 1. 项目简介

该项目面向在线课程学习场景，核心目标是在传统课程平台功能的基础上，加入一定的智能学习辅助能力。系统支持用户注册登录、课程查询、选课学习、课程视频管理、学习进度记录、学习能力分析、知识图谱展示和个性化推荐等功能。

项目中的“智能”主要体现在以下几个方面：

- 基于学习行为数据生成隐式评分
- 调用 Python 推荐服务，使用 `surprise` 库中的 `SVD` 算法生成个性化推荐候选
- 结合 Neo4j 知识图谱中的知识点、先修关系和掌握度，对推荐结果进行二次融合
- 提供知识图谱、学习进度图和能力雷达图等学习分析能力

## 2. 技术栈

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
- ffprobe

## 3. 项目结构

```text
course-system
├── src/main/java/com/sy/course_system
│   ├── common        # 通用返回体、上下文、工具类
│   ├── config        # JWT、Redis、Neo4j、Web 配置
│   ├── controller    # 前台/后台接口
│   ├── dto           # 请求/响应传输对象
│   ├── entity        # 实体类
│   ├── enums         # 枚举定义
│   ├── graph         # Neo4j 节点对象
│   ├── mapper        # MySQL Mapper
│   ├── repository    # Neo4j Repository
│   ├── service       # 业务接口
│   └── vo            # 页面展示对象
├── src/main/resources
│   ├── application.yaml
│   ├── application-dev.yaml
│   ├── application-prod.yaml
│   └── mapper        # MyBatis XML
├── course_db.sql     # 数据库表结构和初始化数据
├── docker-compose.yml
└── README.md
```

## 4. 核心功能

### 4.1 用户与权限

- 用户注册、登录
- JWT 鉴权
- 获取当前登录用户信息
- 后台用户管理

### 4.2 课程与学习

- 课程分页查询、详情查看、分类查看
- 用户选课
- 视频地址获取与断点续学
- 后台课程增删改查
- 课程视频上传

### 4.3 智能学习功能

- 学习行为记录
- 热门课程统计
- 个性化课程推荐
- 知识图谱展示
- 学习进度分析
- 能力雷达图分析

## 5. 运行前准备

### 5.1 软件要求

建议本地至少准备以下环境：

- JDK 17
- Maven 3.9+，或者直接使用项目自带的 `mvnw`
- MySQL 8
- Redis 7
- Neo4j 5
- ffprobe

如果你使用 Docker，也可以直接启动仓库里的依赖服务。

如果你要运行完整系统，除了本仓库外，还需要准备下面两个仓库：

- 前端仓库：`https://github.com/zxhysy2003/course-system-frontend`
- Python 推荐服务仓库：`https://github.com/zxhysy2003/recommend_service`

### 5.2 重要说明

在首次运行前，建议先阅读下面几个配置点：

1. `application.yaml` 中默认的视频目录是本地开发机路径，公开使用时请务必通过环境变量重新指定 `VIDEO_DIR`。
2. 推荐服务默认地址为 `http://localhost:8000`，该服务不在本仓库内，需要你自行准备。
3. `docker-compose.yml` 中 Neo4j 当前配置为 `NEO4J_AUTH: none`，但后端默认按用户名密码连接 Neo4j。直接使用前，请先调整其中一种方式：

- 方式一：修改 `docker-compose.yml`，将 Neo4j 改为开启账号密码，例如 `NEO4J_AUTH=neo4j/neo4j123`
- 方式二：同步修改后端配置，保证后端与 Neo4j 实际认证方式一致

如果不处理这一点，后端可能无法正常连接 Neo4j。

同时需要注意，前端仓库和推荐服务仓库也各自有独立的运行环境和配置项。联调前请分别检查它们的 README，并确保前端调用的后端地址、后端调用的推荐服务地址保持一致。

## 6. 配置说明

项目主配置文件位于 `src/main/resources/application.yaml`。

### 6.1 常用环境变量

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
| `NEO4J_URI` | Neo4j 地址 | `bolt://localhost:7687` |
| `NEO4J_USERNAME` | Neo4j 用户名 | `neo4j` |
| `NEO4J_PASSWORD` | Neo4j 密码 | `neo4j123` |
| `RECOMMEND_SERVICE_URL` | 推荐服务地址 | `http://localhost:8000` |
| `VIDEO_DIR` | 视频存储目录 | 本地开发绝对路径 |
| `VIDEO_BASE_URL` | 视频访问基础地址 | `http://localhost:8080` |
| `FFPROBE_PATH` | ffprobe 路径 | 平台相关默认值 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | CORS 允许来源 | 本地开发前端地址 |

### 6.2 开发环境示例

Linux / macOS:

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

## 7. 数据库初始化

### 7.1 创建 MySQL 数据库

```sql
CREATE DATABASE course_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 7.2 导入初始化脚本

```bash
mysql -u dev -p course_db < course_db.sql
```

如果你使用 Docker 启动 MySQL，也可以执行：

```bash
docker exec -i mysql8-dev mysql -udev -pdev123 course_db < course_db.sql
```

`course_db.sql` 中已经包含表结构和一部分初始化测试数据。

## 8. 使用 Docker 启动依赖服务

### 8.1 启动

```bash
docker compose up -d
```

### 8.2 查看状态

```bash
docker compose ps
```

### 8.3 停止

```bash
docker compose down
```

如果你需要删除持久化数据卷：

```bash
docker compose down -v
```

### 8.4 Docker 模式下的注意事项

- MySQL 默认用户为 `dev`，密码为 `dev123`
- Redis 默认密码为 `redis123`
- Neo4j 认证配置需要与你的后端配置保持一致

## 9. 启动项目

### 9.1 编译检查

```bash
./mvnw -q -DskipTests compile
```

### 9.2 本地运行

```bash
./mvnw spring-boot:run
```

如果你希望显式指定开发环境：

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

### 9.3 打包运行

```bash
./mvnw clean package
java -jar target/course-system-0.0.1-SNAPSHOT.jar
```

服务默认端口为 `8080`。

## 10. 多仓库联调说明

如果你要运行完整系统，建议按下面顺序启动三个仓库：

### 10.1 第一步：启动基础依赖

先启动 MySQL、Redis 和 Neo4j，保证后端和推荐服务所需的基础环境可用。

```bash
docker compose up -d
```

### 10.2 第二步：启动 Python 推荐服务

推荐服务仓库地址：

- `https://github.com/zxhysy2003/recommend_service`

后端默认通过 `RECOMMEND_SERVICE_URL` 调用该服务，默认地址为 `http://localhost:8000`。因此，推荐服务启动后，应确保它的实际监听地址与后端配置保持一致。

推荐服务的 Python 环境、依赖安装和启动命令，请以该仓库自身的 README 为准。

### 10.3 第三步：启动当前后端仓库

在基础依赖和推荐服务就绪后，再启动当前后端服务：

```bash
./mvnw spring-boot:run
```

### 10.4 第四步：启动前端仓库

前端仓库地址：

- `https://github.com/zxhysy2003/course-system-frontend`

前端启动后，需要确保其接口请求地址正确指向当前后端服务地址，例如本地开发场景下常见的 `http://localhost:8080`。

前端项目的 Node.js 依赖安装、环境变量和运行命令，请以该仓库自身的 README 为准。

### 10.5 联调检查建议

三个仓库都启动完成后，建议至少检查以下内容：

- 前端能否正常访问登录接口
- 后端能否正常连接 MySQL、Redis 和 Neo4j
- 后端调用推荐服务时是否返回正常结果
- 前端推荐页面是否能正确展示推荐数据
- 知识图谱、学习进度和能力分析接口是否能够正常返回数据

## 11. 推荐服务说明

本项目的推荐模块依赖外部 Python 服务。后端会把用户课程隐式评分数据发送给推荐服务，再由推荐服务返回候选课程列表。

当前约定如下：

- 推荐服务地址由 `RECOMMEND_SERVICE_URL` 指定
- 默认请求地址为 `POST /recommend`
- 推荐算法使用 Python `surprise` 库中的 `SVD` 算法
- 推荐服务仓库地址为 `https://github.com/zxhysy2003/recommend_service`

如果你准备将该项目完整发布给其他人使用，建议一并补充以下内容：

- 推荐服务源码仓库地址
- 推荐服务启动方式
- Python 依赖安装方式
- 推荐接口请求与响应格式

如果暂时没有公开推荐服务，README 中至少应明确说明该部分为外部依赖，否则使用者会在启动推荐接口时遇到调用失败的问题。

## 12. 视频功能说明

系统支持后台上传课程视频，并通过 `/videos/**` 暴露静态访问路径。

使用视频功能前请确认：

- `VIDEO_DIR` 指向一个真实存在或可创建的目录
- 当前运行用户对该目录拥有读写权限
- 本机已安装 `ffprobe`
- `FFPROBE_PATH` 配置正确

上传成功后，系统会自动读取视频时长并回写到数据库。

## 13. 接口使用示例

项目大部分接口都需要登录后访问。登录成功后，请把返回的 JWT 放到请求头中：

```text
Authorization: Bearer <your_token>
```

### 13.1 注册

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

### 13.2 登录

```bash
curl -X POST "http://127.0.0.1:8080/user/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "123456"
  }'
```

### 13.3 获取当前用户信息

```bash
curl "http://127.0.0.1:8080/user/profile" \
  -H "Authorization: Bearer <your_token>"
```

### 13.4 课程分页查询

```bash
curl -X POST "http://127.0.0.1:8080/course/list" \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "page": 1,
    "pageSize": 9
  }'
```

### 13.5 选课

```bash
curl "http://127.0.0.1:8080/course/attend/1" \
  -H "Authorization: Bearer <your_token>"
```

### 13.6 记录学习行为

```bash
curl -X POST "http://127.0.0.1:8080/behavior/record?courseId=1&behaviorType=STUDY&duration=300" \
  -H "Authorization: Bearer <your_token>"
```

### 13.7 获取融合推荐结果

```bash
curl "http://127.0.0.1:8080/recommend/hybrid" \
  -H "Authorization: Bearer <your_token>"
```

### 13.8 获取学习分析数据

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

## 14. 常用接口分组

### 14.1 前台接口

- `/user/*`
- `/course/*`
- `/behavior/*`
- `/recommend/*`
- `/analysis/*`

### 14.2 后台接口

- `/admin/user/*`
- `/admin/course/*`

## 15. 常见问题

### 15.1 项目启动后连接不上 MySQL

请检查以下内容：

- 数据库是否已启动
- 数据库名是否为 `course_db`
- 用户名和密码是否与配置一致
- `course_db.sql` 是否已正确导入

### 15.2 Redis 连接失败

如果你开启了 Redis 密码，请确认 `REDIS_PASSWORD` 与实际配置一致。

### 15.3 Neo4j 连接失败

优先检查：

- `NEO4J_URI` 是否正确
- Neo4j 是否已经启动
- 认证方式是否与后端配置一致
- `docker-compose.yml` 中的 `NEO4J_AUTH` 是否已经按需修改

### 15.4 推荐接口报错

通常是以下原因：

- Python 推荐服务未启动
- `RECOMMEND_SERVICE_URL` 配置错误
- 推荐服务接口路径或请求格式与后端约定不一致
- 推荐服务仓库本身未按其 README 正确完成环境安装

### 15.5 视频上传成功但无法播放

请检查：

- `VIDEO_DIR` 是否配置正确
- 视频文件是否真实存在于对应目录
- 当前服务是否能访问 `/videos/**`
- 上传目录权限是否正确
- `VIDEO_BASE_URL` 是否与实际访问地址一致

### 15.6 前端能启动，但页面请求接口失败

请优先检查：

- 前端仓库中的后端接口地址配置是否正确
- 后端是否已经正常启动
- 后端 CORS 配置是否允许前端当前访问来源
- 浏览器开发者工具中的网络请求是否返回 401、404 或跨域错误

## 16. 开发建议

如果你计划继续完善该项目，比较值得补充的内容包括：

- 前端项目仓库地址与部署说明
- Python 推荐服务仓库地址
- Swagger 或 OpenAPI 文档
- 单元测试和集成测试
- 默认管理员账号说明
- 生产环境部署手册
