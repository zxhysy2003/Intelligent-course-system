# 推荐评分快照刷新

## 核心结论

- `recommend_user_course_score` 保存当前用户对课程的隐式偏好分，是 SVD 协同过滤训练数据，不是评分历史、考试成绩或最终展示推荐分。
- 增量刷新只更新一对用户课程，但会重新聚合这对用户课程的相关行为，计算后覆盖，不在旧分上累加。
- 快照刷新、模型重训、Redis 推荐结果重建是三个独立阶段；关联流程见 [推荐缓存更新与失效](recommend-cache-invalidation.md)。
- `FOR UPDATE` 的锁保持到实际事务提交或回滚；`REQUIRES_NEW` 挂起外层事务不会释放外层锁。Outbox 把评分刷新放到业务提交后，避免持锁外层同步等待内层的相互等待。
- Outbox 写入使用 `MANDATORY`：必须加入已有业务事务，没有事务就报错，约束学习数据与待办任务一起提交或回滚。

## 问答记录

### 2026-09-09：评分快照在哪里发挥作用？

- **问题**：为什么维护单独的评分表，Python 如何使用它？
- **精炼答案**：Java 统一将浏览、学习、收藏、完成行为转换为隐式评分；Python 直接读取归一化评分训练模型，避免训练时重复聚合业务明细。
- **工作原理或流程**：

```text
行为明细 → Java 计算 MySQL 评分快照 → Python 读取并训练 SVD
→ 预测未交互课程的 CF 分 → 后端融合图谱 readiness 等信息
→ 最终推荐结果 → Redis 缓存
```

- Python 读取 `user_id AS user, course_id AS item, score AS rating`；使用训练集中的已有评分课程集合过滤用户已交互课程。
- **示例**：用户 A 对 Java、数据库的隐式分较高，模型可根据全部用户课程数据中的关联预测 A 对其他课程的偏好，预测分再参与后端融合。
- **易错点**：Python 启动或 `POST /model/reload` 才读取快照并训练；SCORE_REFRESH 不自动调用 reload。模型重训本身也不会自动更新已经存在的 Redis 推荐结果。

### 2026-09-09：单个用户课程的快照如何刷新？

- **问题**：怎样计算、落库和处理重复或并发任务？
- **精炼答案**：独立事务中先锁关系行，再读取业务数据，聚合、衰减、归一化，最后 UPSERT 或删除对应快照。

1. `refreshUserCourseScore` 使用 `REQUIRES_NEW` 开启独立 MySQL 事务；用户或课程 ID 为空则返回。
2. 在首次一致性读之前，通过 `selectForUpdate` 锁住对应 `user_course_relation`。关系不存在则删除该对用户课程的快照并返回。
3. 查询 `learning_behavior`，关联 `behavior_weight` 和当前收藏状态，聚合基础分。
4. 对基础分做时间衰减与归一化；没有聚合结果或低于最小评分则删除旧快照。
5. 按 `(user_id, course_id)` 联合主键 UPSERT；提交后 Outbox 再标记任务完成。

| 基础分组成 | 当前公式 |
|---|---|
| 学习分 | 学习权重 × `ln(1 + min(累计 STUDY 秒数, 10800))` |
| 浏览分 | VIEW 行为权重之和 |
| 收藏分 | 当前 `is_favorite=1` 时加收藏权重，否则为 0 |
| 完成分 | FINISH 权重最大值，不按次数叠加 |

```text
baseScore = 学习分 + 浏览分 + 收藏分 + 完成分
rawScore = baseScore × decay(lastTime)
score = 10 × (1 - exp(-rawScore / scale))
```

学习时长贡献在 10800 秒（3 小时）封顶，对数使增长放缓；取消收藏会移除收藏加分。非法、负数基础分按 0 处理；默认 `scale=20`，归一化分低于 `0.1` 不保留。参数以 `recommend.score-snapshot` 为准。

`lastTime` 仅取最近 STUDY/FINISH 时间，衰减按距离现在的整天数计算：

| 时间 | 系数 |
|---|---:|
| ≤ 7 天 | 1.0 |
| 8～30 天 | 0.85 |
| 31～90 天 | 0.6 |
| 91～180 天 | 0.4 |
| > 180 天 | 0.25 |
| 没有 STUDY/FINISH 时间 | 1.0 |

