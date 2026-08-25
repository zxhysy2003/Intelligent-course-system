# CON-01 并发选课实验

## 1. 实验要回答的问题

当同一个用户在极短时间内重复提交同一门课程的选课请求时：

1. 数据库最终是否只有一条用户课程关系？
2. 有多少请求获得业务成功响应？
3. 重复请求会被转换为可理解的业务结果，还是泄漏为服务器异常？
4. 当前实现属于“数据不重复”，还是完整的“接口幂等”？
5. 如果后端扩展为多个实例，结论是否仍然成立？

这个实验的重点不是追求高 QPS，而是证明并发条件下的最终状态正确。

## 2. 当前调用链

```text
k6 并发请求
  -> POST /api/v1/courses/{courseId}/enrollment
  -> JWT 过滤器解析出同一个 userId
  -> CourseController.userAttendCourse
  -> UserCourseServiceImpl.userAttendCourse
  -> INSERT user_course_relation
  -> MySQL 唯一索引 uk_user_course(user_id, course_id)
```

相关代码：

- Controller：`backend/src/main/java/com/sy/course_system/controller/client/CourseController.java`
- Service：`backend/src/main/java/com/sy/course_system/service/impl/UserCourseServiceImpl.java`
- 表结构：`backend/src/main/resources/db/migration/V1__baseline_schema.sql`

当前 Service 没有先执行“查询是否已选课”，而是直接 `INSERT`：

- 第一个拿到唯一键写入资格的请求插入成功。
- 其他请求触发 `DuplicateKeyException`。
- Service 捕获异常并返回 `false`。
- Controller 将 `false` 转成业务码 `400`。

数据库中的 `uk_user_course(user_id, course_id)` 是最终正确性的关键。它对单实例和多实例都有效，不依赖某个 JVM 的本地锁。

## 3. 实验假设

假设使用一个此前未选择目标课程的用户，同时发送 `N` 个请求：

- H1：最终关系表中恰好有一行。
- H2：业务码 `200` 恰好出现一次。
- H3：其余 `N-1` 个请求返回业务码 `400`，消息为“用户已添加过该课程。”。
- H4：没有请求出现业务码 `500`，服务端没有未处理的 `DuplicateKeyException`。
- H5：将后端扩展成两个实例后，H1～H4 仍成立。

注意：项目的 `Result.code` 是响应体中的业务码。Controller 返回业务失败时，HTTP 状态目前仍可能是 `200`，因此压测脚本必须解析 JSON，不能只检查 HTTP 状态。

## 4. 实验环境

### 4.1 数据隔离

使用实验专用数据库，例如：

```text
course_concurrency
```

不要在日常开发库中执行删除关系或删除唯一索引的操作。

### 4.2 测试身份

可以使用实验库基线中的普通学生账号，也可以单独创建压测账号。基线数据中的示例账号为：

```text
username: stu_newbie
password: 123456
```

账号仅用于本机实验。压测结果和提交文件中不得保存登录后得到的 JWT。

### 4.3 目标课程

选择一门状态为上线、且该用户尚未选择的课程。开始前执行数据库断言：

```sql
SELECT COUNT(*)
FROM user_course_relation
WHERE user_id = :userId
  AND course_id = :courseId;
```

结果必须为 `0`。如果不是 `0`，应更换用户/课程，或者只在实验数据库中清理该关系。

## 5. 第一轮：顺序冒烟测试

并发压测前先验证接口语义：

1. 调用 `POST /api/v1/auth/login` 获取 Token。
2. 第一次调用选课接口，预期响应体 `code=200`、`data=true`。
3. 第二次调用同一接口，预期响应体 `code=400`。
4. 查询数据库，预期关系行数为 `1`。

这一轮失败时不要继续提高并发，否则无法区分接口配置问题和并发问题。

## 6. 第二轮：同一用户并发选同一课程

### 6.1 负载模型

建议依次运行三组：

| 轮次 | 虚拟用户数 | 每个虚拟用户请求数 | 总请求数 |
|---|---:|---:|---:|
| A | 10 | 1 | 10 |
| B | 50 | 1 | 50 |
| C | 100 | 1 | 100 |

k6 使用 `per-vu-iterations` 场景，每个 VU 只发一次请求。所有 VU 复用同一个 Token 和 `courseId`，这是本实验刻意制造的唯一键竞争。

