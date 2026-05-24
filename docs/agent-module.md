# 学习助手 Agent 模块

## 1. 模块作用

学习助手 Agent 模块为学习者提供只读的课程学习问答能力。它会读取用户学习画像、学习进度、能力雷达、最近学习课程和混合推荐结果，组装成模型上下文，再调用 OpenAI 兼容接口生成回答。

这个模块不负责替用户执行选课、收藏、删除课程、修改学习进度等写操作。它的写入范围只限于保存 Agent 会话和消息，用于聊天历史展示、失败重试和幂等恢复。

## 2. 入口位置

- Controller：`backend/src/main/java/com/sy/course_system/controller/client/AgentController.java`
- API 路径：
  - `GET /agent/sessions`：查询当前用户的有效会话
  - `POST /agent/sessions`：创建会话
  - `PATCH /agent/sessions/{sessionId}`：重命名会话
  - `DELETE /agent/sessions/{sessionId}`：软删除会话
  - `GET /agent/sessions/{sessionId}/messages`：查询会话消息
  - `POST /agent/chat`：发送聊天消息
- Service：`AgentService` / `AgentServiceImpl`
- 前端页面：`frontend/src/views/user/AgentAssistant.vue`
- 前端接口封装：`frontend/src/api/agent.js`
- 前端路由：`/agent`

## 3. 核心流程

1. 前端进入 `/agent` 页面后，通过 `ListAgentSessions` 拉取当前用户的会话列表；如果有会话，默认选择第一条并加载消息。历史消息加载后会执行 `normalizeLoadedMessages`，把“有 `clientMessageId` 但没有对应 ASSISTANT”的 USER 消息标记为可重试。
2. 用户发送消息时，前端生成 `clientMessageId`，先在页面插入一条本地 `sending` 状态的 USER 消息，再调用 `POST /agent/chat`。
3. `AgentController.chat` 从 `UserContext` 获取当前用户 ID，并调用 `AgentServiceImpl.chat`。
4. `AgentServiceImpl` 先检查 `AGENT_ENABLED`，再校验消息内容和 `clientMessageId`。消息不能为空，长度不能超过 2000；`clientMessageId` 需要符合 `[A-Za-z0-9_-]{8,64}`。
5. Service 根据 `clientMessageId` 查询是否已有 USER / ASSISTANT 消息：
   - 如果 USER 和 ASSISTANT 都存在，说明这是已完成请求的重试，直接返回已有消息。
   - 如果 USER 存在但 ASSISTANT 不存在，说明之前可能超时或进程中断，会进入恢复流程；拿到恢复租约后会重新读取一次消息对，避免原请求刚补写 ASSISTANT 时重复调用模型。
   - 如果 USER 不存在，则按普通新消息处理。
6. 普通新消息会先解析会话：
   - 请求带 `sessionId` 时，调用 `requireSession` 校验该会话属于当前用户且状态为有效。
   - 请求不带 `sessionId` 时，根据用户问题生成标题并创建新会话。
7. USER 消息落库后，`AgentContextAssembler` 组装学习上下文，主要包括学习画像、近 30 天学习进度、能力雷达、最近学习课程和混合推荐候选。
8. `OpenAiCompatibleAgentLlmClient` 根据配置决定调用真实模型或本地 mock：
   - `provider=mock` 或 `apiKey` 为空时返回 mock 回答。
   - 否则向 `{AGENT_LLM_BASE_URL}/chat/completions` 发送 OpenAI 兼容请求。
9. Service 将 ASSISTANT 消息和 metadata 落库，metadata 中保存 provider、model、sourceCount、sources、status、error、latencyMs 等信息。
10. 前端收到响应后，用后端返回的 USER / ASSISTANT 消息替换本地 `sending` 消息；如果请求失败，则本地消息变为 `failed`，用户可以点击“重试”。

## 4. 关键类与职责

