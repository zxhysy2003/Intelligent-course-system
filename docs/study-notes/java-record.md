# Java record 与数据对象

## 核心结论

- record 的功能通常也能用普通类实现，主要益处是减少样板代码，明确表达“一份由组件组成的数据”。
- 组件对应字段为 final，不提供 setter，但只是浅不可变；引用的可变对象仍需额外保护。
- 固定任务参数、查询结果、配置适合 record；需要逐字段修改或依赖继承、JavaBean 构造方式的对象通常更适合普通类。

## 问答记录

### 2026-09-12：项目为什么使用 record，而不直接创建普通类？

- **问题**：既然功能相近，record 有什么实际益处和限制？
- **精炼答案**：编译器默认生成组件字段、规范构造器、访问方法及基于组件的 equals/hashCode/toString，减少重复代码；同时通过不可变字段和 final 类型限制，表达稳定的数据载体语义，不需要 Lombok 等额外依赖。

| 自动提供的内容 | 含义 |
|---|---|
| private final 组件字段 | 构造后不能重新赋值 |
| 接收所有组件的构造器 | 创建时提供完整组件值；值是否有效仍需校验 |
| `id()`、`attempts()` 等访问方法 | 命名不是 JavaBean 的 `getId()` |
| equals/hashCode | 默认按同一种 record 的全部组件判断相等与计算哈希 |
| toString | 默认展示组件名称和值 |

- **工作原理或流程**：`LearningOutboxStore.claim()` 读取 candidate 后，新建 `LearningOutboxTask`，增加 attempts 并生成新 leaseToken。旧对象保持查询时的状态，新对象代表本次领取，不会因原地 setter 修改混淆两种状态。
- **示例**：

```java
record Point(int x, int y) {}
new Point(1, 2).equals(new Point(1, 2)); // true
```

普通类若未重写 equals，通常按对象身份比较；record 的默认组件比较适合数据对象，但不一定适合只按数据库 ID 判断身份的实体。

**浅不可变的边界：**

```java
record Example(List<Long> ids) {}
List<Long> ids = new ArrayList<>();
Example value = new Example(ids);
ids.add(1L); // value.ids() 也发生变化
```

`LearningOutboxPayload.of()` 用 `List.copyOf(ids)` 保护知识点列表，避免外部列表后续修改影响载荷。保护来自工厂方法的复制，不是 record 自动提供；直接调用当前公开规范构造器仍可传入可变列表。

| 场景 | 选择依据 |
|---|---|
| `LearningOutboxTask`、`LearningOutboxPayload` | 固定任务信息适合 record |
| `LearningOutboxProperties` | 配置数据适合 record，并在紧凑构造器中校验参数 |
| `UserCourseRelation` | 进度、收藏状态等需要修改，普通实体类更自然 |

- **易错点**：record 可添加方法、实现接口、校验构造参数，但本身为 final，不能被继承或继承其他业务父类。不能把 final 引用理解为深不可变，也不能据此断言所有 record 都线程安全。框架若要求无参构造加 setter 或通过子类代理，需要检查兼容性，不能机械地把所有类都换成 record。

## 代码定位

| 文件 | 类 / 方法 | 作用 |
|---|---|---|
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxTask.java` | `LearningOutboxTask` | 不可重新赋值的任务组件 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxStore.java` | `claim` | 通过新对象表达领取状态变化 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxPayload.java` | `of` | 工厂方法防御性复制列表 |
| `backend/src/main/java/com/sy/course_system/outbox/LearningOutboxProperties.java` | 紧凑构造器 | 配置组件及参数校验 |
| `backend/src/main/java/com/sy/course_system/entity/UserCourseRelation.java` | `UserCourseRelation` | 需要更新字段的普通实体 |

## 复习自测

1. record 的主要收益是否是普通类无法实现的新功能？
   - 参考答案：不是，主要是简化声明并明确数据语义和约束。
2. record 中有 List 就一定不可修改吗？
   - 参考答案：不是，字段不能重新赋值，但列表本身可能可变，需要复制等保护。
3. 为什么领取任务创建新的 LearningOutboxTask？
   - 参考答案：组件为 final，通过新对象表达新尝试次数与租约令牌。