写入字段包括 `raw_score`（已衰减、未归一化）、`score`、`last_behavior_time` 和 `update_time`。后两者分别是最近学习/完成时间与快照写入时间，不应混淆。

- **示例**：基础分 20、最近学习距今 10 天，`rawScore=17`，默认归一化后约 `5.73`。
- **并发与重试**：先锁关系行使同一用户课程的增量刷新串行，避免旧计算后写覆盖新结果；锁在首次普通查询前获取，避免先建立旧读快照。任务 payload 不保存要写回的评分，执行时重查当前数据。快照提交后若进程在任务完成标记前崩溃，重试重新计算覆盖，不会重复加分。
- **易错点**：这是当前值快照，不记录变化历史；“增量”指更新范围小，不代表只计算本次行为。重复执行可因时间衰减变化而得到不同结果。Outbox Handler 会先检查业务是否仍有效，失效业务可以直接 SKIPPED；刷新方法中的删除分支不代表每个被跳过任务都会清理快照。

### 2026-09-09：全量重建和时间衰减何时生效？

- **问题**：全量重建怎样执行？长时间没有新行为时评分会自动下降吗？
- **精炼答案**：后端启动默认全量重建；时间衰减仅在重算时应用，已存分数不会随时间自动变化。
- **工作原理或流程**：`rebuildAllScores` 在一个事务中先查询全部基础分，再删除全部旧快照，计算后分批写入，默认每批 500 条，最后统一提交。批次不是独立事务；查询结果也是先整体加载。`RecommendScoreSnapshotStartupRunner` 根据 `rebuild-on-startup` 决定是否调用，失败记录警告。
- **示例**：某条快照按 7 天内系数 1.0 写入，之后长期无刷新，即使已过 30 天，存储值也不会自行变化；需要重算才能应用新系数。
- **易错点**：当前没有定时全量重建入口。`/model/reload` 只读已有快照训练，不重新计算时间衰减。全量重建未采用增量方法的逐关系行加锁流程，不能把增量并发保障直接套用到全量与在线写入并发的场景。

### 2026-09-11：FOR UPDATE 与普通 SELECT 有什么不同？

- **问题**：`selectForUpdate` 为什么在 SQL 末尾增加 `FOR UPDATE`？
- **精炼答案**：它是加排他锁的当前读，用数据库锁协调同一关系记录的并发操作；普通 SELECT 在 InnoDB 常见 READ COMMITTED / REPEATABLE READ 隔离级别下通常是 MVCC 快照读。

| 对比 | 普通 SELECT | SELECT ... FOR UPDATE |
|---|---|---|
| 读取方式 | 读取事务可见版本 | 取得锁后读取当前记录 |
| 对其他事务修改的影响 | 通常不阻止修改 | 冲突的更新、加锁读取需要等待 |
| 对其他普通查询的影响 | 可并发读取 | 普通查询通常仍可读取已提交版本 |
| 锁释放时机 | 通常不持有这种排他行锁 | 实际事务提交或回滚时 |

- **工作原理或流程**：评分刷新先锁 `user_course_relation`，再聚合行为并写快照。同一用户课程的另一个增量刷新等待前一个事务结束后再计算，避免旧计算后写覆盖新结果。锁属于数据库事务，因此多个线程、多个后端实例都受约束。
- **示例**：A 锁住用户 2、课程 1 的关系行，B 对同一行执行 FOR UPDATE 会等待；A 提交评分并释放锁后，B 才能取得锁继续。
- **易错点**：锁持续到事务结束，不一定是当前 Java 方法结束；加入外层事务时要等外层结束。自动提交模式下，单条语句结束便无法继续保护后续操作。锁范围受索引、查询条件和隔离级别影响，不能断言总是只锁返回的一行。锁关系表不会自动锁行为表，相关业务需要遵守先锁同一关系行的约定。

### 2026-09-11：为什么评分刷新使用 REQUIRES_NEW？挂起会释放锁吗？

- **问题**：独立事务带来什么好处？内层执行时外层锁如何处理？
- **精炼答案**：REQUIRES_NEW 固定评分刷新的独立事务边界；外层存在事务时将其挂起，内层独立提交或回滚后恢复外层。挂起不等于结束，外层持有的锁不会释放。

| 调用时情况 | REQUIRED（默认） | REQUIRES_NEW |
|---|---|---|
| 没有事务 | 新建事务 | 新建事务 |
| 已有事务 | 加入外层事务 | 挂起外层，新建独立事务 |
| 正常返回 | 加入外层时尚未提交 | 提交自己的事务并恢复外层 |

