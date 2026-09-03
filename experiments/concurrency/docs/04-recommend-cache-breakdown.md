# CON-04 推荐缓存击穿与雪崩实验

## 1. 实验目标

本实验围绕 `GET /api/v1/recommendations` 验证两个不同问题：

1. **缓存击穿**：大量请求同时访问同一个刚失效的用户推荐 key，是否会重复回源构建。
2. **缓存雪崩**：大量不同用户的推荐 key 同时失效时，推荐上游会承受多大的瞬时并发。

实验不会把“HTTP 全部成功”当成通过条件，还要同时检查：

- 推荐 Stub 的 `requestTotal`：真实回源次数。
- 推荐 Stub 的 `maxActiveRequests`：上游最大同时请求数。
- k6 的 p95、错误率和每个后端收到的请求数。
- Redis 中缓存 key 和构建锁的存在状态、TTL。

## 2. 重构前基线与当前实现

推荐结果缓存位于：

```text
backend/src/main/java/com/sy/course_system/recommend/RecommendResultCache.java
```

重构前普通用户使用：

```text
cache key = recommend:user:{userId}
lock key  = recommend:lock:user:{userId}
```

重构前流程：

```text
读取缓存
  ├─ 命中：直接返回
  └─ 未命中
       ↓
     SET NX EX 抢构建锁
       ├─ 抢到锁
       │    ↓
       │  再次检查缓存
       │    ↓
       │  回源构建并写入缓存
       │    ↓
       │  使用 token + Lua 安全释放锁
       │
       └─ 未抢到锁
            ↓
          每隔 80ms 检查一次缓存，共 3 次
            ├─ 等到缓存：返回
            └─ 240ms 后仍未命中：当前请求自行回源构建
```

默认参数：

| 参数 | 默认值 | 含义 |
|---|---:|---|
| `regular-ttl-minutes` | 30 分钟 | 普通推荐逻辑 TTL 基值 |
| `cold-start-ttl-minutes` | 10 分钟 | 冷启动推荐逻辑 TTL 基值 |
| `regular-ttl-jitter-minutes` | 10 分钟 | 普通推荐 TTL 随机抖动上限 |
| `cold-start-ttl-jitter-minutes` | 3 分钟 | 冷启动 TTL 随机抖动上限 |
| `stale-retention-minutes` | 60 分钟 | 逻辑到期后的旧值物理保留时间 |
| `build-lock-ttl-seconds` | 20 秒 | 构建锁租约 |
| `wait-retry-times` | 3 | 未抢到锁后的轮询次数 |
| `wait-millis` | 80ms | 每次轮询等待时间 |
| `initial-build-wait-millis` | 2500ms | 首次 miss 请求等待后台构建的上限 |
| `build-core-size / max-size / queue-capacity` | 2 / 4 / 16 | 单 JVM 构建线程池边界 |

因此重构前默认等待预算约为：

```text
3 × 80ms = 240ms
```

完成本轮优化后，缓存升级为 `recommend:v2:*`，缓存值包含逻辑过期时间和用户版本。
当前流程为：fresh 直接返回；stale 立即返回旧值并由锁持有者异步刷新；首次 miss 进入
有界构建线程池并最多等待 2500ms。锁忙等待后只允许读取缓存、二次抢锁或读取预计算
热门快照，任何未持锁请求都不能执行昂贵 builder。普通逻辑 TTL 为
`30 + 0..10` 分钟，物理 TTL 再保留 60 分钟旧值。
缓存写入使用 Redis Lua 在同一条原子命令中比较用户版本并执行 SET：失效先发生时
旧构建无法写入，写入先发生时后续强失效会直接删除它，不再依赖写后补偿删除。

本项目直接启用最新缓存实现，不保留 v1 读取或双删兼容路径。Redis 辅助脚本仍会清理
`recommend:user:*` 等旧 key，但这些 key 不再参与应用运行时逻辑。

Micrometer 使用 `recommend.cache.events{event=...}` 记录 `hit`、`miss`、`stale_hit`、
`lock_acquired`、`lock_busy`、`wait_timeout`、`refresh_rejected`、`degraded`、
`redis_error`、`build_failed` 和 `build_discarded`；构建数量与耗时分别记录为
`recommend.cache.build.count` 和 `recommend.cache.build.duration`。构建 Timer 显式发布 p95/p99，
并通过带 `quantile` 标签的 Gauge 转换成 Actuator 可以直接读取的毫秒值。
`recommend.cache.build.inflight` 则统计当前 JVM 中排队或正在执行的构建任务。

