# CON-01 并发选课实验结果

## 1. 实验信息

| 项目 | 值 |
|---|---|
| 实验日期 | 2026-08-17～2026-08-18 |
| Git 分支 | `codex/concurrency-optimization` |
| 基线 commit | `4d157c6` |
| 操作系统 | macOS 26.5.2 |
| k6 | `v2.2.0`，darwin/arm64 |
| 后端实例 | 单实例基线 + 双实例跨 JVM 验证 |
| 数据库 | MySQL 8，`course_concurrency` |
| 测试用户 | `stu_newbie` |
| 目标课程 | 单实例依次使用课程 10、11、12；双实例使用课程 14 |
| 负载模型 | `per-vu-iterations`，每个 VU 只请求一次 |

本实验使用同一个用户并发选择同一门课程，验证 `user_course_relation` 表上的
`uk_user_course(user_id, course_id)` 唯一索引能否在竞争条件下保证最终数据唯一。

## 2. 实验假设

每轮使用一个尚未选择目标课程的用户，并发发送 `N` 次选课请求：

- 业务成功应恰好为 1 次。
- 重复选课应恰好为 `N - 1` 次。
- 非预期响应和 HTTP 失败应为 0。
- 所有迭代都应完成，不能出现中断。
- 实验结束后，该用户和课程的关系行数应恰好为 1。
- 全表不应存在重复的 `(user_id, course_id)` 组合。

## 3. 结果汇总

| 部署形态 | 并发数 | 业务成功 | 重复选课 | 非预期 | HTTP 失败率 | avg | p95 | max | 完成迭代 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 单实例 | 10 | 1 | 9 | 0 | 0% | 62.87ms | 70.76ms | 70.81ms | 10/10 |
| 单实例 | 50 | 1 | 49 | 0 | 0% | 21.68ms | 27.15ms | 27.28ms | 50/50 |
| 单实例 | 100 | 1 | 99 | 0 | 0% | 33.56ms | 46.33ms | 47.88ms | 100/100 |
| 双实例 | 100 | 1 | 99 | 0 | 0% | 147.64ms | 169.83ms | 170.79ms | 100/100 |

四轮阈值均通过：

```text
enrollment_success    = 1
enrollment_duplicate  = VUS - 1
enrollment_unexpected = 0
checks                = 100%
```

检查总数也与脚本设计一致。登录阶段固定包含 3 个检查，每个选课请求包含 2 个检查：

```text
10 并发：  3 + 10 × 2  = 23
50 并发：  3 + 50 × 2  = 103
100 并发： 3 + 100 × 2 = 203
```

双实例的 100 个选课请求按 VU 编号轮询分配，实例 1 和实例 2 各收到 50 个请求，对应的两个流量分布阈值均通过。这排除了“虽然启动两个实例，但请求实际只进入一个实例”的无效验证。

## 4. 数据库正确性断言

实验结束后执行：

```sql
SELECT
    u.username,
    ucr.course_id,
    COUNT(*) AS relation_count
FROM course_concurrency.user_course_relation ucr
JOIN course_concurrency.`user` u ON u.id = ucr.user_id
WHERE u.username = 'stu_newbie'
  AND ucr.course_id IN (10, 11, 12, 14)
GROUP BY u.username, ucr.course_id
ORDER BY ucr.course_id;
```

结果：

```text
username     course_id  relation_count
stu_newbie   10         1
stu_newbie   11         1
stu_newbie   12         1
stu_newbie   14         1
```

全表重复组合检查：

```sql
SELECT COUNT(*) AS duplicate_groups
FROM (
    SELECT user_id, course_id
    FROM course_concurrency.user_course_relation
    GROUP BY user_id, course_id
    HAVING COUNT(*) > 1
) duplicated;
```

结果：

```text
duplicate_groups = 0
```

k6 的业务响应计数和 MySQL 最终状态相互印证：四轮实验均只有一个请求成功创建关系，其他请求进入重复选课分支，最终没有产生重复数据。课程 14 的双实例实验结束后关系行数仍为 1，说明两个 JVM 共享数据库时也没有绕过唯一性约束。

## 5. 性能结果解读

四轮 p95 分别为：

```text
单实例 10 并发： 70.76ms
单实例 50 并发： 27.15ms
单实例 100 并发：46.33ms
双实例 100 并发：169.83ms
```

不能据此得出“50 并发比 10 并发更快”的结论，原因包括：

- 10 并发是第一轮，可能包含 JVM JIT、数据库连接池和缓存预热成本。
- 每轮持续时间不足 0.1 秒，属于一次极短的突发测试。
- 四轮使用了不同的课程 ID。
- `http_req_duration` 同时包含一次登录请求和选课请求。
- 每个并发等级只运行一次，无法排除瞬时抖动。

