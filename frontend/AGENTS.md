# AGENTS 指南

本文件面向仓库协作者与 AI 编码代理。目标是让前端改动小、界面行为可预测、验证路径清晰。

## 项目概览

这是基于 Vue 3 / Vite 的课程学习系统前端，主代码位于 `src`。

常用目录：

- `src/views/user`：学习者侧页面，例如课程、推荐、仪表盘和知识图谱。
- `src/views/admin`：后台管理页面，例如课程管理和用户管理。
- `src/api`：后端接口封装与 Axios 请求实例。
- `src/store`：Pinia 状态管理。
- `src/router/index.js`：路由、角色访问和页面注册。
- `src/utils`：登录态、日志等通用工具。
- `src/assets`：源码内静态资源。
- `public`：需要保持原始访问路径的公开资源。

## 工作原则

- 先读页面、接口封装和 store 的现有调用链，再改代码。
- 优先做最小闭环改动，避免顺手重写无关页面或样式。
- 不擅自修改路由路径、接口字段、权限判断或登录态语义。
- 发现工作区已有改动时不要回退，除非用户明确要求。
- 页面组件负责交互和展示，接口细节优先放在 `src/api`。
- 共享状态放在 Pinia store；只属于单个页面的状态优先留在页面内。

## 配置与接口约束

- 本地开发通过 `vite.config.js` 代理 `/api` 和 `/videos` 到后端 `http://localhost:8080`。
- 不要把本地临时地址、真实令牌、账号密码或生产环境配置写回仓库。
- 新增后端接口时，优先在 `src/api` 中封装，再由页面或 store 调用。
- 修改接口返回结构时，同步检查所有使用该接口的页面、store 和工具函数。
- 新增页面时在 `src/router/index.js` 注册，并保持后台角色访问检查有效。
- 路由当前使用 `createWebHashHistory()`，不要无需求切换 history 模式。

## 编码与界面

- 使用 Vue SFC、`<script setup>` 和 Composition API。
- 从 `src` 导入时优先使用 `@` 别名。
- 视图和组件文件使用 `PascalCase`，例如 `CourseManage.vue`。
- store、工具函数和局部变量使用 `camelCase`，例如 `useUserStore`。
- 保持单个文件内部格式一致；不要为了风格统一做全局格式化。
- 优先复用 Element Plus 和 `@element-plus/icons-vue` 的既有组件与图标。
- 修改用户流程时同步考虑加载、空状态、错误提示和权限跳转。
- 注释默认使用中文，解释业务边界、兼容原因或不明显的交互逻辑。

## 构建与验证

常用命令：

- `npm install`：按 `package-lock.json` 安装依赖。
- `npm run dev`：启动 Vite 开发服务。
- `npm run build`：执行生产构建。
- `npm run preview`：本地预览生产构建。

验证要求：

- 当前 `package.json` 暂无自动化测试脚本，前端验证以构建和手动走查为主。
- 修改普通页面或组件时，至少确认 `npm run build` 可通过。
- 修改登录、路由、权限、请求封装或全局状态时，手动验证相关主流程。
- 重点流程包括 `/login`、`/register`、`/course`、`/recommend`、`/dashboard`、`/admin/course` 和 `/admin/users`。
- UI 改动应检查桌面宽度下的布局、空状态、错误提示和主要按钮交互。

## 提交规范

Commit message 使用 Conventional Commits：

```text
<type>(<scope>): <summary>
```

常用 `type`：

- `feat`：新增功能。
- `fix`：修复 bug。
- `refactor`：重构但不改变外部行为。
- `perf`：性能优化。
- `docs`：文档。
- `test`：测试。
- `build`、`ci`、`chore`：构建、CI 或维护性改动。

常用 `scope`：

- `auth`、`course`、`recommend`、`dashboard`
- `admin`、`router`、`store`、`api`
- `ui`、`video`、`graph`、`config`
- `docs`、`test`

规则：

- 标题使用英文祈使句，首字母默认小写，结尾不加句号。
- 根据 staged diff 选择准确的 `type` 和 `scope`，不要只根据需求猜测。
- 一个 commit 尽量只包含一类逻辑改动；小 UI 调整可和对应功能放在同一 commit。
- 破坏性变更必须使用 `!` 并在正文写 `BREAKING CHANGE:`。

示例：

```text
feat(recommend): show filtered course reasons
fix(auth): preserve redirect after login
refactor(api): centralize course requests
docs(frontend): simplify agent guide
```

## PR 与安全

PR 描述应包含变更目的、影响页面或流程、后端接口假设、截图或录屏、验证方式。涉及路由、权限、登录态、上传或视频访问时，需要说明兼容性和部署影响。

严禁提交真实密钥、账号密码、生产接口地址、用户隐私数据和本地调试令牌。
