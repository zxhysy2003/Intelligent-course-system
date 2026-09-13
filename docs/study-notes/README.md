# 项目学习问答笔记

这里保存通过 `$study-notes-recorder` 整理的项目学习问答。内容按主题归档，用于回顾源码实现、并发机制、数据库与工程实践；不保存逐字聊天记录。

## 使用方式

只有明确提出记录请求时才会修改这里，例如：

```text
使用 $study-notes-recorder 记录刚才关于线程池参数的问题。
把刚才对 SOFT_INVALIDATE_SCRIPT 的讲解整理到复习笔记。
更新推荐缓存主题笔记，加入本地 JVM 缓存与 Redis 缓存的区别。
```

相同主题会继续更新已有文件；重复问题会合并或修正，而不是创建多份内容相近的笔记。

## 主题索引

| 主题 | 文件 | 核心内容 | 最后更新 |
|---|---|---|---|
| 推荐构建线程池 | [recommend-build-thread-pool.md](recommend-build-thread-pool.md) | 核心线程、最大线程与队列容量；2 / 4 / 16 接收顺序；single-flight、拒绝与超时降级 | 2026-09-03 |
| Micrometer 与推荐缓存指标观测 | [micrometer-observability.md](micrometer-observability.md) | Counter、Timer、Gauge；推荐缓存埋点；Actuator 暴露与 CON-04 实验观测 | 2026-09-04 |
| 推荐缓存更新与失效 | [recommend-cache-invalidation.md](recommend-cache-invalidation.md) | 缓存三态、软/强失效、版本校验；Outbox 依赖、租约续租与超时接管 | 2026-09-12 |
| 推荐评分快照刷新 | [recommend-score-snapshot.md](recommend-score-snapshot.md) | 隐式评分与重建；FOR UPDATE、REQUIRES_NEW、挂起不释放锁；MANDATORY 原子入队、Outbox 事务拆分与训练边界 | 2026-09-13 |
| 热度 Lua 与任务去重 | [learning-hot-lua.md](learning-hot-lua.md) | ZINCRBY 与执行凭证、Lua 原子性和失败边界、参数序列化、unchecked 泛型警告 | 2026-09-12 |
| Java record 与数据对象 | [java-record.md](java-record.md) | 自动生成方法、浅不可变、值相等、Outbox 数据载体与普通类的选择 | 2026-09-12 |
| Outbox 关闭流程与线程中断 | [outbox-shutdown.md](outbox-shutdown.md) | @PreDestroy 生命周期、线程池关闭顺序、续租收尾、中断标记恢复与外层响应示例 | 2026-09-13 |

## 组织约定

需要在手机上复习时，参考 [同步到个人博客](../STUDY_NOTES_BLOG_SYNC.md)，将这里的原稿导出到博客。

- 主题文件使用英文 kebab-case 文件名，正文使用中文。
- 代码引用使用仓库相对路径，并优先记录类名和方法名。
- 笔记聚焦核心结论、工作原理、示例、易错点、代码定位和复习自测。
- 不记录密码、Token、密钥、个人隐私或本地未脱敏配置。
