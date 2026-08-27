# CON-04 推荐缓存击穿与雪崩实验结果

## 1. 实验范围

本文记录 CON-04 的全部六轮实验：

- 轮次 A：缓存命中基线。
- 轮次 B：快速构建下的单 key 冷缓存。
- 轮次 C：慢构建下的单 key 击穿。
- 轮次 D：双后端实例共享 Redis 构建锁。
- 轮次 E：多个不同 key 同时冷启动。
- 轮次 F：固定 TTL 集中过期。

实验方案、启动命令和参数说明见 [CON-04 推荐缓存击穿与雪崩实验](./concurrency/docs/04-recommend-cache-breakdown.md)。

A-D 使用同一用户验证单 key 行为，E-F 使用 50 个用户验证多 key 行为。当前缓存等待预算为：

```text
3 次 × 80ms = 240ms
```

## 2. 结果汇总

| 轮次 | 缓存状态 | Stub 延迟 | 业务请求 | HTTP 失败 | 回源次数 | 最大上游并发 | 推荐接口平均耗时 | 推荐接口 p95 | 结果 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| A | 已预热 | 100ms | 100 | 0 | 0 | 0 | 46.37ms | 65.26ms | 热缓存全部命中 |
| B | 单 key 冷缓存 | 100ms | 100 | 0 | 1 | 1 | 214.58ms | 230.20ms | 快速构建时防击穿有效 |
| C | 单 key 冷缓存 | 800ms | 100 | 0 | 100 | 100 | 1.40s | 1.44s | 等待超时后发生完全击穿 |
| D | 双实例、单 key 冷缓存 | 100ms | 100 | 0 | 1 | 1 | 220.44ms | 245.01ms | 跨 JVM 共享锁有效 |
| E | 50 个 key 同时冷启动 | 800ms | 50 | 0 | 50 | 50 | 965.98ms | 984.50ms | 单 key 锁不能限制全局并发 |
| F | 50 个 key 固定 TTL 到期 | 800ms | 50 | 0 | 50 | 50 | 946.25ms | 957.48ms | 集中过期触发雪崩 |

A-F 请求的业务响应全部成功，但资源侧表现完全不同：

```text
A：100 个请求 → 0 次回源
B：100 个请求 → 1 次回源
C：100 个请求 → 100 次回源
D：两个实例各 50 个请求 → 合计 1 次回源
E：50 个不同 key → 50 次回源，最大并发 50
F：50 个固定 TTL key 同时到期 → 50 次回源，最大并发 50
```

## 3. 分轮分析

### 3.1 轮次 A：缓存命中基线

关键结果：

```text
con04_recommend_requests = 100
con04_upstream_requests = 0
con04_upstream_max_active = 0
p95 = 65.26ms
http_req_failed = 0
```

setup 阶段先完成一次预热，然后清空 Stub 统计。正式并发阶段没有任何请求到达推荐 Stub，证明 100 个请求全部读取了 Redis 中已有的推荐结果。

本轮建立了性能基线：在当前机器和数据规模下，热缓存推荐请求的平均耗时约为 46ms，p95 约为 65ms。后续轮次的额外耗时主要来自缓存构建、等待和上游竞争。

### 3.2 轮次 B：快速构建下防击穿有效

关键结果：

```text
con04_recommend_requests = 100
con04_upstream_requests = 1
con04_upstream_max_active = 1
p95 = 230.20ms
http_req_failed = 0
```

100 个请求同时发现缓存不存在，其中一个请求取得 Redis 构建锁并回源；其余请求没有同时调用 Stub，而是在短轮询期间读到了锁持有者写入的缓存。因此回源次数和最大上游并发都保持为 1。

这证明当前方案在“完整构建时间没有超过等待预算”的条件下能够防止单 key 击穿。不过 p95 已达到 230.20ms，十分接近 240ms 等待预算，安全余量很小。机器抖动、数据库变慢或上游延迟稍有增加，就可能跨过当前策略的边界。

### 3.3 轮次 C：慢构建导致完全击穿

关键结果：

```text
con04_recommend_requests = 100
con04_upstream_requests = 100
con04_upstream_max_active = 100
p95 = 1.44s
http_req_failed = 0
```

Stub 延迟提高到 800ms 后，锁持有者无法在约 240ms 的等待预算内构建并写入缓存。其余 99 个请求结束轮询时仍然读不到缓存，于是执行当前实现的“无锁回源”分支：

```text
1 个锁持有者回源
+ 99 个等待超时请求无锁回源
= 100 次回源
```