| 类 / 文件 | 作用 |
|---|---|
| `AgentController` | 提供 `/agent/**` 接口，并将业务异常转换为统一 `Result`。 |
| `AgentServiceImpl` | Agent 主流程编排：会话校验、消息落库、幂等处理、上下文组装、模型调用、响应组装。 |
| `AgentContextAssembler` | 从用户画像、学习分析、最近课程、混合推荐中组装 system prompt 和 sources。 |
| `OpenAiCompatibleAgentLlmClient` | 调用 OpenAI 兼容模型接口；无密钥或 mock provider 时返回本地 mock 回答。 |
| `AgentMessageMapper` | 查询会话消息、锚定历史消息、按 `clientMessageId` 查找幂等消息。 |
| `AgentSessionMapper` | 查询当前用户的有效会话，以及按用户和会话 ID 校验会话有效性。 |
| `AgentProperties` | 绑定 `agent.*` 配置，包括模型、超时、上下文数量和恢复窗口。 |
| `AgentAsyncConfig` | 提供 `agentContextExecutor`，用于给推荐上下文加载设置独立线程池。 |
| `AgentMapperStruct` | 将 `AgentSession` / `AgentMessage` 转成前端使用的 VO。 |
| `AgentAssistant.vue` | 学习助手页面，负责会话列表、消息展示、发送中状态、失败重试和迟到响应处理。 |

## 5. 涉及的数据表或外部组件

| 表 / 组件 | 用途 |
|---|---|
| `agent_session` | 保存 Agent 会话。`status=1` 表示有效，`status=0` 表示软删除。 |
| `agent_message` | 保存 USER / ASSISTANT 消息、`client_message_id` 和模型 metadata。 |
| `uk_agent_message_user_role_client` | `(user_id, role, client_message_id)` 唯一索引，用于防止同一用户同一角色重复落同一个客户端消息。 |
| `OnboardingService` | 提供学习画像、基础水平、学习目标和兴趣标签。 |
| `LearningAnalysisService` | 提供学习进度图和能力雷达数据。 |
| `UserCourseRelationMapper` | 查询最近学习课程和进度。 |
| `HybridRecommendService` | 提供推荐候选、推荐理由和 readiness。 |
| `TagMapper` | 根据引导标签 ID 查询标签名称。 |
| OpenAI 兼容模型服务 | 真实模型调用目标，配置为 `AGENT_LLM_BASE_URL`。 |

Agent 模块当前不直接访问 Redis、Neo4j 或 Python 推荐服务；这些可能会被推荐、学习分析等被调用模块间接使用。

## 6. 核心规则与特殊处理

- **功能开关**：所有 Service 方法都会调用 `ensureEnabled`。当 `AGENT_ENABLED=false` 时，接口返回禁用错误，不再继续创建会话或保存消息。
- **只读业务边界**：system prompt 明确要求助手只提供学习建议、推荐解释、薄弱点分析和学习路径建议，不执行选课、收藏、删除或进度更新。
- **会话权限与生命周期**：所有读取、更新、删除消息或会话的操作都通过 `selectActiveByIdAndUserId` 校验当前用户和 `status=1`。删除会话是软删除。
- **发送幂等**：`POST /agent/chat` 必须传 `clientMessageId`。同一个用户、同一个角色、同一个 `clientMessageId` 只能落一条消息。
- **处理中保护**：Service 内部用 `processingClientMessages` 记录 `userId:clientMessageId` 的处理中状态，避免同一条消息并发重复调用模型。
- **半成品恢复**：如果 USER 已落库但 ASSISTANT 未落库，且处理中标记超过 `AGENT_INCOMPLETE_RECOVERY_AFTER_MS`，后续重试可以接管并补齐 ASSISTANT 消息。接管后会再次查询同一 `clientMessageId`，如果 ASSISTANT 已经由原请求写入，则直接回放已有结果，不再调用模型。
- **幂等回放会话校验**：即使 `clientMessageId` 已存在，Service 也会重新校验 stored USER message 所属会话仍然有效。如果请求传了 `sessionId`，还必须和 stored message 的 `sessionId` 一致。
- **历史消息锚定**：构造 LLM 历史时使用 `selectRecentUntilMessageBySessionIdAndUserId`，只取被重试 USER 消息及其之前的最近 N 条消息，避免恢复旧请求时错误回答后续消息。
- **最近 N 条历史的 SQL 形态**：Mapper 中先倒序 `LIMIT` 取最近 N 条，再外层正序返回。直接正序 `LIMIT` 会拿到最早 N 条；直接倒序返回又不适合作为对话上下文。
- **模型失败 fallback**：上下文组装或模型调用异常时，Service 会保存一条 fallback ASSISTANT 消息，提示用户模型服务暂不可用，并把错误类型写入 metadata。
- **前端迟到响应处理**：如果用户发送消息后切到其他会话，旧响应回来时只刷新会话列表，不强行切回旧会话，也不把旧消息插入当前会话。

