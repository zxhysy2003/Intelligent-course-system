# k6 压测脚本

本目录保存并发实验的 HTTP 压测脚本，不参与业务服务构建。安装、参数、运行示例和断言见 [k6 使用手册](../docs/K6_GUIDE.md)。

| 脚本 | 场景 |
| --- | --- |
| [con-01-concurrent-enrollment.js](con-01-concurrent-enrollment.js) | 并发选课，支持多实例 |
| [con-02-concurrent-study-progress.js](con-02-concurrent-study-progress.js) | 学习进度累加与首次完课 |
| [con-03-study-idempotency.js](con-03-study-idempotency.js) | STUDY 首次处理、回放与冲突 |
| [con-03-rollback-retry.js](con-03-rollback-retry.js) | 事务回滚后的恢复与重试 |
| [con-04-single-key-cache.js](con-04-single-key-cache.js) | 单用户缓存与共享锁 |
| [con-04-cache-avalanche.js](con-04-cache-avalanche.js) | 多用户缓存与逻辑过期刷新 |

执行前按 [实验手册](../docs/EXPERIMENT_GUIDE.md) 准备隔离环境；HTTP 成功率必须结合数据库或缓存断言验证。实验方案与状态见 [实验总览](../README.md)。