Actuator 已暴露 `health`、`info` 和 `metrics`。健康检查允许匿名访问，指标端点只允许
管理员访问。启动后可使用管理员 Token 查看指标名称和具体事件：

```bash
curl http://127.0.0.1:8080/actuator/health

curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  http://127.0.0.1:8080/actuator/metrics

curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  "http://127.0.0.1:8080/actuator/metrics/recommend.cache.events?tag=event:degraded"

curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  http://127.0.0.1:8080/actuator/metrics/recommend.cache.build.duration

curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  "http://127.0.0.1:8080/actuator/metrics/recommend.cache.build.duration.percentile?tag=quantile:0.95"

curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  http://127.0.0.1:8080/actuator/metrics/recommend.cache.build.inflight
```

推荐指标会在对应事件首次发生后出现在 `/actuator/metrics` 中。
`recommend.cache.build.duration` 提供 count、total time 和 max；
`recommend.cache.build.duration.percentile` 提供 p95/p99。当前阶段使用 Actuator 做单机实验观测；
需要稳定保存历史曲线、聚合查询和告警时，再接入 Prometheus/Grafana。

## 3. 可验证假设

### 3.1 缓存命中

缓存已经存在时，100 个并发请求都应直接返回：

```text
回源次数 = 0
最大上游并发 = 0
```

### 3.2 快速构建

同一 key 冷缓存，Stub 延迟 100ms：

```text
100ms < 2500ms 首次构建等待上限
```

single-flight 所有者应在等待请求超时前写入缓存：

```text
回源次数 = 1
最大上游并发 = 1
```

### 3.3 慢构建

同一 key 冷缓存，Stub 延迟 800ms：

```text
800ms < 2500ms 首次构建等待上限
```

重构前，锁忙请求会在 240ms 后无锁回源，因此历史基线为：

```text
回源次数 > 1
最大上游并发 > 1
```

当前实现中，同 JVM 请求共享一个 future，跨 JVM 锁竞争失败者只能读取缓存或降级，不能无锁执行
builder；因此当前复测目标仍为回源和最大上游并发都等于 1。

### 3.4 多 key 同时失效

50 个用户分别拥有独立缓存 key 和独立构建锁。它们同时失效时，每个用户都可以合法获得自己的锁，因此单 key 锁不能限制全局回源并发：

```text
回源次数约等于用户数
最大上游并发显著大于 1
```

## 4. 实验文件

```text
experiments/concurrency/docs/04-recommend-cache-breakdown.md
experiments/concurrency/k6/con-04-single-key-cache.js
experiments/concurrency/k6/con-04-cache-avalanche.js
experiments/concurrency/data/con-04-prepare.sql
experiments/concurrency/data/con-04-cleanup.sql
experiments/concurrency/data/con-04-redis.sh
experiments/concurrency/stubs/recommend-cache-stub.py
scripts/start-con04-backend.sh
```

## 5. 隔离范围

实验固定使用：

```text
database: course_concurrency
users:    con04_user_001 ~ con04_user_100
password: 123456
stub:     http://127.0.0.1:18000
```

数据 SQL 只清理用户名符合 `con04_user_NNN` 的实验用户数据。Redis 工具先从 MySQL 精确查询这些用户 ID，再删除对应 key，不使用 `FLUSHDB` 或宽泛的 `KEYS recommend:*`。

每个实验用户都会得到一条 600 秒 `STUDY` 信号，使冷启动判定中的累计学习时长达到阈值，确保请求进入普通推荐分支并调用推荐 Stub。

## 6. 前置准备

所有命令默认从仓库根目录执行。

### 6.1 启动依赖

```bash
docker compose -f scripts/docker-compose.yml up -d
docker compose -f scripts/docker-compose.yml ps
```

### 6.2 构建后端

```bash
cd backend
./mvnw -q -DskipTests package
cd ..
```

### 6.3 准备实验用户

```bash
docker compose -f scripts/docker-compose.yml exec -T \
  -e MYSQL_PWD=root123 mysql mysql -uroot \
  < experiments/concurrency/data/con-04-prepare.sql
```

必须得到：

