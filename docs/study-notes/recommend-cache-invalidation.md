# 推荐缓存更新与失效

## 核心结论

- 请求负责读取或重建推荐结果；业务变化负责让缓存失效。失效本身不重新计算推荐。
- 软失效保留旧结果，通过版本变化使其过时；强失效增加版本并删除结果。
- 学习行为通过 Outbox 在评分刷新、必要的掌握度更新结束后失效缓存；这不代表 CF 模型已重训。
- Outbox 用短事务行锁保护领取，用可续期租约管理执行资格；租约限制任务状态回写，外部效果仍需要幂等处理。
- 本文补充缓存生命周期；构建并发细节见 [推荐构建线程池](recommend-build-thread-pool.md)，训练数据见 [推荐评分快照刷新](recommend-score-snapshot.md)。

## 问答记录

### 2026-09-09：推荐请求如何读取和更新缓存？

- **问题**：缓存何时直接返回，何时更新，为什么过期后还可以返回旧数据？
- **精炼答案**：先判断冷启动分支，再按缓存状态决定直接返回、异步刷新或等待首次构建。

| Redis key | 用途 |
|---|---|
| `recommend:v2:user:{userId}` | 普通推荐结果 |
| `recommend:v2:cold:user:{userId}` | 冷启动推荐结果 |
| `recommend:v2:version:user:{userId}` | 用户推荐版本 |
| `recommend:cold:status:user:{userId}` | 冷启动判定结果 |

| 状态 | 条件 | 请求行为 |
|---|---|---|
| FRESH | 逻辑未过期且版本一致 | 直接返回 |
| STALE | 逻辑过期或版本不一致，结果仍存在 | 返回旧结果并后台重建 |
| MISS | 结果不存在或数据格式无效 | 发起构建并有限等待，失败或超时降级 |

- **工作原理或流程**：普通推荐默认逻辑 TTL 为 30～40 分钟，冷启动为 10～13 分钟，均包含随机抖动；Redis 物理 TTL 比逻辑 TTL 多 60 分钟。过期旧数据用于刷新期间兜底，请求到来才触发重建。首次构建默认最多等待 2500 毫秒，超时后构建仍可继续；降级读取预计算热门内存快照，尚无快照时可以返回空列表。用户进入普通推荐分支后删除冷启动结果缓存。以上数字均为配置默认值。
- **示例**：结果逻辑过期但尚未被 Redis 删除，本次请求返回旧列表，后台算好后覆盖缓存，后续请求使用新列表。
- **易错点**：逻辑过期不等于 Redis 删除；热门兜底快照、MySQL 评分快照和用户推荐结果缓存是不同数据。

### 2026-09-09：软失效、强失效和版本号分别解决什么问题？

- **问题**：为什么有时只改版本，有时删除结果？如何防止旧构建覆盖新状态？
- **精炼答案**：频繁学习上报采用软失效以复用旧结果，关键偏好变化采用强失效；两者都通过版本递增阻止旧版本写回。

| 触发 | 方式 | Redis 操作 |
|---|---|---|
| 普通 STUDY 上报 | soft | 节流通过后版本加一、删除冷启动状态，保留推荐结果 |
| 学习触发完成、收藏、取消收藏 | strong | 版本加一，删除普通/冷启动推荐结果及冷启动状态 |
| 保存兴趣标签 | strong | 事务提交后直接执行强失效 |
| 普通 VIEW | 不直接失效推荐结果 | 仍可创建热度、评分刷新任务 |

- **工作原理或流程**：soft 默认按用户节流 90 秒，节流键为 `recommend:invalidate:study:user:{userId}`。窗口内调用直接返回 0，不增加版本，也不再次删除冷启动状态。节流检查、版本更新和删除操作由 Lua 执行；强失效也使用 Lua。构建开始记录用户版本，写回时通过 Lua 比较当前版本，一致才写入。
- **示例**：请求 A 按版本 5 构建；学习完成使版本变为 6；A 结束时写回版本 5 的结果被拒绝。
- **易错点**：版本校验保护缓存写回，不保证在途请求不返回旧构建结果。强失效也不保证下一次请求立即得到最新推荐，仍可能等待或降级。软失效的节流不是“90 秒后自动执行一次刷新”。

