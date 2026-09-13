# 热度 Lua 与任务去重

## 核心结论

- `LearningHotUpdater.increment` 将 HOT_INCREMENT 的热度增量写入 Redis，通过任务 ID 凭证避免重试重复加分。
- Lua 将校验、查凭证、增加热度、写凭证放在一次执行中，其他客户端命令不会穿插；这不等于发生错误时自动回滚。
- 课程 ID 必须沿用现有 RedisTemplate 的 member 编码；泛型转换的安全依据来自当前序列化器配置，不来自 `@SuppressWarnings`。
- Outbox 调度与失败恢复背景见 [推荐缓存更新与失效](recommend-cache-invalidation.md)，事务拆分见 [推荐评分快照刷新](recommend-score-snapshot.md)。

## 问答记录

### 2026-09-12：为什么热度增加需要 Lua 和任务凭证？

- **问题**：直接 ZINCRBY 不够吗？如何避免后台重试多加一次热度？
- **精炼答案**：Redis 已更新、MySQL 任务尚未标记 DONE 时进程可能崩溃，任务会重试；以任务 ID 保存执行凭证，重试发现凭证便跳过增量。

| Redis key | 类型 | 用途 |
|---|---|---|
| `course:hot` | ZSet | member 为课程 ID 的序列化结果，score 为课程热度 |
| `learning:outbox:hot:{taskId}` | String | 值为 `1`，标识该任务已应用 |

- **工作原理或流程**：

```text
Java 校验增量并编码课程 ID
  → Lua 校验 key 类型和增量
  → 凭证存在：返回 0
  → 凭证不存在：ZINCRBY → SET 凭证 → 返回 1
  → Java 正常返回，消费者可标记任务 DONE
```

- **示例**：任务 A 给课程 1 加 0.5，A 重试不再增加；另一个任务 B 给同一课程加 0.5 正常执行，总增量 1.0。去重单位是 taskId，不是 courseId。
- **易错点**：MySQL 的 DONE 标记与 Redis 不在一个事务中，凭证弥补重复执行窗口。不能只用多条独立 Redis 命令先查再写，否则两个执行者可能同时查到无凭证并分别加分。

### 2026-09-12：Lua 参数、校验和返回值怎样对应？

- **问题**：KEYS、ARGV、类型判断和特殊数字检查分别做什么？
- **精炼答案**：两个 key 分别指热榜和凭证，两个参数分别是增量和 member；先拒绝已知非法输入，再决定是否应用任务。

| Lua 参数 | Java 传入值 |
|---|---|
| `KEYS[1]` | `course:hot` |
| `KEYS[2]` | `learning:outbox:hot:` 加 taskId |
| `ARGV[1]` | `Double.toString(delta)` |
| `ARGV[2]` | 按现有 value 序列化器编码的课程 ID 字符串 |

1. `redis.call('TYPE', key).ok` 取得类型名称；热榜仅允许 `none` / `zset`，凭证仅允许 `none` / `string`。`none` 表示 key 尚不存在，可以后续创建。
2. `tonumber(ARGV[1])` 解析数字；`not delta` 检查解析失败，`delta ~= delta` 检查 NaN，`math.huge` / `-math.huge` 表示正负无穷，`~=` 表示不等于。
3. Java 入口另外要求增量非负；Lua 自身只校验有限数字，没有单独拒绝负数。零增量允许执行并写凭证。
4. `EXISTS` 返回 1 就返回 0；只检查凭证存在，不要求其内容等于 `1`。
5. `ZINCRBY` 给 member 累加增量，`SET` 写执行凭证，最后返回 1。

| 执行结果 | Java / Outbox 含义 |
|---|---|
| 1 | 本次已更新，正常返回 |
| 0 | 先前已执行，正常返回，可完成任务 |
| 脚本报错、Redis 异常 | 异常向消费者传播，安排失败重试 |
| null | Java 抛出“热度脚本未返回结果”异常 |

- **示例**：凭证 key 意外是 List，脚本在 ZINCRBY 之前报错，避免先加热度后才发现类型异常。
- **易错点**：类型和增量校验在去重检查前，因此即使存在凭证，非法 key 类型或参数也会报错。返回 0 是幂等成功，不是任务失败。

### 2026-09-12：Lua 的原子性有什么边界？

