# CON-04 推荐缓存击穿与雪崩实验

本文的操作步骤用于 **重构后版本验收**。第 14.1 节仅保存重构前历史基线，不能作为当前通过标准。
当前重构提交为 `aaf5ed4`；执行前还需记录包含本次实验脚本修订的实际 commit 和工作区状态。
本文中的“预期”不是已取得的实测结果，重构后结果统一填入第 14.2 节及结果报告的新区。

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

重构后默认参数：

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
热门快照；Redis 正常时，未持锁任务不能执行昂贵 builder。普通逻辑 TTL 为
`30 + 0..10` 分钟，物理 TTL 再保留 60 分钟旧值。
缓存写入使用 Redis Lua 在同一条原子命令中比较用户版本并执行 SET：失效先发生时
旧构建无法写入，写入先发生时后续强失效会直接删除它，不再依赖写后补偿删除。

构建任务从线程池出队后才抢 Redis 锁，排队不消耗锁租期。若 Redis 在缓存构建阶段故障，
首次 miss 可通过本机 single-flight 执行不写 Redis 的本地构建；stale 刷新跳过。
若入口的冷启动状态读取已经失败，则直接内存降级，不进入构建流程（见 G 轮）。

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

事件 Counter 会在对应事件首次发生后出现；首次发生前查询可能返回 404，可在记录差值时视为 0。
构建耗时与 in-flight 指标在组件初始化时注册。不要将接口 p95 等同于后台构建 p95。
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

50 个用户分别拥有独立缓存 key 和独立构建锁。重构前仅有单 key 锁时，回源数和并发可达到 50。
当前完整构建已进入有界线程池；使用单实例、默认配置时应验证：

```text
最大上游并发 <= 4
每个用户回源次数 <= 1
本轮累计回源次数 <= 50（不是 <= 20）
```

## 4. 实验文件

```text
experiments/concurrency/docs/04-recommend-cache-breakdown.md
experiments/concurrency/k6/con-04-single-key-cache.js
experiments/concurrency/k6/con-04-cache-avalanche.js
experiments/concurrency/data/con-04-prepare.sql
experiments/concurrency/data/con-04-cleanup.sql
experiments/concurrency/data/con-04-redis.sh
experiments/concurrency/data/con-04-cache-snapshot.lua
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

数据 SQL 只操作 `con04_user_NNN` 和指标账号 `con04_admin`。Redis 工具先从 MySQL 精确查询
实验用户 ID，再操作对应 key，不使用 `FLUSHDB` 或宽泛的 `KEYS recommend:*`。
G 轮会停止整个 Redis 容器，数据库名隔离不能隔离这一故障；先确认没有其他开发实例依赖它。

每个实验用户都会得到一条 600 秒 `STUDY` 信号，使冷启动判定中的累计学习时长达到阈值，确保请求进入普通推荐分支并调用推荐 Stub。

## 6. 前置准备

所有命令默认从仓库根目录执行。

前置条件：`course_concurrency` 已创建并完成现有 Flyway 迁移，根目录 `.env.local` 已按项目要求配置，
本机具备 k6、Docker Compose 和 Python 3。不要把本地配置或实际 Token 复制进结果报告。
各轮修改参数只作用于实验进程，不改 `application.yaml`。开始前执行 `git rev-parse --short HEAD`
并记录实际配置；后端须重新打包，不能沿用重构前 JAR。

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

等待应用完成启动，再运行 k6。脚本会使用实验管理员检查 `/actuator/metrics/recommend.cache.build.inflight`；
无法读取或后台任务未排空时应停止排查，不跳过 setup/teardown。A～C、E～J 默认只请求 8080；
命令中显式传入单实例 `CON04_BASE_URLS`，避免终端遗留环境变量改变目标。

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

所有 reset/expire 操作都应在停止业务流量、相关后端 `inflight=0` 且 Stub `activeRequests=0` 后执行。
reset 会删除构建锁、版本和 STUDY 节流 key；构建期间执行会破坏实验前提。上轮 k6 收尾失败时，
应先停止相关后端并处理残留任务，不能直接 reset 后重跑。

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

读取单用户或前 50 个实验用户的 v2 元数据（只读，不修改缓存，也不输出 items）：

```bash
./experiments/concurrency/data/con-04-redis.sh inspect-single 1 | python3 -m json.tool
./experiments/concurrency/data/con-04-redis.sh inspect-all 50 | python3 -m json.tool
```

快照包含 Redis 服务器时间 `sampledAt`、fresh/stale/missing/invalid 数量、逻辑 TTL 分布，以及
每个 key 的 `generatedAt`、`logicalExpireAt`、版本和 `physicalTtlMs`。`inspect-all N` 与 k6 的
`CON04_USERS=N` 对齐；只检查这 N 个用户，不把未预热的其余账号误算为缺失。

给已经存在的全部普通推荐缓存设置相同短 **物理 TTL**：

```bash
./experiments/concurrency/data/con-04-redis.sh expire-all 5
```

`expire-all` 会让 Redis 删除旧值，只适合额外的物理过期/miss 实验。它不修改 `logicalExpireAt`，
不能用于验收旧值返回。第 13 节通过后端逻辑 TTL 配置和自然等待进行 F 轮，勿混用该命令。

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
  -e CON04_BASE_URLS=http://127.0.0.1:8080 \
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
  -e CON04_BASE_URLS=http://127.0.0.1:8080 \
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
  -e CON04_BASE_URLS=http://127.0.0.1:8080 \
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

停止 800ms Stub，重新使用 `STUB_DELAY_MS=100`、`STUB_ERROR_RATE=0` 的 Stub。
两个后端必须共享 MySQL、Redis 和 JWT 密钥。以下命令分别放在两个终端执行：

```bash
./scripts/start-con04-backend.sh 8080 http://127.0.0.1:18000
```

```bash
./scripts/start-con04-backend.sh 8081 http://127.0.0.1:18000
```

若 8080 已以相同配置运行，只需启动 8081，不重复占用端口。开始前核对 Stub `/stats` 中
`delayMs=100`、`errorRate=0`，避免把上一轮的慢服务或故障参数带进来。

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

D 轮结束且后台排空后停止 8081，保留默认 `2 / 4 / 16` 的 8080。停止快速 Stub，再启动
`STUB_DELAY_MS=800 STUB_ERROR_RATE=0` 的 Stub。确认 `/stats` 配置后，清理全部实验用户缓存：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-all
```

