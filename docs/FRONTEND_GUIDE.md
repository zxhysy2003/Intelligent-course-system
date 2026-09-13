# 前端开发与联调手册

本文面向前端开发与接口联调，说明页面权限、请求封装、路由、字段和排障。服务概览见 [前端 README](../frontend/README.md)，完整环境启动见 [总操作手册](OPERATION_MANUAL.md)。

## 环境要求

- Node.js `^20.19.0` 或 `>=22.12.0`
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

如果本机使用 Volta，可以直接使用该版本；否则应使用满足 Vite 7 要求的 Node.js 版本。

## 本地开发

下面的命令默认从仓库根目录执行。
进入 `frontend` 后，后续 `npm` 命令均在该目录执行。

### 1. 启动后端和推荐服务

前端依赖后端接口和视频资源。完整联调建议先启动：

1. 启动 MySQL、Redis、Neo4j 基础依赖。
2. 启动 Spring Boot 后端，等待数据库迁移完成。
3. 启动 FastAPI 推荐服务，再启动前端。

详细步骤见 [`../docs/OPERATION_MANUAL.md`](OPERATION_MANUAL.md)。

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

### 4. 检查、构建和预览

```bash
npm run lint
npm run test
npm run check
npm run build
npm run preview
```

生产构建通过路由级懒加载拆分页面代码，并在 `vite.config.js` 中把 Vue、Element Plus、ECharts 和其他第三方依赖拆成独立 vendor chunk。ECharts 页面统一从 `src/utils/echarts.js` 使用按需注册后的 `init`，当前注册了柱状图、折线图、知识图谱和雷达图所需能力。

组件和业务测试使用 Vitest，测试默认与所属功能放在 `__tests__` 目录，全局媒体元素模拟位于 `src/test/setup.js`。`npm run check` 会依次执行 ESLint、测试和生产构建；功能变更后仍应手动验证登录、课程、视频播放、推荐、分析、知识图谱、学习助手和后台管理流程。

## 代理配置

代理配置位于 [`vite.config.js`](../frontend/vite.config.js)：

| 前端路径  | 代理目标                | 说明                                   |
| --------- | ----------------------- | -------------------------------------- |
| `/api`    | `http://localhost:8080` | 后端业务接口，保留完整 `/api/v1` 路径   |
| `/videos` | `http://localhost:8080` | 课程视频静态资源                       |

Axios 实例定义在 [`src/api/request.js`](../frontend/src/api/request.js)，默认配置：

- `baseURL: "/api/v1"`
- `timeout: 5000`
- 请求拦截器自动添加 `Authorization: Bearer <token>`

如果后端地址或端口发生变化，启动前设置 `VITE_BACKEND_TARGET`，例如 `VITE_BACKEND_TARGET=http://localhost:8081 npm run dev`；该变量同时覆盖 `/api` 和 `/videos` 的代理目标。

## 登录态与认证

路由使用 `createWebHashHistory()`。除登录和注册页外，业务页面需要登录；`/admin/*` 仅允许 `role === "ADMIN"` 的用户访问，非管理员会跳转到 `/courses`。普通学生未完成引导时会先进入 `/onboarding`。

登录流程：

1. 登录页调用 `POST /api/v1/auth/login`
2. 后端返回 JWT
3. Pinia 用户 store 将 token 写入 `localStorage` 的 `token`
4. `jwt-decode` 从 token 中解析 `userId`、`username` 和 `role`
5. 路由守卫根据登录态和角色控制页面访问
6. Axios 请求自动携带 `Authorization` 请求头

视频播放不复用登录 JWT，也不写认证 Cookie。课程详情页先通过 Axios 调用
`POST /api/v1/courses/:courseId/playback`，再把返回的路径绑定限时 URL 交给原生 `<video>`。
播放器首次加载失败会自动续签一次并恢复当前位置；第二次失败才显示错误。

## 新用户引导

引导页位于 [`src/views/user/Onboarding.vue`](../frontend/src/views/user/Onboarding.vue)，状态由 [`src/store/onboarding.js`](../frontend/src/store/onboarding.js) 管理。

流程很短，适合演示：

1. 路由守卫在普通用户访问学生端页面前调用 `GET /api/v1/onboarding/status`
2. 如果 `completed=false`，跳转到 `/onboarding?redirect=<原目标页>`
3. 页面通过 `GET /api/v1/onboarding/options` 加载等级、学习目标和可选标签
4. 用户选择 `currentLevel`、可选 `learningGoal`，并至少选择一个 `tagIds`
5. `PUT /api/v1/onboarding/profile` 保存画像，完成后回到原目标页；没有 redirect 时默认进入 `/recommendations`

可选值：

| 字段           | 说明                                         |
| -------------- | -------------------------------------------- |
| `currentLevel` | 必填，`1` 零基础、`2` 入门、`3` 有基础       |
| `learningGoal` | 可空，`JOB`、`PROJECT`、`FOUNDATION`、`EXAM` |
| `tagIds`       | 必填，至少一个来自 `options.tags` 的启用标签 |

