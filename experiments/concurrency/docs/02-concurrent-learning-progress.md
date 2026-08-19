# CON-02 并发学习进度与首次完课实验

## 1. 实验要回答的问题

同一个用户同时上报多段学习行为时：

1. `learned_seconds` 是否等于所有独立学习事件时长之和，还是会发生丢失更新？
2. 学习进度跨过 100% 时，是否只有一个请求能够触发首次完课？
3. `complete_time` 是否只设置一次，`FINISH` 行为是否只生成一条？
4. 后端扩展为两个实例后，原子累加和首次完课结论是否仍然成立？
5. “并发累加正确”和“重复请求幂等”有什么区别？

CON-02 的重点仍然是并发正确性，不用于测量系统最大 QPS。

## 2. 当前调用链

```text
k6 并发上报独立 STUDY 事件
  -> POST /api/v1/learning-behaviors
  -> LearningBehaviorRecordController.recordBehavior
  -> LearningBehaviorServiceImpl.recordBehavior (@Transactional)
  -> UPDATE user_course_relation 原子累加学习时长和进度
  -> UPDATE ... WHERE complete_time IS NULL AND progress >= 100
  -> INSERT learning_behavior(STUDY)
  -> 仅首次完成时 INSERT learning_behavior(FINISH)
```

相关实现：

- Controller：`backend/src/main/java/com/sy/course_system/controller/client/LearningBehaviorRecordController.java`
- Service：`backend/src/main/java/com/sy/course_system/service/impl/LearningBehaviorServiceImpl.java`
- Mapper：`backend/src/main/resources/mapper/UserCourseRelationMapper.xml`
- 表结构：`backend/src/main/resources/db/migration/V1__baseline_schema.sql`

学习时长使用一条 SQL 原子累加：

```sql
learned_seconds = LEAST(learned_seconds + :duration, :totalSeconds)
```

如果改成“先查询、在 Java 中相加、再写回”，两个请求可能同时读到旧值并互相覆盖。当前写法让 MySQL 对同一关系行的更新串行持有行锁，因此不会丢失已经成功提交的增量。

首次完课使用条件更新作为门闩：

```sql
WHERE complete_time IS NULL
  AND progress >= 100
```

并发请求中只有一个事务能把 `complete_time` 从 `NULL` 改成具体时间并获得 `affected_rows = 1`；只有这个事务继续生成 `FINISH` 行为。这是数据库条件更新实现的 CAS 语义。

## 3. 实验假设

### H1：并发累加不会丢失

初始学习时长为 0，100 个独立请求各上报 5 秒，课程总时长为 705 秒：

```text
learned_seconds = 100 × 5 = 500
progress = (500 × 100) DIV 705 = 70
status = 1
complete_time = NULL
```

### H2：首次完课只触发一次

初始学习时长为 700 秒，100 个请求各上报 5 秒：

```text
learned_seconds = 705
progress = 100
status = 2
complete_time != NULL
FINISH 行为数 = 1
```

关系表中的学习时长会封顶在 705 秒，但每个独立请求仍会保存一条时长为 5 秒的 `STUDY` 行为，所以行为日志的 `duration` 总和为 500 秒。这两个字段表达的语义不同：前者是课程完成进度，后者是收到的事件日志。

### H3：跨 JVM 结论不变

两个后端实例各收到 50 个请求时，H2 仍然成立，因为两个实例最终竞争的是 MySQL 中同一行，而不是某个 JVM 内的锁。

## 4. 实验数据与隔离

只使用实验数据库：

```text
course_concurrency
```

固定使用：

```text
username: stu_newbie
user_id:  6
password: 123456
```

三轮分别使用不同课程，避免前一轮结果影响后一轮：

| 轮次 | 课程 | 初始状态 | 用途 |
|---|---:|---|---|
| A | 10 | 0/705 秒 | 验证并发累加不丢失 |
| B | 11 | 700/705 秒 | 验证单实例首次完课 |
| C | 12 | 700/705 秒 | 验证双实例首次完课 |

数据准备脚本会删除该用户在课程 10、11、12 上的学习行为和推荐评分快照，并重置关系表。它不会操作其他用户、其他课程或日常开发库。

