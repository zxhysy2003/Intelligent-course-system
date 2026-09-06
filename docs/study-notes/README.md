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

## 组织约定

- 主题文件使用英文 kebab-case 文件名，正文使用中文。
- 代码引用使用仓库相对路径，并优先记录类名和方法名。
- 笔记聚焦核心结论、工作原理、示例、易错点、代码定位和复习自测。
- 不记录密码、Token、密钥、个人隐私或本地未脱敏配置。
