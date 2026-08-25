# CON-03 STUDY 请求重复消费幂等实验

## 1. 实验要回答的问题

行为日志幂等改造完成后，本实验直接验证：

1. 同一个 STUDY 逻辑事件并发到达 100 次，是否只增加一次学习时长？
2. `learning_behavior` 是否只保存一条对应的 STUDY 日志？
3. 100 个请求分发到两个后端实例时，去重结论是否仍然成立？
4. 100 个不同事件是否仍然全部累计，避免“过度去重”？
5. 相同事件 ID 携带不同请求内容时，是否返回冲突且不改变数据？

本实验不再执行“改造前发送 100 个无事件 ID 请求”的基线轮。当前目标是验证已经实现的幂等方案，而不是再次证明旧接口没有幂等能力。

## 2. 原子性与幂等性的区别

CON-02 解决原子累加问题，CON-03 解决重复投递问题：

```text
原子性：100 个不同事件各增加 5 秒，最终必须增加 500 秒
幂等性：同一个 5 秒事件投递 100 次，最终只能增加 5 秒
```

| 请求语义 | 正确的 learned_seconds 增量 | 正确的 STUDY 数量 |
|---|---:|---:|
| 100 个不同事件，每个 5 秒 | 500 | 100 |
| 同一个 5 秒事件重试 100 次 | 5 | 1 |

服务端不能通过“请求 JSON 看起来相同”推断它们是同一事件，因为用户可能连续产生两段内容相同的真实学习行为。必须由能够识别重试的客户端为逻辑事件提供稳定的 `eventId`。

## 3. 已实现的请求协议

STUDY 请求现在必须携带事件 ID：

```json
{
  "eventId": "7ed9b405-2f19-4b43-94a0-6e088737ad8f",
  "courseId": 10,
  "behaviorType": "STUDY",
  "duration": 5
}
```

接口语义：

- `eventId` 长度为 1～64，只允许字母、数字、点、下划线、冒号和连字符。
- 同一逻辑事件的网络重试必须复用同一个 `eventId`。
- 下一段真实学习事件必须生成新的 `eventId`。
- VIEW、FAVORITE、UNFAVORITE 暂不要求 `eventId`，本实验只研究 STUDY。
- FINISH 仍由首次完课逻辑在服务端内部产生，不允许客户端提交。

首次处理成功时：

```json
{
  "code": 200,
  "data": {
    "replayed": false
  }
}
```

相同事件、相同内容再次到达时，不重复执行业务，仍返回成功：

```json
{
  "code": 200,
  "data": {
    "replayed": true
  }
}
```

对于 STUDY，相同事件 ID 对应不同课程或时长时返回业务码 `409`。VIEW、FAVORITE、UNFAVORITE 不参与本次事件 ID 去重。重复投递返回成功，是因为第一次请求可能已经提交，只是响应在网络中丢失；客户端重试时需要确认目标事件已经生效。

## 4. 已实现的数据库约束

Flyway 迁移：

```text
backend/src/main/resources/db/migration/V3__add_learning_behavior_event_id.sql
```

迁移为 `learning_behavior` 增加：

```sql
event_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
```

并建立：

```sql
UNIQUE KEY uk_learning_behavior_user_event (user_id, event_id)
```

设计含义：

- STUDY 的 `event_id` 非空，由唯一索引保证同一用户的同一事件最多一条。
- 非 STUDY 行为的 `event_id` 保持 `NULL`；MySQL 唯一索引允许多行 `NULL`，不会影响原有 VIEW、FAVORITE、FINISH 日志。
- 使用 `ascii_bin` 让事件 ID 按字节、区分大小写比较，避免大小写不敏感排序规则误判。
- 唯一范围是 `(user_id, event_id)`，不同用户可以使用相同格式的本地事件 ID；同一用户不能跨课程复用事件 ID。

数据库唯一键是单实例和多实例共同使用的最终仲裁点，不依赖某个 JVM 的本地状态。

## 5. 已实现的事务流程

当前调用链：