登录应放在 k6 的 `setup()` 中，只执行一次；登录流量不能混入选课接口的性能统计。

当前脚本：[`../k6/con-01-concurrent-enrollment.js`](../k6/con-01-concurrent-enrollment.js)

如果本机尚未安装 k6，macOS 先执行：

```bash
brew install k6
k6 version
```

其他安装方式见 [Grafana k6 官方安装文档](https://grafana.com/docs/k6/latest/set-up/install-k6/)。

运行示例：

```bash
CON01_BASE_URL=http://127.0.0.1:8080 \
CON01_USERNAME=stu_newbie \
CON01_PASSWORD=123456 \
CON01_COURSE_ID=10 \
CON01_VUS=50 \
k6 run experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

也可以通过 `CON01_TOKEN` 传入当前终端中的 JWT，并省略 `CON01_USERNAME`、`CON01_PASSWORD`。`CON01_COURSE_ID` 必填，`CON01_VUS` 默认是 `50`，`CON01_MAX_DURATION` 默认是 `30s`。实验变量统一使用 `CON01_` 前缀，避免误读 zsh 自带的 `USERNAME` 等环境变量。

脚本内置以下阈值：

- 成功选课恰好一次。
- 重复选课恰好 `CON01_VUS - 1` 次。
- 非预期结果为零。
- 所有 HTTP 与业务响应检查通过。

只要有一项不满足，k6 就会以非零状态退出。每次运行前都必须重新确认关系行数为 `0`；脚本刻意不自动清理数据，以便运行后执行数据库断言。

### 6.2 客户端统计

压测脚本至少记录：

- `enrollment_success`：响应体 `code === 200`。
- `enrollment_duplicate`：响应体 `code === 400`。
- `enrollment_unexpected`：其他业务码、非 JSON 响应或网络错误。
- `http_req_duration`：选课接口耗时。

不能给每个重复响应都打完整日志，否则客户端日志 IO 可能反过来影响压测。

### 6.3 数据库断言

每轮结束后必须执行：

```sql
SELECT COUNT(*) AS relation_count
FROM user_course_relation
WHERE user_id = :userId
  AND course_id = :courseId;
```

期望：

```text
relation_count = 1
```

同时检查是否出现重复组合：

```sql
SELECT user_id, course_id, COUNT(*) AS row_count
FROM user_course_relation
GROUP BY user_id, course_id
HAVING COUNT(*) > 1;
```

期望返回空集。

## 7. 第三轮：双后端实例

启动两个共享 MySQL、Redis、Neo4j 和 JWT 签名密钥的后端实例，再重复第二轮。当前阶段不需要先引入 Nginx：通过 `CON01_BASE_URLS` 传入两个地址，k6 按照 VU 编号均匀选择目标实例，并使用 `http_reqs{backend_index=...}` 阈值验证每个实例收到的请求数。该方式适合先验证跨 JVM 并发正确性；后续研究真实负载均衡策略时再引入 Nginx。

先停止可能占用 `8080` 端口的 `./scripts/dev.sh`。在仓库根目录启动基础设施并构建后端：

```bash
cd scripts
docker compose up -d

cd ../backend
./mvnw -q -DskipTests package
```

在终端 A 中启动实例 1，等待日志显示启动完成：

```bash
cd /path/to/Intelligent-course-system
./scripts/start-concurrency-backend.sh 8080
```

再在终端 B 中启动实例 2：

```bash
cd /path/to/Intelligent-course-system
./scripts/start-concurrency-backend.sh 8081
```

脚本从仓库根目录定位并加载同一个 `.env.local`，固定使用 `course_concurrency`，同时关闭推荐快照重建和热榜同步。因此实例 1 签发的 JWT 可以被实例 2 验证，且无关后台任务不会干扰启动和观测。

确认两个实例都健康：

```bash
curl http://127.0.0.1:8080/actuator/health
curl http://127.0.0.1:8081/actuator/health
```

当前实验库中课程 `10`、`11`、`12` 已用于前三轮；确认 `stu_newbie` 尚未选择课程 `14` 后，执行双实例实验：

```bash
k6 run \
  -e CON01_BASE_URLS=http://127.0.0.1:8080,http://127.0.0.1:8081 \
  -e CON01_USERNAME=stu_newbie \
  -e CON01_PASSWORD=123456 \
  -e CON01_COURSE_ID=14 \
  -e CON01_VUS=100 \
  experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

100 VU 时，除原有的“一次成功、99 次重复、零个非预期结果”外，脚本还要求两个后端实例各收到 50 次选课请求。

预期结果仍是一行，因为唯一性由 MySQL 维护。这个对比可以说明：

- Java `synchronized` 只能保护一个 JVM。
- 本地 `ReentrantLock` 也不能覆盖另一个实例。
- 数据库唯一约束位于所有实例最终共享的写入点，因此是不可省略的最后防线。

本实验不需要为了选课额外引入 Redis 分布式锁。相对于一次短 `INSERT`，分布式锁会增加网络往返、锁超时和释放正确性问题，而唯一索引已经能够表达业务约束。

## 8. 可选对照：没有唯一约束会怎样

该对照只能在一次性实验数据库或临时表中进行，禁止修改正式 Flyway 基线后直接运行。

对照方法：

1. 复制 `user_course_relation` 结构到临时表。
2. 在临时表中移除 `(user_id, course_id)` 唯一约束。
3. 对临时写入入口发送相同并发请求。
4. 比较最终重复行数。

它用于证明“先查不存在再插入”并不能在并发下保证唯一：多个请求可能同时查询到不存在，然后全部插入。即便应用层增加一次存在性查询，数据库唯一约束仍然必须保留。

## 9. 正确性与幂等语义的区别

当前实现能够保证：

```text
同一用户和课程最多只有一条关系
```

但重复请求的返回结果不同：第一次成功，后续返回业务错误。因此它实现了数据层面的去重，却不是最强意义上的接口幂等回放。

可以比较两种接口语义：

### 语义 A：重复选课是错误

- 第一次返回成功。
- 后续返回 `400`。
- 适合强调用户进行了重复操作。

### 语义 B：保证目标状态即可

- 第一次创建关系。
- 后续发现关系已存在，也返回“当前已选课”的成功状态。
- 相同请求重复执行，对客户端表现保持一致，更接近声明式幂等。

是否修改语义需要结合前端和产品约定决定，不能只为了技术上的“幂等”擅自改变现有接口。

## 10. 可选的 JUnit 并发测试

HTTP 压测用于观察真实链路和性能，JUnit 并发测试用于稳定验证正确性。后续可增加一个使用真实 MySQL 或 Testcontainers 的集成测试：

1. 准备同一个 `userId` 和 `courseId`。
2. 创建固定大小线程池。
3. 使用 `CountDownLatch` 让所有线程同时开始。
4. 每个线程调用一次 `userAttendCourse`。
5. 等待所有任务结束。
6. 断言成功计数为 `1`、数据库行数为 `1`。

不建议使用 H2 代替 MySQL 验证这个实验，因为唯一键冲突、事务和异常转换行为应尽量贴近真实数据库。

## 11. 需要记录的结果

| 指标 | 10 并发 | 50 并发 | 100 并发 | 双实例 100 并发 |
|---|---:|---:|---:|---:|
| 业务成功数 | | | | |
| 重复选课数 | | | | |
| 非预期错误数 | | | | |
| 最终关系行数 | | | | |
| p95 | | | | |
| p99 | | | | |

还应记录 MySQL 的锁等待、后端错误日志和连接池等待情况。失败插入可能消耗自增 ID，出现 ID 间隙是正常现象，不代表生成了重复业务数据。

## 12. 面试表达模板

可以按照下面的顺序说明实验：

1. 业务约束是同一用户只能选择同一门课程一次。
2. 应用层不做“查询后插入”，而是直接写数据库。
3. MySQL 通过 `(user_id, course_id)` 唯一索引完成最终仲裁。
4. 并发冲突由 Service 捕获并转换为业务结果。
5. 单实例和双实例实验都验证最终只有一行。
6. 不选择 JVM 锁，是因为它不能覆盖集群；不额外选择分布式锁，是因为短写入场景下唯一索引更简单可靠。
7. 当前仍可讨论重复请求返回错误还是幂等成功，这是数据正确性之外的 API 语义问题。

## 13. 实验完成标准

- 10、50、100 并发下最终都只有一条用户课程关系。
- 单实例和双实例结果一致。
- 没有未处理的数据库唯一键异常。
- 压测脚本区分 HTTP 状态与 `Result.code`。
- 实验报告包含性能结果和数据库正确性断言。
- 可以清楚说明数据库唯一约束为什么比本地锁更适合作为最终防线。
