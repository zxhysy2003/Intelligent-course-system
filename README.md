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

backend 同时依赖 MySQL、Redis、Neo4j 和本地视频目录；recommend-service 会直连 MySQL 读取推荐评分快照。
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
4. 完成后进入 `/recommendations`，推荐卡片会显示推荐分、原因、准备度和来源标签
5. 可继续进入课程详情、播放视频并回写学习行为，再刷新推荐观察结果变化

推荐来源字段由后端 `GET /api/v1/recommendations` 透出为 `recommendSource`：

| 值 | 含义 |
| --- | --- |
| `CF` | 协同过滤候选，经课程状态、已选过滤和图谱准备度加权 |
| `COLD_START_USER` | 新用户或行为不足时，基于引导画像和兴趣标签生成 |
| `COLD_START_COURSE` | 常规链路中的新课注入候选 |
| `HOT_FALLBACK` | CF 和新课候选不可用时的热门课程兜底 |

## 学习助手 Agent

学生端 `/assistant` 学习助手由后端内置实现，复用课程、推荐、进度和知识图谱数据生成只读学习建议。模型接入和配置项见 [backend/README.md](./backend/README.md)。

## 快速开始

完成依赖安装并启动基础服务后，推荐用一键脚本拉起前端、后端和推荐服务：

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

完整手动启动、Flyway 接管旧库、数据库重建和排查步骤见 [docs/OPERATION_MANUAL.md](./docs/OPERATION_MANUAL.md)。

如果只是想学习部署流程，也可以使用本机 Docker Compose 一次启动前端 Nginx、后端、推荐服务和基础依赖：

```bash
docker compose -f docker-compose.local.yml up -d --build
```

默认访问地址为 `http://localhost:8088`，详细说明见 [docs/DOCKER_LOCAL_DEPLOY.md](./docs/DOCKER_LOCAL_DEPLOY.md)。

## 文档入口

- 总操作手册：[docs/OPERATION_MANUAL.md](./docs/OPERATION_MANUAL.md)
- 本机 Docker 部署：[docs/DOCKER_LOCAL_DEPLOY.md](./docs/DOCKER_LOCAL_DEPLOY.md)
- 后端说明：[backend/README.md](./backend/README.md)
- 前端说明：[frontend/README.md](./frontend/README.md)
- 推荐服务说明：[recommend-service/README.md](./recommend-service/README.md)

## 开发说明

- 根目录负责项目总览和文档入口
- 服务级 README 负责各自的依赖、启动、配置、接口和常见问题
- 前端通过 `/api/v1` 访问版本化 JSON API，通过 `/videos` 访问静态视频
- 后端通过 `RECOMMEND_SERVICE_URL` 调用推荐服务
- 推荐服务直连 MySQL 读取后端维护的 `recommend_user_course_score` 快照表

## License

本项目采用 MIT License，详见 [LICENSE](./LICENSE)。
