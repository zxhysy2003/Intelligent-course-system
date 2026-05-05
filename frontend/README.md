# Course System Frontend

一个基于 Vue 3 + Vite 的课程系统前端项目，面向普通用户与管理员两类角色，覆盖课程浏览、课程推荐、学习分析、知识图谱以及后台课程与用户管理等核心流程。

## 功能概览

- 用户侧：登录、注册、课程列表、课程详情、视频学习、推荐课程、学习进度、能力雷达图、知识图谱、个人资料页
- 管理侧：课程管理、课程新增、课程编辑、课程上下线、批量删除、用户管理、用户信息编辑
- 权限控制：未登录用户会被重定向到登录页；`/admin` 路由仅允许 `ADMIN` 角色访问

## 技术栈

- Vue 3
- Vite
- Vue Router
- Pinia
- Element Plus
- Axios
- ECharts
- Windi CSS

## 项目结构

```text
src/
  api/         # 后端接口封装
  assets/      # 静态资源
  router/      # 路由与导航守卫
  store/       # Pinia 状态管理
  utils/       # 工具函数
  views/
    user/      # 用户端页面
    admin/     # 管理端页面
public/        # 公开静态资源
```

## 本地开发

### 环境要求

- Node.js 18+
- npm 9+
- 可访问的后端服务，默认地址为 `http://localhost:8080`

### 安装依赖

```bash
npm install
```

### 启动开发环境

```bash
npm run dev
```

Vite 开发服务器会读取 `vite.config.js`，并将以下请求代理到后端：

- `/api` -> `http://localhost:8080`
- `/videos` -> `http://localhost:8080`

### 构建与预览

```bash
npm run build
npm run preview
```

## 接口与认证说明

- Axios 实例统一定义在 `src/api/request.js`
- 前端默认以 `/api` 作为接口前缀
- 登录成功后的 JWT 会写入 `localStorage`
- 请求拦截器会自动附带 `Authorization: Bearer <token>`

## 开发约定

- 页面组件使用 Vue SFC 与 `<script setup>`
- `src` 下的模块导入优先使用 `@` 别名
- 页面组件使用 PascalCase 命名，例如 `CourseManage.vue`
- API、store、工具模块使用 camelCase 命名
- 当前仓库未配置独立测试脚本，功能变更后请至少手动验证登录、课程、推荐、分析和后台管理流程

## 许可证

本项目采用 [MIT License](./LICENSE)。

## 声明

本项目为本人毕业设计项目，主要用于学习、课程设计展示与技术交流。

项目中如涉及第三方框架、依赖库、图片、图标、数据集或其他资源，其版权归原作者或原版权方所有。相关内容的使用须遵循各自对应的许可证或使用条款。