- **工作原理或流程**：独立事务避免继承外层在 REPEATABLE READ 下已经建立的旧读快照。配合“先取得关系锁、再首次普通查询”，评分查询不会沿用调用方先前的读快照。自己的评分写入完成即可提交并释放自己的锁，不必等调用方完成其他工作。
- **当前代码边界**：Outbox Processor 和 Handler 没有包裹评分刷新的外层事务，领取任务的事务也已结束。因此当前正常路径中 REQUIRED 同样会新建事务；REQUIRES_NEW 主要明确独立执行约定，不能说只有它才让当前路径具备事务。
- **示例：挂起后仍相互等待**：

```text
外层事务 A：FOR UPDATE 锁住 X
  → 同步调用另一 Bean 的 REQUIRES_NEW 方法
  → A 挂起，保留 X 的锁，等待方法返回
内层事务 B：尝试 FOR UPDATE / UPDATE 同一 X
  → B 等待 A 释放锁
  → 通常最终锁等待超时
```

- **易错点**：不能指望数据库必然识别为死锁，因为 A 等待 B 返回发生在应用层。B 若只执行普通 SELECT，通常能读已提交版本，但看不到 A 未提交的修改。内层提交不会因外层之后回滚而撤销；内层异常若继续向外传播，仍可能导致外层也回滚，独立事务不等于异常自动隔离。默认 Spring 代理模式下，同对象 `this.inner()` 调用通常不会触发新的事务拦截。已有外层事务时，内层独立 JDBC 事务通常还需额外数据库连接。

### 2026-09-11：引入 Outbox 改变了什么？

- **问题**：为什么业务提交后消费 Outbox 能避免上述事务相互等待？
- **精炼答案**：业务事务只持久化待办任务，不同步执行评分刷新。业务提交释放锁后，后台用另一个事务完成评分计算；改变的是执行时机、原子记录方式和失败恢复方式。

```text
业务事务 A
  锁关系行 → 更新进度、写学习行为 → 插入 Outbox 任务
  → 一起提交，释放锁
                 ↓
后台领取任务，领取事务提交
                 ↓
评分事务 B
  锁关系行 → 读取已提交行为 → 计算并覆盖快照 → 提交
                 ↓
Outbox 标记 DONE；前置条件满足后再消费缓存失效任务
```

- **工作原理或流程**：`LearningOutboxWriter.enqueue` 使用 MANDATORY 加入业务事务，要求调用时已有事务。学习数据和任务要么一起保存，要么一起回滚，避免业务已提交但安排后台刷新之前进程崩溃导致工作丢失。消费者执行评分刷新时，原业务事务已经结束，B 不再是持锁 A 同步等待的内层事务。
- **示例**：评分写入失败则回滚本次评分事务，已经提交的学习数据保留；Outbox 记录失败并重试，达到上限进入 DEAD。评分提交后、标记 DONE 前崩溃也可重试，重新计算覆盖不会重复加分。
- **易错点**：将“业务内直接调用 REQUIRES_NEW”作为对比是假设场景，不代表已核实的历史实现。Outbox 不消除所有锁竞争，新的学习请求仍可能暂时持有同一关系行锁；它避免的是持锁外层同步等待独立内层的结构。代价是异步延迟，学习数据与快照不在同一个事务里更新；持久化和重试也不代表必然成功，DEAD 任务需要处理。模型重训仍是另一阶段。

### 2026-09-13：Outbox 写入为什么使用 Propagation.MANDATORY？

- **问题**：`LearningOutboxWriter.enqueue()` 的 `propagation = Propagation.MANDATORY` 是什么意思，与默认传播行为有什么区别？
- **精炼答案**：`propagation` 决定方法被调用时如何处理事务。`MANDATORY` 要求调用时已有指定事务管理器管理的事务，并加入其中；没有事务则抛出 `IllegalTransactionStateException`，不执行方法体，也不会自行新建事务。

| 调用时情况 | REQUIRED（默认） | MANDATORY |
|---|---|---|
| 已有事务 | 加入现有事务 | 加入现有事务 |
| 没有事务 | 新建事务 | 报错，拒绝执行 |