这三个 Counter 是当前 JVM 启动以来的累计值。完成环境准备后、启动 k6 前先采集起点：

```bash
./experiments/concurrency/data/con-04-metrics.sh before
```

执行 50 个不同用户的并发请求：

```bash
k6 run \
  -e CON04_AVALANCHE_MODE=COLD \
  -e CON04_BASE_URLS=http://127.0.0.1:8080 \
  -e CON04_USERS=50 \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=1 \
  -e CON04_EXPECT_UPSTREAM_MAX=50 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=4 \
  experiments/concurrency/k6/con-04-cache-avalanche.js
```

等待 k6 完整结束（包括 teardown 排空检查）后，再采集终点并自动计算差值：

```bash
./experiments/concurrency/data/con-04-metrics.sh after
```

每个实验账号对应独立缓存 key。`4 个执行中 + 16 个排队中` 是瞬时容量，不是累计吞吐上限；
先完成的任务会腾出容量，因此整轮回源可能超过 20 次。脚本另外断言
`con04_avalanche_upstream_per_user_max <= 1` 和 `con04_avalanche_upstream_failures = 0`。

通过条件是：50 个业务请求合法返回、每用户不重复回源、累计回源 1～50 次、最大上游并发不超过 4，
收尾 `inflight=0`、`activeRequests=0`。记录 `refresh_rejected`、`degraded`、`wait_timeout`
的前后差值，而不要求任何一次突发必定拒绝恰好 30 个任务。

默认使用 `http://127.0.0.1:8080` 和实验管理员 `con04_admin / 123456`，快照分别保存在
`/tmp/con04-e-metrics-before.tsv`、`/tmp/con04-e-metrics-after.tsv`。已有管理员 Token 时可以设置
`CON04_ADMIN_TOKEN`；地址、账号或密码可分别通过 `CON04_METRICS_BASE_URL`、
`CON04_ADMIN_USERNAME`、`CON04_ADMIN_PASSWORD` 覆盖。例如：

```bash
CON04_ADMIN_TOKEN="$ADMIN_TOKEN" \
CON04_METRICS_BASE_URL=http://127.0.0.1:8080 \
./experiments/concurrency/data/con-04-metrics.sh before
```