## 7. 容易忘记或容易出错的点

- `clientMessageId` 是发送链路的核心字段，前端重试必须复用原值，不能重新生成。
- `client_message_id` 在迁移中允许 NULL，是为了兼容已有表结构；但当前 `chat` 新请求会在 Service 层强制要求非空。
- 幂等回放不能绕过会话校验，否则删除后的会话可能通过旧 `clientMessageId` 被访问。
- 恢复未完成发送时，LLM 历史必须锚定到被重试的 USER 消息，而不是取当前会话最新消息。
- 前端历史消息中只有 USER 且带 `clientMessageId` 时，会显示“回答未完成，可重试”；如果同一 `clientMessageId` 已经有 ASSISTANT，则不会显示重试按钮。
- 前端收到迟到响应后不能自动 `selectSession(data.sessionId)`，否则用户会被拉回旧会话。
- 恢复分支拿到 `ProcessingLease` 后仍要重新读取 ASSISTANT；只靠拿锁前的查询会留下“原请求刚完成、重试又调用一次模型”的竞态窗口。
- `AgentContextAssembler` 对推荐上下文设置了短预算，并使用独立线程池，避免 Agent 外层等待和推荐内部并行任务互相占用线程。
- `OpenAiCompatibleAgentLlmClient` 在 `apiKey` 为空时会走 mock，不会真实调用模型。部署真实模型时需要确认环境变量已注入。
- `AgentController` 中 `AgentChatProcessingException` 映射为 `409`，前端需要通过 `Result.code` 判断业务失败，而不能只看 HTTP 200。
- 当前代码中未明确体现数据库事务边界；如果后续要加强 USER / ASSISTANT 消息的一致性，需要重新评估事务和外部模型调用的关系。

## 8. 后续维护建议

- 修改聊天发送逻辑时，优先从 `AgentServiceImpl.chat` 看主流程，再看 `buildAssistantDraft`、`buildLlmRequest` 和幂等相关方法。
- 修改上下文内容时，从 `AgentContextAssembler.assemble` 入手，确认新增数据源是否需要出现在 `sources` 和 `fallbackSummary` 中。
- 修改模型供应商时，优先改 `OpenAiCompatibleAgentLlmClient` 或新增 `AgentLlmClient` 实现，并保持 `AgentProperties` 的配置语义清晰。
- 修改前端交互时，重点检查 `sendMessage`、`retryMessage`、`submitChat` 和 `shouldApplyChatResponse`，避免引入重复提交或跨会话消息混入。
- 修改表结构时，同步检查 `AgentMessage`、`AgentSession`、Mapper 查询、MapStruct 转换、Flyway 迁移和测试。
- 如果后续要实现“重新生成回答”，建议不要复用当前第一阶段的 `clientMessageId` 语义；需要单独设计 regenerate 的消息关系和版本策略。