```text
POST /api/v1/learning-behaviors
  -> LearningBehaviorRecordController.recordBehavior
  -> LearningBehaviorServiceImpl.recordBehavior (@Transactional)
  -> 校验并规范化 eventId
  -> 确认用户已经选课
  -> INSERT IGNORE learning_behavior(STUDY, event_id)
     -> affected_rows = 1：取得处理资格
        -> UPDATE user_course_relation 原子累加
        -> 首次完课门闩
        -> 热度、缓存和评分快照逻辑
        -> 提交事务
     -> affected_rows = 0：事件 ID 已存在
        -> SELECT ... FOR SHARE 读取原日志
        -> 请求内容一致：返回 replayed=true
        -> 请求内容不同：返回业务码 409
```

相关代码：

- DTO：`backend/src/main/java/com/sy/course_system/dto/LearningBehaviorRecordDTO.java`
- Controller：`backend/src/main/java/com/sy/course_system/controller/client/LearningBehaviorRecordController.java`
- Service：`backend/src/main/java/com/sy/course_system/service/impl/LearningBehaviorServiceImpl.java`
- Mapper：`backend/src/main/java/com/sy/course_system/mapper/LearningBehaviorMapper.java`
- Entity：`backend/src/main/java/com/sy/course_system/entity/LearningBehavior.java`

### 5.1 为什么先插入日志再更新进度

只有成功插入唯一事件日志的请求才能继续更新进度。幂等日志、关系表更新和 FINISH 日志位于同一个 MySQL 事务中：

- 后续业务成功：事件日志和进度一起提交。
- 后续业务失败：事件日志和进度一起回滚，同一个 `eventId` 可以再次重试。
- 重复事件：在任何进度更新之前返回，不会出现“日志一条、进度累计多次”。

不能先更新进度，再尝试插入唯一日志并在事务内吞掉 `DuplicateKeyException`。否则异常被转换后，前面的进度更新可能仍然提交。

### 5.2 为什么重复后使用 FOR SHARE

实验数据库默认可能使用 MySQL `REPEATABLE READ`。事务在查询用户课程关系时已经建立一致性快照；如果普通 `SELECT` 随后读取一个刚由竞争事务提交的事件日志，可能仍然看到旧快照。

`SELECT ... FOR SHARE` 属于当前读，能够读取唯一键竞争后的最新已提交记录，并用于比较原事件的课程、类型和时长。

这里不使用 `FOR UPDATE`。`INSERT IGNORE` 命中重复唯一键后，多个回放事务可能同时持有该索引记录的共享锁；如果它们随后都通过 `FOR UPDATE` 请求升级为独占锁，就可能互相等待并触发死锁。事件日志在该流程中只需要读取和比较，使用兼容的共享锁即可避免不必要的锁升级。

### 5.3 为什么不是“先查是否存在”

下面的流程存在竞态：

```text
SELECT event_id 是否存在
  -> 不存在
  -> 更新进度
  -> 插入 event_id
```

两个请求可能同时查到不存在，然后都更新进度。当前实现直接竞争数据库唯一键，查询只用于唯一键已存在后的内容核对，不承担最终仲裁职责。

## 6. 前端和既有实验兼容

课程详情页在一次播放会话开始时生成一个 STUDY 事件 ID，保存观看时长时随请求发送。相同请求如果需要重试，应复用这次播放会话已经生成的 ID。

相关实现：

```text
frontend/src/features/course-detail/studyEventId.js
frontend/src/views/user/CourseDetail.vue
```

CON-02 的 100 个请求代表 100 个不同事件，因此其 k6 脚本现在按课程和 VU 生成不同的 `eventId`，仍应累计 500 秒，而不会被 CON-03 的唯一键误去重。

## 7. 实验假设

### H1：单实例相同事件只生效一次

初始学习时长为 0，100 个 VU 使用同一个 `eventId` 上报 5 秒：

```text
HTTP/业务成功 = 100
replayed=false = 1
replayed=true = 99
learned_seconds = 5
progress = 0
status = 1
STUDY 数量 = 1
STUDY duration 总和 = 5
不同的非空 event_id 数量 = 1
```

课程总时长为 705 秒，进度使用整数除法，因此 `5 × 100 DIV 705 = 0`。

### H2：双实例相同事件仍只生效一次