```text
experiment_user_count = 100
regular_user_signal_count = 100
min_study_seconds = 600
max_study_seconds = 600
```

### 6.4 启动推荐 Stub

快速构建版本：

```bash
STUB_DELAY_MS=100 \
python3 experiments/concurrency/stubs/recommend-cache-stub.py
```

检查：

```bash
curl http://127.0.0.1:18000/health
curl http://127.0.0.1:18000/stats
```

### 6.5 启动 CON-04 后端

另开终端：

```bash
./scripts/start-con04-backend.sh 8080 http://127.0.0.1:18000
```

该脚本固定使用 `course_concurrency`，并关闭快照重建、热榜同步、新课注入和推荐异步编排，减少与缓存实验无关的后台任务和并行分支。

启动日志应显示：

```text
[con04-backend] database: course_concurrency
[con04-backend] recommend stub: http://127.0.0.1:18000
[con04-backend] wait budget: 3 x 80ms
[con04-backend] initial build wait: 2500ms
[con04-backend] lock TTL: 20s, logical TTL: 30min + 0..10min jitter
```

## 7. Redis 辅助脚本

清理单个用户全部相关 key：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1
```

清理全部 CON-04 用户 key：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-all
```

查看单用户缓存和锁：

```bash
./experiments/concurrency/data/con-04-redis.sh status-single 1
```

给已经存在的全部普通推荐缓存设置相同短 TTL：

```bash
./experiments/concurrency/data/con-04-redis.sh expire-all 5
```

最后一个命令适合手动观察 Redis key 同时过期；自动化固定 TTL 雪崩使用第 12 节的双场景 k6 脚本。

## 8. 轮次 A：缓存命中基线

先清理用户 1 的缓存：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1
```

运行 WARM 模式。脚本会在 setup 中请求一次完成预热，等待所有后台构建和
Stub 请求排空后才把统计清零，再发送 100 个并发请求：

```bash
k6 run \
  -e CON04_MODE=WARM \
  -e CON04_VUS=100 \
  -e CON04_USERNAME=con04_user_001 \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=0 \
  -e CON04_EXPECT_UPSTREAM_MAX=0 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=0 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=0 \
  experiments/concurrency/k6/con-04-single-key-cache.js
```

必须满足：

```text
con04_recommend_requests = 100
con04_upstream_requests = 0
con04_upstream_max_active = 0
con04_unexpected = 0
```

## 9. 轮次 B：快速构建下的单 key 冷缓存

保持 Stub 延迟为 100ms，清理用户 1：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1
```

执行：

```bash
k6 run \
  -e CON04_MODE=COLD \
  -e CON04_VUS=100 \
  -e CON04_USERNAME=con04_user_001 \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=1 \
  -e CON04_EXPECT_UPSTREAM_MAX=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=1 \
  experiments/concurrency/k6/con-04-single-key-cache.js
```

预期只有 single-flight 所有者调用一次 Stub；同 JVM 请求共享构建 future，其他 JVM 通过 Redis 锁等待缓存。
若偶尔超过 1 次，应检查构建耗时是否接近 20 秒锁 TTL，以及多个实例是否连接同一个 Redis。

## 10. 轮次 C：慢构建下的单 key 击穿

停止 100ms Stub，然后启动 800ms Stub：

```bash
STUB_DELAY_MS=800 \
python3 experiments/concurrency/stubs/recommend-cache-stub.py
```

清理缓存：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1
```

执行：

```bash
k6 run \
  -e CON04_MODE=COLD \
  -e CON04_VUS=100 \
  -e CON04_USERNAME=con04_user_001 \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=1 \
  -e CON04_EXPECT_UPSTREAM_MAX=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=1 \
  experiments/concurrency/k6/con-04-single-key-cache.js
```

历史基线曾出现：

```text
con04_upstream_requests > 1
con04_upstream_max_active > 1
```

当前命令直接按重构后标准验收：800ms 构建小于 2500ms 请求等待预算，100 个同 key 请求
应只产生一次回源；若已有逻辑过期旧值，其余请求会立即返回旧值。

## 11. 轮次 D：双实例共享锁

重新使用 100ms Stub，并启动两个后端：

```bash
./scripts/start-con04-backend.sh 8080 http://127.0.0.1:18000
./scripts/start-con04-backend.sh 8081 http://127.0.0.1:18000
```

清理单用户缓存后执行：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1

k6 run \
  -e CON04_MODE=COLD \
  -e CON04_VUS=100 \
  -e CON04_BASE_URLS=http://127.0.0.1:8080,http://127.0.0.1:8081 \
  -e CON04_USERNAME=con04_user_001 \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=1 \
  -e CON04_EXPECT_UPSTREAM_MAX=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=1 \
  experiments/concurrency/k6/con-04-single-key-cache.js
```