### 2026-09-09：Outbox 如何安排推荐缓存失效？

- **问题**：为什么缓存失效要依赖评分和掌握度任务，失败后如何处理？
- **精炼答案**：业务写入与任务创建在同一 MySQL 事务中提交；失效任务等前置数据更新后执行，失败交给 Outbox 重试。

```text
学习行为、进度更新 + Outbox 任务写入（同一事务）
                         ↓ 提交
SCORE_REFRESH ────────────┐
MASTERY_UPDATE（存在时）───┴→ RECOMMEND_INVALIDATE
HOT_INCREMENT 独立执行
```

| 任务字段 | 在推荐链路中的作用 |
|---|---|
| `id` / `source_event_id` | 每条任务独立 UUID；同次 enqueue 的任务共享来源 UUID，不是行为表主键 |
| `task_type` / `user_id` / `course_id` | 标识操作及对应用户课程 |
| `payload` | 保存执行参数；评分刷新本身会重新读业务表计算 |
| `dependency_id` | 缓存失效任务依赖的评分刷新任务 ID |
| `mastery_dependency_id` | 缓存失效任务依赖的掌握度任务 ID，可为空 |
| `status` / `attempts` | 状态及已领取次数，包含首次执行 |
| `next_attempt_at` | 最早允许执行或重试的时间 |
| `lease_token` / `lease_until` | 本次领取令牌及到期时间，用于续租、回收和限制旧执行者回写 |
| `last_error` | 最近错误，最多 1000 字符 |
| `created_at` / `completed_at` | 创建时间；成功或跳过时间，DEAD 当前不填写完成时间 |

- **工作原理或流程**：前置状态必须为 DONE 或 SKIPPED 才能领取依赖任务；PENDING 等待，PROCESSING 执行中，DEAD 达到尝试上限。租约到期可重新领取，但受次数上限限制。来源事件与任务类型的联合唯一约束防止同一来源重复创建同类型任务；状态与执行时间、租约时间的索引用于调度查询。
- **示例**：掌握度更新失败时，推荐失效任务继续等待，避免先失效后立即用尚未更新的图谱数据重建。
- **易错点**：缓存失效不依赖热度任务；前置 DEAD 不满足放行条件。Outbox 的 Redis 失败会传播并重试，兴趣标签的直接失效入口失败只记录日志。评分刷新完成只代表训练数据更新，不保证内存 CF 模型更新。

### 2026-09-12：Outbox 租约如何领取、续租和超时接管？

- **问题**：执行者崩溃后如何恢复，旧执行者回来后为什么不能覆盖新状态？
- **精炼答案**：领取生成本次 UUID 令牌并设置截止时间，心跳续租；过期后其他消费者可重新领取并替换令牌。续租、完成、失败回写都必须满足令牌匹配、PROCESSING、租约未过期。

| 数据 | 语义 |
|---|---|
| taskId | 同一任务跨重试保持不变，用于外部效果去重 |
| lease_token | 每次领取新建，区分不同执行资格 |
| lease_until | 资格到期时间，使用数据库时间计算 |
| attempts | 每次领取加一，包括租约到期后重新领取 |

**工作原理或流程：**

1. 扫描器默认 fixedDelay 为 1000 毫秒，取得本地并发名额后调用 claim。
2. claim 在 REQUIRES_NEW、READ_COMMITTED 短事务中，先将过期且尝试耗尽的 PROCESSING 标为 DEAD，再寻找到时间的 PENDING 或过期 PROCESSING。候选还须满足次数上限及前置任务条件。
3. `FOR UPDATE SKIP LOCKED` 锁住候选并跳过其他消费者占用的行；将状态改成 PROCESSING，次数加一，写新令牌与“数据库现在 + 60 秒”的默认租约，然后提交释放行锁。
4. Handler 执行前立即续租一次；无资格则返回。执行期间独立续租线程池默认每 20 秒尝试续租，将到期时间设为“数据库现在 + 60 秒”，不是在旧截止时间上累加。
5. 成功写 DONE 或 SKIPPED 并清租约；失败设置重试时间、转 PENDING 或 DEAD 并清租约。执行结束取消心跳、释放本地并发名额。

