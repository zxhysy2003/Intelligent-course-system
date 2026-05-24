---
name: module-doc-writer
description: Generate or update a module implementation document for this Spring Boot project. Use when the user asks to document how a module is implemented, summarize a feature module, or create docs for recently changed code.
---

You are helping maintain implementation documents for this project.

## Goal

Generate or update a Markdown document under `docs/` that explains how a specific module is implemented.

The document is for the future developer or the future me, not for marketing. Keep it practical, concrete, and easy to review.

## When to use this skill

Use this skill when the user says things like:

- 给这个模块写说明文档
- 总结一下这个功能是怎么实现的
- 帮我生成模块开发文档
- 根据最近改动更新 docs
- explain how this module works

## Required behavior

Before writing the document, inspect the relevant code files. Do not rely only on file names.

Prefer checking these layers when they exist:

1. Controller
2. Service / ServiceImpl
3. Mapper / Repository
4. DTO / VO / Entity
5. SQL / XML mapper
6. Config classes
7. Frontend API calls if related
8. Existing docs under `docs/`

## Output location

Write or update a Markdown file under `docs/`.

Use a clear filename, for example:

- `docs/recommend-module.md`
- `docs/cold-start-module.md`
- `docs/learning-behavior-module.md`
- `docs/knowledge-graph-module.md`

## Document structure

Use this structure:

# 模块名称

## 1. 模块作用

Explain what problem this module solves.

## 2. 入口位置

List the main entry points:

- Controller
- API path
- Service method
- Frontend page or API call, if relevant

## 3. 核心流程

Use numbered steps to explain the runtime flow.

The flow should be concrete enough that someone can follow the code from entry to database or external service.

## 4. 关键类与职责

Use a table:

| 类 / 文件 | 作用 |
|---|---|

## 5. 涉及的数据表或外部组件

Mention related tables, Redis keys, Neo4j nodes/relationships, Python services, or other components if present.

## 6. 核心规则与特殊处理

Explain important business rules, formulas, edge cases, and non-obvious decisions.

Focus on “why”, not only “what”.

## 7. 容易忘记或容易出错的点

List implementation details that future maintainers may forget.

Examples:

- relationship direction
- cache refresh timing
- idempotent behavior
- compatibility fields
- naming inconsistency
- transaction boundary
- async execution behavior

## 8. 后续维护建议

Explain where to start if the feature needs to be modified later.

## Style rules

- Use Chinese.
- Keep the tone practical and undergraduate-project friendly.
- Avoid exaggerated wording.
- Avoid writing like a product advertisement.
- Do not invent implementation details.
- If something is uncertain, write “当前代码中未明确体现” or “需要结合实际代码进一步确认”.
- Prefer concrete class names, method names, API paths, and table names.
- Do not paste huge code blocks unless necessary.
- If updating an existing document, preserve useful existing content and only revise inaccurate or outdated parts.

## Final response after editing

After creating or updating the document, summarize:

1. Which file was created or updated
2. Which code files were inspected
3. What sections were added or changed
4. Any uncertain points that need manual confirmation