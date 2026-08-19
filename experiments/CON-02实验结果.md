# CON-02 并发学习进度与首次完课实验结果

## 1. 实验信息

| 项目 | 值 |
|---|---|
| 实验日期 | 2026-08-19 |
| Git 分支 | `codex/concurrency-optimization` |
| 基线 commit | `7d8ffd8` |
| 操作系统 | macOS 26.5.2 |
| k6 | `v2.2.0`，darwin/arm64 |
| 数据库 | MySQL 8，`course_concurrency` |
| 测试用户 | `stu_newbie`，user_id=6 |
| 负载模型 | `per-vu-iterations`，100 VU，每个 VU 上报一次 5 秒的 STUDY |
| 轮次 A | 单实例，课程 10，从 0/705 秒开始 |
| 轮次 B | 单实例，课程 11，从 700/705 秒开始 |
| 轮次 C | 双实例，课程 12，从 700/705 秒开始 |

实验方案见 [CON-02 实验文档](./concurrency/docs/02-concurrent-learning-progress.md)，压测脚本为 [`con-02-concurrent-study-progress.js`](./concurrency/k6/con-02-concurrent-study-progress.js)。

## 2. 实验假设

- H1：100 个独立的 5 秒学习事件全部成功后，`learned_seconds` 应为 500，不发生丢失更新；按 Mapper 中的 `DIV` 公式，`progress` 应为 70。
- H2：从 700/705 秒开始并发上报时，最终时长封顶为 705，只生成一条 `FINISH`，`complete_time` 只设置一次。
- H3：两个后端实例各处理 50 个请求时，H2 仍然成立。
- H4：三轮 HTTP 失败、业务失败和非预期响应均为 0，所有迭代完整执行。

## 3. k6 结果汇总

| 轮次 | 实例数 | 课程 | 实例分流 | 业务成功 | 非预期 | HTTP 失败率 | avg | p95 | max | 完成迭代 |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|
| A：并发累加 | 1 | 10 | 100 | 100 | 0 | 0% | 630.58ms | 687.81ms | 698.56ms | 100/100 |
| B：首次完课 | 1 | 11 | 100 | 100 | 0 | 0% | 198.99ms | 249.59ms | 251.22ms | 100/100 |
| C：跨 JVM 完课 | 2 | 12 | 50/50 | 100 | 0 | 0% | 374.27ms | 639.72ms | 644.09ms | 100/100 |

三轮 k6 阈值全部通过：

```text
checks           = 100%
study_success    = 100
study_unexpected = 0
```

每轮包含 3 个登录检查和 100 × 2 个学习行为检查，因此均为 203/203 个检查通过。轮次 C 的两个 `backend_index` 数量阈值分别为 50，证明两个 JVM 都实际处理了请求。

## 4. 数据库正确性结果

SQL 原始导出：

- [轮次 A](./round-a.csv)
- [轮次 B](./round-b.csv)
- [轮次 C](./round-c.csv)
- [三轮汇总](./round-result.csv)

### 4.1 轮次 A：并发累加

| 断言 | 期望 | 实际 | 结果 |
|---|---:|---:|---|
| `learned_seconds` | 500 | 500 | 通过 |
| `STUDY` 行为数 | 100 | 100 | 通过 |
| `STUDY duration` 总和 | 500 | 500 | 通过 |
| `status` | 1 | 1 | 通过 |
| `complete_time` | NULL | NULL | 通过 |
| `progress` | 70 | 71 | **未通过** |

学习时长和行为日志证明 100 个增量全部提交，没有发生丢失更新；但进度值比 `500 × 100 DIV 705 = 70` 多 1，关系表内部出现 `learned_seconds=500`、`progress=71` 的不一致状态。

### 4.2 轮次 B：单实例首次完课

```text
progress=100
learned_seconds=705
status=2
complete_time=2026-08-19 17:17:41
STUDY: 100 条，duration_sum=500
FINISH: 1 条，duration_sum=0
```

所有断言通过。100 个请求同时跨过完课线时，学习时长正确封顶，首次完课事件只生成一次。

### 4.3 轮次 C：双实例首次完课

```text
progress=100
learned_seconds=705
status=2
complete_time=2026-08-19 17:39:52
STUDY: 100 条，duration_sum=500
FINISH: 1 条，duration_sum=0
```

所有断言通过。两个实例各处理 50 个请求时，最终仍只有一条 `FINISH`，说明 `complete_time IS NULL` 条件更新能够跨 JVM 充当首次完成门闩。

