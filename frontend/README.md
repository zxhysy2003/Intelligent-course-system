# Course System Frontend

`frontend` 是智能课程学习系统的 Vue 3 前端服务，面向学生用户和管理员两类角色，提供课程学习、个性化推荐、学习分析、知识图谱、课程管理和用户管理等页面。

当前项目采用同仓多服务结构：

- 前端服务：`frontend`
- 后端服务：[`../backend`](../backend)
- FastAPI 推荐服务：[`../recommend-service`](../recommend-service)
- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)

前端默认运行在 `http://127.0.0.1:5173`，通过 Vite 代理访问后端 `http://localhost:8080`。推荐服务不由前端直接调用，而是由后端通过 `RECOMMEND_SERVICE_URL` 转发和融合推荐结果。

## 功能概览

### 学生端

- 登录、注册和 JWT 登录态保持
- 新用户引导：学习基础、学习目标和兴趣标签采集
- 课程列表、分类筛选、课程详情和加入课程
- 视频学习、断点续播、观看行为记录和学习进度回写
- 个性化推荐课程展示，包含推荐分、推荐理由、来源核验、知识点和相关课程跳转
- 学习进度图表、能力雷达图和个人中心
- 知识图谱展示，以及按知识点跳转相关课程

### 管理端

- 课程列表、筛选、上下线和批量删除
- 课程新增、编辑、分类/标签/知识点配置
- 课程视频上传，支持 `mp4`、`webm`、`ogg`
- 用户列表、角色修改、状态修改、删除和编辑

### 权限控制

- 路由使用 `createWebHashHistory()`
- 除 `/login` 和 `/register` 外，其他页面都需要登录
- `/admin/*` 路由仅允许 `role === "ADMIN"` 的用户访问
- 非管理员访问管理页会被重定向到 `/course`
- 普通用户首次进入学生端时会请求 `/onboarding/status`，未完成则重定向到 `/onboarding`

## 技术栈

- Vue 3.5
- Vite 7
- Vue Router 4
- Pinia 3
- Element Plus 2
- Element Plus Icons
- Axios
- ECharts 6
- jwt-decode

## 目录结构

```text
frontend
├── src
│   ├── api                 # 后端接口封装
│   ├── assets              # 静态资源
│   ├── router              # 路由和导航守卫
│   ├── store               # Pinia 状态管理
│   ├── utils               # Cookie、日志等工具
│   └── views
│       ├── admin           # 管理端页面
│       └── user            # 学生端页面
├── public
├── index.html
├── package.json
├── package-lock.json
└── vite.config.js
```

## 环境要求

- Node.js 18+
- npm 9+
- 可访问的后端服务，默认地址为 `http://localhost:8080`

`package.json` 中配置了 Volta Node 版本：

```json
{
  "volta": {
    "node": "22.22.2"
  }
}
```

如果本机使用 Volta，可以直接使用该版本；否则使用 Node.js 18+ 即可。

## 本地开发

下面的命令默认从仓库根目录执行。
进入 `frontend` 后，后续 `npm` 命令均在该目录执行。

### 1. 启动后端和推荐服务

前端依赖后端接口和视频资源。完整联调建议先启动：

1. `../backend` 中的 MySQL、Redis、Neo4j
2. `../recommend-service` FastAPI 推荐服务
3. `../backend` Spring Boot 后端服务