实验前后不得重启后端；否则 JVM Counter 会清零，两个快照不可比较。脚本只把“事件从未发生”的
HTTP 404 视为 0，鉴权失败和其他 HTTP 错误会直接终止。需要自定义快照文件或稍后重新计算时使用：

```bash
./experiments/concurrency/data/con-04-metrics.sh snapshot /tmp/my-before.tsv
./experiments/concurrency/data/con-04-metrics.sh snapshot /tmp/my-after.tsv
./experiments/concurrency/data/con-04-metrics.sh diff /tmp/my-before.tsv /tmp/my-after.tsv
```

当前 in-flight Gauge 是协调器任务数，不是分别测量的线程 active/queue。
本轮不能仅凭该 Gauge 宣称已实测队列容量为 16；需要直接测量线程池时，在 CON-05 补充观测。

## 13. 轮次 F：逻辑过期、旧值返回与 TTL 抖动

本轮验收的是当前随机逻辑 TTL，而不是再次复现旧版固定物理 TTL 雪崩。
保留单个 8080 后端，Stub 为 `delayMs=800`、`errorRate=0`。

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
  -e CON04_BASE_URLS=http://127.0.0.1:8080 \
  -e CON04_USERS=50 \
  -e CON04_AVALANCHE_START_TIME=100s \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=0 \
  -e CON04_EXPECT_UPSTREAM_MAX=50 \
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
稳定回源 50 次；但这一结论必须用缓存元数据证明，不能仅用回源总数判断。

### 13.1 必须记录的前后快照

运行 k6 的同时，在另一个终端准备 `inspect-all 50`。k6 在约 62s 后输出 `prewarm drained`
提示；看到后、正式 100s 负载开始前执行一次：

```bash
./experiments/concurrency/data/con-04-redis.sh inspect-all 50 | python3 -m json.tool
```

正式负载及 teardown 完成后，再执行同一条命令。将两份输出作为 Markdown 代码块记录，
不提交原始用户 Token。必须检查：

1. 负载前 `total=50`、`missing=0`、`invalid=0`，确认预热确实写入全部缓存。
2. 每个 key 的 `(logicalExpireAt-generatedAt)/60000` 在 1～11 分钟内，并记录实际分布；
   用多个不同 TTL 桶直接证明这批缓存的过期时间被打散，而不是由回源数反推。
3. 以 `sampledAt` 和 k6 输出的负载参考时间对照到期时刻，记录负载前 fresh/stale 数量。
   快照后到正式负载前仍可能有 key 跨过逻辑到期点，不应要求回源数严格等于快照中的 stale 数。
4. 物理 TTL 仍大于 0；近似满足 `physicalTtlMs = logicalExpireAt - sampledAt + 60 分钟`。
   即使逻辑过期，旧值仍在，不是物理过期造成的 miss。
5. 结合 Stub `perUser`、`stale_hit` 和后快照，确认获得执行机会的刷新更新了 `generatedAt`，
   每个用户最多回源一次、最大并发不超过 4，拒绝的刷新不影响旧值返回。

`requestTotal < 50` 不能单独证明抖动生效：即使 50 个 key 全部 stale，线程池拒绝部分刷新后也会
得到这个结果。因此脚本仅给出安全边界 `0..50`，F 轮是否通过还必须人工核对上述快照。
若本次随机样本没有 stale，则本轮不算覆盖了旧值刷新，须另做第 15.5 节的确定性单 key 实验。
如需固定逻辑 TTL 对照，可用 `CON04_REGULAR_TTL_JITTER_MINUTES=0` 独立重跑，仍保留旧值保留期和
有界线程池；它不是重构前版本，也不要求全部回源 50 次。

### 13.2 收尾规则

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

## 14. 结果记录区

### 14.1 重构前历史基线

下表全部来自重构前实测，保留用于对照；特别是 C/E/F 的回源放大属于历史问题，不是当前验收目标。

完整输出与逐轮分析见 [CON-04 实验结果](../../CON-04实验结果.md)。