两个 JVM 各收到 50 个请求时，H1 仍成立。唯一胜出请求可能落在任意实例，但整个集群只能出现一次 `replayed=false`。

### H3：不同事件不会被过度去重

100 个 VU 各自使用不同 `eventId` 上报 5 秒：

```text
HTTP/业务成功 = 100
replayed=false = 100
replayed=true = 0
learned_seconds = 500
progress = 70
status = 1
STUDY 数量 = 100
STUDY duration 总和 = 500
不同的非空 event_id 数量 = 100
```

### H4：事件 ID 复用冲突不会修改数据

先使用某个 `eventId` 上报课程 10、时长 5 秒，再用同一个 ID 上报时长 10 秒：

```text
第二次业务码 = 409
learned_seconds 仍为 5
STUDY 数量仍为 1
原日志 duration 仍为 5
```

## 8. 实验数据与隔离

只使用实验数据库：

```text
course_concurrency
```

固定测试身份：

```text
username: stu_newbie
user_id:  6
password: 123456
```

三组并发验证使用不同课程：

| 验证 | 课程 | 初始状态 | 用途 |
|---|---:|---|---|
| 一 | 10 | 0/705 秒 | 单实例相同事件 |
| 二 | 11 | 0/705 秒 | 双实例相同事件 |
| 三 | 12 | 0/705 秒 | 100 个不同事件 |

整个正式实验开始前执行一次准备脚本：

```bash
docker compose -f scripts/docker-compose.yml exec -T \
  -e MYSQL_PWD=root123 mysql mysql -uroot \
  < experiments/concurrency/data/con-03-prepare.sql
```

准备脚本会：

1. 确认用户已经选择目标课程。
2. 将关系表重置为 `progress=0`、`learned_seconds=0`、`status=0`、`complete_time=NULL`。
3. 删除该用户和课程的实验 STUDY/FINISH 日志，事件 ID 会随日志一起删除。
4. 删除对应推荐评分快照，避免旧聚合结果干扰观察。
5. 确认课程视频总时长为 705 秒。
6. 输出 V3 的 `event_id` 字段和 `(user_id, event_id)` 唯一索引信息。

对应文件：

```text
experiments/concurrency/data/con-03-prepare.sql
experiments/concurrency/data/con-03-assert.sql
```

准备脚本只能清理上述实验用户和课程，不能操作日常开发数据库或其他用户数据。如果字段或唯一索引查询没有得到预期结果，应先确认后端已对 `course_concurrency` 执行 V3 迁移，不要继续压测。

正式实验的四个请求轮次共用一次准备结果。不要在轮次之间重新执行准备 SQL，因为它会同时清空课程 10、11、12 的数据；需要重新开始时，应重新准备并从第一轮完整执行。

## 9. 并发前冒烟测试

可以使用 2 VU 先验证协议：

```bash
k6 run \
  -e CON03_BASE_URL=http://127.0.0.1:8080 \
  -e CON03_USERNAME=stu_newbie \
  -e CON03_PASSWORD=123456 \
  -e CON03_COURSE_ID=10 \
  -e CON03_DURATION=5 \
  -e CON03_VUS=2 \
  -e CON03_EVENT_MODE=SAME \
  -e CON03_EVENT_ID=con03-smoke-same \
  experiments/concurrency/k6/con-03-study-idempotency.js
```

这次应得到 `study_processed=1`、`study_replayed=1`。随后复用同一事件 ID，但改为 10 秒：

```bash
k6 run \
  -e CON03_BASE_URL=http://127.0.0.1:8080 \
  -e CON03_USERNAME=stu_newbie \
  -e CON03_PASSWORD=123456 \
  -e CON03_COURSE_ID=10 \
  -e CON03_DURATION=10 \
  -e CON03_VUS=1 \
  -e CON03_EVENT_MODE=CONFLICT \
  -e CON03_EVENT_ID=con03-smoke-same \
  experiments/concurrency/k6/con-03-study-idempotency.js
```

第二次应得到 `study_conflict=1`，且数据库仍只有 5 秒和一条 STUDY。其验证逻辑是：

