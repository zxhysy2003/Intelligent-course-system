# Intelligent Course System

智能课程学习系统是一个面向在线课程学习场景的毕业设计项目，包含前端页面、Spring Boot 后端和 FastAPI 推荐服务。系统支持课程学习、视频播放、学习行为记录、个性化推荐、知识图谱、学习分析，以及后台课程和用户管理。

本仓库采用 monorepo 结构，根目录 README 只作为项目总入口；各服务的安装、配置和开发细节请进入对应服务 README 查看。

## 项目组成

| 服务 | 路径 | 技术栈 | 默认端口 | 说明 |
| --- | --- | --- | --- | --- |
| frontend | [`./frontend`](./frontend) | Vue 3 + Vite | `5173` | 学生端和管理端前端页面 |
| backend | [`./backend`](./backend) | Spring Boot | `8080` | 核心业务后端、鉴权、课程、学习分析和推荐融合 |
| recommend-service | [`./recommend-service`](./recommend-service) | FastAPI | `8000` | 基于 SVD 的协同过滤推荐候选服务 |

## 系统架构

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

## 核心功能

- 用户注册、登录和 JWT 鉴权
- 新用户三步引导：学习基础、学习目标和兴趣方向
- 课程浏览、分类筛选、选课和视频学习
- 视频断点续播、学习行为记录和课程热度同步
- 个性化推荐、用户冷启动推荐、新课曝光和混合推荐，推荐页可核验来源
- 学生端学习助手 Agent：基于课程、推荐、进度和知识图谱提供只读学习建议
- Neo4j 知识图谱展示和课程知识点关系查询
- 学习进度图表、能力雷达图和个人中心
- 后台课程管理、视频上传和用户管理

## Demo 流程

1. 注册或登录一个普通学生账号
2. 首次进入学生端会自动跳到 `/onboarding`
3. 依次选择当前基础、学习目标和至少一个兴趣标签
4. 完成后进入 `/recommend`，推荐卡片会显示推荐分、原因、准备度和来源标签
5. 可继续进入课程详情、播放视频并回写学习行为，再刷新推荐观察结果变化

推荐来源字段由后端 `/recommend/hybrid` 透出为 `recommendSource`：

| 值 | 含义 |
| --- | --- |
| `CF` | 协同过滤候选，经课程状态、已选过滤和图谱准备度加权 |
| `COLD_START_USER` | 新用户或行为不足时，基于引导画像和兴趣标签生成 |
| `COLD_START_COURSE` | 常规链路中的新课注入候选 |
| `HOT_FALLBACK` | CF 和新课候选不可用时的热门课程兜底 |

## 学习助手 Agent

学生端新增 `/agent` 学习助手页面。首版 Agent 由后端 Spring Boot 内置实现，复用已有 JWT 鉴权、课程、推荐、学习分析和知识图谱服务；会话和消息保存在 MySQL 中。默认未配置模型密钥时会使用本地 mock 回答，便于开发演示；接入 OpenAI-compatible 模型时设置：

```bash
AGENT_LLM_API_KEY=your_api_key
AGENT_LLM_BASE_URL=https://api.openai.com/v1
AGENT_LLM_MODEL=gpt-4o-mini
```

Agent 当前只提供学习建议、推荐解释、薄弱点分析和路径建议，不替用户执行选课、收藏或进度修改等写操作。

## 快速开始

完整系统建议按下面顺序启动：

1. 启动 `scripts/docker-compose.yml` 中的 MySQL、Redis、Neo4j
2. 使用 `scripts/course_db.sql` 初始化 MySQL，首次启动 Compose 时会自动导入
3. 使用 `scripts/neo4j-backups/neo4j.dump` 初始化 Neo4j，首次启动 Compose 时会自动恢复
4. 启动 `recommend-service`
5. 启动 `backend`
6. 启动 `frontend`

详细步骤见 [docs/OPERATION_MANUAL.md](./docs/OPERATION_MANUAL.md)。

### 一键启动开发服务

完成各服务依赖安装、数据库初始化和 `scripts/docker-compose.yml` 基础依赖启动后，可在仓库根目录同时启动前端、后端和推荐服务：

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

推荐服务会优先通过 Conda 环境 `lab_autumn` 启动；如需切换可设置 `RECOMMEND_CONDA_ENV`。

常用覆盖项可直接在命令前设置，例如：

```bash
FRONTEND_PORT=5174 BACKEND_PORT=8081 RECOMMEND_PORT=8001 ./scripts/dev.sh
```

## 常用命令

### 后端依赖

```bash
cd scripts
docker compose up -d
```

### 推荐服务

```bash
cd recommend-service
conda env create -f environment.yml
conda activate lab_autumn
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

### 后端服务

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 ./mvnw spring-boot:run
```

`dev` profile 会开启 MyBatis SQL 调试日志；默认配置不打印 SQL，更适合生产或演示环境。

### 前端服务

```bash
cd frontend
npm install
npm run dev
```

## 文档入口

- 总操作手册：[docs/OPERATION_MANUAL.md](./docs/OPERATION_MANUAL.md)
- 后端说明：[backend/README.md](./backend/README.md)
- 前端说明：[frontend/README.md](./frontend/README.md)
- 推荐服务说明：[recommend-service/README.md](./recommend-service/README.md)

## 开发说明

- 根目录负责项目总览和文档入口
- 服务级 README 负责各自的依赖、启动、配置、接口和常见问题
- 前端通过 `/api` 和 `/videos` 代理访问后端
- 后端通过 `RECOMMEND_SERVICE_URL` 调用推荐服务
- 推荐服务不直接访问数据库，评分数据由后端聚合后传入

## License

本项目采用 MIT License，详见 [LICENSE](./LICENSE)。