`maxActiveRequests = 100` 进一步证明这些回源不是先后重复执行，而是同时压到了上游。也就是说，Redis 构建锁本身仍然存在，但等待超时后的降级策略绕开了锁，使防击穿能力在慢构建场景下完全失效。

接口仍然全部返回 HTTP 200，说明仅观察可用性会掩盖资源风险。如果真实推荐服务、网络连接池或数据库不能承受 100 倍瞬时放大，系统会进一步出现超时、线程堆积和级联故障。

### 3.4 轮次 D：双实例共享 Redis 锁有效

有效复跑的关键结果：

```text
backend_index:1 = 50
backend_index:2 = 50
con04_recommend_requests = 100
con04_upstream_requests = 1
con04_upstream_max_active = 1
p95 = 245.01ms
http_req_failed = 0
```

k6 将 100 个请求平均分发到两个独立后端实例，每个实例处理 50 个请求，但推荐 Stub 合计只收到 1 次回源。这证明构建锁并非 JVM 进程内锁，而是由两个实例共同连接的 Redis 进行仲裁；一个实例获得锁并完成构建后，另一个实例也能读取它写入的共享缓存。

D 轮首次运行曾出现 55 次回源、最大上游并发 29、p95 约 912ms。该次结果说明当时的完整构建时间再次超过了 240ms 等待预算，但由于没有同步保留 Stub `delayMs` 和两实例启动配置，不能仅凭结果确定是旧的 800ms Stub 未重启，还是运行环境抖动。因此首次结果作为排障记录保留，不纳入“共享锁是否有效”的最终判断。

有效复跑的 p95 为 245.01ms，仍然贴近等待预算边界。这里不能用接口 p95 直接等同于锁持有者的构建时间，但它再次表明当前参数安全余量较小；D 轮证明了锁能够跨实例协调，并没有消除 C 轮暴露的等待超时缺陷。

### 3.5 轮次 E：不同 key 同时冷启动造成全局并发

关键结果：

```text
con04_avalanche_recommend_requests = 50
con04_avalanche_upstream_requests = 50
con04_avalanche_upstream_max_active = 50
p95 = 984.50ms
http_req_failed = 0
```

50 个用户对应 50 个缓存 key 和 50 个构建锁，每个请求都能合法获得自己的锁。因此单 key 锁没有失效，却也无法限制推荐上游的总体并发。该轮证明：分布式锁解决的是同一 key 的重复构建，不能代替全局舱壁、排队上限和降级策略。

### 3.6 轮次 F：固定 TTL 集中过期造成雪崩

关键结果：

```text
con04_avalanche_recommend_requests = 50
con04_avalanche_upstream_requests = 50
con04_avalanche_upstream_max_active = 50
p95 = 957.48ms
http_req_failed = 0
```

50 个缓存先在相近时间写入，再使用相同的一分钟 TTL，并在 70 秒时同时访问。结果是 50 个 key 全部回源且最大上游并发达到 50，证明固定 TTL 会把同批缓存的重建压力集中到同一时间窗口。TTL 随机抖动可以打散过期时刻，但仍需全局并发限制保护首次访问和主动失效等不受 TTL 抖动控制的场景。

## 4. 如何理解问题复现轮次的绿色阈值

C、E、F 的 k6 阈值有意设置为确认问题是否被复现，例如：

```text
con04_upstream_requests >= 2
con04_upstream_max_active >= 2
con04_avalanche_upstream_requests = 50
```

因此绿色代表“实验现象符合预期”，并不代表当前实现已经解决缓存击穿或雪崩。

判断生产防护是否有效，应使用优化后的目标：

```text
con04_upstream_requests = 1
con04_upstream_max_active = 1
```

## 5. 当前结论

1. Redis 热缓存能够避免推荐上游调用，并显著降低接口耗时。
2. 当前分布式构建锁在快速构建场景中有效，同一 key 只会回源一次。
3. 防击穿能力依赖固定的 240ms 等待预算，不具备对慢构建的稳定保护。
4. 等待超时后直接无锁构建，会把 100 个同 key 请求放大为 100 个上游请求。
5. “接口全部成功”和“系统没有发生击穿”是两个不同维度，必须同时观测业务响应、回源次数和上游并发。
6. 两个后端实例各处理 50 个请求时仍然只回源一次，证明 Redis 构建锁和推荐缓存能够跨 JVM 共享。
7. 不同 key 拥有不同构建锁，单 key 锁无法限制推荐服务的全局并发。
8. 固定 TTL 会让同批写入的缓存集中失效，需要打散过期时间。

当前实现可以概括为：

```text
构建时间 < 等待预算：分布式锁有效
构建时间 > 等待预算：等待者绕开锁，发生惊群
```

