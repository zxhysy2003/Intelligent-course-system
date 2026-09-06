# Micrometer 与推荐缓存指标观测

## 核心结论

- Micrometer 是 JVM 应用的指标采集门面，为 `Counter`、`Timer`、`Gauge` 等指标提供统一 API；它负责采集，不负责持久化历史数据或绘制监控面板。
- 本项目通过 Spring Boot Actuator 间接引入并自动配置 Micrometer，业务代码通过 `MeterRegistry` 注册推荐缓存指标。
- 推荐缓存使用事件计数器观察命中、锁竞争、异常和降级，使用计时器观察构建耗时，使用仪表值观察当前排队或执行中的构建任务数。
- Actuator 暴露 `health`、`info`、`metrics`。健康检查允许匿名访问，其他管理端点要求 `ADMIN` 角色。
- 当前没有引入 Prometheus Registry，指标主要用于当前 JVM 的 Actuator 单机观测和 CON-04 并发实验；进程重启后不保留历史曲线。

## 问答记录

### 2026-09-04：Micrometer 是什么？

- **问题**：Micrometer 的职责是什么，它和 Actuator、Prometheus 有什么区别？
- **精炼答案**：Micrometer 为应用代码提供与监控厂商无关的指标 API。代码只需要面向 `MeterRegistry` 记录指标，之后可以按需对接不同后端。Actuator 负责在 Spring Boot 中自动配置和通过 HTTP 端点暴露指标；Prometheus 则是可选的指标采集、存储和查询系统。
- **常用指标类型**：

  | 类型 | 适合回答的问题 | 特征 |
  |---|---|---|
  | `Counter` | 某个事件累计发生多少次？ | 只递增，适合命中、失败等事件 |
  | `Timer` | 操作执行了多少次、耗时多久？ | 记录次数、总耗时、最大值及配置的分位数 |
  | `Gauge` | 某个状态此刻是多少？ | 可升可降，读取对象当前值 |

- **工作流程**：

  ```text
  业务代码产生事件
      → Micrometer 将测量值登记到 MeterRegistry
      → Actuator 暴露当前指标
      → 管理员或并发测试脚本读取指标
      → 可选：以后接入 Prometheus/Grafana 做历史存储和展示
  ```

- **易错点**：
  - Micrometer 不是日志框架。日志保存一次请求的具体上下文，指标聚合大量请求的数量、耗时和当前状态。
  - Micrometer 不等于 Prometheus。没有 Prometheus，也可以通过 Actuator 查看当前进程中的指标。
  - 标签会形成新的时间序列，应使用 `hit`、`miss` 这类数量有限的值，不应把用户 ID 等高基数数据作为标签。

### 2026-09-04：本项目如何使用 Micrometer？

- **问题**：Micrometer 在智能课程系统中的依赖、埋点、暴露和实验用途分别是什么？
- **精炼答案**：项目通过 `spring-boot-starter-actuator` 获得 Micrometer，由 `RecommendCacheMetrics` 和 `RecommendBuildCoordinatorMetrics` 集中定义指标，推荐缓存业务在关键分支调用它们。指标通过 `/actuator/metrics` 查询，CON-04 的 k6 脚本还会读取构建任务数来判断实验是否已经收尾。
- **指标定义**：

  | 指标 | 类型 | 含义 |
  |---|---|---|
  | `recommend.cache.events{event=...}` | `Counter` | 按事件标签累计缓存运行事件 |
  | `recommend.cache.build.count` | `Counter` | 实际推荐构建尝试的完成次数 |
  | `recommend.cache.build.duration` | `Timer` | 实际构建耗时，显式发布 P95、P99 |
  | `recommend.cache.build.duration.percentile{quantile=...}` | `Gauge` | 将 P95、P99 转换为 Actuator 可直接读取的毫秒值 |
  | `recommend.cache.build.inflight` | `Gauge` | 当前 JVM 中排队或执行中的不同缓存 key 构建数 |

- **事件标签**：

  | `event` | 触发时机 |
  |---|---|
  | `hit` | 命中新鲜缓存 |
  | `stale_hit` | 命中逻辑过期的旧值，并触发后台刷新 |
  | `miss` | 缓存未命中 |
  | `lock_acquired` / `lock_busy` | 获得 Redis 构建锁 / 锁已被占用 |
  | `wait_timeout` | 首次构建等待超时 |
  | `refresh_rejected` | 有界构建线程池拒绝新任务 |
  | `degraded` | 请求返回降级结果 |
  | `redis_error` | 推荐结果缓存或冷启动状态缓存访问 Redis 失败 |
  | `build_failed` | 实际执行推荐构建时发生异常 |
  | `build_discarded` | 构建期间用户版本变化，结果不再写入缓存 |