详细步骤见 [`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)。

### 2. 安装依赖

```bash
cd frontend
npm install
```

### 3. 启动开发服务器

```bash
npm run dev
```

默认访问地址：

```text
http://127.0.0.1:5173
```

### 4. 构建和预览

```bash
npm run build
npm run preview
```

当前项目没有配置单独的测试脚本。功能变更后建议至少手动验证登录、课程、视频播放、推荐、分析、知识图谱和后台管理流程。

## 代理配置

代理配置位于 [`vite.config.js`](vite.config.js)：

| 前端路径 | 代理目标 | 说明 |
| --- | --- | --- |
| `/api` | `http://localhost:8080` | 后端业务接口，代理时会去掉 `/api` 前缀 |
| `/videos` | `http://localhost:8080` | 课程视频静态资源 |

Axios 实例定义在 [`src/api/request.js`](src/api/request.js)，默认配置：

- `baseURL: "/api"`
- `timeout: 5000`
- 请求拦截器自动添加 `Authorization: Bearer <token>`

如果后端地址或端口发生变化，需要同步修改 `vite.config.js` 中 `/api` 和 `/videos` 的 `target`。

## 登录态与认证

登录流程：

1. 登录页调用 `POST /user/login`
2. 后端返回 JWT
3. Pinia 用户 store 将 token 写入 `localStorage` 的 `token`
4. `jwt-decode` 从 token 中解析 `userId`、`username` 和 `role`
5. 路由守卫根据登录态和角色控制页面访问
6. Axios 请求自动携带 `Authorization` 请求头

视频播放额外使用 [`src/utils/authCookie.js`](src/utils/authCookie.js) 将 token 临时写入 `auth_token` Cookie，便于浏览器原生 `<video>` 请求 `/videos/**` 时携带鉴权信息。

## 新用户引导

引导页位于 [`src/views/user/Onboarding.vue`](src/views/user/Onboarding.vue)，状态由 [`src/store/onboarding.js`](src/store/onboarding.js) 管理。

流程很短，适合演示：

1. 路由守卫在普通用户访问学生端页面前调用 `GET /onboarding/status`
2. 如果 `completed=false`，跳转到 `/onboarding?redirect=<原目标页>`
3. 页面通过 `GET /onboarding/options` 加载等级、学习目标和可选标签
4. 用户选择 `currentLevel`、可选 `learningGoal`，并至少选择一个 `tagIds`
5. `POST /onboarding/submit` 保存画像，完成后回到原目标页；没有 redirect 时默认进入 `/recommend`

可选值：

| 字段 | 说明 |
| --- | --- |
| `currentLevel` | 必填，`1` 零基础、`2` 入门、`3` 有基础 |
| `learningGoal` | 可空，`JOB`、`PROJECT`、`FOUNDATION`、`EXAM` |
| `tagIds` | 必填，至少一个来自 `options.tags` 的启用标签 |

## 路由说明

路由入口为 [`src/router/index.js`](src/router/index.js)。

### 公共路由

- `/login`：登录
- `/register`：注册
- `/:pathMatch(.*)*`：404

### 学生端路由

- `/course`：课程列表
- `/courseDetail/:id`：课程详情和视频学习
- `/onboarding`：新用户引导
- `/recommend`：个性化推荐
- `/dashboard`：学习进度
- `/graph`：知识图谱
- `/profile`：个人中心

### 管理端路由

- `/admin/course`：课程管理
- `/admin/course/register`：新增课程
- `/admin/course/edit/:id`：编辑课程
- `/admin/users`：用户管理
- `/admin/users/edit/:id`：编辑用户

## API 模块

接口封装位于 `src/api`：

- [`src/api/request.js`](src/api/request.js)：Axios 实例和鉴权拦截器
- [`src/api/user.js`](src/api/user.js)：登录、注册、用户信息和后台用户管理
- [`src/api/course.js`](src/api/course.js)：课程查询、选课、视频、后台课程管理和视频上传
- [`src/api/onboarding.js`](src/api/onboarding.js)：引导选项、状态和提交
- [`src/api/recommend.js`](src/api/recommend.js)：混合推荐
- [`src/api/analysis.js`](src/api/analysis.js)：学习进度、能力雷达图和知识图谱
- [`src/api/learningBehavior.js`](src/api/learningBehavior.js)：学习行为记录

## 推荐页字段

前端只调用 `GET /recommend/hybrid`。推荐卡片依赖后端已经裁剪过的稳定字段：

| 字段 | 用途 |
| --- | --- |
| `courseId`、`title`、`difficulty` | 课程跳转和基础展示 |
| `recommendScore` | 推荐页展示分，前端优先使用该字段 |
| `reason` | 推荐原因文案 |
| `readiness` | 学习准备度进度条，按 0~1 转百分比 |
| `recommendSource` | 来源核验：`CF`、`COLD_START_USER`、`COLD_START_COURSE`、`HOT_FALLBACK` |
| `isNewCourse` | 兼容字段；缺少 `recommendSource` 时用于识别新课注入 |
| `knowledgePoints` | 涵盖知识点 |
| `missingPrerequisitesMastery` | 薄弱前置项 |
| `learningPaths` | 建议学习路径 |

## 开发约定

- 页面组件使用 Vue SFC 和 `<script setup>`
- `src` 下模块导入优先使用 `@` 别名
- 用户端页面放在 `src/views/user`
- 管理端页面放在 `src/views/admin`
- API 封装放在 `src/api`
- Pinia store 放在 `src/store`
- 新增页面时同步更新 `src/router/index.js`
- 新增管理端页面时保留现有 `ADMIN` 角色访问控制

## 常见问题

### 页面能打开但接口失败

检查后端是否运行在 `http://localhost:8080`，以及 `vite.config.js` 中 `/api` 的代理目标是否正确。浏览器 Network 面板中如果看到 401，通常表示 token 缺失或已过期。

### 登录后刷新又回到登录页

Pinia store 会从 `localStorage.token` 初始化用户信息。如果 token 已过期、格式无法解析，或 token 中缺少 `userId`、`username`、`role`，会被视为未登录。

### 管理菜单不显示

管理菜单依赖 JWT 中的 `role === "ADMIN"`。如果当前账号不是管理员，侧边栏只显示学生端菜单，并且无法访问 `/admin/*`。

### 视频无法播放

检查后端 `/videos/**` 是否可访问、`VIDEO_DIR` 是否配置正确，以及浏览器请求中是否带上 `auth_token` Cookie。视频播放页会在设置视频地址前先写入临时 Cookie。

### 推荐页面无数据

前端只调用后端 `/recommend/hybrid`。请确认后端已经启动、推荐服务 `../recommend-service` 可用，并且后端的 `RECOMMEND_SERVICE_URL` 指向推荐服务实际地址。

## 更多文档

- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)
- 后端说明：[`../backend/README.md`](../backend/README.md)
- 推荐服务配置：[`../recommend-service/environment.yml`](../recommend-service/environment.yml)

## License

本前端服务采用 MIT License，详见根目录 [`../LICENSE`](../LICENSE)。