k6 还会断言两个实例各收到 50 个业务请求。快速构建下仍只回源一次，证明锁的仲裁范围是共享 Redis，而不是单 JVM。

## 12. 轮次 E：多 key 同时冷启动

使用 800ms Stub，清理全部实验用户缓存：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-all
```

执行 50 个不同用户的并发请求：

```bash
k6 run \
  -e CON04_AVALANCHE_MODE=COLD \
  -e CON04_USERS=50 \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=1 \
  -e CON04_EXPECT_UPSTREAM_MAX=20 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=4 \
  experiments/concurrency/k6/con-04-cache-avalanche.js
```

每个实验账号对应独立缓存 key。重构前会产生 50 次回源；重构后单实例最多接收
`4 个执行中 + 16 个排队中` 的构建任务，其余请求读取热门快照降级，且
`maxActiveRequests <= 4`。

## 13. 轮次 F：固定 TTL 到期雪崩

这一轮直接证明固定 TTL 会让同一批写入的 key 在相近时刻过期。

先停止后端，再用 1 分钟普通缓存 TTL 重启：

```bash
CON04_REGULAR_TTL_MINUTES=1 \
CON04_REGULAR_TTL_JITTER_MINUTES=10 \
./scripts/start-con04-backend.sh 8080 http://127.0.0.1:18000
```

清理全部 key：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-all
```

执行 EXPIRE 模式：

```bash
k6 run \
  -e CON04_AVALANCHE_MODE=EXPIRE \
  -e CON04_USERS=50 \
  -e CON04_AVALANCHE_START_TIME=100s \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=0 \
  -e CON04_EXPECT_UPSTREAM_MAX=49 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=0 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=4 \
  experiments/concurrency/k6/con-04-cache-avalanche.js
```

脚本内部时序：

```text
0s：最多 4 个 VU 完成 50 个用户的受控预热，避免预热任务被有界线程池拒绝
62s：开始等待所有后端构建和 Stub 请求排空，随后清零统计
约 60s 起：逻辑 TTL 为 1 分钟并带 0..10 分钟随机抖动，只有部分缓存开始到期
100s：50 个用户再次并发请求，该时刻晚于预热收尾的最长等待窗口
结束：等待每个后端的 recommend.cache.build.inflight 和 Stub activeRequests 都归零，
      再读取最终统计
```

当前缓存已经加入随机抖动，100s 负载应只刷新已经逻辑到期的部分 key，而不是
稳定回源 50 次；所有 stale 请求会先返回旧值，刷新并发不超过 4。

两个 k6 脚本在实验开始、预热收尾和正式负载收尾时，默认每 200ms 同时轮询
所有后端构建指标和 Stub，最长等待 30 秒。
只有各 JVM 的“排队 + 执行中”任务数为 0、Stub `activeRequests=0`，且 Stub
`requestTotal` 连续 5 次不再变化，脚本才接受最终统计。
这避免了前一个构建已经结束 CF 请求、但队列中的下一个构建尚未发起 CF 请求时，
因 Stub 短暂安静而过早收尾。可通过 `CON04_STATS_POLL_INTERVAL_MS` 和
`CON04_STATS_SETTLE_TIMEOUT_MS`、`CON04_STATS_STABLE_POLLS` 调整；收尾轮询带
`phase:teardown_poll` 标签，不计入正式负载。

`con-04-prepare.sql` 会额外准备实验专用管理员 `con04_admin / 123456`，用于 k6 读取
Actuator 指标。可使用 `CON04_ADMIN_USERNAME`、`CON04_ADMIN_PASSWORD` 覆盖，或直接传入
`CON04_ADMIN_TOKEN`。该账号只存在于 `course_concurrency`，清理 SQL 会一并删除。

## 14. 结果记录表

完整输出与逐轮分析见 [CON-04 实验结果](../../CON-04实验结果.md)。

