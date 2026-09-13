# 学习笔记同步到个人博客

项目的 `docs/study-notes/*.md` 是唯一原稿。同步脚本将除 README 外的主题笔记导出到博客的 `docs/projects/java/course-system-notes/`，生成复习目录，并适配现有 VitePress 的自动文章索引。需要 Python 3.9 或更新版本，无第三方 Python 依赖。

## 日常使用

在智能课程系统项目根目录运行（路径中不需要写 `code\_space`）：

```bash
python3 scripts/sync-study-notes.py --blog-dir /Users/shiyang/Desktop/code_space/zxhysy2003.github.io --dry-run
python3 scripts/sync-study-notes.py --blog-dir /Users/shiyang/Desktop/code_space/zxhysy2003.github.io
```

第一条只查看变更，第二条写入博客。然后进入博客检查：

```bash
cd /Users/shiyang/Desktop/code_space/zxhysy2003.github.io
npm run docs:build
npm run docs:dev
```

通过“项目复盘 → Java 项目 → 智能课程系统学习笔记”进入目录。构建和开发启动前会自动更新首页及分类索引，侧边栏自动发现新文章。博客现有本地搜索也会包含这些文章。

检查 `git diff` 和 `git status` 后，在博客仓库提交本次变更并推送：

```bash
git add docs/projects/java/course-system-notes docs/index.md docs/projects/index.md docs/projects/java/index.md
git commit -m "docs: sync course system study notes"
git push origin main
```

推送 main 后沿用博客已有 GitHub Actions 部署流程。发布后的复习入口是：

<https://zxhysy2003.github.io/projects/java/course-system-notes/overview>

可在手机浏览器中收藏该地址。

## 同步规则与边界

- 文件名决定稳定文章地址；修改原稿后重新执行即可更新文章。
- 初次导出使用问答标题中的最早/最晚日期，缺少日期时使用当天。后续内容变化更新日期；重复同步不会刷新日期或产生文件变化。博客的 `date` 使用最近更新日期，以适配现有排序。
- `.sync-manifest.json` 保存内容摘要和日期，需要和生成文章一起提交。它用于检测博客端手工修改；冲突时停止写入，请先把需要保留的内容合并回项目原稿。
- 博客生成目录里的文章应通过项目原稿修改，不要直接编辑。其他博客目录不由同步脚本修改；博客自身的索引脚本会更新首页和分类页。
- 原稿删除或改名时只提示，保留旧博客文章，避免误删；旧页面需要人工处理。
- 保留代码块、自测和笔记互链；README 链接转换为博客复习目录。代码定位仍是项目相对路径文本。
- 当前仅适配平铺的 Markdown 主题笔记，不支持原稿 frontmatter、引用式链接及本地图片/附件链接；遇到这些链接会报错，需先扩展脚本。外部 URL 和页内锚点原样保留。
- 脚本不会执行 Git 提交、推送或部署。
