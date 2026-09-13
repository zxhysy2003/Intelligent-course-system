# 项目文档

本目录保存操作手册、开发参考和模块说明；项目功能与服务关系见 [项目总览](../README.md)。

## 操作与开发手册

| 文档 | 适用场景 |
| --- | --- |
| [总操作手册](OPERATION_MANUAL.md) | 环境准备、启动、数据库迁移、完整联调、Outbox 运维恢复 |
| [本机 Docker 部署](DOCKER_LOCAL_DEPLOY.md) | 使用 Compose 构建和部署完整系统 |
| [后端开发与接口手册](BACKEND_GUIDE.md) | 后端启动、构建、接口分组和排障 |
| [前端开发与联调手册](FRONTEND_GUIDE.md) | 前端代理、认证、路由、API 字段和排障 |
| [推荐服务开发与接口手册](RECOMMEND_SERVICE_GUIDE.md) | 推荐配置、接口、模型训练与排障 |
| [学习笔记同步到个人博客](STUDY_NOTES_BLOG_SYNC.md) | 笔记导出预览、博客更新与手工修改冲突处理 |

## 模块与学习资料

- [学习助手模块](agent-module.md)
- [Spring Security 鉴权说明](spring-security-authentication.md)
- [学习问答笔记](study-notes/README.md)
- [并发实验](../experiments/concurrency/README.md)

## 文档分工

- 根 README：项目目标、核心功能、服务关系和文档总入口。
- 服务 README：模块职责、主要技术与目录、最短启动方式和常用验证命令。
- 目录 README：该目录的用途、文件索引和必要使用边界。
- 手册：配置清单、接口契约、操作步骤、排障与恢复流程。
- 模块说明：实现流程、关键类、数据模型和业务规则。
- 实验文档：实验假设、负载、观测、断言和结果；不替代正式运行手册。

新增详细内容时优先更新对应手册或模块文档，并从 README 链接进入，避免在多个概述中重复维护。