| 轮次 | 场景 | Stub 延迟 | 用户/key 数 | VU | 回源次数 | 最大上游并发 | p95 | 结论 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| A | 热缓存 | 100ms | 1 | 100 | 0 | 0 | 65.26ms | 热缓存全部命中 |
| B | 冷缓存快速构建 | 100ms | 1 | 100 | 1 | 1 | 230.20ms | 快速构建时防击穿有效 |
| C | 冷缓存慢构建 | 800ms | 1 | 100 | 100 | 100 | 1.44s | 等待超时后完全击穿 |
| D | 双实例共享锁 | 100ms | 1 | 100 | 1 | 1 | 245.01ms | Redis 锁能够跨 JVM 仲裁 |
| E | 多 key 同时冷缓存 | 800ms | 50 | 50 | 50 | 50 | 984.50ms | 单 key 锁不能限制全局并发 |
| F | 固定 TTL 同时到期 | 800ms | 50 | 50 | 50 | 50 | 957.48ms | 集中过期触发雪崩 |

## 15. 优化方向与复测标准

### 15.1 单 key 击穿

候选策略：

- 等待结束后重新抢锁，禁止直接无锁回源。
- 等待预算根据真实构建 p99 配置，并设置总请求超时上限。
- 保存旧值并使用逻辑过期；过期时先返回旧值，只允许一个请求异步刷新。
- 构建锁 TTL 大于构建 p99 加安全余量，或者提供受控续期。

优化后复跑轮次 C，最低目标：

```text
100 个同 key 并发请求
回源次数从大于 1 降为 1
最大上游并发从大于 1 降为 1
```

### 15.2 多 key 雪崩

候选策略：

- 物理 TTL 增加随机抖动。
- 使用逻辑过期和异步刷新，避免请求线程同步重建。
- 对全局推荐构建设置并发上限和排队上限。
- 上游使用舱壁、超时和降级；这些内容可继续放到 CON-05 验证。

加入 TTL 抖动后，应使用相同预热时间和 100s 请求时刻复跑轮次 F。优化目标不是“永远零回源”，而是避免大量 key 在同一个时间窗口全部回源。

### 15.3 故障轮次 G：Redis 不可用

先清理用户 1，再停止 Redis，使用单实例运行 COLD 模式：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1
docker compose -f scripts/docker-compose.yml stop redis

k6 run \
  -e CON04_MODE=COLD \
  -e CON04_VUS=100 \
  -e CON04_EXPECT_UPSTREAM_MIN=0 \
  -e CON04_EXPECT_UPSTREAM_MAX=0 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=0 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=0 \
  experiments/concurrency/k6/con-04-single-key-cache.js

docker compose -f scripts/docker-compose.yml start redis
```

预期冷启动状态读取失败后返回 `UNAVAILABLE`，100 个请求全部直接读取 JVM 内的
`RecommendFallbackSnapshot`：MySQL 冷启动统计查询为 0、推荐服务回源为 0、上游最大并发为 0。
这样 Redis 故障不会继续放大为数据库或推荐服务雪崩。该轮只在实验环境停止 Redis，完成后立即恢复。

### 15.4 故障轮次 H：推荐服务返回 503

用 `STUB_ERROR_RATE=1` 启动 Stub，模拟推荐服务舱壁拒绝；后端不重试，CF 调用失败后沿用
构建内的业务兜底，并把合法结果写入缓存：

```bash
STUB_DELAY_MS=100 STUB_ERROR_RATE=1 \
python3 experiments/concurrency/stubs/recommend-cache-stub.py

./experiments/concurrency/data/con-04-redis.sh reset-single 1
k6 run \
  -e CON04_MODE=COLD \
  -e CON04_VUS=100 \
  -e CON04_EXPECT_UPSTREAM_MIN=1 \
  -e CON04_EXPECT_UPSTREAM_MAX=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=1 \
  experiments/concurrency/k6/con-04-single-key-cache.js
```

Python 侧的真实许可获取、503 响应头和异常后许可释放由 `test_main.py` 覆盖。

## 16. 实验结束与清理

停止 k6、后端和 Stub 后，先删除 Redis key：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-all
```

如不再保留实验用户：

```bash
docker compose -f scripts/docker-compose.yml exec -T \
  -e MYSQL_PWD=root123 mysql mysql -uroot \
  < experiments/concurrency/data/con-04-cleanup.sql
```

必须得到：

```text
remaining_con04_users = 0
```
