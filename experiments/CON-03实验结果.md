# CON-03 STUDY 请求重复消费幂等实验结果

## 1. 实验信息

| 项目 | 值 |
|---|---|
| 实验日期 | 2026-08-22 |
| Git 分支 | `codex/concurrency-optimization` |
| 基线 commit | `20aca0a`；CON-03 改造与实验文件尚在工作区 |
| 操作系统 | macOS 26.5.2，darwin/arm64 |
| k6 | `v2.2.0` |
| 数据库 | MySQL 8，`course_concurrency` |
| 测试用户 | `stu_newbie`，user_id=6 |
| 负载模型 | `per-vu-iterations`，每个 VU 执行一次 STUDY 请求 |
| 轮次 A | 单实例，课程 10，100 VU 复用同一个事件 ID，上报 5 秒 |
| 冲突轮 | 单实例，课程 10，复用轮次 A 的事件 ID，改为上报 10 秒 |
| 轮次 B | 双实例，课程 11，100 VU 复用同一个事件 ID，上报 5 秒 |
| 轮次 C | 单实例，课程 12，100 VU 使用不同事件 ID，各上报 5 秒 |

实验方案见 [CON-03 实验文档](./concurrency/docs/03-study-event-idempotency.md)，压测脚本为 [`con-03-study-idempotency.js`](./concurrency/k6/con-03-study-idempotency.js)，数据库最终状态由 [`con-03-assert.sql`](./concurrency/data/con-03-assert.sql) 断言。

## 2. 实验假设

- H1：单实例收到同一事件的 100 次并发投递时，只允许一次首次处理，其余 99 次返回幂等回放；学习时长只增加 5 秒，只写入一条 STUDY。
- H2：请求均匀分发到两个后端实例时，H1 仍成立，说明最终仲裁不依赖 JVM 本地状态。
- H3：100 个不同事件 ID 必须全部处理，最终增加 500 秒并写入 100 条 STUDY，避免过度去重。
- H4：已经使用的事件 ID 携带不同 `duration` 再次提交时，必须返回业务码 409，且不能修改原进度或原行为日志。
- H5：所有轮次的 HTTP 失败、非预期响应和中断迭代均为 0，最终数据库状态与响应语义一致。

## 3. k6 结果汇总

| 轮次 | 实例数 | 课程 | VU/请求语义 | 实例分流 | 首次处理 | 幂等回放 | 冲突 | 非预期 | HTTP 失败率 | avg | p95 | max | 完成迭代 |
|---|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| A：相同事件 | 1 | 10 | 100 VU，同一 ID | 100 | 1 | 99 | 0 | 0 | 0% | 121.32ms | 165.40ms | 167.47ms | 100/100 |
| 冲突：不同内容 | 1 | 10 | 1 VU，复用 A 的 ID | 1 | 0 | 0 | 1 | 0 | 0% | 9.28ms | 12.85ms | 13.25ms | 1/1 |
| B：跨 JVM 相同事件 | 2 | 11 | 100 VU，同一 ID | 50/50 | 1 | 99 | 0 | 0 | 0% | 396.49ms | 475.27ms | 497.59ms | 100/100 |
| C：不同事件 | 1 | 12 | 100 VU，不同 ID | 100 | 100 | 0 | 0 | 0 | 0% | 233.46ms | 272.15ms | 275.52ms | 100/100 |

四轮 k6 阈值全部通过。轮次 A、B、C 均为 203/203 个检查通过，冲突轮为 5/5 个检查通过：

```text
SAME：     study_processed=1,   study_replayed=99, study_conflict=0
CONFLICT： study_processed=0,   study_replayed=0,  study_conflict=1
UNIQUE：   study_processed=100, study_replayed=0,  study_conflict=0
全部轮次： study_unexpected=0, checks=100%, http_req_failed=0%
```

轮次 B 的两个 `backend_index` 请求数均为 50，证明两个 JVM 实际都参与了处理，而不是只启动了第二个实例却仍把请求全部发往第一个实例。

## 4. 数据库正确性结果

### 4.1 最终状态

| 课程 | 场景 | learned | progress | status | STUDY 数 | 时长和 | 事件 ID 数 | FINISH 数 | 结果 |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---|
| 10 | 单实例相同事件 + 冲突 | 5 | 0 | 1 | 1 | 5 | 1 | 0 | PASS |
| 11 | 双实例相同事件 | 5 | 0 | 1 | 1 | 5 | 1 | 0 | PASS |
| 12 | 100 个不同事件 | 500 | 70 | 1 | 100 | 500 | 100 | 0 | PASS |

三门课程的 `complete_time` 均为 `NULL`。课程总时长为 705 秒，本实验没有任何课程达到完课条件，因此没有生成 FINISH，符合预期。

### 4.2 各假设的数据库证据

