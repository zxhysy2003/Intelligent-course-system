# 实验数据

本目录保存高并发实验专用的数据生成、校验和清理脚本。

约定：

- 只允许连接明确命名的实验数据库，例如 `course_concurrency`。
- 清理脚本执行前必须校验数据库名，禁止对空变量或默认开发库执行批量删除。
- 大规模数据不加入正式 Flyway 基线。
- 数据脚本应支持重复执行，并记录生成的数据规模。
- 正确性断言 SQL 与造数脚本分开保存。

已实现：

- [`con-02-prepare.sql`](./con-02-prepare.sql)：为 `stu_newbie` 准备课程 10、11、12 的并发累加与首次完课状态。
- [`con-03-prepare.sql`](./con-03-prepare.sql)：清空 CON-03 的三门课程并统一重置为 0 秒，同时检查 V3 字段和唯一索引。
- [`con-03-assert.sql`](./con-03-assert.sql)：在 CON-03 四个请求轮次结束后，统一断言三门课程的进度、日志数量、时长和事件 ID 数量。
- [`con-03-rollback-prepare.sql`](./con-03-rollback-prepare.sql)：只重置 `stu_newbie` 的课程 14，用于事件日志插入后、事务提交前的故障实验。
- [`con-03-rollback-assert.sql`](./con-03-rollback-assert.sql)：分别断言故障后的 `ROLLED_BACK` 和成功重试后的 `RECOVERED` 两种状态。
- [`con-04-prepare.sql`](./con-04-prepare.sql)：创建 100 个专用普通推荐用户，并写入 600 秒学习信号。
- [`con-04-cleanup.sql`](./con-04-cleanup.sql)：删除 CON-04 用户和它们产生的数据。
- [`con-04-redis.sh`](./con-04-redis.sh)：按实验用户 ID 精确重置、读取元数据，或统一缩短物理 TTL。
- [`con-04-cache-snapshot.lua`](./con-04-cache-snapshot.lua)：只读输出 v2 时间戳、版本、fresh/stale 数量和逻辑 TTL 分布。
- [`con-04-metrics.sh`](./con-04-metrics.sh)：采集推荐缓存事件 Counter 的实验前后快照并计算差值。

CON-02 脚本会清理目标用户在三门课程上的学习行为和推荐评分快照，并重置学习关系。执行前必须确认连接的是 `course_concurrency`。

CON-03 的准备脚本同样会清理课程 10、11、12。正式实验轮次之间不要重复执行；需要重跑时，从准备数据开始完整执行所有轮次。

CON-03 故障扩展脚本只作用于课程 14，不会覆盖主实验的课程 10、11、12。故障和恢复阶段之间不得重新执行准备脚本，否则无法证明同一事务回滚后的恢复过程。

CON-04 SQL 只作用于 `con04_user_001` 至 `con04_user_100` 及 `con04_admin`；Redis 脚本不会执行
`FLUSHDB`，也不会按宽泛通配符删除其他用户缓存。`inspect-all N` 只读前 N 个实验用户，默认 50；
reset 还会清理用户版本及 STUDY 节流 key，须等相关构建排空后执行。
`expire-all` 改的是物理 TTL，会删除旧值，不用于测试逻辑过期的 stale 返回。

轮次 E 等需要观察 `refresh_rejected`、`degraded`、`wait_timeout` 的场景，可以在同一后端进程
运行期间分别执行 `con-04-metrics.sh before` 和 `con-04-metrics.sh after`。默认快照写到 `/tmp`，
也可以通过脚本参数或 `CON04_METRICS_BEFORE_FILE`、`CON04_METRICS_AFTER_FILE` 指定位置。