## 6. 面试表达

可以将本实验概括为：

> 项目使用 Redis `SET NX` 对推荐缓存构建加锁。实验发现，快速构建和双实例场景都只回源一次，证明分布式锁能够跨 JVM 防止同 key 重复构建；但构建超过 240ms 后，等待请求会无锁回源，100 个请求产生 100 次上游调用。进一步使用 50 个不同 key 验证时，无论首次冷启动还是固定 TTL 集中过期，上游并发都达到 50。这说明缓存保护需要同时处理单 key 击穿和多 key 雪崩，不能只依赖分布式锁。

后续优化时，应重点比较以下方案：

- 等待超时后重新竞争锁，不允许直接无锁回源。
- 使用逻辑过期，非刷新请求返回旧值。
- 根据真实构建 p99 设置等待预算、锁租约和请求总超时。
- 对推荐上游设置全局并发上限、排队上限和降级策略。

## 7. 优化顺序与复测目标

1. 禁止等待超时后直接无锁回源；未持锁请求只能读取新值、返回旧值或走轻量热门兜底。
2. 使用逻辑过期和较长物理 TTL，让刷新期间的请求立即返回旧推荐。
3. 为物理和逻辑 TTL 增加随机抖动，打散同批 key 的过期时间。
4. 使用独立、有限容量的刷新线程池，并在推荐服务侧设置最终并发上限。
5. 使完整构建硬超时小于锁 TTL，避免旧构建未结束时锁已过期。
6. 记录命中、锁竞争、等待超时、旧值返回、构建耗时、回源和降级指标。

优化后使用相同脚本复测，目标为：

```text
C：回源次数 = 1，最大上游并发 = 1
D：双实例回源次数仍为 1
E：最大上游并发不超过配置的舱壁上限
F：70 秒时只有部分 key 到期，且最大上游并发不超过舱壁上限
```

## 附录 A：轮次 A 原始 k6 输出

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-04-single-key-cache.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * recommend_single_key: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    con04_recommend_duration
    ✓ 'p(95)<5000' p(95)=65.26ms

    con04_recommend_requests
    ✓ 'count==100' count=100

    con04_unexpected
    ✓ 'count==0' count=0

    con04_upstream_max_active
    ✓ 'value>=0' value=0
    ✓ 'value<=0' value=0

    con04_upstream_requests
    ✓ 'count>=0' count=0
    ✓ 'count<=0' count=0

    http_req_failed
    ✓ 'rate==0' rate=0.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==100' count=100


  █ TOTAL RESULTS

    checks_total.......: 309     500.387031/s
    checks_succeeded...: 100.00% 309 out of 309
    checks_failed......: 0.00%   0 out of 309

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ 预热推荐 HTTP 状态为 200
    ✓ 预热推荐业务码为 200
    ✓ 预热推荐返回 items 数组
    ✓ Stub 统计重置成功
    ✓ 并发推荐 HTTP 状态为 200
    ✓ 并发推荐业务码为 200
    ✓ 并发推荐返回 items 数组
    ✓ Stub 统计 HTTP 状态为 200
    ✓ Stub 统计字段完整

    CUSTOM
    con04_recommend_duration...........: avg=46.37ms min=18.36ms med=48.99ms max=66.54ms  p(90)=64.74ms p(95)=65.26ms
    con04_recommend_requests...........: 100    161.93755/s
    con04_unexpected...................: 0      0/s
    con04_upstream_max_active..........: 0      min=0        max=0
    con04_upstream_requests............: 0      0/s

    HTTP
    http_req_duration..................: avg=49.81ms min=330µs   med=48.99ms max=433.45ms p(90)=64.92ms p(95)=65.72ms
      { expected_response:true }.......: avg=49.81ms min=330µs   med=48.99ms max=433.45ms p(90)=64.92ms p(95)=65.72ms
    http_req_failed....................: 0.00%  0 out of 104
    http_reqs..........................: 104    168.415052/s
      { phase:load,backend_index:1 }...: 100    161.93755/s

    EXECUTION
    iteration_duration.................: avg=47.75ms min=19.23ms med=50.38ms max=67.99ms  p(90)=66.43ms p(95)=66.79ms
    iterations.........................: 100    161.93755/s

    NETWORK
    data_received......................: 183 kB 296 kB/s
    data_sent..........................: 34 kB  55 kB/s




running (00.6s), 000/100 VUs, 100 complete and 0 interrupted iterations
recommend_single_key ✓ [ 100% ] 100 VUs  00.1s/30s  100/100 iters, 1 per VU