1. 使用新 `eventId` 上报一次 5 秒 STUDY，预期 `code=200, replayed=false`。
2. 使用完全相同的请求再上报一次，预期 `code=200, replayed=true`。
3. 查询数据库，预期只增加 5 秒、只有一条 STUDY。
4. 使用相同 `eventId`、不同 `duration` 再请求，预期 `code=409`。
5. 再次查询数据库，确认进度和原日志没有变化。

冒烟测试失败时不要进入 100 VU 实验，否则无法区分协议实现错误与并发竞态。

冒烟测试会写入课程 10。冒烟通过后，必须重新执行一次准备 SQL，再开始下面的正式轮次。

## 10. 验证一：单实例相同事件

负载模型：

```text
100 VU × 每个 VU 1 次请求 = 同一逻辑事件的 100 次并发投递
```

```bash
k6 run \
  -e CON03_BASE_URL=http://127.0.0.1:8080 \
  -e CON03_USERNAME=stu_newbie \
  -e CON03_PASSWORD=123456 \
  -e CON03_COURSE_ID=10 \
  -e CON03_DURATION=5 \
  -e CON03_VUS=100 \
  -e CON03_EVENT_MODE=SAME \
  -e CON03_EVENT_ID=con03-10-same \
  experiments/concurrency/k6/con-03-study-idempotency.js
```

数据库必须得到 5 秒、一条 STUDY；k6 必须得到一次首次处理和 99 次回放。

### 10.1 验证事件 ID 冲突

紧接验证一执行，不要清理数据。复用 `con03-10-same`，只把时长改为 10 秒：

```bash
k6 run \
  -e CON03_BASE_URL=http://127.0.0.1:8080 \
  -e CON03_USERNAME=stu_newbie \
  -e CON03_PASSWORD=123456 \
  -e CON03_COURSE_ID=10 \
  -e CON03_DURATION=10 \
  -e CON03_VUS=1 \
  -e CON03_EVENT_MODE=CONFLICT \
  -e CON03_EVENT_ID=con03-10-same \
  experiments/concurrency/k6/con-03-study-idempotency.js
```

k6 必须得到一次 `study_conflict`。课程 10 的学习时长和原日志仍应保持 5 秒，冲突请求不能写入新日志。

## 11. 验证二：双实例相同事件

在两个终端中启动共享 MySQL、Redis、Neo4j 和 JWT 签名密钥的后端实例：

```bash
./scripts/start-concurrency-backend.sh 8080
```

```bash
./scripts/start-concurrency-backend.sh 8081
```

对应地址：

```text
instance 1: http://127.0.0.1:8080
instance 2: http://127.0.0.1:8081
```

```bash
k6 run \
  -e CON03_BASE_URLS=http://127.0.0.1:8080,http://127.0.0.1:8081 \
  -e CON03_USERNAME=stu_newbie \
  -e CON03_PASSWORD=123456 \
  -e CON03_COURSE_ID=11 \
  -e CON03_DURATION=5 \
  -e CON03_VUS=100 \
  -e CON03_EVENT_MODE=SAME \
  -e CON03_EVENT_ID=con03-11-same \
  experiments/concurrency/k6/con-03-study-idempotency.js
```

k6 按 VU 编号让两个实例各收到 50 次请求。最终仍只能是 5 秒、一条 STUDY、一次首次处理和 99 次回放。

这一组证明最终正确性来自共享的 MySQL 唯一索引，不来自 `synchronized`、`ReentrantLock` 或 JVM 本地集合。

## 12. 验证三：100 个不同事件

```bash
k6 run \
  -e CON03_BASE_URL=http://127.0.0.1:8080 \
  -e CON03_USERNAME=stu_newbie \
  -e CON03_PASSWORD=123456 \
  -e CON03_COURSE_ID=12 \
  -e CON03_DURATION=5 \
  -e CON03_VUS=100 \
  -e CON03_EVENT_MODE=UNIQUE \
  -e CON03_EVENT_ID=con03-12-unique \
  experiments/concurrency/k6/con-03-study-idempotency.js
```

UNIQUE 模式会把 VU 和迭代编号追加到事件 ID，例如 `con03-12-unique-1-0`。最终必须得到 500 秒、100 条 STUDY、100 个不同事件 ID，且所有响应均为 `replayed=false`。

