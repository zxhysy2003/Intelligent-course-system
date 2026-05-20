# 仓库协作指南

## 项目整体结构

本仓库是智能课程系统的 monorepo，根目录只维护跨模块约定和总入口：

- `frontend/`：Vue 3 + Vite 前端，具体规则见 `frontend/AGENTS.md`。
- `backend/`：Spring Boot 3 / Java 17 后端，具体规则见 `backend/AGENTS.md`。
- `recommend-service/`：FastAPI 协同过滤推荐服务，说明见 `recommend-service/README.md`。
- `scripts/`：本地依赖、数据库初始化和一键开发脚本。
- `docs/`：部署、运行和操作手册。

跨模块改动前，先确认接口、配置、数据库和页面调用链，避免只改其中一端。

## 常用开发命令

- `cd scripts && docker compose up -d`：启动 MySQL、Redis、Neo4j。
- `./scripts/dev.sh` 或 `./scripts/dev.bash`：同时启动推荐服务、后端和前端。
- `cd backend && ./mvnw -q -DskipTests compile`：后端快速编译检查。
- `cd backend && ./mvnw test`：运行后端测试。
- `cd frontend && npm install && npm run build`：安装依赖并验证前端构建。
- `cd recommend-service && uvicorn main:app --reload --host 127.0.0.1 --port 8000`：单独启动推荐服务。

## 全局协作原则

- 优先阅读对应模块的 README 和 AGENTS 指南，再修改代码。
- 保持改动范围清晰，不顺手重构无关模块。
- 涉及接口字段、数据库表、环境变量或路由时，同步检查所有调用方。
- 不提交真实密钥、生产地址、用户隐私数据或本地临时配置。
- 工作区已有改动默认视为他人改动，不要擅自回退。

## 测试与验证

后端业务改动优先补充 `backend/src/test/java` 下的 JUnit 测试。前端暂无测试脚本，至少执行 `npm run build` 并手动检查受影响页面。推荐服务改动需启动服务并验证 `/model/status` 或 `/recommend`。跨模块功能应按真实链路验证：前端请求、后端接口、数据库/缓存、推荐服务返回。

## 提交与 PR

提交信息遵循 Conventional Commits，例如 `feat(recommend): add cold-start flow`、`fix(dev): repair startup script`、`docs: update operation guide`。PR 描述应包含变更目的、影响模块、配置或表结构变化、验证方式；UI 改动附截图或录屏。
