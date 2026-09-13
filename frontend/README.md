# Course System Frontend

智能课程系统的 Vue 3 前端，面向学生和管理员，负责页面展示、交互和登录态管理。默认运行在 `http://127.0.0.1:5173`，通过 Vite 代理访问后端，不直接调用推荐服务。

## 功能概览

- 学生端：登录注册、新用户引导、课程与视频学习、推荐、学习分析、知识图谱和学习助手
- 管理端：课程维护、视频上传和用户管理
- 登录与角色路由控制；管理页面仅允许管理员访问

## 技术与目录

Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts；测试使用 Vitest。

| 目录 | 用途 |
| --- | --- |
| `src/views`、`src/layouts` | 学生端、管理端页面与布局 |
| `src/features` | 功能组件、composable 和测试 |
| `src/api`、`src/store`、`src/router` | 接口、状态和路由 |
| `src/utils`、`src/services` | 通用工具与通知等服务 |

## 本地开发

需要 Node.js `^20.19.0` 或 `>=22.12.0`（项目 Volta 固定为 `22.22.2`）和 npm。先按 [操作手册](../docs/OPERATION_MANUAL.md) 启动后端及其依赖，再从仓库根目录执行：

```bash
cd frontend
npm install
npm run dev
```

默认代理到 `http://localhost:8080`，可通过 `VITE_BACKEND_TARGET` 覆盖。

在 `frontend` 目录执行 `npm run check` 完成 lint、测试和生产构建；`npm run preview` 预览构建产物。

## 详细文档

- [前端开发与联调手册](../docs/FRONTEND_GUIDE.md)：认证、代理、路由、接口字段和排障
- [总操作手册](../docs/OPERATION_MANUAL.md)：环境准备与完整联调
- [学习助手模块](../docs/agent-module.md)
- [前端协作指南](AGENTS.md)
- [项目总览](../README.md)

## License

采用 MIT License，详见 [LICENSE](../LICENSE)。