```

## 附录 B：轮次 B 原始 k6 输出

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-04-single-key-cache.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * recommend_single_key: 1 iterations for each of 100 VUs (maxDuration: 30s)



  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    con04_recommend_duration
    ✓ 'p(95)<5000' p(95)=230.2ms

    con04_recommend_requests
    ✓ 'count==100' count=100

    con04_unexpected
    ✓ 'count==0' count=0

    con04_upstream_max_active
    ✓ 'value>=1' value=1
    ✓ 'value<=1' value=1

    con04_upstream_requests
    ✓ 'count>=1' count=1
    ✓ 'count<=1' count=1

    http_req_failed
    ✓ 'rate==0' rate=0.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==100' count=100


  █ TOTAL RESULTS

    checks_total.......: 306     1177.22191/s
    checks_succeeded...: 100.00% 306 out of 306
    checks_failed......: 0.00%   0 out of 306

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ Stub 统计重置成功
    ✓ 并发推荐 HTTP 状态为 200
    ✓ 并发推荐业务码为 200
    ✓ 并发推荐返回 items 数组
    ✓ Stub 统计 HTTP 状态为 200
    ✓ Stub 统计字段完整

    CUSTOM
    con04_recommend_duration...........: avg=214.58ms min=188.49ms med=216ms    max=231.25ms p(90)=228.05ms p(95)=230.2ms
    con04_recommend_requests...........: 100    384.713043/s
    con04_unexpected...................: 0      0/s
    con04_upstream_max_active..........: 1      min=1        max=1
    con04_upstream_requests............: 1      3.84713/s

    HTTP
    http_req_duration..................: avg=208.53ms min=377µs    med=215.51ms max=231.25ms p(90)=227.84ms p(95)=230.17ms
      { expected_response:true }.......: avg=208.53ms min=377µs    med=215.51ms max=231.25ms p(90)=227.84ms p(95)=230.17ms
    http_req_failed....................: 0.00%  0 out of 103
    http_reqs..........................: 103    396.254434/s
      { phase:load,backend_index:1 }...: 100    384.713043/s

    EXECUTION
    iteration_duration.................: avg=215.93ms min=189.57ms med=217.47ms max=232.69ms p(90)=229.33ms p(95)=231.93ms
    iterations.........................: 100    384.713043/s

    NETWORK
    data_received......................: 181 kB 697 kB/s
    data_sent..........................: 34 kB  129 kB/s




running (00.3s), 000/100 VUs, 100 complete and 0 interrupted iterations
recommend_single_key ✓ [ 100% ] 100 VUs  00.2s/30s  100/100 iters, 1 per VU

```

## 附录 C：轮次 C 原始 k6 输出

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-04-single-key-cache.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * recommend_single_key: 1 iterations for each of 100 VUs (maxDuration: 30s)


running (01.0s), 099/100 VUs, 1 complete and 0 interrupted iterations
recommend_single_key   [   1% ] 100 VUs  01.0s/30s  001/100 iters, 1 per VU


  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    con04_recommend_duration
    ✓ 'p(95)<5000' p(95)=1.44s

    con04_recommend_requests
    ✓ 'count==100' count=100

    con04_unexpected
    ✓ 'count==0' count=0

    con04_upstream_max_active
    ✓ 'value>=2' value=100
    ✓ 'value<=100' value=100

    con04_upstream_requests
    ✓ 'count>=2' count=100
    ✓ 'count<=100' count=100

    http_req_failed
    ✓ 'rate==0' rate=0.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==100' count=100


  █ TOTAL RESULTS

    checks_total.......: 306     205.920552/s
    checks_succeeded...: 100.00% 306 out of 306
    checks_failed......: 0.00%   0 out of 306

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ Stub 统计重置成功
    ✓ 并发推荐 HTTP 状态为 200
    ✓ 并发推荐业务码为 200
    ✓ 并发推荐返回 items 数组
    ✓ Stub 统计 HTTP 状态为 200
    ✓ Stub 统计字段完整

    CUSTOM
    con04_recommend_duration...........: avg=1.4s  min=954.81ms med=1.41s max=1.45s p(90)=1.44s p(95)=1.44s
    con04_recommend_requests...........: 100    67.294298/s
    con04_unexpected...................: 0      0/s
    con04_upstream_max_active..........: 100    min=100      max=100
    con04_upstream_requests............: 100    67.294298/s

    HTTP
    http_req_duration..................: avg=1.36s min=477µs    med=1.41s max=1.45s p(90)=1.44s p(95)=1.44s
      { expected_response:true }.......: avg=1.36s min=477µs    med=1.41s max=1.45s p(90)=1.44s p(95)=1.44s
    http_req_failed....................: 0.00%  0 out of 103
    http_reqs..........................: 103    69.313127/s
      { phase:load,backend_index:1 }...: 100    67.294298/s

    EXECUTION
    iteration_duration.................: avg=1.4s  min=955.92ms med=1.41s max=1.45s p(90)=1.44s p(95)=1.44s
    iterations.........................: 100    67.294298/s
    vus................................: 99     min=99       max=99
    vus_max............................: 100    min=100      max=100

    NETWORK
    data_received......................: 181 kB 122 kB/s
    data_sent..........................: 34 kB  23 kB/s