这一组用于防止错误实现按用户、课程、请求内容或时间窗口粗粒度去重，导致真实事件被丢弃。

## 13. k6 脚本设计

已实现脚本：

```text
experiments/concurrency/k6/con-03-study-idempotency.js
```

脚本使用 `per-vu-iterations`，登录只在 `setup()` 中执行一次。通过环境变量切换模式：

```text
CON03_EVENT_MODE=SAME    所有 VU 使用同一个 eventId
CON03_EVENT_MODE=UNIQUE  每个 VU 使用不同 eventId
CON03_EVENT_MODE=CONFLICT 复用已有 eventId，且预期所有请求返回业务码 409
```

支持的环境变量：

```text
CON03_BASE_URL / CON03_BASE_URLS
CON03_USERNAME / CON03_PASSWORD / CON03_TOKEN
CON03_COURSE_ID
CON03_DURATION
CON03_VUS
CON03_EVENT_ID
CON03_EVENT_MODE
CON03_MAX_DURATION
```

SAME 模式阈值：

```text
study_processed = 1
study_replayed = VUS - 1
study_conflict = 0
study_unexpected = 0
checks = 100%
```

UNIQUE 模式阈值：

```text
study_processed = VUS
study_replayed = 0
study_conflict = 0
study_unexpected = 0
checks = 100%
```

CONFLICT 模式阈值：

```text
study_processed = 0
study_replayed = 0
study_conflict = VUS
study_unexpected = 0
checks = 100%
```

双实例模式还必须验证每个实例实际收到预期请求数。k6 的绿色结果不能单独证明幂等，结束后必须执行 SQL 检查最终数据。

## 14. 数据库统一断言

四个请求轮次全部完成后执行：

```bash
docker compose -f scripts/docker-compose.yml exec -T \
  -e MYSQL_PWD=root123 mysql mysql -uroot \
  < experiments/concurrency/data/con-03-assert.sql
```

第一张结果表中课程 10、11、12 的 `assertion_result` 必须全部为 `PASS`。第二张结果表应只显示课程 10、11 各一条相同事件日志，且 `duration=5`。

三组预期汇总：

| 验证 | learned | progress | status | STUDY 数 | 时长和 | 事件 ID 数 |
|---|---:|---:|---:|---:|---:|---:|
| 一：单实例相同事件 | 5 | 0 | 1 | 1 | 5 | 1 |
| 二：双实例相同事件 | 5 | 0 | 1 | 1 | 5 | 1 |
| 三：不同事件 | 500 | 70 | 1 | 100 | 500 | 100 |

三组都不应产生 FINISH，`complete_time` 应为 `NULL`。

## 15. 可选故障与边界验证

### 15.1 事务回滚后重试

该扩展实验使用课程 14，避免重置主实验课程 10、11、12。故障注入完全保存在后端测试源码中，不进入正式 Service 和正式 JAR；只有显式启用并命中指定 `eventId` 时，测试专用 Mapper 代理才会在 STUDY 日志插入成功后抛出一次异常。

涉及文件：

```text
experiments/concurrency/k6/con-03-rollback-retry.js
experiments/concurrency/data/con-03-rollback-prepare.sql
experiments/concurrency/data/con-03-rollback-assert.sql
backend/src/test/java/com/sy/course_system/experiment/ConcurrencyExperimentApplication.java
backend/src/test/java/com/sy/course_system/experiment/StudyInsertFaultInjectionConfiguration.java
```

故障实验与正常启动使用不同类路径：

```text
正常启动：java -jar 正式 JAR，不包含任何故障注入类。
故障实验：spring-boot:test-run 加载 src/test/java 中的实验启动器和 Mapper 代理。
```

在单独终端启动一个开启故障注入的实验实例；启动脚本会自动编译测试源码，并通过 `spring-boot:test-run` 使用测试类路径：

```bash
CON03_FAIL_AFTER_STUDY_INSERT_ENABLED=true \
CON03_FAIL_AFTER_STUDY_INSERT_EVENT_ID=con03-rollback-event \
./scripts/start-concurrency-backend.sh 8080
```

