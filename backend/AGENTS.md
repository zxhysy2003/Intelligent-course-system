# AGENTS 指南

本文件面向仓库协作者与 AI 编码代理，目标是让改动尽量小、行为可预测、验证路径清晰。除非用户明确要求，否则优先遵循本文件中的约束。

## 仓库概览
该项目是一个基于 Spring Boot 3 的课程学习系统后端，主代码位于 `src/main/java/com/sy/course_system`。

当前主要模块如下：
- `controller/client`：面向前台用户的接口。
- `controller/server`：面向后台管理的接口。
- `service` 与 `service/impl`：业务逻辑实现，绝大多数功能修改应从这里进入。
- `mapper`：MyBatis Mapper 接口。
- `repository`：Neo4j 等仓储访问层。
- `entity`、`dto`、`vo`：实体、入参对象、出参对象。
- `graph` 与 `graph/node`：图数据相关模型。
- `config`：Spring、跨域、鉴权、基础设施配置。
- `common`：通用响应、工具类、基础能力。

资源文件主要位于：
- `src/main/resources/mapper`：MyBatis XML。
- `src/main/resources/application.yaml`：公共配置。
- `src/main/resources/application-dev.yaml`：本地开发配置。
- `src/main/resources/application-prod.yaml`：生产环境配置。
- `deploy/`：部署示例。
- `course_db.sql`：数据库结构与初始化数据。

## 代理工作原则
- 先理解上下文，再修改代码；不要按文件名猜测行为。
- 优先做最小闭环改动，避免顺手重构无关代码。
- 不要擅自修改用户未提及的业务语义、接口字段或数据库含义。
- 涉及接口、SQL、配置三者联动时，必须同时检查调用链是否闭合。
- 如果发现工作区已有未解释的改动，默认不要回退；只在当前任务范围内继续工作。
- 不要提交真实密码、访问令牌、云数据库地址等敏感信息。

## 目录与改动落点
在本仓库中，常见需求应优先落在以下位置：
- 新增接口：先放到合适的 `controller/client` 或 `controller/server`，再补 service。
- 新增业务逻辑：优先改 `service` / `service/impl`，控制器保持薄。
- 新增 MySQL 查询：同步修改 `mapper` 接口和 `src/main/resources/mapper/*.xml`。
- 新增 Neo4j 查询：优先查看 `repository` 与 `graph` 相关类型。
- 修改请求/响应结构：优先使用 `dto` / `vo`，不要直接暴露 entity。
- 通用能力修改：放在 `common` 或 `config`，避免散落在业务代码中。

如果一个改动需要同时修改 Java Mapper 和 XML Mapper，提交前必须确认方法名、参数名、返回类型、XML `id` 一致。

## 配置使用约定
配置采用“公共配置 + profile 覆盖”的方式：
- `application.yaml` 放通用配置和默认值。
- `application-dev.yaml` 放本地开发配置。
- `application-prod.yaml` 放生产环境配置，敏感项通过环境变量注入。