## 5. 准备实验数据

先确认基础设施已启动、后端连接的是 `course_concurrency`。在仓库根目录执行：

```bash
docker compose -f scripts/docker-compose.yml exec -T \
  -e MYSQL_PWD=root123 mysql mysql -uroot \
  < experiments/concurrency/data/con-02-prepare.sql
```

脚本最后必须输出三行准备状态：

```text
course 10: progress=0,  learned_seconds=0,   status=0, complete_time=NULL
course 11: progress=99, learned_seconds=700, status=1, complete_time=NULL
course 12: progress=99, learned_seconds=700, status=1, complete_time=NULL
```

三门课程的 `video_duration_seconds` 都必须为 `705`，并且 `behavior_rows=0`。任意前置条件不满足时不要运行 k6。

每次完整重跑 CON-02 前都要重新执行准备脚本。脚本不会自动清理 Redis 热度或 Neo4j 掌握关系，因为它们不是本实验的正确性断言；重复实验时不要把这些副作用当作首次数据。

## 6. k6 负载模型

脚本：[`../k6/con-02-concurrent-study-progress.js`](../k6/con-02-concurrent-study-progress.js)

使用 `per-vu-iterations`：

```text
100 VU × 每个 VU 1 次请求 = 100 个并发 STUDY 事件
```

登录只在 `setup()` 中执行一次，不计入 `study_success`。每个请求发送：

```json
{
  "courseId": 10,
  "behaviorType": "STUDY",
  "duration": 5
}
```

脚本内置阈值：

```text
study_success = CON02_VUS
study_unexpected = 0
checks = 100%
```

如果传入多个 `CON02_BASE_URLS`，脚本还会验证每个实例实际收到的请求数。

## 7. 轮次 A：并发累加

只向单个实例发送课程 10 的 100 个请求：

```bash
k6 run \
  -e CON02_BASE_URL=http://127.0.0.1:8080 \
  -e CON02_USERNAME=stu_newbie \
  -e CON02_PASSWORD=123456 \
  -e CON02_COURSE_ID=10 \
  -e CON02_DURATION=5 \
  -e CON02_VUS=100 \
  experiments/concurrency/k6/con-02-concurrent-study-progress.js
```

k6 预期 100 次业务成功。随后查询数据库：

```sql
SELECT progress, learned_seconds, status, complete_time
FROM course_concurrency.user_course_relation
WHERE user_id = 6 AND course_id = 10;

SELECT behavior_type, COUNT(*) AS behavior_count, SUM(duration) AS duration_sum
FROM course_concurrency.learning_behavior
WHERE user_id = 6 AND course_id = 10
GROUP BY behavior_type;
```

预期：

```text
progress=70, learned_seconds=500, status=1, complete_time=NULL
STUDY: behavior_count=100, duration_sum=500
FINISH: 0 行
```

只要最终 `learned_seconds < 500`，就说明成功响应与最终累计状态不一致，实验失败。

## 8. 轮次 B：单实例首次完课

课程 11 已由准备脚本设置为 700/705 秒：

```bash
k6 run \
  -e CON02_BASE_URL=http://127.0.0.1:8080 \
  -e CON02_USERNAME=stu_newbie \
  -e CON02_PASSWORD=123456 \
  -e CON02_COURSE_ID=11 \
  -e CON02_DURATION=5 \
  -e CON02_VUS=100 \
  experiments/concurrency/k6/con-02-concurrent-study-progress.js
```

数据库预期：

```text
progress=100, learned_seconds=705, status=2, complete_time 非空
STUDY: behavior_count=100, duration_sum=500
FINISH: behavior_count=1, duration_sum=0
```

如果 `FINISH > 1`，说明首次完课门闩失效；如果 `FINISH = 0`，说明状态已经完成但里程碑事件没有可靠产生。

## 9. 轮次 C：双实例首次完课

按照 CON-01 的方式启动两个共享 MySQL、Redis、Neo4j 和 JWT 密钥的后端实例：

```text
instance 1: http://127.0.0.1:8080
instance 2: http://127.0.0.1:8081
```