- 课程 10 最终只有 5 秒和一条 `duration=5` 的 STUDY，说明相同事件的 99 次回放没有重复更新进度。冲突轮提交的是 10 秒，但原日志仍为 5 秒，学习时长也仍为 5 秒，H1、H4 通过。
- 课程 11 在两个实例各处理 50 个请求后，仍只有 5 秒、一条 STUDY 和一个事件 ID，H2 通过。
- 课程 12 得到 500 秒、100 条 STUDY 和 100 个不同事件 ID，且 `progress=500×100 DIV 705=70`，H3 通过。
- k6 响应计数与 SQL 最终状态一致，没有出现“接口报告回放，但数据库多累计”或“接口报告首次处理，但数据库少写入”的分裂结果，H5 通过。

原始明细输出中只保留了课程 10 的事件行：

```text
10  con03-10-same  STUDY  5  2026-08-22 21:37:21
```

课程 11 的汇总断言已经证明其 `study_count=1`、`duration_sum=5`、`distinct_event_count=1`，但本次文档没有保留该行的 `event_id/create_time` 明细。这不影响核心正确性结论，不过后续实验报告可以完整粘贴断言 SQL 的第二张结果表，形成更完整的原始证据链。

## 5. 为什么这些结果能够证明幂等性

### 5.1 SAME 不是“100 次都没处理”，而是“处理一次、确认 99 次”

轮次 A 和 B 都得到一次 `replayed=false` 与 99 次 `replayed=true`。这说明 100 个请求均获得了明确业务结果：唯一胜出者执行学习进度更新，其余请求确认同一事件已经成功处理，而不是简单丢弃、超时或统一返回错误。

数据库中的 `(user_id, event_id)` 唯一索引是最终仲裁点。100 个请求同时执行 `INSERT IGNORE` 时，只有一个事务能够插入事件日志并获得处理资格；其余事务在唯一键竞争结束后，通过 `SELECT ... FOR SHARE` 当前读取得已提交日志，核对课程、行为类型和时长后返回回放结果。

### 5.2 双实例证明 JVM 本地锁不是正确性前提

轮次 B 将请求严格分成 50/50，两个实例没有共享 Java 堆内存。如果实现依赖 `synchronized`、`ReentrantLock` 或 JVM 本地事件集合，两个实例各自都可能放行一次，最终形成 10 秒和两条日志。实际结果仍是 5 秒和一条日志，说明共享 MySQL 唯一约束能够跨 JVM 仲裁。

### 5.3 UNIQUE 证明没有过度去重

幂等不能把“内容相同”误认为“同一事件”。轮次 C 的 100 个请求虽然 `courseId` 和 `duration` 完全相同，但事件 ID 不同，因此全部得到 `replayed=false`，最终精确累计 500 秒。服务端去重依据是客户端提供的逻辑事件身份，而不是请求内容、用户课程组合或时间窗口。

### 5.4 CONFLICT 防止事件 ID 被错误复用

如果相同事件 ID 的不同内容也直接返回回放成功，客户端错误可能被静默掩盖。冲突轮复用 `con03-10-same`，把时长从 5 秒改为 10 秒，服务端返回业务码 409；数据库仍保存原始 5 秒事件。这证明回放前执行了内容一致性校验。

## 6. 性能结果解读

本实验的主要目标是并发正确性，性能数字只代表当前机器上的一次短突发观测：

- 单实例 SAME 的 p95 为 165.40ms，双实例 SAME 的 p95 为 475.27ms。双实例并没有更快，因为两个 JVM 仍集中竞争 MySQL 中同一个唯一键，并且运行在同一台 Mac 上，会增加 CPU、连接池和数据库锁竞争。
- UNIQUE 的 p95 为 272.15ms。它避免了同一唯一键的热点竞争，但 100 个事件都会完整执行行为落库、进度更新及相关副作用，实际业务工作量远大于 99 次快速回放。
- 每个 100 VU 轮次持续时间不足 1 秒，只执行了一次，没有预热轮、重复样本、稳定到达率或持续负载，因此不能据此推导系统最大 QPS，也不能把不同轮次的 p95 当作严格容量对比。
- k6 汇总的 `http_req_duration` 包含一次 setup 登录请求；由于正式轮次有 100 个业务请求，其影响较小，但后续性能专项实验应为业务请求建立独立 Trend 指标。

SAME 模式故意制造单个唯一键热点，适合验证幂等正确性和观察锁竞争，不代表正常学习流量的事件分布。

## 7. 实验结论与边界

CON-03 的核心假设全部通过：