启动日志会显示 `launcher: test classpath` 和命中的事件 ID。两个条件缺一不可：开关必须是 `true`，且请求事件 ID 必须完全等于 `con03-rollback-event`；同一实例只会注入一次故障。正式 JAR 即使收到相同环境变量，也不具备故障注入能力。

在另一个终端准备课程 14：

```bash
docker compose -f scripts/docker-compose.yml exec -T \
  -e MYSQL_PWD=root123 mysql mysql -uroot \
  < experiments/concurrency/data/con-03-rollback-prepare.sql
```

第一阶段只发送一次请求，并将 HTTP 500 明确标记为本轮预期结果：

```bash
k6 run \
  -e CON03_ROLLBACK_BASE_URL=http://127.0.0.1:8080 \
  -e CON03_ROLLBACK_USERNAME=stu_newbie \
  -e CON03_ROLLBACK_PASSWORD=123456 \
  -e CON03_ROLLBACK_COURSE_ID=14 \
  -e CON03_ROLLBACK_DURATION=5 \
  -e CON03_ROLLBACK_EVENT_ID=con03-rollback-event \
  -e CON03_ROLLBACK_PHASE=FAIL \
  experiments/concurrency/k6/con-03-rollback-retry.js
```

预期：

```text
rollback_expected_failure = 1
rollback_retry_processed  = 0
rollback_retry_replayed   = 0
rollback_unexpected       = 0
http_req_failed           = 0%
```

这里 `http_req_failed=0%` 并不表示没有发生 HTTP 500，而是脚本使用 `http.expectedStatuses(500)` 将这次人为故障声明为实验预期。后端日志应出现 `CON-03 故障注入：STUDY 事件日志插入后、事务提交前`。

不要立即重试，先查询 MySQL 中间状态：

```bash
docker compose -f scripts/docker-compose.yml exec -T \
  -e MYSQL_PWD=root123 mysql mysql -uroot \
  < experiments/concurrency/data/con-03-rollback-assert.sql
```

必须得到：

```text
course_id = 14
learned_seconds = 0
study_count = 0
event_id = NULL
assertion_state = ROLLED_BACK
```

这一步直接证明：事件日志虽然曾经成功执行插入，但由于异常发生在事务提交前，它与学习进度一起回滚，没有留下永久阻塞重试的“脏占位”。

保持同一个后端进程运行，不要重启，然后运行恢复阶段：

```bash
k6 run \
  -e CON03_ROLLBACK_BASE_URL=http://127.0.0.1:8080 \
  -e CON03_ROLLBACK_USERNAME=stu_newbie \
  -e CON03_ROLLBACK_PASSWORD=123456 \
  -e CON03_ROLLBACK_COURSE_ID=14 \
  -e CON03_ROLLBACK_DURATION=5 \
  -e CON03_ROLLBACK_EVENT_ID=con03-rollback-event \
  -e CON03_ROLLBACK_PHASE=RECOVER \
  experiments/concurrency/k6/con-03-rollback-retry.js
```

恢复阶段顺序发送两个完全相同的请求：

```text
第一次重试：code=200，replayed=false
再次请求：code=200，replayed=true

rollback_expected_failure = 0
rollback_retry_processed  = 1
rollback_retry_replayed   = 1
rollback_unexpected       = 0
```

再次执行相同的断言 SQL，最终应得到：

```text
course_id = 14
learned_seconds = 5
study_count = 1
duration_sum = 5
event_id = con03-rollback-event
assertion_state = RECOVERED
```

第一次重试必须是 `replayed=false`：前一次事务已经回滚，因此它并不属于已处理事件；只有真正成功提交之后，再次请求才应该得到 `replayed=true`。

如需重新执行完整实验，先停止并重新启动启用了故障注入的测试后端，再重新执行课程 14 准备 SQL。实验结束后使用不带故障环境变量的正常启动命令，脚本将重新使用不包含实验类的正式 JAR。

### 15.2 事件 ID 大小写

由于 `event_id` 使用 `ascii_bin`：

```text
study-event-a
study-event-A
```

会被视为两个不同事件。客户端应统一使用 UUID 或其他规范化格式，不依赖大小写变化表达重试。

### 15.3 日志保留时间