启动时可通过 profile 切换环境，例如：
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`

或通过 IDE / VSCode 传入：
- `-Dspring.profiles.active=dev`
- `-Dspring.profiles.active=prod`

部署前重点检查以下配置项：
- `spring.datasource.*`
- `spring.data.redis.*`
- `spring.neo4j.*`
- `app.upload.video-dir`
- `app.video.base-url`
- `recommend.service.url`

## 构建、运行与验证命令
常用命令如下：
- `./mvnw spring-boot:run`：本地启动服务。
- `./mvnw test`：运行测试。
- `./mvnw -q -DskipTests compile`：快速编译检查。
- `./mvnw clean package`：构建可运行 JAR。
- `docker compose up -d`：启动本地依赖。

本地运行前，请确保 MySQL、Redis、Neo4j 与当前 profile 的配置一致；若不一致，使用环境变量覆盖，不要把临时地址直接写回仓库。

## 编码风格
- 使用 Java 17。
- 使用 4 空格缩进，保持现有风格，不混用制表符。
- 包名统一放在 `com.sy.course_system` 下。
- 类名使用 `PascalCase`，方法和字段使用 `camelCase`。
- DTO、VO、Entity 保持明确后缀，例如 `CourseUpdateDTO`、`CourseDetailVO`。
- 控制器只做参数接收、权限边界和响应封装，不承载复杂业务判断。
- Service 承载业务规则，Mapper/Repository 只处理数据访问。
- 接口路径保持现有分组风格，例如 `/course/*`、`/admin/course/*`、`/analysis/*`。

## 注释规则
- 在“补充注释”类任务中，默认目标是**只增加注释，不修改业务逻辑、不重构代码、不顺手修复无关注题**。
- 如果发现疑似 bug、命名不清或结构问题，默认不要直接改动；可在注释中简要提示，或在结果说明中单独列出。
- 注释应优先解释“为什么这样做”、边界条件、状态约束、兼容性原因和容易误解的业务语义，不要逐行翻译代码。
- 优先为以下内容补充注释：复杂分支、核心业务规则、推荐/评分逻辑、时间衰减、缓存一致性、事务边界、复杂 SQL、Neo4j 查询、外部服务调用约束。
- 方法名已经足够清晰时，不必重复写方法注释；只有在方法用途、关键参数、返回含义或副作用不直观时，才补充简短方法注释。
- Controller 层注释保持简短，只说明接口用途、权限边界或特殊输入限制；不要在控制器中堆积业务细节。
- Service 层如果承载关键业务规则，应明确注释规则来源、执行顺序、状态变化和“不要随意改动”的约束。
- Mapper XML 与 Cypher 查询仅对复杂语句补充必要注释，重点说明关联目的、筛选原因、聚合/去重意图和性能敏感点；不要重复 SQL/Cypher 字面含义。
- 涉及临时兼容逻辑、历史包袱、外部系统限制或线上行为对齐时，必须注明“为什么存在”以及“未来在什么条件下可以删除”。
- 允许使用 `TODO:`、`FIXME:`、`NOTE:`，但必须写明具体原因，不要只留空泛标记。
- 不要添加无信息量注释，例如“给变量赋值”“进入循环”“查询数据库”“调用方法”。
- 注释默认使用中文，要求简洁、稳定、可维护，避免口语化和大段论文式描述。
- 当用户要求“给代码加注释”时，默认直接输出修改后的完整代码；除非用户明确要求，否则不要只返回解释而不落到代码。

## 数据与接口变更约束
- 修改数据库查询前，先确认对应表结构是否已在 `course_db.sql` 中体现。
- 新增字段时，检查 entity、DTO、VO、Mapper XML、前端依赖接口是否一致。
- 修改返回结构时，优先新增字段，谨慎删除或重命名已有字段，避免破坏兼容性。
- 涉及推荐、学习分析、课程管理逻辑时，优先保护现有行为，不要在缺乏验证的情况下重写核心算法路径。

## 测试与最低验证要求
项目已引入 `spring-boot-starter-test`，但当前测试覆盖有限。新增测试时，放在 `src/test/java/com/sy/course_system`，目录结构尽量镜像生产代码。

测试类命名使用 `*Test`，例如 `CourseServiceTest`。

在没有完整自动化测试的情况下，至少执行以下之一：
- `./mvnw -q -DskipTests compile`
- `./mvnw test`

如果改动涉及 SQL、配置或启动链路，优先做编译检查；如果改动涉及明确的业务逻辑分支，优先补 service 层测试。

## 提交与 PR 约定

### Commit Message 规则（面向 Codex CLI）

Commit 信息使用 Conventional Commits 风格，便于保持提交历史清晰，也便于后续生成变更日志。

基本格式如下：

```text
<type>(<scope>): <summary>
```

如果本次改动影响范围较大，或标题无法说明原因，可以添加正文：

```text
<type>(<scope>): <summary>

<why this change was made>
<what changed at a high level>
```

允许使用的 `type` 如下：

- `feat`：新增功能。
- `fix`：修复 bug。
- `refactor`：重构代码，不改变外部行为。
- `perf`：性能优化。
- `docs`：只修改文档。
- `test`：新增或修改测试。
- `style`：代码格式调整，不影响逻辑。
- `build`：构建脚本、依赖、打包配置变更。
- `ci`：CI/CD 配置变更。
- `chore`：其他维护性改动。

`scope` 应使用简短英文，表示本次改动影响的模块或业务区域。优先使用以下范围：

- `auth`
- `course`
- `behavior`
- `recommend`
- `cold-start`
- `graph`
- `cache`
- `video`
- `config`
- `mapper`
- `repository`
- `service`
- `common`
- `docs`
- `test`

标题规则：

- 使用英文。
- 使用祈使句，例如 `add`、`fix`、`remove`、`update`。
- 首字母默认小写，专有名词除外。
- 结尾不加句号。
- 描述实际完成的改动，不写空泛描述。

推荐示例：

```text
feat(recommend): add new course cold-start injection
fix(behavior): prevent duplicate finish records
refactor(graph): extract missing prerequisite query
docs(thesis): describe cold-start recommendation flow
test(recommend): add hybrid score calculation tests
build(deps): update backend dependency versions
```

避免使用以下模糊提交信息：

```text
fix bug
update code
improve project
optimize logic
change files
完善功能
修改代码
```

当改动包含破坏性变更时，必须显式标记：

```text
feat(api)!: change recommendation response format

BREAKING CHANGE: recommendation items now return objects with courseId and score instead of a plain courseId list.
```

如果提交关联 issue 或任务，可以在正文末尾补充：

```text
Fixes #123
Refs #456
```

Codex 在生成 commit message 前，应先查看 staged diff，根据实际改动选择最准确的 `type` 和 `scope`。不要根据用户最初的需求猜测提交信息。

### Commit 拆分原则

一个 commit 尽量只包含一类逻辑改动。

如果一次任务同时修改了业务逻辑、文档和测试，优先拆成多个 commit，例如：

```text
feat(recommend): add new course cold-start recall
test(recommend): cover cold-start injection rules
docs(recommend): document cold-start recommendation flow
```

如果只是一次小改动附带必要测试，可以放在同一个 commit 中。

### PR 描述规则

PR 描述应包含：

- 变更目的。
- 影响模块。
- 配置或表结构变化。
- 验证方式。

如果改动影响接口行为，应附上请求示例、返回示例，或说明兼容性影响。

## 安全注意事项
- 严禁提交真实密钥、数据库密码、云服务地址和生产环境凭据。
- 生产配置优先使用环境变量，不要把生产值硬编码进 `application-prod.yaml`。
- 修改 JWT、CORS、上传目录、视频访问地址、Neo4j 连接时，需明确说明部署影响。