### 4.4 三轮最终状态

| 课程 | progress | learned_seconds | status | complete_time | STUDY 数 | STUDY 时长和 | FINISH 数 |
|---:|---:|---:|---:|---|---:|---:|---:|
| 10 | 71 | 500 | 1 | NULL | 100 | 500 | 0 |
| 11 | 100 | 705 | 2 | 2026-08-19 17:17:41 | 100 | 500 | 1 |
| 12 | 100 | 705 | 2 | 2026-08-19 17:39:52 | 100 | 500 | 1 |

## 5. 进度偏差分析

当前 Mapper 的更新顺序是：

```sql
SET learned_seconds = LEAST(learned_seconds + :duration, :totalSeconds),
    progress = LEAST(
        100,
        (LEAST(learned_seconds + :duration, :totalSeconds) * 100) DIV :totalSeconds
    )
```

使用相同赋值顺序在 MySQL 临时表中复现后，确认同一条 `UPDATE` 的后续赋值读取到了前面已经更新的 `learned_seconds`，随后又加了一次 `duration`。临时表从 495 秒上报 5 秒，同样得到 `learned_seconds=500, progress=71`。最后一次更新的进度实际按下面的值计算：

```text
(500 + 5) × 100 DIV 705 = 71
```

而正确的一致性公式应为：

```text
500 × 100 DIV 705 = 70
```

这不只是显示误差：在接近完课线时，进度和 `status` 可能比 `learned_seconds` 提前一个事件达到 100。例如从 695 秒上报 5 秒，关系时长可能只有 700 秒，但进度计算可能已经使用 705 秒并触发完课。轮次 B、C 都从 700 秒开始，第一次 5 秒上报本来就应该完成，因此没有覆盖这个提前完课边界。

本次只记录和定位问题，不在结果整理提交中修改业务 Mapper。修复后需要重新运行轮次 A，并增加“695 + 5 秒不能完课”的边界用例。

## 6. 性能结果解读

轮次 A、B、C 的 p95 分别为 687.81ms、249.59ms、639.72ms，但不能用这三个单次突发结果比较容量：

- 轮次 A 是第一轮，可能包含 JVM JIT、连接池和缓存预热成本。
- 三轮都集中竞争同一条 `user_course_relation`，行锁串行化本来就是主要等待来源。
- 学习行为链路还同步涉及行为落库、Redis 热度、缓存失效及事务提交后的推荐评分快照刷新。
- 双实例运行在同一台 Mac 上，增加 JVM 的同时也增加了本机 CPU、内存和数据库连接竞争。
- 每轮只执行一次且持续不足 1 秒，`req/s` 只是短窗口平均值，不代表系统最大吞吐量。

因此 CON-02 的性能数据只作为本次实验环境记录，核心结论来自最终数据库状态。

## 7. 实验结论与边界

已验证通过：

- 100 个并发 STUDY 请求全部成功，`learned_seconds` 精确累计到 500，没有丢失更新。
- 每个独立事件都生成一条 STUDY 日志，数量和时长总和正确。
- 单实例和双实例场景都只生成一条 FINISH，首次完课门闩有效。
- 双实例各收到 50 个请求，跨 JVM 验证有效。
- 关系时长能够封顶在课程总时长 705 秒。

发现的问题：

- 轮次 A 的 `progress=71` 与 `learned_seconds=500` 不一致，暴露出同一条 UPDATE 中重复计入 `duration` 的进度计算问题。
- k6 的 HTTP 和业务阈值全部为绿色，但数据库状态仍然违反业务不变量，说明并发实验不能只看接口成功率。

CON-02 当前结论为“原子累加和首次完课门闩通过，进度计算发现缺陷，待修复复测”，不能标记为完全完成。

本实验仍未验证同一逻辑学习事件的重复投递。当前 100 个请求被定义为 100 个不同事件；如果它们是同一个请求的重试，仍会累计 100 次。该幂等问题属于 CON-03。

后续顺序：

1. 修复 `addStudyTimeAndUpdateProgress` 中进度和状态的计算偏差。
2. 增加 Mapper/Service 并发测试及 695 秒边界测试。
3. 复跑轮次 A，并确认 `learned_seconds=500`、`progress=70`。
4. 复跑首次完课场景，确认 FINISH 仍然只有一条。
5. CON-02 全部通过后进入 CON-03 重复消费幂等实验。

## 8. 原始 k6 输出