双实例 100 并发的 p95 为 `169.83ms`，约为单实例 100 并发 `46.33ms` 的 3.67 倍，但不能据此得出“增加实例导致性能下降”的结论：两个 JVM 运行在同一台 Mac 上，共享 CPU、内存和同一个 MySQL；两组测试也没有进行多轮预热和重复采样。双实例实验使用客户端轮询，仅用于确保请求跨 JVM，不代表真实负载均衡环境下的扩容收益。

单实例和双实例 100 并发分别显示 `1556.46 req/s` 和 `401.22 req/s`，都只是小于 0.3 秒的突发窗口平均值，不能表述为系统最大 QPS。CON-01 的目标是验证并发正确性，不是测量持续负载下的容量上限。

## 6. 实验结论

在单实例 10、50、100 并发以及双实例 100 并发下：

- 每轮均只有一次业务成功。
- 其余请求均被正确识别为重复选课。
- HTTP 失败率和非预期响应均为 0。
- 所有迭代均完整执行，没有中断。
- MySQL 中每个用户课程组合最终只有一行。
- 全表不存在重复的 `(user_id, course_id)` 组合。
- 双实例各处理 50 个选课请求，证明测试流量实际跨越了两个 JVM。

结果说明，当前实现通过数据库唯一索引完成最终并发仲裁，并在 Service 层将唯一键冲突转换成可预期的重复选课结果。唯一索引位于所有实例共享的写入点，所以结论不依赖某个 JVM 的本地锁；在当前场景中，不需要额外使用 `synchronized`、`ReentrantLock` 或 Redis 分布式锁来保证关系唯一。

当前接口保证的是“最终数据不重复”，而不是最强意义上的接口幂等：第一次请求返回业务成功，后续重复请求返回业务码 `400`，并不会重放第一次的成功结果。

## 7. 结论边界与后续实验

当前已经验证：

- 单后端实例。
- 两个后端实例同时写入同一个 MySQL。
- 最高 100 个突发并发。
- 同一用户、同一课程的唯一键竞争。
- 接口响应和数据库最终状态一致。
- 客户端轮询下两个实例各收到一半请求。

当前尚未验证：

- 三个或更多实例以及 Nginx 等真实负载均衡入口。
- 持续高 QPS 下的稳定吞吐量。
- 大连接池排队、数据库锁等待和 CPU 使用率。
- 普通选课与重复选课混合流量。
- 重复请求返回业务错误还是幂等成功的接口语义选择。

CON-01 至此完成。下一步进入 CON-02“并发学习进度与首次完课”，验证原子累加是否避免丢失更新，以及 `complete_time IS NULL` 条件更新能否保证首次完课事件只触发一次。

## 8. 原始 k6 输出

### 8.1 10 并发结果

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-01-concurrent-enrollment.js
        output: -

     scenarios: (100.00%) 1 scenario, 10 max VUs, 30s max duration (incl. graceful stop):
              * concurrent_enrollment: 1 iterations for each of 10 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    enrollment_duplicate
    ✓ 'count==9' count=9

    enrollment_success
    ✓ 'count==1' count=1

    enrollment_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 23      213.568073/s
    checks_succeeded...: 100.00% 23 out of 23
    checks_failed......: 0.00%   0 out of 23

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 选课 HTTP 状态为 200
    ✓ 选课响应是预期业务结果

    CUSTOM
    enrollment_duplicate...........: 9      83.570115/s
    enrollment_success.............: 1      9.285568/s
    enrollment_unexpected..........: 0      0/s

    HTTP
    http_req_duration..............: avg=62.87ms min=24.25ms med=70.52ms max=70.81ms p(90)=70.71ms p(95)=70.76ms
      { expected_response:true }...: avg=62.87ms min=24.25ms med=70.52ms max=70.81ms p(90)=70.71ms p(95)=70.76ms
    http_req_failed................: 0.00%  0 out of 11
    http_reqs......................: 11     102.141252/s

    EXECUTION
    iteration_duration.............: avg=66.65ms min=25.07ms med=71.29ms max=71.36ms p(90)=71.34ms p(95)=71.35ms
    iterations.....................: 10     92.855684/s

    NETWORK
    data_received..................: 5.1 kB 47 kB/s
    data_sent......................: 3.7 kB 34 kB/s




