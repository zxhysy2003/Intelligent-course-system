# Repository Guidelines

## Project Structure & Module Organization
`src/` contains the application code. Use `src/views/user/` for learner-facing pages and `src/views/admin/` for admin screens. Put API wrappers in `src/api/`, Pinia stores in `src/store/`, router setup in `src/router/index.js`, and shared helpers in `src/utils/`. Static assets live in `src/assets/`; public files that must keep their original URL go in `public/`.

## Build, Test, and Development Commands
- `npm install`: install dependencies from `package-lock.json`.
- `npm run dev`: start the Vite dev server. `vite.config.js` proxies `/api` and `/videos` to `http://localhost:8080`.
- `npm run build`: create a production build.
- `npm run preview`: serve the production build locally for a final check.

There is no automated test script in `package.json` yet, so local verification is currently manual.

## Coding Style & Naming Conventions
Follow the existing Vue 3 + Vite style:
- Use Vue SFCs with `<script setup>` and the Composition API.
- Prefer the `@` alias for imports from `src/`.
- Name views and components in PascalCase, for example `CourseManage.vue` and `KnowledgeGraph.vue`.
- Keep stores, utilities, and local variables in camelCase, for example `useUserStore` and `authCookie.js`.
- Match the surrounding file’s formatting. Existing files use single quotes in some modules and double quotes in others, so consistency within a file matters more than global rewrites.

## Testing Guidelines
Until a test runner is added, verify changes by running `npm run dev` and exercising the affected flow:
- authentication (`/login`, `/register`)
- user pages under `/course`, `/recommend`, `/dashboard`
- admin pages under `/admin/course` and `/admin/users`

If you add tests later, place them near the feature or under a dedicated `tests/` directory and name them after the feature they cover.

## Commit & Pull Request Guidelines
Recent commits use short Chinese summaries describing completed work, for example `完成了用户管理的界面。`. Keep commits focused on one change and describe the delivered behavior clearly.

For pull requests, include:
- a short summary of the user or admin flow changed
- any backend API or proxy assumptions
- screenshots or short recordings for UI changes
- manual verification steps performed locally

## Configuration Notes
Routing uses `createWebHashHistory()`. If you add new pages, register them in `src/router/index.js` and keep role-based admin access checks intact.