### 8.1 轮次 A：并发累加

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-02-concurrent-study-progress.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * concurrent_study_progress: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==100' count=100

    study_success
    ✓ 'count==100' count=100

    study_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 203     243.223587/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 学习行为 HTTP 状态为 200
    ✓ 学习行为业务码为 200

    CUSTOM
    study_success......................: 100   119.814575/s
    study_unexpected...................: 0     0/s

    HTTP
    http_req_duration..................: avg=630.58ms min=129.47ms med=650.01ms max=698.56ms p(90)=685.31ms p(95)=687.81m
      { expected_response:true }.......: avg=630.58ms min=129.47ms med=650.01ms max=698.56ms p(90)=685.31ms p(95)=687.81m
    http_req_failed....................: 0.00% 0 out of 101
    http_reqs..........................: 101   121.012721/s
      { phase:load,backend_index:1 }...: 100   119.814575/s

    EXECUTION
    iteration_duration.................: avg=636.05ms min=438.92ms med=652.07ms max=699.01ms p(90)=685.7ms  p(95)=688.43m
    iterations.........................: 100   119.814575/s

    NETWORK
    data_received......................: 43 kB 52 kB/s
    data_sent..........................: 43 kB 52 kB/s




running (00.8s), 000/100 VUs, 100 complete and 0 interrupted iterations
concurrent_study_progress ✓ [ 100% ] 100 VUs  00.7s/30s  100/100 iters, 1 per VU
```

### 8.2 轮次 B：单实例首次完课

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-02-concurrent-study-progress.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * concurrent_study_progress: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==100' count=100

    study_success
    ✓ 'count==100' count=100

    study_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 203     750.218044/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 学习行为 HTTP 状态为 200
    ✓ 学习行为业务码为 200

    CUSTOM
    study_success......................: 100   369.565539/s
    study_unexpected...................: 0     0/s

    HTTP
    http_req_duration..................: avg=198.99ms min=12.36ms med=225.11ms max=251.22ms p(90)=245.99ms p(95)=249.59ms
      { expected_response:true }.......: avg=198.99ms min=12.36ms med=225.11ms max=251.22ms p(90)=245.99ms p(95)=249.59ms
    http_req_failed....................: 0.00% 0 out of 101
    http_reqs..........................: 101   373.261194/s
      { phase:load,backend_index:1 }...: 100   369.565539/s

    EXECUTION
    iteration_duration.................: avg=201.8ms  min=77.25ms med=225.88ms max=253.27ms p(90)=246.33ms p(95)=250.56ms
    iterations.........................: 100   369.565539/s

    NETWORK
    data_received......................: 43 kB 159 kB/s
    data_sent..........................: 43 kB 160 kB/s




running (00.3s), 000/100 VUs, 100 complete and 0 interrupted iterations
concurrent_study_progress ✓ [ 100% ] 100 VUs  00.3s/30s  100/100 iters, 1 per VU
```

### 8.3 轮次 C：双实例首次完课

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-02-concurrent-study-progress.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * concurrent_study_progress: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==50' count=50

    http_reqs{phase:load,backend_index:2}
    ✓ 'count==50' count=50

    study_success
    ✓ 'count==100' count=100

    study_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 203     308.591773/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 学习行为 HTTP 状态为 200
    ✓ 学习行为业务码为 200

    CUSTOM
    study_success......................: 100   152.015652/s
    study_unexpected...................: 0     0/s

    HTTP
    http_req_duration..................: avg=374.27ms min=8.8ms   med=153.29ms max=644.09ms p(90)=637.53ms p(95)=639.72ms
      { expected_response:true }.......: avg=374.27ms min=8.8ms   med=153.29ms max=644.09ms p(90)=637.53ms p(95)=639.72ms
    http_req_failed....................: 0.00% 0 out of 101
    http_reqs..........................: 101   153.535808/s
      { phase:load,backend_index:1 }...: 50    76.007826/s
      { phase:load,backend_index:2 }...: 50    76.007826/s

    EXECUTION
    iteration_duration.................: avg=378.64ms min=85.82ms med=387.42ms max=644.91ms p(90)=638.53ms p(95)=641.07ms
    iterations.........................: 100   152.015652/s

    NETWORK
    data_received......................: 43 kB 65 kB/s
    data_sent..........................: 43 kB 66 kB/s




running (00.7s), 000/100 VUs, 100 complete and 0 interrupted iterations
concurrent_study_progress ✓ [ 100% ] 100 VUs  00.6s/30s  100/100 iters, 1 per VU
```
