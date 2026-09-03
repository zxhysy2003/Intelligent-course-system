---
name: study-notes-recorder
description: Record explicitly requested project Q&A as concise Chinese study notes under docs/study-notes, grouped by topic and deduplicated. Use only when the user explicitly asks to record, archive, or update review notes; do not use for ordinary explanations.
---

# Study Notes Recorder

将用户明确要求保存的项目问答整理成面向未来复习的中文 Markdown，而不是逐字复制对话。

## Trigger boundary

- 仅在用户明确要求“记录这个问题”“整理到复习笔记”“更新某主题笔记”，或调用 `$study-notes-recorder` 时写文件。
- 普通提问、源码讲解和代码审查不自动写笔记。
- 一次请求只整理用户指定的内容；不要追溯导入未被点名的历史对话。

## Storage and topic selection

- 笔记目录固定为 `docs/study-notes/`，总索引固定为 `docs/study-notes/README.md`。
- 写入前先阅读总索引，并使用 `rg` 在该目录搜索问题关键词、类名和核心概念。
- 相同或高度相关的主题更新已有文件。只有没有合适主题时才新建文件，并同步增加索引行。
- 主题文件使用简短、稳定的英文 kebab-case，例如 `recommend-cache-concurrency.md`。
- 每个主题在索引中只保留一行；更新内容时同步更新核心内容摘要和 `YYYY-MM-DD` 日期。

## Recording workflow

1. 从当前对话提取用户点名的问题、已经确认的结论以及真正有复习价值的例子和注意事项。
2. 如果结论依赖当前项目实现，先检查相关源码、配置或测试；不要只根据对话记忆落笔。
3. 选择或创建主题文件。相同问题应合并、修正或补充原记录，不机械追加重复答案。
4. 用自己的话精炼内容，保留因果关系和运行流程；省略寒暄、工具调用过程和无关上下文。
5. 更新总索引，再检查链接、日期、重复内容和敏感信息。

## Topic document shape

主题文件使用以下结构；没有复杂流程或代码定位时可以保持对应部分简短，但不要编造内容：

```markdown
# 中文主题名称

## 核心结论

## 问答记录

### YYYY-MM-DD：问题标题

- **问题**：用户真正想弄清楚的内容。
- **精炼答案**：可以直接用于复习的结论。
- **工作原理或流程**：必要时使用列表、表格或小型流程图。
- **示例**：保留能够解释机制的最小例子。
- **易错点**：记录容易混淆的概念、边界和故障表现。

## 代码定位

| 文件 | 类 / 方法 | 作用 |
|---|---|---|

## 复习自测

1. 自测问题
   - 参考答案：简短答案
```

新增问答时可在“问答记录”中增加日期小节，并按需要更新“核心结论”“代码定位”和“复习自测”；不要为每次记录复制整份固定结构。

## Writing rules

- 使用中文，面向正在学习 Spring Boot、数据库和高并发知识的项目作者。
- 优先解释“为什么”和运行时行为，避免只改写代码表面含义。
- 代码引用使用仓库相对路径与稳定的类名、方法名；通常不记录容易漂移的行号。
- 不粘贴大段源码或完整聊天记录，只保留必要片段。
- 不记录密码、Token、密钥、个人隐私、本地绝对路径或未脱敏配置。
- 不把推测写成事实；无法从对话或源码确认时明确标注待确认。
- 保留已有笔记中仍然准确且有复习价值的内容，不因改写而丢失信息。

## Completion response

完成后简要说明：

- 创建或更新了哪些主题文件；
- 是否合并、修正了已有记录；
- 为确认准确性检查了哪些关键代码；
- 如有待确认内容，明确列出。