running (01.5s), 000/100 VUs, 100 complete and 0 interrupted iterations
recommend_single_key ✓ [ 100% ] 100 VUs  01.5s/30s  100/100 iters, 1 per VU

```

## 附录 D-1：轮次 D 首次异常输出（不纳入有效结论）

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-04-single-key-cache.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * recommend_single_key: 1 iterations for each of 100 VUs (maxDuration: 30s)


running (01.0s), 014/100 VUs, 86 complete and 0 interrupted iterations
recommend_single_key   [  86% ] 100 VUs  00.9s/30s  086/100 iters, 1 per VU


  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    con04_recommend_duration
    ✓ 'p(95)<5000' p(95)=911.91ms

    con04_recommend_requests
    ✓ 'count==100' count=100

    con04_unexpected
    ✓ 'count==0' count=0

    con04_upstream_max_active
    ✓ 'value>=1' value=29
    ✗ 'value<=1' value=29

    con04_upstream_requests
    ✓ 'count>=1' count=55
    ✗ 'count<=1' count=55

    http_req_failed
    ✓ 'rate==0' rate=0.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==50' count=50

    http_reqs{phase:load,backend_index:2}
    ✓ 'count==50' count=50


  █ TOTAL RESULTS

    checks_total.......: 306     275.170948/s
    checks_succeeded...: 100.00% 306 out of 306
    checks_failed......: 0.00%   0 out of 306

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ Stub 统计重置成功
    ✓ 并发推荐 HTTP 状态为 200
    ✓ 并发推荐业务码为 200
    ✓ 并发推荐返回 items 数组
    ✓ Stub 统计 HTTP 状态为 200
    ✓ Stub 统计字段完整

    CUSTOM
    con04_recommend_duration...........: avg=698.81ms min=579.05ms med=668.43ms max=984.29ms p(90)=893.23ms p(95)=911.91ms
    con04_recommend_requests...........: 100    89.925146/s
    con04_unexpected...................: 0      0/s
    con04_upstream_max_active..........: 29     min=29       max=29
    con04_upstream_requests............: 55     49.45883/s

    HTTP
    http_req_duration..................: avg=679.64ms min=437µs    med=666.79ms max=984.29ms p(90)=892.32ms p(95)=911.7ms
      { expected_response:true }.......: avg=679.64ms min=437µs    med=666.79ms max=984.29ms p(90)=892.32ms p(95)=911.7ms
    http_req_failed....................: 0.00%  0 out of 103
    http_reqs..........................: 103    92.622901/s
      { phase:load,backend_index:1 }...: 50     44.962573/s
      { phase:load,backend_index:2 }...: 50     44.962573/s

    EXECUTION
    iteration_duration.................: avg=700.41ms min=580.03ms med=671.15ms max=985.28ms p(90)=895.18ms p(95)=912.66ms
    iterations.........................: 100    89.925146/s
    vus................................: 14     min=14       max=14
    vus_max............................: 100    min=100      max=100

    NETWORK
    data_received......................: 181 kB 163 kB/s
    data_sent..........................: 34 kB  30 kB/s




running (01.1s), 000/100 VUs, 100 complete and 0 interrupted iterations
recommend_single_key ✓ [ 100% ] 100 VUs  01.0s/30s  100/100 iters, 1 per VU

```

## 附录 D-2：轮次 D 有效复跑阈值

本次只记录用户提供的阈值输出；结论所需的请求分布、回源次数、最大上游并发和 p95 均已包含。

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-04-single-key-cache.js
        output: -

     scenarios: (100.00%) 1 scenario, 100 max VUs, 30s max duration (incl. graceful stop):
              * recommend_single_key: 1 iterations for each of 100 VUs (maxDuration: 30s)