- **关键运行流程**：

  ```text
  RecommendResultCache.getOrBuild
      ├─ 新鲜缓存 → event(hit)
      ├─ 旧缓存   → event(stale_hit) → 提交后台刷新
      └─ 未命中   → event(miss) → 提交构建并等待
                                      ├─ 超时 → event(wait_timeout) → event(degraded)
                                      └─ 完成 → build.count + build.duration
  ```

- **构建指标语义**：`buildCompleted` 位于构建方法的 `finally` 中，因此成功和失败的实际构建都会增加 `build.count` 并记录耗时；失败还会额外增加 `event=build_failed`。多个请求若通过 single-flight 共享同一个构建 Future，只计算一次实际构建，而不是按等待请求数重复计算。
- **查询示例**：取得管理员 Token 后，可查询某个事件的累计值：

  ```bash
  curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
    "http://127.0.0.1:8080/actuator/metrics/recommend.cache.events?tag=event:hit"
  ```

- **并发实验用途**：CON-04 的 k6 公共脚本轮询 `/actuator/metrics/recommend.cache.build.inflight`，只有各后端实例的值和推荐 Stub 的活动请求数都归零，才认为本轮实验真正结束。Stub 负责统计上游回源次数和并发量，Micrometer 负责观察后端内部缓存分支和构建状态，两者不能互相替代。
- **易错点**：
  - `recommend.cache.build.inflight` 是单个 JVM 的状态；多实例实验要逐个后端读取，不能把一个实例的值当作全局值。
  - `build.count` 统计实际构建尝试，不等于推荐接口请求数，也不等于 Stub 回源次数。
  - `/actuator/health` 可以匿名访问，但 `/actuator/metrics` 需要管理员权限。

## 代码定位

| 文件 | 类 / 方法 | 作用 |
|---|---|---|
| `backend/pom.xml` | `spring-boot-starter-actuator` 依赖 | 引入 Actuator 和 Micrometer 基础能力 |
| `backend/src/main/resources/application.yaml` | `management.endpoints.web.exposure` | 暴露 `health`、`info`、`metrics` |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendCacheMetrics.java` | `event`、`buildCompleted` | 定义事件、构建次数、耗时和分位数指标 |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendBuildCoordinatorMetrics.java` | 构造方法 | 注册正在排队或执行的构建 Gauge |
| `backend/src/main/java/com/sy/course_system/recommend/RecommendResultCache.java` | `getOrBuild`、`buildAndMaybeWrite` 等 | 在推荐缓存关键运行分支记录指标 |
| `backend/src/main/java/com/sy/course_system/service/impl/ColdStartSupportServiceImpl.java` | `decide` | 冷启动状态缓存访问失败时记录 `redis_error` |
| `backend/src/main/java/com/sy/course_system/config/SecurityConfig.java` | `filterChain` | 配置 Actuator 健康检查和指标端点权限 |
| `experiments/concurrency/k6/con-04-common.js` | `readBackendBuildStates` | 读取 `build.inflight` 判断构建任务是否排空 |
| `backend/src/test/java/com/sy/course_system/recommend/RecommendCacheMetricsTest.java` | 分位数测试 | 验证 P95、P99 Gauge 为有限有效值 |
| `backend/src/test/java/com/sy/course_system/recommend/RecommendBuildCoordinatorTest.java` | `metricsShouldIncludeQueuedAndRunningBuilds` | 验证 Gauge 同时覆盖排队和执行中的任务 |

## 复习自测

1. Micrometer、Actuator 和 Prometheus 各自负责什么？
   - 参考答案：Micrometer 提供指标采集 API，Actuator 自动配置并暴露管理端点，Prometheus 可选地采集、存储和查询历史指标。
2. 为什么缓存事件使用一个指标名加 `event` 标签，而不是为每个事件编写一套统计类？
   - 参考答案：维持统一的维度模型，便于按事件筛选和聚合；事件值集合固定，不会产生高基数问题。
3. `build.count` 为什么可能包含失败构建？
   - 参考答案：`buildCompleted` 在 `finally` 中执行，所有真正开始的构建尝试都会被计数和计时，失败由额外的 `build_failed` 事件区分。
4. `build.inflight=0` 表示什么？
   - 参考答案：当前这个 JVM 的推荐构建协调器中没有排队或执行中的缓存 key；它不代表其他后端实例也已经排空。
5. 为什么 CON-04 同时需要 Stub 统计和 Micrometer 指标？
   - 参考答案：Stub 观察上游回源次数与并发，Micrometer 观察后端内部缓存命中、锁竞争、降级和构建状态，观测层次不同。
