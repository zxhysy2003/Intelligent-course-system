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
- 课程浏览、分类筛选、选课和视频学习
- 视频断点续播、学习行为记录和课程热度同步
- 个性化推荐、用户冷启动推荐、新课曝光和混合推荐
- Neo4j 知识图谱展示和课程知识点关系查询
- 学习进度图表、能力雷达图和个人中心
- 后台课程管理、视频上传和用户管理

## 快速开始

完整系统建议按下面顺序启动：

1. 启动 `backend/docker-compose.yml` 中的 MySQL、Redis、Neo4j
2. 导入 `backend/course_db.sql` 初始化数据库
3. 启动 `recommend-service`
4. 启动 `backend`
5. 启动 `frontend`

详细步骤见 [docs/OPERATION_MANUAL.md](./docs/OPERATION_MANUAL.md)。

## 常用命令

### 后端依赖

```bash
cd backend
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
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 ./mvnw spring-boot:run
```

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