INFO[0000] [CON-04][single-key][COLD] stubStats={"perUser":{"136":1},"requestTotal":1,"successTotal":1,"activeRequests":0,"maxActiveRequests":1,"delayMs":100,"startedAt":"2026-08-27T13:23:32.616615+00:00","failureTotal":0,"errorRate":0}  source=console


  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    con04_recommend_duration
    ✓ 'p(95)<5000' p(95)=245.01ms

    con04_recommend_requests
    ✓ 'count==100' count=100

    con04_unexpected
    ✓ 'count==0' count=0

    con04_upstream_max_active
    ✓ 'value>=1' value=1
    ✓ 'value<=1' value=1

    con04_upstream_requests
    ✓ 'count>=1' count=1
    ✓ 'count<=1' count=1

    http_req_failed
    ✓ 'rate==0' rate=0.00%

    http_reqs{phase:load,backend_index:1}
    ✓ 'count==50' count=50

    http_reqs{phase:load,backend_index:2}
    ✓ 'count==50' count=50


  █ TOTAL RESULTS

    checks_total.......: 306     1152.321023/s
    checks_succeeded...: 100.00% 306 out of 306
    checks_failed......: 0.00%   0 out of 306

    ✓ 登录 HTTP 状态为 200
    ✓ 登录业务码为 200
    ✓ 登录返回非空 Token
    ✓ Stub 统计重置成功
    ✓ 并发推荐 HTTP 状态为 200
    ✓ 并发推荐业务码为 200
    ✓ 并发推荐返回 items 数组
    ✓ Stub 统计 HTTP 状态为 200
    ✓ Stub 统计字段完整

    CUSTOM
    con04_recommend_duration...........: avg=220.44ms min=175.05ms med=221.49ms max=247.76ms p(90)=244.05ms p(95)=245.01ms
    con04_recommend_requests...........: 100    376.575498/s
    con04_unexpected...................: 0      0/s
    con04_upstream_max_active..........: 1      min=1        max=1
    con04_upstream_requests............: 1      3.765755/s

    HTTP
    http_req_duration..................: avg=214.11ms min=348µs    med=221.42ms max=247.76ms p(90)=244.04ms p(95)=244.97ms
      { expected_response:true }.......: avg=214.11ms min=348µs    med=221.42ms max=247.76ms p(90)=244.04ms p(95)=244.97ms
    http_req_failed....................: 0.00%  0 out of 103
    http_reqs..........................: 103    387.872763/s
      { phase:load,backend_index:1 }...: 50     188.287749/s
      { phase:load,backend_index:2 }...: 50     188.287749/s

    EXECUTION
    iteration_duration.................: avg=222ms    min=176.49ms med=223.49ms max=249.59ms p(90)=245.73ms p(95)=246.2ms
    iterations.........................: 100    376.575498/s

    NETWORK
    data_received......................: 181 kB 682 kB/s
    data_sent..........................: 34 kB  126 kB/s




running (00.3s), 000/100 VUs, 100 complete and 0 interrupted iterations
recommend_single_key ✓ [======================================] 100 VUs  00.3s/30s  100/100 iters, 1 per VU
```

## 附录 E：多 key 同时冷启动

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-04-cache-avalanche.js
        output: -

     scenarios: (100.00%) 1 scenario, 50 max VUs, 30s max duration (incl. graceful stop):
              * avalanche: 1 iterations for each of 50 VUs (maxDuration: 30s, exec: avalanche)


running (01.0s), 50/50 VUs, 0 complete and 0 interrupted iterations
avalanche   [   0% ] 50 VUs  00.9s/30s  00/50 iters, 1 per VU


  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    con04_avalanche_duration
    ✓ 'p(95)<10000' p(95)=984.5ms

    con04_avalanche_recommend_requests
    ✓ 'count==50' count=50

    con04_avalanche_unexpected
    ✓ 'count==0' count=0

    con04_avalanche_upstream_max_active
    ✓ 'value>=2' value=50

    con04_avalanche_upstream_requests
    ✓ 'count>=50' count=50
    ✓ 'count<=50' count=50

    http_req_failed
    ✓ 'rate==0' rate=0.00%

    http_reqs{phase:load}
    ✓ 'count==50' count=50


  █ TOTAL RESULTS

    checks_total.......: 303     275.519919/s
    checks_succeeded...: 100.00% 303 out of 303
    checks_failed......: 0.00%   0 out of 303

    ✓ 实验用户登录 HTTP 状态为 200
    ✓ 实验用户登录业务码为 200
    ✓ 实验用户登录返回 Token
    ✓ Stub 统计重置成功
    ✓ 雪崩并发推荐 HTTP 状态为 200
    ✓ 雪崩并发推荐业务码为 200
    ✓ 雪崩并发推荐返回 items 数组
    ✓ Stub 统计 HTTP 状态为 200
    ✓ Stub 统计字段完整

    CUSTOM
    con04_avalanche_duration..............: avg=965.98ms min=925.54ms med=966.69ms max=988.56ms p(90)=984.06ms p(95)=984.5ms
    con04_avalanche_recommend_requests....: 50     45.465333/s
    con04_avalanche_unexpected............: 0      0/s
    con04_avalanche_upstream_max_active...: 50     min=50       max=50
    con04_avalanche_upstream_requests.....: 50     45.465333/s

    HTTP
    http_req_duration.....................: avg=474.51ms min=483µs    med=4.66ms   max=988.56ms p(90)=982.24ms p(95)=983.99ms
      { expected_response:true }..........: avg=474.51ms min=483µs    med=4.66ms   max=988.56ms p(90)=982.24ms p(95)=983.99ms
    http_req_failed.......................: 0.00%  0 out of 102
    http_reqs.............................: 102    92.74928/s
      { phase:load }......................: 50     45.465333/s

    EXECUTION
    iteration_duration....................: avg=966.94ms min=926.33ms med=968.18ms max=990.1ms  p(90)=984.35ms p(95)=985.84ms
    iterations............................: 50     45.465333/s
    vus...................................: 50     min=50       max=50
    vus_max...............................: 50     min=50       max=50

    NETWORK
    data_received.........................: 122 kB 111 kB/s
    data_sent.............................: 28 kB  25 kB/s




running (01.1s), 00/50 VUs, 50 complete and 0 interrupted iterations
avalanche ✓ [ 100% ] 50 VUs  01.0s/30s  50/50 iters, 1 per VU

```

