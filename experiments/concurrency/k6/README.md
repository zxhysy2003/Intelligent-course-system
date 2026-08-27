# k6 压测脚本

本目录保存高并发实验的 HTTP 压测脚本。

约定：

- 文件名使用实验编号，例如 `con-01-concurrent-enrollment.js`。
- 基础地址、账号、密码、课程 ID 通过环境变量传入，不把 Token 写入仓库。
- 每个实验使用编号前缀，例如 `CON01_USERNAME`，避免与 shell 的 `USERNAME` 等系统变量冲突。
- 登录和数据准备放在 `setup()`，不混入目标接口指标。
- 同时检查 HTTP 状态和响应体中的 `Result.code`。
- 使用自定义 Counter 区分业务成功、预期冲突和非预期异常。
- 默认不输出每次请求的响应体，避免日志 IO 干扰结果。

## 安装

当前项目不把 k6 作为 npm 依赖。macOS 可以使用 Homebrew 安装：

```bash
brew install k6
k6 version
```

其他系统或 Docker 方式见 [Grafana k6 官方安装文档](https://grafana.com/docs/k6/latest/set-up/install-k6/)。

## CON-01 并发选课

已实现脚本：

- [`con-01-concurrent-enrollment.js`](./con-01-concurrent-enrollment.js)
- 详细方案见 [CON-01 并发选课实验](../docs/01-concurrent-enrollment.md)。

运行前必须保证目标用户尚未选择目标课程。下面的命令会在 `setup()` 中登录一次，然后让 50 个 VU 各发送一次相同的选课请求：

```bash
CON01_BASE_URL=http://127.0.0.1:8080 \
CON01_USERNAME=stu_newbie \
CON01_PASSWORD=123456 \
CON01_COURSE_ID=10 \
CON01_VUS=50 \
k6 run experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

也可以传入已有 Token，跳过登录：

```bash
CON01_BASE_URL=http://127.0.0.1:8080 \
CON01_TOKEN=仅在当前终端保存的JWT \
CON01_COURSE_ID=10 \
CON01_VUS=50 \
k6 run experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

脚本内置正确性阈值：

- `enrollment_success == 1`
- `enrollment_duplicate == CON01_VUS - 1`
- `enrollment_unexpected == 0`
- 所有响应检查通过

任意条件不满足时，k6 会以非零状态退出。脚本不会自动删除选课关系，因为实验后还需要查询数据库验证最终状态；再次运行前必须换一个未选课程，或者只在实验数据库中清理关系。

## 双后端实例

通过 `CON01_BASE_URLS` 传入逗号分隔的后端地址：

```bash
CON01_BASE_URLS=http://127.0.0.1:8080,http://127.0.0.1:8081 \
CON01_USERNAME=stu_newbie \
CON01_PASSWORD=123456 \
CON01_COURSE_ID=14 \
CON01_VUS=100 \
k6 run experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

登录请求发送到第一个地址；选课请求按照 VU 编号在所有地址间轮询。100 VU、两个地址时，每个实例应恰好收到 50 次选课请求。脚本会为每个 `backend_index` 增加 `http_reqs` 数量阈值，确保双实例不是“启动了两个、实际只请求了一个”。两个实例必须共享 MySQL 和 JWT 签名密钥。

## CON-02 并发学习进度

已实现脚本：

- [`con-02-concurrent-study-progress.js`](./con-02-concurrent-study-progress.js)
- 详细方案见 [CON-02 并发学习进度与首次完课实验](../docs/02-concurrent-learning-progress.md)。
- 数据准备见 [`../data/con-02-prepare.sql`](../data/con-02-prepare.sql)。

单实例运行示例：

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

双实例通过 `CON02_BASE_URLS` 传入两个地址。脚本要求所有 STUDY 请求业务成功，并验证每个实例实际收到的请求数。学习时长、进度和 FINISH 数量必须在运行后通过 SQL 断言，不能仅根据 k6 成功率判断实验通过。

## CON-03 STUDY 请求重复消费幂等

已实现脚本：

- [`con-03-study-idempotency.js`](./con-03-study-idempotency.js)
- 详细方案见 [CON-03 STUDY 请求重复消费幂等实验](../docs/03-study-event-idempotency.md)。
- 数据准备与断言见 [`../data/con-03-prepare.sql`](../data/con-03-prepare.sql) 和 [`../data/con-03-assert.sql`](../data/con-03-assert.sql)。

脚本支持三种模式：

- `SAME`：全部 VU 复用同一 `eventId`，预期首次处理 1 次，其余均为回放。
- `UNIQUE`：脚本按 VU 生成不同 `eventId`，预期全部首次处理。
- `CONFLICT`：复用数据库中已有的 `eventId`，但提交不同内容，预期全部返回业务码 409。

单实例 SAME 示例：

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

双实例通过 `CON03_BASE_URLS` 传入两个地址。脚本会严格检查首次处理、回放、冲突、非预期响应和每个后端的请求数；运行结束后仍必须执行断言 SQL 验证数据库最终状态。

### CON-03 事务回滚故障扩展

[`con-03-rollback-retry.js`](./con-03-rollback-retry.js) 将故障实验拆成两个独立阶段：

- `CON03_ROLLBACK_PHASE=FAIL`：指定事件在日志插入后触发一次预期 HTTP 500，随后使用 SQL 验证 `ROLLED_BACK`。
- `CON03_ROLLBACK_PHASE=RECOVER`：同一事件先正常首次处理，再执行一次幂等回放，最终使用 SQL 验证 `RECOVERED`。

该实验固定使用 `stu_newbie` 的课程 14，与主实验课程 10、11、12 隔离。完整启动、故障开关、k6 和 SQL 命令见 [CON-03 事务回滚后重试](../docs/03-study-event-idempotency.md#151-事务回滚后重试)。

故障实例由 `scripts/start-concurrency-backend.sh` 通过 `spring-boot:test-run` 启动，故障代理只存在于 `backend/src/test/java`；正常启动仍使用正式 JAR，生产代码不携带实验故障能力。

## CON-04 推荐缓存击穿与雪崩

已实现：

- [`con-04-single-key-cache.js`](./con-04-single-key-cache.js)：同一用户热缓存、快速冷构建、慢冷构建和双实例共享锁。
- [`con-04-cache-avalanche.js`](./con-04-cache-avalanche.js)：多用户同时冷缓存，以及固定 TTL 到期后的多 key 雪崩。

两个脚本都会在 teardown 阶段读取推荐 Stub 的 `/stats`，把真实回源次数和最大上游并发写入 k6 自定义指标。完整轮次、数据、Stub 和参数说明见 [CON-04 推荐缓存击穿与雪崩实验](../docs/04-recommend-cache-breakdown.md)。