running (00.1s), 00/10 VUs, 10 complete and 0 interrupted iterations
```

### 8.2 50 并发结果

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-01-concurrent-enrollment.js
        output: -

     scenarios: (100.00%) 1 scenario, 50 max VUs, 30s max duration (incl. graceful stop):
              * concurrent_enrollment: 1 iterations for each of 50 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    enrollment_duplicate
    ✓ 'count==49' count=49

    enrollment_success
    ✓ 'count==1' count=1

    enrollment_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 103     2392.122254/s
    checks_succeeded...: 100.00% 103 out of 103
    checks_failed......: 0.00%   0 out of 103

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 选课 HTTP 状态为 200
    ✓ 选课响应是预期业务结果

    CUSTOM
    enrollment_duplicate...........: 49    1137.999907/s
    enrollment_success.............: 1     23.224488/s
    enrollment_unexpected..........: 0     0/s

    HTTP
    http_req_duration..............: avg=21.68ms min=10.66ms med=21.75ms max=27.28ms p(90)=27.02ms p(95)=27.15ms
      { expected_response:true }...: avg=21.68ms min=10.66ms med=21.75ms max=27.28ms p(90)=27.02ms p(95)=27.15ms
    http_req_failed................: 0.00% 0 out of 51
    http_reqs......................: 51    1184.448883/s

    EXECUTION
    iteration_duration.............: avg=22.55ms min=15.47ms med=22.46ms max=27.85ms p(90)=27.66ms p(95)=27.74ms
    iterations.....................: 50    1161.224395/s

    NETWORK
    data_received..................: 23 kB 533 kB/s
    data_sent......................: 18 kB 410 kB/s




running (00.0s), 00/50 VUs, 50 complete and 0 interrupted iterations
concurrent_enrollment ✓ [ 100% ] 50 VUs  00.0s/30s  50/50 iters, 1 per VU

```

### 8.3 100 并发结果

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-01-concurrent-enrollment.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * concurrent_enrollment: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    enrollment_duplicate
    ✓ 'count==99' count=99

    enrollment_success
    ✓ 'count==1' count=1

    enrollment_unexpected
    ✓ 'count==0' count=0


  █ TOTAL RESULTS

    checks_total.......: 203     3128.32288/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 选课 HTTP 状态为 200
    ✓ 选课响应是预期业务结果

    CUSTOM
    enrollment_duplicate...........: 99    1525.635296/s
    enrollment_success.............: 1     15.410458/s
    enrollment_unexpected..........: 0     0/s

    HTTP
    http_req_duration..............: avg=33.56ms min=11.39ms med=35.52ms max=47.88ms p(90)=45.76ms p(95)=46.33ms
      { expected_response:true }...: avg=33.56ms min=11.39ms med=35.52ms max=47.88ms p(90)=45.76ms p(95)=46.33ms
    http_req_failed................: 0.00% 0 out of 101
    http_reqs......................: 101   1556.456211/s

    EXECUTION
    iteration_duration.............: avg=34.8ms  min=12.66ms med=36.51ms max=48.74ms p(90)=46.83ms p(95)=47.35ms
    iterations.....................: 100   1541.045754/s

    NETWORK
    data_received..................: 45 kB 698 kB/s
    data_sent......................: 35 kB 541 kB/s




running (00.1s), 000/100 VUs, 100 complete and 0 interrupted iterations
concurrent_enrollment ✓ [ 100% ] 100 VUs  00.0s/30s  100/100 iters, 1 per VU

```

### 8.4 双实例100 并发结果

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-01-concurrent-enrollment.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * concurrent_enrollment: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    enrollment_duplicate
    ✓ 'count==99' count=99

    enrollment_success
    ✓ 'count==1' count=1

    enrollment_unexpected
    ✓ 'count==0' count=0

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==50' count=50

    http_reqs{phase:load,backend_index:2}
    ✓ 'count==50' count=50


  █ TOTAL RESULTS

    checks_total.......: 203     806.403559/s
    checks_succeeded...: 100.00% 203 out of 203
    checks_failed......: 0.00%   0 out of 203

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 选课 HTTP 状态为 200
    ✓ 选课响应是预期业务结果

    CUSTOM
    enrollment_duplicate...............: 99    393.270701/s
    enrollment_success.................: 1     3.972431/s
    enrollment_unexpected..............: 0     0/s

    HTTP
    http_req_duration..................: avg=147.64ms min=69.86ms med=132.4ms  max=170.79ms p(90)=169.07ms p(95)=169.83ms
      { expected_response:true }.......: avg=147.64ms min=69.86ms med=132.4ms  max=170.79ms p(90)=169.07ms p(95)=169.83ms
    http_req_failed....................: 0.00% 0 out of 101
    http_reqs..........................: 101   401.215564/s
      { phase:load,backend_index:1 }...: 50    198.621566/s
      { phase:load,backend_index:2 }...: 50    198.621566/s

    EXECUTION
    iteration_duration.................: avg=149.29ms min=70.69ms med=150.28ms max=171.26ms p(90)=170.19ms p(95)=170.78ms
    iterations.........................: 100   397.243133/s

    NETWORK
    data_received......................: 45 kB 180 kB/s
    data_sent..........................: 35 kB 140 kB/s




running (00.3s), 000/100 VUs, 100 complete and 0 interrupted iterations
concurrent_enrollment ✓ [======================================] 100 VUs  00.2s/30s  100/100 iters, 1 per VU
```