## 附录 F：固定 TTL 到期雪崩

```bash

         /\      Grafana   /‾‾/
    /\  /  \     |\  __   /  /
   /  \/    \    | |/ /  /   ‾‾\
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/


     execution: local
        script: experiments/concurrency/k6/con-04-cache-avalanche.js
        output: -

     scenarios: (100.00%) 3 scenarios, 51 max VUs, 1m40s max duration (incl. graceful stop):
              * prewarm: 1 iterations for each of 50 VUs (maxDuration: 30s, exec: prewarm)
              * reset_stub_stats: 1 iterations shared among 1 VUs (maxDuration: 10s, exec: resetStatsAfterPrewarm, startTime: 10s, gracefulStop: 30s)
              * avalanche: 1 iterations for each of 50 VUs (maxDuration: 30s, exec: avalanche, startTime: 1m10s)


running (0m01.0s), 50/51 VUs, 0 complete and 0 interrupted iterations
prewarm            [   0% ] 50 VUs   00.8s/30s  00/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  09.2s
avalanche        • [   0% ] waiting  1m09.2s

running (0m02.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  08.2s
avalanche        • [   0% ] waiting  1m08.2s

running (0m03.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  07.2s
avalanche        • [   0% ] waiting  1m07.2s

running (0m04.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  06.2s
avalanche        • [   0% ] waiting  1m06.2s

running (0m05.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  05.2s
avalanche        • [   0% ] waiting  1m05.2s

running (0m06.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  04.2s
avalanche        • [   0% ] waiting  1m04.2s

running (0m07.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  03.2s
avalanche        • [   0% ] waiting  1m03.2s

running (0m08.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  02.2s
avalanche        • [   0% ] waiting  1m02.2s

running (0m09.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  01.2s
avalanche        • [   0% ] waiting  1m01.2s

running (0m10.0s), 00/51 VUs, 50 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats • [   0% ] waiting  00.2s
avalanche        • [   0% ] waiting  1m00.2s

running (0m11.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m59.2s

running (0m12.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m58.2s

running (0m13.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m57.2s

running (0m14.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m56.2s

running (0m15.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m55.2s

running (0m16.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m54.2s

running (0m17.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m53.2s

running (0m18.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m52.2s

running (0m19.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m51.2s

running (0m20.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m50.2s

running (0m21.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m49.2s

running (0m22.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m48.2s

running (0m23.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m47.2s

running (0m24.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m46.2s

running (0m25.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m45.2s

running (0m26.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m44.2s

running (0m27.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m43.2s

running (0m28.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m42.2s

running (0m29.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m41.2s

running (0m30.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m40.2s

running (0m31.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m39.2s

running (0m32.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m38.2s

running (0m33.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m37.2s

running (0m34.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m36.2s

running (0m35.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m35.2s

running (0m36.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m34.2s

running (0m37.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m33.2s

running (0m38.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m32.2s

running (0m39.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m31.2s

running (0m40.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m30.2s

running (0m41.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m29.2s

running (0m42.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m28.2s

running (0m43.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m27.2s

running (0m44.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m26.2s

running (0m45.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m25.2s

running (0m46.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m24.2s

running (0m47.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m23.2s

running (0m48.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m22.2s

running (0m49.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m21.2s

running (0m50.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m20.2s

running (0m51.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m19.2s

running (0m52.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m18.2s

running (0m53.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m17.2s

running (0m54.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m16.2s

running (0m55.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m15.2s

running (0m56.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m14.2s

running (0m57.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m13.2s

running (0m58.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m12.2s

running (0m59.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m11.2s

running (1m00.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m10.2s

running (1m01.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m09.2s

running (1m02.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m08.2s

running (1m03.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m07.2s

running (1m04.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m06.2s

running (1m05.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m05.2s

running (1m06.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m04.2s

running (1m07.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m03.2s

running (1m08.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m02.2s

running (1m09.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m01.2s

running (1m10.0s), 00/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs   01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs    00.0s/10s  1/1 shared iters
avalanche        • [   0% ] waiting  0m00.2s

running (1m11.0s), 50/51 VUs, 51 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs  01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs   00.0s/10s  1/1 shared iters
avalanche          [   0% ] 50 VUs  00.8s/30s  00/50 iters, 1 per VU


  █ THRESHOLDS

    checks
    ✓ 'rate==1' rate=100.00%

    con04_avalanche_duration
    ✓ 'p(95)<10000' p(95)=957.48ms

    con04_avalanche_recommend_requests
    ✓ 'count==50' count=50

    con04_avalanche_unexpected
    ✓ 'count==0' count=0

    con04_avalanche_upstream_max_active
    ✓ 'value>=2' value=50

    con04_avalanche_upstream_requests
    ✓ 'count>=50' count=50
    ✓ 'count<=50' count=50

    http_req_failed
    ✓ 'rate==0' rate=0.00%

    http_reqs{phase:load}
    ✓ 'count==50' count=50


  █ TOTAL RESULTS

    checks_total.......: 454     6.378219/s
    checks_succeeded...: 100.00% 454 out of 454
    checks_failed......: 0.00%   0 out of 454

    ✓ 实验用户登录 HTTP 状态为 200
    ✓ 实验用户登录业务码为 200
    ✓ 实验用户登录返回 Token
    ✓ Stub 统计重置成功
    ✓ 多用户预热 HTTP 状态为 200
    ✓ 多用户预热业务码为 200
    ✓ 多用户预热返回 items 数组
    ✓ 雪崩并发推荐 HTTP 状态为 200
    ✓ 雪崩并发推荐业务码为 200
    ✓ 雪崩并发推荐返回 items 数组
    ✓ Stub 统计 HTTP 状态为 200
    ✓ Stub 统计字段完整

    CUSTOM
    con04_avalanche_duration..............: avg=946.25ms min=914.27ms med=947.51ms max=960.77ms p(90)=956.7ms p(95)=957.48ms
    con04_avalanche_recommend_requests....: 50     0.702447/s
    con04_avalanche_unexpected............: 0      0/s
    con04_avalanche_upstream_max_active...: 50     min=50       max=50
    con04_avalanche_upstream_requests.....: 50     0.702447/s

    HTTP
    http_req_duration.....................: avg=735.26ms min=444µs    med=947.46ms max=1.62s    p(90)=1.27s   p(95)=1.27s
      { expected_response:true }..........: avg=735.26ms min=444µs    med=947.46ms max=1.62s    p(90)=1.27s   p(95)=1.27s
    http_req_failed.......................: 0.00%  0 out of 153
    http_reqs.............................: 153    2.149488/s
      { phase:load }......................: 50     0.702447/s

    EXECUTION
    iteration_duration....................: avg=1.11s    min=2.28ms   med=963.68ms max=1.62s    p(90)=1.27s   p(95)=1.38s
    iterations............................: 101    1.418943/s
    vus...................................: 50     min=0        max=50
    vus_max...............................: 51     min=51       max=51

    NETWORK
    data_received.........................: 212 kB 3.0 kB/s
    data_sent.............................: 44 kB  620 B/s




running (1m11.2s), 00/51 VUs, 101 complete and 0 interrupted iterations
prewarm          ✓ [ 100% ] 50 VUs  01.6s/30s  50/50 iters, 1 per VU
reset_stub_stats ✓ [ 100% ] 1 VUs   00.0s/10s  1/1 shared iters
avalanche        ✓ [ 100% ] 50 VUs  01.0s/30s  50/50 iters, 1 per VU

```