- **问题**：脚本能否保证任何故障下都恰好更新一次？凭证为什么没有 TTL？
- **精炼答案**：脚本防止其他命令穿插造成并发重复，但不提供错误回滚；去重还依赖凭证保留。
- **工作原理或流程**：类型、数字检查尽量前置，减少修改热度后才报错的可能。Lua 后续命令若失败，已经成功的写入不会自动撤销，不能把它理解为 MySQL 的事务回滚。客户端在服务器正常完成后丢失响应时，重试可凭已写入凭证跳过。
- **示例**：热度和凭证都已写成功，但任务状态未保存便崩溃；重新领取原 taskId 后返回 0，避免重复增加。
- **易错点**：凭证通过 SET 写入，没有设置过期时间，支持较晚的同 ID 重放，但会持续积累。无 TTL 不代表绝不丢失；凭证被删除或丢失后，重试可能再次加分。重新生成 taskId 也无法复用原任务凭证。

### 2026-09-12：课程 ID 为什么先序列化？unchecked 警告如何理解？

- **问题**：为什么不用 `courseId.toString()`？`RedisSerializer<?>` 转 `RedisSerializer<Object>` 为什么警告？
- **精炼答案**：Redis 用字节区分 ZSet member，Lua 必须与现有读写格式一致；API 返回通配符类型，编译器不能证明该序列化器接受任意 Object。
- **工作原理或流程**：当前 `RedisConfiguration` 配置 `GenericJackson2JsonRedisSerializer`。先调用同一 value 序列化器编码课程 ID，再按 UTF-8 转字符串，通过脚本参数的 `StringRedisSerializer` 发送，保持当前 JSON 编码的 member 字节一致，避免重复 JSON 序列化。`GenericToStringSerializer<Long>` 是 execute 指定的结果序列化器，脚本返回类型也声明为 Long。

```java
@SuppressWarnings("unchecked")
RedisSerializer<Object> valueSerializer =
        (RedisSerializer<Object>) redis.getValueSerializer();
byte[] memberBytes = valueSerializer.serialize(courseId);
```

- **示例**：如果直接传 ID 的普通文本，而原热榜使用不同 JSON 编码，同一课程可能形成另一个 member，原来的 `score(key, courseId)` 也可能查不到新写入项。
- **易错点**：`?` 表示未知类型，不等同于 Object；泛型转换警告不是编译错误。当前配置的序列化器支持 Object，所以将抑制限定在局部变量并注释依据，未改变编码行为。`@SuppressWarnings` 不增加运行时检查；将来更换序列化器仍需重新核对类型和编码，当前 UTF-8 转换不能直接推广到任意二进制序列化器。

## 代码定位

| 文件 | 类 / 方法 | 作用 |
|---|---|---|
| `backend/src/main/java/com/sy/course_system/outbox/LearningHotUpdater.java` | `SCRIPT`、`increment` | Lua 去重、参数编码与错误传播 |
| `backend/src/main/java/com/sy/course_system/config/RedisConfiguration.java` | `redisTemplate` | String key、JSON value 序列化配置 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxHandler.java` | `handle` 的 HOT_INCREMENT 分支 | 传 taskId、courseId、固定 hotDelta |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxProcessor.java` | `execute` | 完成标记、失败重试入口 |
| `backend/src/test/java/com/sy/course_system/outbox/LearningOutboxIntegrationTest.java` | `crashAfterRedisSuccessCanBeReclaimedWithoutDoubleIncrement`、`hotScriptValidatesTypesBeforeIncrement` | Redis 成功后重试去重、类型校验先于写入 |

## 复习自测

1. Redis 更新成功但 DONE 没保存，重试如何避免多加热度？
   - 参考答案：同 taskId 的凭证已存在，Lua 返回 0，不执行 ZINCRBY。
2. Lua 原子执行是否代表任何错误都会回滚？
   - 参考答案：否，禁止其他命令穿插，不自动撤销已完成写入。
3. 为什么不能只按课程 ID 去重？
   - 参考答案：不同学习事件对同一课程的热度增量都应生效，只有同一任务重试要去重。
4. 为什么保留原序列化器，还单独使用 StringRedisSerializer 传脚本参数？
   - 参考答案：先生成正确的 member 编码，再原样传输，避免改编码或重复 JSON 包装。
5. 局部抑制 unchecked 警告是否证明转换永远安全？
   - 参考答案：否，依据是当前配置；更换序列化器要重新检查。