续租、完成、失败 SQL 共用的资格约束为：

```sql
WHERE id = #{task.id}
  AND status = 'PROCESSING'
  AND lease_token = #{task.leaseToken}
  AND lease_until > NOW(6)
```

更新 0 行代表条件不满足。过期令牌即使未被接管也不能续活，需重新领取。默认最多尝试 20 次，租约到期且耗尽次数的任务由后续 claim 标记 DEAD。具体数值可通过配置覆盖。

- **示例**：A 在 12:00:00 领取任务，截止 12:01:00；12:00:20 续租后截止改为 12:01:20。A 随后失联，到期后 B 在后续扫描领取同 taskId，令牌由 token-A 换为 token-B。A 恢复后仍持旧令牌，无法续租、完成或记录失败，不能覆盖 B 的任务状态。
- **易错点**：到期并不即时启动接管，仍需扫描、有执行名额且满足领取条件。执行 Handler 期间不持续持有领取时的行锁，租约也不是一把持续持有的数据库行锁。

**租约丢失不等于业务代码停止：**

- 心跳续租返回 false 时，将本地 `AtomicBoolean owned` 改为 false，阻止后续状态回写，但不会自动中断正在执行的 Handler。
- 心跳遇到数据库异常时只记录日志，不立即改变 owned，因为异常不一定证明租约已丢失；数据库条件仍是回写资格的最终判断。
- A 暂停后恢复时，可能与已接管的 B 同时执行外部操作。因此租约不能单独保证副作用只发生一次。热度 Lua 以不变 taskId 的凭证去重，详见 [热度 Lua 与任务去重](learning-hot-lua.md)。UUID 租约令牌并不是外部系统自动检查的递增栅栏令牌。

## 代码定位

| 文件 | 类 / 方法 | 作用 |
|---|---|---|
| `backend/src/main/java/com/sy/course_system/service/impl/HybridRecommendServiceImpl.java` | `recommend` | 分支选择、缓存调用 |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendResultCache.java` | `getOrBuild`、`read`、`write` | 三态读取、构建及版本校验 |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendCacheInvalidator.java` | `invalidateFromOutbox`、Lua 脚本 | 软/强失效与错误传播 |
| `backend/src/main/java/com/sy/course_system/service/impl/LearningBehaviorServiceImpl.java` | 行为处理与 `outbox.enqueue` | 选择失效模式 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxWriter.java` | `enqueue` | 创建前置依赖 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxStore.java` | `claim`、`complete`、`fail` | 领取、依赖检查及重试 |
| `backend/src/main/java/com/sy/course_system/mapper/LearningOutboxMapper.java` | `selectClaimableForUpdate`、`renew`、`complete`、`fail` | 领取 SQL、依赖与租约资格约束 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxProcessor.java` | `scan`、`execute` | 本地并发名额、心跳与 owned 标志 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxProperties.java` | 配置组件 | 租约、续租、尝试上限及合法性校验 |
| `backend/src/main/resources/db/migration/V4__add_learning_outbox.sql` | `learning_outbox_task` | 字段和索引 |
| `backend/src/main/resources/application.yaml` | `recommend.cache` | 默认 TTL、等待与节流参数 |

## 复习自测

1. soft 之后为什么还能返回旧推荐？
   - 参考答案：结果未删除，版本不一致使它成为 STALE，返回旧结果同时重建。
2. 为什么删除缓存之外还要增加版本？
   - 参考答案：防止失效之前开始的旧构建随后写回有效缓存。
3. SCORE_REFRESH 完成是否意味着 CF 已使用最新行为？
   - 参考答案：否，只更新训练快照，还需要重训模型并重建推荐结果。
4. 领取行锁是否保持到 Handler 执行完？
   - 参考答案：否，领取短事务提交即释放，之后通过数据库租约字段管理资格。
5. 为什么 taskId 不变而 lease_token 每次变？
   - 参考答案：taskId 识别同一任务以去重，令牌区分每次领取并限制旧执行者回写。
6. 续租失败是否会自动停止 Handler？
   - 参考答案：不会。当前实现限制状态回写，外部操作还需幂等机制防重复。