课程 12 已由准备脚本设置为 700/705 秒。执行：

```bash
k6 run \
  -e CON02_BASE_URLS=http://127.0.0.1:8080,http://127.0.0.1:8081 \
  -e CON02_USERNAME=stu_newbie \
  -e CON02_PASSWORD=123456 \
  -e CON02_COURSE_ID=12 \
  -e CON02_DURATION=5 \
  -e CON02_VUS=100 \
  experiments/concurrency/k6/con-02-concurrent-study-progress.js
```

除了轮次 B 的数据库断言，还要求：

```text
backend_index:1 = 50
backend_index:2 = 50
```

这能证明两个 JVM 都参与了同一行的竞争。

## 10. 三轮统一数据库断言

全部运行后执行：

```sql
SELECT
    course_id,
    progress,
    learned_seconds,
    status,
    complete_time
FROM course_concurrency.user_course_relation
WHERE user_id = 6
  AND course_id IN (10, 11, 12)
ORDER BY course_id;

SELECT
    course_id,
    behavior_type,
    COUNT(*) AS behavior_count,
    COALESCE(SUM(duration), 0) AS duration_sum
FROM course_concurrency.learning_behavior
WHERE user_id = 6
  AND course_id IN (10, 11, 12)
GROUP BY course_id, behavior_type
ORDER BY course_id, behavior_type;
```

最终期望：

| 课程 | progress | learned_seconds | status | STUDY 数 | STUDY 时长和 | FINISH 数 |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 70 | 500 | 1 | 100 | 500 | 0 |
| 11 | 100 | 705 | 2 | 100 | 500 | 1 |
| 12 | 100 | 705 | 2 | 100 | 500 | 1 |

课程 11、12 的 `complete_time` 必须非空。

## 11. 原子性与幂等性的边界

CON-02 中的 100 个请求被刻意定义为 100 个不同的学习事件，因此每个请求都应该参与累计并写入行为日志。

如果这 100 个请求其实是同一个逻辑事件的网络重试，当前接口仍会累计 100 次。原子更新只能保证“所有请求都正确累加且不互相覆盖”，不能识别“这些请求是不是同一个事件”。

```text
原子性：每个成功请求的增量都不丢失
幂等性：同一个逻辑请求重复到达也只生效一次
```

重复消费去重属于 CON-03，需要引入 `eventId` 或 `requestId`，并在数据库建立唯一约束。

## 12. 可选对照实验

为了直观看到丢失更新，可以在专用测试替身或临时分支中把原子 SQL 替换为：

```text
SELECT learned_seconds
Java: learnedSeconds += duration
UPDATE learned_seconds = :newValue
```

然后复用完全相同的轮次 A。最终结果通常小于 500。该坏实现只能用于临时实验，不能提交到正式业务代码，也不能删除现有原子 SQL。

## 13. 完成标准

- 三轮 k6 的 HTTP、业务码和自定义计数阈值全部通过。
- 单实例并发累加最终严格等于 500 秒。
- 单实例和双实例完课都只生成一条 `FINISH`。
- 双实例各收到 50 个请求。
- 所有结果均由 k6 输出和 SQL 最终状态共同证明。
- 结论明确区分原子累加、条件更新和请求幂等。

## 14. 实测发现

2026-08-19 的首轮实测中，三轮 k6 阈值、学习时长累加和 FINISH 唯一性均符合预期，但轮次 A 得到：

```text
learned_seconds=500
progress=71
```

按本实验公式，进度应为 `500 × 100 DIV 705 = 70`。结果表明当前 Mapper 在同一条 UPDATE 中先更新 `learned_seconds`，随后计算 `progress` 时又加了一次 `duration`。详细分析见 [`../../CON-02实验结果.md`](../../CON-02实验结果.md)。

该缺陷随后已在 `UserCourseRelationMapper.xml` 中修复，并通过 Mapper SQL 契约测试和 MySQL 临时表的 495、695、700 秒三组边界验证。完整复跑 ABC 三轮后，课程 10 得到 `learned_seconds=500, progress=70`，课程 11、12 仍各只有一条 FINISH，双实例仍为 50/50 分流。CON-02 已满足本节完成标准。