- 单实例下，同一学习事件并发投递 100 次只生效一次，其余 99 次返回幂等成功。
- 双实例各接收 50 个请求时，集群范围内仍只生效一次，数据库唯一索引能够跨 JVM 仲裁。
- 100 个不同事件全部生效，没有因为请求内容相同而被过度去重。
- 相同事件 ID 携带不同内容时返回 409，原进度和原日志保持不变。
- k6 响应语义、请求分流和 MySQL 最终状态相互吻合，所有核心 SQL 断言均为 `PASS`。
- 在事件日志插入成功后注入一次异常时，HTTP 500 后事件日志和学习进度均回滚；同一事件随后能够首次处理并继续按幂等语义回放。

由此可以确认：CON-02 解决了“不同事件并发累加不丢失”，CON-03 进一步解决了“同一事件重复投递不重复生效”。当前方案以客户端稳定 `eventId`、数据库唯一键和同一 MySQL 事务共同保证学习进度与行为日志的幂等一致性。

仍需明确以下边界：

- 课程 14 的故障扩展已经完成：故障阶段得到 `learned_seconds=0`、`study_count=0`、`event_id=NULL`、`ROLLED_BACK`；恢复阶段得到 `learned_seconds=5`、`study_count=1`、`event_id=con03-rollback-event`、`RECOVERED`。
- 故障注入实现位于 `backend/src/test/java/com/sy/course_system/experiment`，通过测试类路径启动器包装 MyBatis Mapper；正式 Service 和正式 JAR 均不包含故障开关或主动抛错逻辑。
- MySQL 中的事件日志、进度和 FINISH 可以共同提交或回滚，但 Redis、缓存和 Neo4j 不参与 MySQL 本地事务。进程在外部副作用成功后崩溃，仍可能产生跨系统不一致，需要后续通过 Outbox、幂等消费者或补偿机制研究。
- 当前幂等凭据依赖 `learning_behavior` 日志持续保留；未来如果归档或删除日志，保留周期必须覆盖客户端和消息链路的最大重试窗口。
- 本实验验证的是正确性，不是容量上限。若要评估性能，需要增加预热、多次重复、持续到达率、MySQL 锁等待和连接池指标。

因此 CON-03 及其事务回滚扩展均可以标记为完成。后续可进入 CON-04 推荐缓存击穿与雪崩实验。

## 8. 原始 k6 输出

### 8.1 验证一：单实例相同事件

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

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-03-study-idempotency.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * study_event_idempotency: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==100' count=100

    study_conflict
    ✓ 'count==0' count=0

    study_processed
    ✓ 'count==1' count=1

    study_replayed
    ✓ 'count==99' count=99

    study_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 203     1119.184925/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 学习行为 HTTP 状态为 200
    ✓ 学习行为响应符合当前模式

    CUSTOM
    study_conflict.....................: 0     0/s
    study_processed....................: 1     5.513226/s
    study_replayed.....................: 99    545.809397/s
    study_unexpected...................: 0     0/s

    HTTP
    http_req_duration..................: avg=121.32ms min=5.64ms med=130.07ms max=167.47ms p(90)=164.13ms p(95)=165.4ms
      { expected_response:true }.......: avg=121.32ms min=5.64ms med=130.07ms max=167.47ms p(90)=164.13ms p(95)=165.4ms
    http_req_failed....................: 0.00% 0 out of 101
    http_reqs..........................: 101   556.835849/s
      { phase:load,backend_index:1 }...: 100   551.322623/s

    EXECUTION
    iteration_duration.................: avg=124.41ms min=43.9ms med=132.68ms max=170.3ms  p(90)=167.36ms p(95)=168.23ms
    iterations.........................: 100   551.322623/s

    NETWORK
    data_received......................: 44 kB 244 kB/s
    data_sent..........................: 46 kB 253 kB/s




running (00.2s), 000/100 VUs, 100 complete and 0 interrupted iterations
study_event_idempotency ✓ [======================================] 100 VUs  00.2s/30s  100/100 iters, 1 per VU
```

### 8.2 验证事件 ID 冲突

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

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-03-study-idempotency.js
        output: -

     scenarios: (100.00%) 1 scenario, 1 max VUs, 30s max duration (incl. graceful stop):
              * study_event_idempotency: 1 iterations for each of 1 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==1' count=1

    study_conflict
    ✓ 'count==1' count=1

    study_processed
    ✓ 'count==0' count=0

    study_replayed
    ✓ 'count==0' count=0

    study_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 5       211.345/s
    checks_succeeded...: 100.00% 5 out of 5
    checks_failed......: 0.00%   0 out of 5

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 学习行为 HTTP 状态为 200
    ✓ 学习行为响应符合当前模式

    CUSTOM
    study_conflict.....................: 1      42.269/s
    study_processed....................: 0      0/s
    study_replayed.....................: 0      0/s
    study_unexpected...................: 0      0/s

    HTTP
    http_req_duration..................: avg=9.28ms min=5.32ms med=9.28ms max=13.25ms p(90)=12.46ms p(95)=12.85ms
      { expected_response:true }.......: avg=9.28ms min=5.32ms med=9.28ms max=13.25ms p(90)=12.46ms p(95)=12.85ms
    http_req_failed....................: 0.00%  0 out of 2
    http_reqs..........................: 2      84.538/s
      { phase:load,backend_index:1 }...: 1      42.269/s

    EXECUTION
    iteration_duration.................: avg=13.9ms min=13.9ms med=13.9ms max=13.9ms  p(90)=13.9ms  p(95)=13.9ms
    iterations.........................: 1      42.269/s

    NETWORK
    data_received......................: 1.1 kB 45 kB/s
    data_sent..........................: 668 B  28 kB/s




running (00.0s), 0/1 VUs, 1 complete and 0 interrupted iterations
study_event_idempotency ✓ [ 100% ] 1 VUs  00.0s/30s  1/1 iters, 1 per VU

```