当前幂等凭据就是行为日志本身。只要日志保留，历史事件 ID 就不会被同一用户重新使用。如果未来归档或删除行为日志，归档策略必须考虑客户端和消息系统可能发生重试的最长时间。

## 16. MySQL 幂等与跨系统一致性的边界

事件日志、进度关系和 FINISH 日志位于同一个 MySQL 事务，因此这些 MySQL 状态能够随事务一起提交或回滚。

但当前学习行为还可能同步修改 Redis 热度、失效缓存，并在首次完课时更新 Neo4j。这些系统不参与 MySQL 本地事务，仍存在故障窗口：

```text
外部副作用已经成功
  -> MySQL 事务随后回滚或进程崩溃
  -> 客户端重试
  -> 外部副作用可能再次执行
```

CON-03 的核心完成标准是 MySQL 进度和行为日志不重复；正常的幂等回放也不会再次进入外部副作用。要保证故障场景下跨 MySQL、Redis、Neo4j 的可靠最终一致性，需要事务 Outbox、幂等消费者和补偿机制，属于 CON-06 等后续实验范围。

## 17. 需要记录的结果

| 指标 | 单实例相同事件 | 双实例相同事件 | 不同事件 |
|---|---:|---:|---:|
| HTTP 请求数 | | | |
| 首次处理数 | | | |
| 幂等回放数 | | | |
| 事件冲突数 | | | |
| 非预期响应数 | | | |
| `learned_seconds` | | | |
| STUDY 数量 | | | |
| STUDY 时长和 | | | |
| 不同事件 ID 数 | | | |
| p95 | | | |
| p99 | | | |

还应记录 MySQL 唯一键竞争和锁等待、应用错误日志、双实例请求分布。SAME 模式刻意让大量请求竞争同一个唯一键，适合验证正确性，但其吞吐量不能代表普通学习流量容量。

## 18. 常见错误方案

### 18.1 使用请求内容作为唯一标识

相同的 `courseId + duration` 可能是连续发生的两个真实事件，不能仅因为内容相同就去重。

### 18.2 由服务端为每次请求生成 eventId

服务端每收到一次重试都会生成新 ID，仍然无法把它和首次请求关联起来。事件 ID 必须由能够识别重试的上游生成并复用。

### 18.3 只在 Redis 中保存 eventId

Redis 丢数据、过期或发生主从切换后，重复事件可能再次写入 MySQL。Redis 可以削峰，但不能代替数据库唯一约束作为最终正确性依据。

### 18.4 重复请求直接返回错误

如果首次请求已经提交但响应丢失，客户端无法知道业务是否成功。相同事件、相同内容应返回幂等成功；只有同一事件 ID 对应不同内容时才返回冲突。

## 19. 面试表达模板

可以按照下面的顺序说明实验：

1. CON-02 解决并发累加不丢失，CON-03 解决同一事件重复投递不重复生效。
2. 客户端为逻辑学习事件生成稳定 `eventId`，重试复用、新事件更换。
3. `learning_behavior` 使用 `(user_id, event_id)` 唯一索引完成单实例和多实例仲裁。
4. STUDY 日志先插入取得处理资格，胜出者才更新学习进度。
5. 事件日志、进度和 FINISH 位于同一 MySQL 事务，失败时一起回滚。
6. 相同事件、相同内容返回 `replayed=true`；相同事件、不同内容返回 409。
7. SAME 和 UNIQUE 两种负载分别证明“重复不多算”和“正常事件不少算”。
8. MySQL 本地事务不能自动覆盖 Redis、Neo4j 等外部副作用，跨系统可靠性需要 Outbox 和幂等消费者。

## 20. 实验完成标准

- 单实例 100 次相同事件只累计 5 秒，只写一条 STUDY。
- 双实例各接收 50 次请求时，最终仍只累计一次。
- SAME 模式中首次处理恰好 1 次、幂等回放恰好 99 次。
- 100 个不同事件全部累计，证明没有过度去重。
- 相同事件 ID 携带不同内容时返回 409 且数据库状态不变。
- 可选故障注入中，事务回滚测试证明同一事件能够安全重试。
- k6 输出和 SQL 最终状态共同证明结论，不能只看 HTTP 200。
- 结果报告明确说明 MySQL 幂等与跨系统一致性的边界。