- **工作原理或流程**：`LearningBehaviorServiceImpl.recordBehavior()` 使用 `transactionManager` 开启业务事务，保存学习行为、更新关系数据，再通过注入的 `outbox` 调用 `enqueue()`。后者指定同一个事务管理器，插入的任务记录加入同一事务，等待外层统一提交或回滚。采用 MANDATORY 能及时发现调用方遗漏事务的问题，避免任务脱离业务事务单独提交。
- **示例**：学习行为已写入但尚未提交，随后任务插入发生运行时异常并向外传播，业务事务回滚，学习行为、进度和本次任务记录都不会提交，避免业务已保存却丢失后续待办任务。
- **易错点**：`enqueue()` 返回不代表事务已经提交。这里保证的是学习数据与 **Outbox 任务记录** 的原子写入，不包含后台之后执行的 Redis 更新或评分刷新。事务传播规则依赖 Spring 事务代理；当前跨 Bean 的注入调用符合条件，直接 `new` 对象或同对象内部调用通常不会触发默认代理拦截。

## 代码定位

| 文件 | 类 / 方法 | 作用 |
|---|---|---|
| `backend/src/main/java/com/sy/course_system/service/impl/RecommendScoreSnapshotServiceImpl.java` | `refreshUserCourseScore`、`rebuildAllScores`、`toSnapshot` | 事务刷新、全量重建和归一化 |
| `backend/src/main/resources/mapper/LearningBehaviorMapper.xml` | `userCourseBaseScoreQuery` | 统一基础评分公式 |
| `backend/src/main/java/com/sy/course_system/mapper/RecommendScoreSnapshotMapper.java` | `upsertBatch`、`deleteByUserCourse` | 覆盖写入或删除 |
| `backend/src/main/java/com/sy/course_system/common/util/TimeDecayUtil.java` | `decay` | 分段时间衰减 |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendScoreSnapshotStartupRunner.java` | `run` | 启动重建入口 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxHandler.java` | `handle` | SCORE_REFRESH 调用与业务有效性检查 |
| `backend/src/main/java/com/sy/course_system/mapper/UserCourseRelationMapper.java` | `selectForUpdate` | 对用户课程关系加锁读取 |
| `backend/src/main/java/com/sy/course_system/service/impl/LearningBehaviorServiceImpl.java` | `recordBehavior` | 业务事务中锁关系、写行为并创建任务 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxWriter.java` | `enqueue` | MANDATORY 保证任务加入业务事务 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxProcessor.java` | `scan`、`execute` | 提交领取后异步执行，完成或记录失败 |
| `backend/src/main/resources/db/migration/V1__baseline_schema.sql` | `recommend_user_course_score` | 字段和联合主键 |
| `recommend-service/model.py` | `_load_score_frame`、`reload`、`recommend` | 读取快照、训练和预测 |
| `recommend-service/main.py` | 启动生命周期、`reload_model` | 训练触发入口 |

## 复习自测

1. 为什么任务重试不会把同一行为分加两次？
   - 参考答案：重新聚合业务数据并按联合主键覆盖，不累加旧快照分。
2. `raw_score` 是否已经包含衰减？
   - 参考答案：包含；它只尚未归一化。
3. 为什么锁关系行要放在首次普通查询之前？
   - 参考答案：串行刷新，并避免先建立旧读快照，等待后仍按旧数据计算。
4. 刷新快照后下一次推荐一定使用新模型吗？
   - 参考答案：不一定，还需要模型重训；最终列表也受 Redis 缓存生命周期影响。
5. REQUIRES_NEW 挂起外层后，外层的 FOR UPDATE 锁会释放吗？
   - 参考答案：不会，外层事务提交或回滚才释放；内层再锁同一行可能等待超时。
6. Outbox 如何避免持锁外层同步等待评分事务？
   - 参考答案：业务事务只插入任务，提交释放锁后后台再执行评分事务。
7. 当前 Outbox 链路改用 REQUIRED 是否一定失去独立评分事务？
   - 参考答案：否，当前调用时没有外层事务，两者都会新建事务；差别在已有事务的调用场景。
8. 没有业务事务时，通过 Spring 代理调用 MANDATORY 的 enqueue 会怎样？
   - 参考答案：抛出 IllegalTransactionStateException，不执行方法体；不会像 REQUIRED 一样新建事务。
9. enqueue 正常返回后，任务是否已经提交，Redis 更新是否也在这个事务内？
   - 参考答案：任务随外层业务事务一起提交；Redis 更新由后台之后执行，不属于这个数据库事务。