### 8.3 验证二：双实例相同事件

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

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-03-study-idempotency.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * study_event_idempotency: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==50' count=50

    http_reqs{phase:load,backend_index:2}
    ✓ 'count==50' count=50

    study_conflict
    ✓ 'count==0' count=0

    study_processed
    ✓ 'count==1' count=1

    study_replayed
    ✓ 'count==99' count=99

    study_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 203     336.528977/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 学习行为 HTTP 状态为 200
    ✓ 学习行为响应符合当前模式

    CUSTOM
    study_conflict.....................: 0     0/s
    study_processed....................: 1     1.657778/s
    study_replayed.....................: 99    164.120043/s
    study_unexpected...................: 0     0/s

    HTTP
    http_req_duration..................: avg=396.49ms min=98.66ms  med=471.72ms max=497.59ms p(90)=474.86ms p(95)=475.27ms
      { expected_response:true }.......: avg=396.49ms min=98.66ms  med=471.72ms max=497.59ms p(90)=474.86ms p(95)=475.27ms
    http_req_failed....................: 0.00% 0 out of 101
    http_reqs..........................: 101   167.435599/s
      { phase:load,backend_index:1 }...: 50    82.888911/s
      { phase:load,backend_index:2 }...: 50    82.888911/s

    EXECUTION
    iteration_duration.................: avg=400.67ms min=291.48ms med=473.38ms max=498.56ms p(90)=475.94ms p(95)=476.23ms
    iterations.........................: 100   165.777821/s

    NETWORK
    data_received......................: 44 kB 73 kB/s
    data_sent..........................: 46 kB 76 kB/s




running (00.6s), 000/100 VUs, 100 complete and 0 interrupted iterations
study_event_idempotency ✓ [======================================] 100 VUs  00.5s/30s  100/100 iters, 1 per VU
```

### 8.4 验证三：100 个不同事件

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

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-03-study-idempotency.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * study_event_idempotency: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==100' count=100

    study_conflict
    ✓ 'count==0' count=0

    study_processed
    ✓ 'count==100' count=100

    study_replayed
    ✓ 'count==0' count=0

    study_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 203     707.413202/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 学习行为 HTTP 状态为 200
    ✓ 学习行为响应符合当前模式

    CUSTOM
    study_conflict.....................: 0     0/s
    study_processed....................: 100   348.47941/s
    study_replayed.....................: 0     0/s
    study_unexpected...................: 0     0/s

    HTTP
    http_req_duration..................: avg=233.46ms min=5.01ms  med=252.8ms  max=275.52ms p(90)=270.86ms p(95)=272.15ms
      { expected_response:true }.......: avg=233.46ms min=5.01ms  med=252.8ms  max=275.52ms p(90)=270.86ms p(95)=272.15ms
    http_req_failed....................: 0.00% 0 out of 101
    http_reqs..........................: 101   351.964204/s
      { phase:load,backend_index:1 }...: 100   348.47941/s

    EXECUTION
    iteration_duration.................: avg=237.05ms min=72.75ms med=254.03ms max=276.9ms  p(90)=272.89ms p(95)=273.59ms
    iterations.........................: 100   348.47941/s

    NETWORK
    data_received......................: 44 kB 155 kB/s
    data_sent..........................: 47 kB 162 kB/s




running (00.3s), 000/100 VUs, 100 complete and 0 interrupted iterations
study_event_idempotency ✓ [ 100% ] 100 VUs  00.3s/30s  100/100 iters, 1 per VU

```

## 9. 原始数据库断言

```bash
course_id	progress	learned_seconds	status	complete_time	study_count	duration_sum	distinct_event_count	finish_count	assertion_result
10	0	5	1	NULL	1	5	1	0	PASS
11	0	5	1	NULL	1	5	1	0	PASS
12	70	500	1	NULL	100	500	100	0	PASS
course_id	event_id	behavior_type	duration	create_time
10	con03-10-same	STUDY	5	2026-08-22 21:37:21
```
