# Intelligent Course System

智能课程学习系统是面向在线课程学习场景的毕业设计项目，包含 Vue 前端、Spring Boot 后端和 FastAPI 推荐服务。

## 核心功能

- 注册登录、新用户学习画像引导和角色权限控制
- 课程浏览、选课、视频学习和学习行为记录
- 个性化推荐、冷启动推荐与推荐来源展示
- 学习分析、知识图谱和只读学习助手
- 后台课程、视频和用户管理

## 项目组成

| 模块 | 职责 | 默认端口 |
| --- | --- | --- |
| [frontend](frontend/README.md) | 学生端和管理端页面 | `5173` |
| [backend](backend/README.md) | 鉴权、课程、学习数据和推荐融合 | `8080` |
| [recommend-service](recommend-service/README.md) | 基于 SVD 的协同过滤候选召回 | `8000` |

```text
浏览器 → frontend → backend → recommend-service
                       │              │
                       ├─ MySQL ←─────┘
                       ├─ Redis
                       ├─ Neo4j
                       └─ 视频目录
```

前端通过后端访问业务数据；推荐服务直连 MySQL 读取后端维护的评分快照。Redis、Neo4j 和视频目录由后端使用。

## 快速开始

首次运行先按 [操作手册](docs/OPERATION_MANUAL.md) 安装依赖、初始化数据库并配置根目录 `.env.local`。完成准备后，在仓库根目录执行：

```bash
./scripts/dev.sh
# 没有 zsh 时使用 ./scripts/dev.bash
```

默认前端入口为 `http://127.0.0.1:5173`。一键脚本参数及演示步骤见 [操作手册](docs/OPERATION_MANUAL.md#一键开发启动与演示)。使用 Docker 启动完整系统见 [本机 Docker 部署手册](docs/DOCKER_LOCAL_DEPLOY.md)。

## 文档入口

- [文档索引](docs/README.md)：操作手册、开发参考与模块说明
- [高并发实验](experiments/README.md)：实验方案、工具和结果
- [仓库协作指南](AGENTS.md)：跨模块修改与验证约定

README 负责概览、最短启动入口和文档导航；配置、接口、排障与操作步骤放在手册中，模块设计放在模块说明中。

## License

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
