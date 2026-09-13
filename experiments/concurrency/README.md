# 高并发实验

本目录保存基于真实业务的并发正确性、缓存与服务隔离实验。实验工具独立于正式启动流程，优化在对应业务模块中验证。

## 目录结构

```text
experiments/concurrency/
├── README.md                 # 实验总览、手册入口和推荐顺序
├── docs/                     # 每个实验的详细手册
├── k6/                       # HTTP 压测脚本
├── data/                     # 实验数据生成和清理脚本
├── stubs/                    # 慢服务、错误响应等故障模拟
├── observability/            # 指标、面板和观测说明
└── results/                  # 实验报告与脱敏结果
```

压测脚本、数据工具和 Stub 已按实验逐步实现；各目录 README 提供文件索引。

## 执行入口

使用独立实验数据库、Compose project 和 volume，禁止清理日常开发数据。执行步骤、观测指标与报告模板见 [实验执行手册](docs/EXPERIMENT_GUIDE.md)；脚本参数与示例见 [k6 使用手册](docs/K6_GUIDE.md)。

## 实验清单

| 编号 | 实验 | 主要入口 | 当前实现切入点 | 重点问题 | 状态 |
|---|---|---|---|---|---|
| CON-01 | [并发选课](./docs/01-concurrent-enrollment.md) | `POST /api/v1/courses/{courseId}/enrollment` | 唯一索引 + `DuplicateKeyException` | 数据库唯一约束、并发正确性、接口幂等语义 | 已完成 |
| CON-02 | [并发学习进度与首次完课](./docs/02-concurrent-learning-progress.md) | `POST /api/v1/learning-behaviors` | 原子累加 + `complete_time IS NULL` | 丢失更新、CAS、状态机、首次事件 | 已完成 |
| CON-03 | [STUDY 请求重复消费](./docs/03-study-event-idempotency.md) | `POST /api/v1/learning-behaviors` | 行为日志事件 ID + 数据库唯一键 | 原子性与幂等、重试去重、唯一键 | 已完成 |
| CON-04 | [推荐缓存击穿与雪崩](./docs/04-recommend-cache-breakdown.md) | `GET /api/v1/recommendations` | v2 逻辑过期、分布式锁、single-flight、有界构建 | 惊群、TTL 抖动、旧值与降级、故障恢复 | 部分完成 |
| CON-05 | 慢推荐服务与线程池背压 | 后端到 `/recommend` | CF 2 秒读取超时、外层有界构建池、Python 舱壁 | 持续过载、超时预算、资源隔离与恢复 | 待设计 |
| CON-06 | Redis 热榜与双写一致性 | 学习行为、`course:hot` | MySQL Outbox + Redis 去重增量 | 热点写、依赖故障、Outbox、最终一致性 | 待设计 |
| CON-07 | 课程列表 SQL 与连接池 | `POST /api/v1/courses/search` | 相关子查询、多次回补、无 pageSize 上限 | 执行计划、组合索引、连接池、分页保护 | 待设计 |
| CON-08 | 多实例定时任务竞争 | 热度快照同步 | 每个后端实例都执行 `@Scheduled` | 集群调度、分布式锁、幂等批处理 | 待设计 |
| CON-09 | 推荐服务 CPU 扩展与模型切换 | FastAPI `/recommend`、`/model/reload` | 在线请求遍历全部课程、进程内模型状态 | CPU 密集、预计算、横向扩容、版本一致性 | 待设计 |
| CON-10 | 视频流量与业务 API 隔离 | `/videos/**` | Nginx 继续代理到 Spring Boot | Range 请求、IO 隔离、对象存储、CDN | 待设计 |
| CON-11 | Agent 多实例幂等恢复 | `POST /api/v1/assistant/messages` | 数据库唯一键 + 单机处理中租约 | 数据幂等与执行互斥、分布式恢复 | 待设计 |

## 推荐实施顺序

1. **并发正确性**：CON-01、CON-02、CON-03。
2. **缓存与过载保护**：CON-04、CON-05。
3. **数据层与最终一致性**：CON-06、CON-07、CON-08。
4. **服务扩展和资源隔离**：CON-09、CON-10、CON-11。

CON-01 最适合作为第一个实验：数据规模要求低，结果可以用唯一行数明确判定，而且能直接对比“应用锁”和“数据库唯一约束”的边界。