## 路由说明

路由入口为 [`src/router/index.js`](../frontend/src/router/index.js)。

### 公共路由

- `/login`：登录
- `/register`：注册
- `/:pathMatch(.*)*`：404

### 学生端路由

- `/courses`：课程列表
- `/courses/:courseId`：课程详情和视频学习
- `/onboarding`：新用户引导
- `/recommendations`：个性化推荐
- `/dashboard`：学习进度
- `/assistant`：学习助手
- `/knowledge-graph`：知识图谱
- `/profile`：个人中心

### 管理端路由

- `/admin/courses`：课程管理
- `/admin/courses/new`：新增课程
- `/admin/courses/:courseId/edit`：编辑课程
- `/admin/users`：用户管理
- `/admin/users/:userId/edit`：编辑用户

## API 模块

接口封装位于 `src/api`：

- [`src/api/request.js`](../frontend/src/api/request.js)：Axios 实例和鉴权拦截器
- [`src/api/user.js`](../frontend/src/api/user.js)：登录、注册、用户信息和后台用户管理
- [`src/api/course.js`](../frontend/src/api/course.js)：课程查询、选课、视频、后台课程管理和视频上传
- [`src/api/onboarding.js`](../frontend/src/api/onboarding.js)：引导选项、状态和提交
- [`src/api/recommend.js`](../frontend/src/api/recommend.js)：混合推荐
- [`src/api/analysis.js`](../frontend/src/api/analysis.js)：学习进度、能力雷达图和知识图谱
- [`src/api/agent.js`](../frontend/src/api/agent.js)：学习助手会话、消息和聊天发送
- [`src/api/learningBehavior.js`](../frontend/src/api/learningBehavior.js)：学习行为记录

## 推荐页字段

前端只调用 `GET /api/v1/recommendations`。推荐卡片依赖后端已经裁剪过的稳定字段：

| 字段                              | 用途                                                                   |
| --------------------------------- | ---------------------------------------------------------------------- |
| `courseId`、`title`、`difficulty` | 课程跳转和基础展示                                                     |
| `recommendScore`                  | 推荐页展示分，前端优先使用该字段                                       |
| `reason`                          | 推荐原因文案                                                           |
| `readiness`                       | 学习准备度进度条，按 0~1 转百分比                                      |
| `recommendSource`                 | 来源核验：`CF`、`COLD_START_USER`、`COLD_START_COURSE`、`HOT_FALLBACK` |
| `isNewCourse`                     | 兼容字段；缺少 `recommendSource` 时用于识别新课注入                    |
| `knowledgePoints`                 | 涵盖知识点                                                             |
| `missingPrerequisitesMastery`     | 薄弱前置项                                                             |
| `learningPaths`                   | 建议学习路径                                                           |

## 开发约定

- 页面组件使用 Vue SFC 和 `<script setup>`
- 跨目录导入使用 `@` 别名，同一功能目录内部可以使用相对路径
- 页面和组件使用 PascalCase，API 和普通函数使用 camelCase
- 用户端页面放在 `src/views/user`
- 管理端页面放在 `src/views/admin`
- 页面专属组件、composable 和测试优先放在 `src/features/<feature>`
- 路由布局放在 `src/layouts`
- API 封装放在 `src/api`
- Pinia store 放在 `src/store`
- 新增页面时同步更新 `src/router/index.js`
- 新增管理端页面时保留现有 `ADMIN` 角色访问控制
- 路由页面组件默认使用动态 `import()` 懒加载；新增图表类型时同步更新 `src/utils/echarts.js` 的按需注册列表

## 常见问题

### 页面能打开但接口失败

检查后端是否运行在 `http://localhost:8080`，以及 `vite.config.js` 中 `/api` 的代理目标是否正确。浏览器 Network 面板中如果看到 401，通常表示 token 缺失或已过期。

### 登录后刷新又回到登录页

Pinia store 会从 `localStorage.token` 初始化用户信息。如果 token 已过期、格式无法解析，或 token 中缺少 `userId`、`username`、`role`，会被视为未登录。

### 管理菜单不显示

管理菜单依赖 JWT 中的 `role === "ADMIN"`。如果当前账号不是管理员，侧边栏只显示学生端菜单，并且无法访问 `/admin/*`。

### 视频无法播放

检查播放凭证接口是否返回 200、返回的 `playbackUrl` 是否包含 `token` 参数，以及 `VIDEO_DIR` 是否配置正确。播放器首次失败会自动续签；若仍失败，请分别查看凭证接口和 `/videos/**` 请求的状态码。

### 推荐页面无数据

前端只调用后端 `/api/v1/recommendations`。请确认后端已经启动、推荐服务 `../recommend-service` 可用，并且后端的 `RECOMMEND_SERVICE_URL` 指向推荐服务实际地址。
