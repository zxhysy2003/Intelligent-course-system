# Outbox 关闭流程与线程中断

## 核心结论

- `LearningOutboxProcessor.close()` 是 `@PreDestroy` 销毁回调，由 Spring 在销毁受管理的 Bean 时调用，不需要业务代码显式引用。
- 关闭顺序是：通知扫描停止领取任务 → 关闭工作线程池入口 → 最多等待 30 秒 → 必要时尝试中断工作线程 → 关闭续租线程池。
- 中断是协作式通知，不是强制终止线程。捕获 `InterruptedException` 后恢复中断标记，是为了让调用方仍能感知中断请求。

## 问答记录

### 2026-09-13：为什么 close() 只在测试中被引用，运行时在哪里发挥作用？

- **问题**：没有业务调用的 `close()` 如何执行，为什么需要它？
- **精炼答案**：类上的 `@Component` 让 Spring 管理实例，方法上的 `@PreDestroy` 注册销毁回调。应用正常关闭、容器销毁 Bean 时会执行它，用于清理手动创建的工作线程池和续租线程池。静态查找引用不一定显示生命周期调用关系。
- **工作原理或流程**：

  | 操作 | 作用与原因 |
  |---|---|
  | `stopping = true` | `scan()` 在入口和领取循环检查该标志，观察到关闭状态后停止领取；`volatile` 保证跨线程可见性，但不会撤销已经开始的领取操作。 |
  | `workers.shutdown()` | 拒绝新提交的任务，允许已经提交的任务继续执行；方法本身不等待结束。 |
  | `workers.awaitTermination(30, TimeUnit.SECONDS)` | 最多等待 30 秒，让任务完成业务、状态回写及 `finally` 清理。等待期间续租线程池仍在运行，可以继续维护任务租约。 |
  | 超时后 `workers.shutdownNow()` | 尝试中断正在执行的工作线程，并阻止排队任务开始；不保证正在执行的代码立即停止。 |
  | `renewer.shutdownNow()` | 最后关闭续租线程池，停止后续心跳。 |

- **示例**：测试中通过 `new LearningOutboxProcessor(...)` 创建实例，不受 Spring 生命周期管理，所以在 `finally` 中显式调用 `processor.close()` 清理线程池。当前策略测试和集成测试均采用这种方式。
- **易错点**：
  - 正常关闭（例如通常情况下的 `Ctrl+C`）可以触发销毁回调；`kill -9` 等强制终止无法保证执行清理。
  - `shutdownNow()` 发出中断请求，不会强杀线程；任务需要响应中断。当前实现调用它后没有再次等待线程池终止，所以 `close()` 返回不等于所有工作线程一定已经结束。
  - 未完成状态回写的任务，在后续扫描时可以依据租约过期和尝试次数规则重新领取或标记为 `DEAD`。相关机制见 [推荐缓存更新与失效](recommend-cache-invalidation.md)。

### 2026-09-13：等待被中断时，为什么还要恢复当前线程的中断标记？

- **问题**：下面两个操作分别针对哪个线程，恢复标记有什么价值？

  ```java
  catch (InterruptedException e) {
      workers.shutdownNow();
      Thread.currentThread().interrupt();
  }
  ```

- **精炼答案**：假设线程 A 正在执行 `close()` 并等待工作线程池结束。其他线程调用 `A.interrupt()` 后，`awaitTermination()` 抛出 `InterruptedException`，并清除 A 的中断标记。捕获后先尝试停止工作线程，再恢复 A 的标记，避免吞掉传给 A 的中断通知。
- **工作原理或流程**：
  1. A 进入 `awaitTermination()` 等待。
  2. 其他线程请求中断 A。
  3. 等待抛出异常，A 的中断标记被清除。
  4. `workers.shutdownNow()` 尝试中断线程池里的工作线程。
  5. `Thread.currentThread().interrupt()` 重新标记 A 被中断。
  6. A 继续执行 `renewer.shutdownNow()`，然后从 `close()` 返回；调用方可以检查标记并决定是否继续工作。

- **示例**：以下是解释机制的假设调用方，并非项目现有关闭逻辑。

  ```java
  public void stopApplication() {
      processor.close();
      if (Thread.currentThread().isInterrupted()) {
          return; // 响应中断请求，跳过可选的耗时工作
      }
      generateShutdownReport();
  }
  ```

  | close() 捕获中断后的处理 | 示例调用方的行为 |
  |---|---|
  | 只执行 `workers.shutdownNow()` | 中断标记已被异常清除，外层检查为 `false`，继续生成报告。 |
  | 同时恢复当前线程的中断标记 | 外层检查为 `true`，跳过报告。 |

- **易错点**：
  - 执行 `close()` 的线程 A，与 `workers` 中执行任务的线程不是同一个概念；两个中断操作针对不同对象。
  - 恢复标记不会自动 `return`、立即抛异常或终止线程；后续代码需要检查标记，或通过可响应中断的阻塞操作响应它。
  - `isInterrupted()` 读取标记但不清除；不要与会读取并清除当前线程标记的 `Thread.interrupted()` 混淆。

## 代码定位

| 文件 | 类 / 方法 | 作用 |
|---|---|---|
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxProcessor.java` | `close`、`scan`、`execute` | 销毁回调、停止领取、任务执行及续租清理 |
| `backend/src/main/java/com/sy/course_system/mapper/LearningOutboxMapper.java` | `selectClaimableForUpdate`、`expireExhausted` | 租约过期后的任务回收与尝试上限处理 |
| `backend/src/test/java/com/sy/course_system/outbox/LearningOutboxPolicyTest.java` | `pausedProcessorOnlyObservesWithoutClaiming` | 手动创建处理器并在 finally 中关闭 |
| `backend/src/test/java/com/sy/course_system/outbox/LearningOutboxIntegrationTest.java` | `backgroundProcessorDrainsDependenciesAndPublishesMetrics` | 集成测试中的显式资源清理 |

## 复习自测

1. 为什么业务代码不调用 close()，应用关闭时它仍能运行？
   - 参考答案：Spring 销毁受管理的 Bean 时调用其 `@PreDestroy` 方法。
2. 为什么续租线程池最后关闭？
   - 参考答案：工作任务收尾期间仍需要续租，避免等待期间租约过期。
3. shutdownNow() 能保证任务立即终止吗？
   - 参考答案：不能，它尝试通过中断请求停止，任务必须配合响应。
4. catch 中恢复的是哪个线程的中断标记，为什么？
   - 参考答案：正在执行 close() 的线程；等待抛出中断异常时清除了标记，恢复后外层仍可感知请求。
