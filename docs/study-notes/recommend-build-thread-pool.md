# 推荐构建线程池

## 核心结论

- `build-core-size` 控制核心线程数，`build-max-size` 控制线程总数上限，`build-queue-capacity` 控制等待队列容量；项目默认分别为 `2 / 4 / 16`。
- 接收任务的关键顺序是：核心线程 → 等待队列 → 扩容至最大线程数 → 拒绝。不是先把线程扩到最大，再开始排队。
- 这些参数约束单个 JVM 的推荐构建任务，不是 HTTP 请求数、整个后端的线程数或 QPS 上限。
- 同一 JVM 内，同一个缓存 key 的并发构建由 single-flight 合并，共享一个 `CompletableFuture`，不会每个请求都独立排队。

## 问答记录

### 2026-09-03：`build-core-size / max-size / queue-capacity` 分别是什么意思？

#### 问题与精炼答案

这三个参数分别决定：优先使用多少工作线程、繁忙时最多有多少工作线程、允许多少任务等待执行。

项目中的完整配置名都位于 `recommend.cache` 下，具体映射如下：

| 配置名 | 默认值 | Spring 设置方法 | 含义 |
|---|---:|---|---|
| `build-core-size` | 2 | `setCorePoolSize` | 线程数未达到核心数时，新任务优先触发创建线程；本项目没有预启动核心线程，线程按需创建。 |
| `build-max-size` | 4 | `setMaxPoolSize` | 工作线程总数上限，包含核心线程，因此最多是 4 个，不是 `2 + 4` 个。 |
| `build-queue-capacity` | 16 | `setQueueCapacity` | 最多容纳 16 个尚未开始执行的任务，不包含已经被工作线程取走的任务。 |

#### 工作原理或执行流程

推荐构建协调器先检查同 key 是否已有任务：已有则返回同一个 future；没有才提交给线程池。

在线程池正常运行时，一个新任务按以下顺序接收：

1. 当前工作线程数小于核心线程数：创建线程处理任务。
2. 已达到核心线程数：优先尝试入队，由可用工作线程取出执行。
3. 队列已满，且工作线程数小于最大线程数：再创建额外线程处理新任务。
4. 队列已满，且线程数已达最大值：执行拒绝策略。

本项目使用 `AbortPolicy`，拒绝时抛出异常。协调器会清理本次任务的 in-flight 记录，缓存层将异常转为失败的 future，并记录 `refresh_rejected`：

- 首次缓存缺失的请求走预计算热门快照降级；没有快照时返回合法空列表。
- 已有逻辑过期旧值的请求继续返回旧值，不等待刷新。
- 被拒绝的构建不会改为在请求线程上执行。

#### 示例：连续提交 21 个不同 key 的构建任务

假设线程池刚开始没有线程，所有任务都需要构建；提交很快，期间没有任务完成、也没有线程从队列取走任务。

| 提交的任务 | 处理方式 | 此时执行中 / 排队中 |
|---|---|---|
| 第 1～2 个 | 创建 2 个核心线程执行 | 2 / 0 |
| 第 3～18 个 | 放入容量为 16 的队列 | 2 / 16 |
| 第 19～20 个 | 队列已满，再创建 2 个线程执行 | 4 / 16 |
| 第 21 个 | 线程和队列均满，触发拒绝 | 4 / 16 |

所以默认容量是最多同时承载 `4 个执行中 + 16 个排队中 = 20 个未完成任务`。这不是一次压测最多只能成功构建 20 次：只要任务完成并腾出容量，后续任务就能继续被接收。

对比：如果 100 个请求在同一个 key 的构建尚未结束时加入该构建，它们共享一个任务，不会占满 100 个队列位置。

#### 易错点与注意事项

- **队列不是线程。** 排队的 16 个任务此时不会各自执行构建，也不会各占一个构建线程。
- **最大线程数不代表始终运行这么多线程。** 默认先使用核心线程；队列未满时，不会仅因任务开始排队就扩容到 4。
- **更大的队列不等于更快。** 它增加的是等待空间，不会直接提升构建处理速度；首次 miss 默认最多等待 future 2500ms，这段等待也可能花在排队上。
- **请求等待超时不等于任务取消。** 当前实现返回降级结果后，已接收的后台任务仍可继续执行并写缓存，直到结束才释放容量。
- **等待队列不保证所有任务严格按提交顺序执行。** 上例中新建线程可直接执行第 19、20 个任务，不必先等待第 3～18 个任务全部完成。
- **限制按 JVM 生效。** 两个后端实例各自拥有线程池，各最多执行 4 个构建；跨实例同 key 互斥仍依赖 Redis 分布式锁。
- **别混淆不同线程池。** 这里是外层 `recommendCacheBuildExecutor`，不是 Tomcat 请求线程池，也不是推荐内部并行使用的 `recommendTaskExecutor`。

## 代码定位

以下路径均相对仓库根目录；以类名、方法名定位，避免依赖容易漂移的行号。

| 文件 | 类 / 方法 | 作用 |
|---|---|---|
| `backend/src/main/resources/application.yaml` | `recommend.cache` | 配置默认值及环境变量覆盖入口。 |
| `backend/src/main/java/com/sy/course_system/config/RecommendProperties.java` | `RecommendProperties.Cache` | 配置绑定；校验核心数大于 0、最大数不小于核心数、队列容量非负。 |
| `backend/src/main/java/com/sy/course_system/config/RecommendCacheBuildConfig.java` | `recommendCacheBuildExecutor` | 创建有界构建线程池，设置三个参数与 `AbortPolicy`。 |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendBuildCoordinator.java` | `submit`、`inFlightCount` | 合并同 key 的构建；清理完成或拒绝的任务；统计排队与执行中的任务。 |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendResultCache.java` | `getOrBuild`、`startCoordinatedBuild`、`awaitOrDegrade` | 处理旧值返回、线程池拒绝、请求等待超时和降级。 |
| `backend/src/test/java/com/sy/course_system/recommend/RecommendBuildCoordinatorTest.java` | `sameKeyShouldShareOneInFlightBuild`、`rejectedExecutorShouldRejectWithoutLeavingInFlightEntry` | 验证任务合并与拒绝后的记录清理。 |
| `backend/src/test/java/com/sy/course_system/recommend/RecommendResultCacheTest.java` | `queuedBuildShouldNotAcquireRedisLockUntilExecutorStartsIt` | 验证请求可以先超时降级，排队任务随后仍能执行。 |

## 复习自测

1. `core=2、max=4、queue=16`，两个核心线程都忙时，第 3 个任务会立刻创建新线程吗？

   - 参考答案：不会，优先入队；队列满后才尝试扩容至最大线程数。

2. 默认配置最多允许多少个构建任务同时执行？能否理解为最多接收 20 个 HTTP 请求？

   - 参考答案：每个 JVM 最多执行 4 个构建。20 是执行中与排队中任务的容量之和，不是 HTTP 请求上限；缓存命中和同 key 合并都会改变请求与任务的对应关系。

3. 构建线程和队列都满了，首次 miss 请求会怎样？

   - 参考答案：`AbortPolicy` 拒绝新任务，缓存层记录拒绝并返回预计算热门快照或空列表，不在请求线程上执行昂贵构建。

4. 把队列从 16 调大，是否必然缩短请求等待？

   - 参考答案：不会；线程处理速度不变时，更多任务只是在等待，且队列更晚填满也可能推迟扩容。

5. 请求等待超过 2500ms 后返回了降级结果，对应后台构建是否已经停止？

   - 参考答案：没有。当前实现没有因等待超时取消任务，任务仍占用排队或执行容量，直到完成。