| 轮次 | 场景 | Stub 延迟 | 用户/key 数 | VU | 回源次数 | 最大上游并发 | p95 | 结论 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| A | 热缓存 | 100ms | 1 | 100 | 0 | 0 | 65.26ms | 热缓存全部命中 |
| B | 冷缓存快速构建 | 100ms | 1 | 100 | 1 | 1 | 230.20ms | 快速构建时防击穿有效 |
| C | 冷缓存慢构建 | 800ms | 1 | 100 | 100 | 100 | 1.44s | 等待超时后完全击穿 |
| D | 双实例共享锁 | 100ms | 1 | 100 | 1 | 1 | 245.01ms | Redis 锁能够跨 JVM 仲裁 |
| E | 多 key 同时冷缓存 | 800ms | 50 | 50 | 50 | 50 | 984.50ms | 单 key 锁不能限制全局并发 |
| F | 固定 TTL 同时到期 | 800ms | 50 | 50 | 50 | 50 | 957.48ms | 集中过期触发雪崩 |

### 14.2 重构后验收结果

实际结果以 [结果报告的重构后验收区](../../CON-04实验结果.md#8-重构后验收结果) 为主，本表只同步摘要。
H 尚缺恢复阶段，版本失效专项尚缺真实链路；未完成项不能用单元测试或预期值代替实测结果。

| 轮次 | 当前验收目标 | 回源 / 最大并发实测 | p95 实测 | 元数据 / 恢复证据 | 状态 |
|---|---|---|---|---|---|
| A | 热缓存 0 / 0 | 0 / 0 | 76.32ms | 100 个请求零回源 | 通过 |
| B | 单 key 快速构建 1 / 1 | 1 / 1 | 230.60ms | 同 key 只构建一次 | 通过 |
| C | 单 key 800ms 构建 1 / 1 | 1 / 1 | 917.69ms | 未出现无锁回源 | 通过 |
| D | 两实例各 50 请求，合计 1 / 1 | 1 / 1 | 389.33ms | 两实例请求分布 50 / 50 | 通过 |
| E | 回源 1～50、每用户 ≤1、并发 ≤4 | 20 / 4 | 2.54s | 每用户 ≤1；拒绝 +30、降级 +42、等待超时 +12 | 通过 |
| F | TTL 元数据分散、旧值保留、每用户 ≤1、并发 ≤4 | 3 / 2 | 64.49ms | TTL 1～11 分钟；前后快照已保存 | 通过 |
| G | Redis 故障零回源，恢复后可构建并命中 | 故障 0 / 0；恢复 COLD 1 / 1、WARM 0 / 0 | 54.77ms（故障） | 恢复后为 FRESH | 通过 |
| H | Stub 确实返回一次 503，恢复后正常 | 1 / 1 | 377.15ms | `failureTotal=1`；未重试 | 故障阶段通过，恢复阶段待验收 |
| I | 单 key stale 立即返回，后台刷新一次 | 1 / 1 | 117.07ms | `stale_hit` +100；`generatedAt` 更新，最终 FRESH | 通过 |
| J | 请求先超时降级，后台仍写缓存 | COLD 1 / 1；WARM 0 / 0 | 270.45ms / 59.37ms | `wait_timeout`、`degraded` 各 +100；最终 FRESH | 通过 |
| 版本失效专项 | 软/强失效和旧构建写回隔离 | — | — | 版本、事务与缓存证据 | 待验收 |

## 15. 故障、恢复与新增机制专项验收

### 15.1 单 key 击穿

已实现的机制与边界：

- 等待结束后重新抢锁，禁止直接无锁回源。
- 首次 miss 默认等待 future 最多 2500ms；它不是包含所有 Redis/入口查询的 HTTP 总超时。
- 保存旧值并使用逻辑过期；过期时先返回旧值，只允许一个请求异步刷新。
- 锁 TTL 默认 20 秒并做配置校验；当前没有锁续期，应观察实际构建 p99 与租期的距离。

优化后复跑轮次 C，最低目标：

```text
100 个同 key 并发请求
回源次数从大于 1 降为 1
最大上游并发从大于 1 降为 1
```

### 15.2 多 key 雪崩

已实现的机制：

- 逻辑 TTL 随机抖动，物理 TTL 在其基础上再保留 60 分钟。
- 使用逻辑过期和异步刷新，避免请求线程同步重建。
- 每个 JVM 的完整推荐构建设置并发上限和排队上限，不是所有实例共享一条本地队列。
- Python 真实推荐服务已有进程级舱壁；持续过载和真实链路的并发上限留到 CON-05 验证。

加入 TTL 抖动后，应使用相同预热时间和 100s 请求时刻复跑轮次 F。优化目标不是“永远零回源”，而是避免大量 key 在同一个时间窗口全部回源。

### 15.3 故障轮次 G：Redis 不可用

结束 F 后先把 8080 以默认参数重启，Stub 恢复 `delayMs=100`、`errorRate=0`。
确认这是可独占停止的实验 Redis；等待旧任务排空后清理用户 1，再停止 Redis。
Redis 故障期间不要重启后端，以便验证仍运行的后端如何降级：

当前后端显式使用 Lettuce，并将连接超时、命令超时都设置为 500ms。不要为了让 G 轮通过而
增大 k6 的 `CON04_MAX_DURATION`；本轮同时验收 Redis 故障能够快速暴露给降级分支。

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1
docker compose -f scripts/docker-compose.yml stop redis

k6 run \
  -e CON04_MODE=COLD \
  -e CON04_BASE_URLS=http://127.0.0.1:8080 \
  -e CON04_VUS=100 \
  -e CON04_EXPECT_UPSTREAM_MIN=0 \
  -e CON04_EXPECT_UPSTREAM_MAX=0 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=0 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=0 \
  experiments/concurrency/k6/con-04-single-key-cache.js

docker compose -f scripts/docker-compose.yml start redis
```

预期冷启动状态读取失败后返回 `UNAVAILABLE`，100 个请求全部直接读取 JVM 内的
`RecommendFallbackSnapshot`；快照尚未生成时允许合法空 items。推荐回源和上游最大并发均为 0。
Redis 调用应在短超时内失败，100 个业务迭代必须在默认 30 秒场景期限内完成，接口 p95 继续满足
默认的 5000ms 上限。若再次出现 `No script iterations fully finished`，优先检查运行中的 JAR 是否
包含最新 `application.yaml`，以及启动环境是否覆盖了 `REDIS_CONNECT_TIMEOUT` 或
`REDIS_COMMAND_TIMEOUT`。
无论 k6 成功、失败还是中断，都必须执行上面的 Redis 恢复命令。

代码预期不会执行 MySQL 冷启动统计查询，但当前 k6 不测 SQL，不能据绿色阈值声称已实测 SQL=0。
这一点目前由 `ColdStartSupportServiceImplTest.decideShouldAvoidDatabaseWhenRedisIsUnavailable` 覆盖；
要取得集成证据须另收集该 Mapper 的 SQL 调用记录，并与登录查询、定时快照查询区分。

恢复 Redis 并确认连接可用后，不重启后端，按 B 轮再次执行 COLD（回源 1 次），再按 A 轮执行
WARM（回源 0 次）；保存 `inspect-single 1` 的 FRESH 快照。故障时成功降级和恢复后重新命中
必须分别记录，不能只把 Redis 容器启动成功当作恢复完成。

### 15.4 故障轮次 H：推荐服务返回 503

先停止正常 Stub，在独立终端用 `STUB_ERROR_RATE=1` 启动故障 Stub，模拟上游 503；后端不重试，CF 调用失败后沿用
构建内的业务兜底，并把合法结果写入缓存：

```bash
STUB_DELAY_MS=100 STUB_ERROR_RATE=1 \
python3 experiments/concurrency/stubs/recommend-cache-stub.py
```

在另一个终端执行：

```bash
./experiments/concurrency/data/con-04-redis.sh reset-single 1
k6 run \
  -e CON04_MODE=COLD \
  -e CON04_BASE_URLS=http://127.0.0.1:8080 \
  -e CON04_VUS=100 \
  -e CON04_EXPECT_FAILURES=1 \
  -e CON04_EXPECT_UPSTREAM_MIN=1 \
  -e CON04_EXPECT_UPSTREAM_MAX=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=1 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=1 \
  experiments/concurrency/k6/con-04-single-key-cache.js
```

脚本额外断言 `con04_upstream_failures=1`，对应 Stub `failureTotal=1`、`successTotal=0`，
从而确认实际注入了 503。其余正常轮次默认要求失败次数为 0；不要遗漏或沿用 H 的参数。

恢复时停止故障 Stub，重新用 `STUB_DELAY_MS=100 STUB_ERROR_RATE=0` 启动；待旧任务排空，清理
用户 1 的缓存，按 B 轮 COLD 再执行 A 轮 WARM。H 产生的合法兜底可能仍是 fresh，若不清理就
直接请求，无法证明已重新调用恢复后的上游。

H 只验证 Java 对 503 的处理，不验证真实 FastAPI 的并发上限或 `Retry-After`。
真实许可与释放已有 `recommend-service/test_main.py` 单元测试，端到端过载验收留给 CON-05。

### 15.5 轮次 I：确定性单 key stale 返回与刷新

这是 F 中随机样本的补充，用一个确定过期的 key 验证旧值返回，不依赖随机抖动碰巧产生 stale。

1. 停止 8080，保持单实例。用以下参数重启，Stub 使用 `delayMs=800`、`errorRate=0`：

   ```bash
   CON04_REGULAR_TTL_MINUTES=1 CON04_REGULAR_TTL_JITTER_MINUTES=0 \
   ./scripts/start-con04-backend.sh 8080 http://127.0.0.1:18000
   ```

2. 按 B 轮清理用户 1 并执行一次 COLD 构建；这里使用 800ms Stub，仍预期回源 1 次。
   记录 `inspect-single 1` 的 `generatedAt`、`logicalExpireAt`，并记录 `stale_hit` Counter 起点。
3. 等到 `inspect-single 1` 显示 STALE 且 `physicalTtlMs>0`。期间不要请求推荐接口，
   不要 reset，也不要用 `expire-all`，否则会变成 miss。
4. 再执行 C 轮 COLD 命令，**跳过清理步骤**，额外传入 `-e CON04_P95_LIMIT_MS=600`。
   此处 COLD 仅表示 k6 不预热，并不会删除已经存在的 stale 缓存。
5. 应观察到 `stale_hit` 增加，p95 明显低于 800ms Stub 延迟，回源和最大并发均为 1；
   teardown 后快照为 FRESH，`generatedAt` 更新。记录前后快照和指标，不能只看 HTTP 200。

600ms 是本地实验的响应目标而不是业务固定 SLA；若基础环境自身超过该值，先排查基线，
不要把阈值放宽到超过 Stub 延迟后仍声称证明了非阻塞旧值返回。

### 15.6 轮次 J：请求先超时降级，后台构建继续

1. 停止 8080，保持 Stub 为 800ms、无错误，使用下面的参数启动后端：

   ```bash
   CON04_INITIAL_BUILD_WAIT_MILLIS=200 \
   ./scripts/start-con04-backend.sh 8080 http://127.0.0.1:18000
   ```

2. 记录 `wait_timeout`、`degraded` Counter 起点，按 C 轮清理用户 1 并运行。
   只缩短请求等待，不把 Stub 延迟提高到超过默认 CF 读取超时 2000ms，避免混入另一种故障。
3. 应看到请求先返回合法降级结果，`wait_timeout` 和 `degraded` 增加；最终 Stub 仍只收到
   1 次成功调用，后台排空后 `inspect-single 1` 显示 FRESH 缓存。
4. 不清理缓存，按 A 轮 WARM 复跑，应为零回源。保存指标和快照后，停止后端并恢复默认参数。

2500ms/200ms 都只是 future 等待预算，不包含入口查询、Redis 调用等全部接口开销。

### 15.7 版本失效一致性专项

基础 A～H 的推荐 GET 请求不触发学习行为，不能覆盖本次新增的版本失效语义。
这部分独立记录，不把现有 k6 全绿当作已完成。检查顺序为：

1. **STUDY 软失效**：预热后记录快照；通过正常学习行为 API 提交新的 STUDY 事件，确保未被 90 秒
   节流跳过。事务提交后应看到用户版本递增、原缓存保留且成为 STALE；请求推荐后再变为 FRESH。
2. **强失效**：提交 FAVORITE、FINISH 或 Onboarding 业务操作，检查提交后版本递增、相关缓存删除。
   不直接改版本 key 代替业务操作，否则无法验收事务提交后的实际调用链。
3. **旧构建写回隔离**：在慢构建已读取旧版本之后触发一次强失效，记录构建期间的版本变化；
   旧构建结束不应把旧版本结果写回，`build_discarded` 应增加。若无法控制并发交错，不能凭一次
   最终快照判定这一竞态已经覆盖。

现有单元测试可确定性覆盖原子写回拒绝、软/强失效脚本调用和提交后回调：

```bash
cd backend
./mvnw -q -Dtest=RecommendCacheInvalidatorTest,RecommendResultCacheTest test
cd ..
```

真实业务操作需要选定实验课程、事件 ID 与前置学习状态；本节没有自动造数或故障注入脚本。
结果区应分别填写“单元测试证据”和“真实链路证据”；未执行后者时保留“集成待验收”，不伪造结果。

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
