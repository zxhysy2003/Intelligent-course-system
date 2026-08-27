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

## 2. 当前实现

推荐结果缓存位于：

```text
backend/src/main/java/com/sy/course_system/recommend/RecommendResultCache.java
```

普通用户使用：

```text
cache key = recommend:user:{userId}
lock key  = recommend:lock:user:{userId}
```

当前流程：

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
| `regular-ttl-minutes` | 30 分钟 | 普通推荐结果物理 TTL |
| `cold-start-ttl-minutes` | 10 分钟 | 冷启动推荐结果物理 TTL |
| `build-lock-ttl-seconds` | 20 秒 | 构建锁租约 |
| `wait-retry-times` | 3 | 未抢到锁后的轮询次数 |
| `wait-millis` | 80ms | 每次轮询等待时间 |

因此默认等待预算约为：

```text
3 × 80ms = 240ms
```

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
100ms < 240ms 等待预算
```

锁持有者应在等待者超时前写入缓存：

```text
回源次数 = 1
最大上游并发 = 1
```

### 3.3 慢构建

同一 key 冷缓存，Stub 延迟 800ms：

```text
800ms > 240ms 等待预算
```

等待者会在缓存尚未写入时结束轮询，并执行当前实现中的无锁回源：

```text
回源次数 > 1
最大上游并发 > 1
```

这轮用来证明：有分布式锁不代表一定不会击穿，等待策略必须覆盖正常构建时间，或者提供旧值返回机制。

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
[con04-backend] lock TTL: 20s, cache TTL: 30min
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

运行 WARM 模式。脚本会在 setup 中请求一次完成预热，随后把 Stub 统计清零，再发送 100 个并发请求：

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

预期只有锁持有者调用一次 Stub，其余请求通过短轮询读到刚写入的缓存。

如果偶尔超过 1 次，先查看本轮实际 p95 和 Stub `delayMs`，确认本机完整推荐构建是否已经超过 240ms。不要先修改代码，应保留该现象作为当前等待预算不足的证据。

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
  -e CON04_EXPECT_UPSTREAM_MIN=2 \
  -e CON04_EXPECT_UPSTREAM_MAX=100 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=2 \
  -e CON04_EXPECT_MAX_ACTIVE_MAX=100 \
  experiments/concurrency/k6/con-04-single-key-cache.js
```

当前实现预计出现：

```text
con04_upstream_requests > 1
con04_upstream_max_active > 1
```

接口可能仍然全部返回 200，但 Stub 统计能够证明多个请求已经同时回源。

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
  -e CON04_EXPECT_UPSTREAM_MIN=50 \
  -e CON04_EXPECT_UPSTREAM_MAX=50 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=2 \
  experiments/concurrency/k6/con-04-cache-avalanche.js
```

每个 VU 使用独立账号，因此对应 50 个缓存 key。预期 50 次回源全部合法获得各自的用户锁，`maxActiveRequests` 显著大于 1。

## 13. 轮次 F：固定 TTL 到期雪崩

这一轮直接证明固定 TTL 会让同一批写入的 key 在相近时刻过期。

先停止后端，再用 1 分钟普通缓存 TTL 重启：

```bash
CON04_REGULAR_TTL_MINUTES=1 \
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
  -e CON04_AVALANCHE_START_TIME=70s \
  -e CON04_PASSWORD=123456 \
  -e CON04_STUB_URL=http://127.0.0.1:18000 \
  -e CON04_EXPECT_UPSTREAM_MIN=50 \
  -e CON04_EXPECT_UPSTREAM_MAX=50 \
  -e CON04_EXPECT_MAX_ACTIVE_MIN=2 \
  experiments/concurrency/k6/con-04-cache-avalanche.js
```

脚本内部时序：

```text
0s：50 个用户并发预热缓存
10s：清零 Stub 统计，只保留到期后的回源数据
约 60s：固定 1 分钟 TTL 的缓存集中到期
70s：50 个用户再次并发请求
结束：读取 Stub requestTotal 和 maxActiveRequests
```

如果缓存 TTL 后续加入随机抖动，同样的 70s 负载应只打到已经到期的部分 key，而不是稳定回源 50 次。

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

加入 TTL 抖动后，应使用相同预热时间和 70s 请求时刻复跑轮次 F。优化目标不是“永远零回源”，而是避免大量 key 在同一个时间窗口全部回源。

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
