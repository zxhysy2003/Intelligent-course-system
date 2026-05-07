# AGENTS 指南

本文件面向仓库协作者与 AI 编码代理。目标是让改动小、行为可预测、验证路径清晰。

## 项目概览

这是基于 Spring Boot 3 / Java 17 的课程学习系统后端，主代码位于 `src/main/java/com/sy/course_system`。

常用目录：

- `controller/client`、`controller/server`：前台与后台接口。
- `service`、`service/impl`：业务规则主要落点。
- `mapper`、`src/main/resources/mapper`：MyBatis 接口与 XML。
- `repository`、`graph`：Neo4j 与图数据相关代码。
- `entity`、`dto`、`vo`：实体、入参、出参。
- `config`、`common`：基础配置与通用能力。
- `course_db.sql`：MySQL 表结构与初始化数据。

## 工作原则

- 先读上下文再改代码，不按文件名猜测行为。
- 优先做最小闭环改动，避免顺手重构无关代码。
- 不擅自修改接口字段、数据库语义或核心业务行为。
- 发现工作区已有改动时不要回退，除非用户明确要求。
- 控制器保持薄，复杂业务放在 service；数据访问放在 mapper/repository。
- 修改 Mapper 时同时确认 Java 方法、XML `id`、参数名和返回类型一致。

## 配置与数据约束

- 通用默认值放在 `application.yaml`，环境差异通过 profile 或环境变量覆盖。
- 不要把本地临时地址、生产密码、令牌等写回仓库。
- 涉及 SQL 前先核对 `course_db.sql` 中的表结构和索引。
- 新增字段时同步检查 entity、DTO、VO、Mapper XML 和接口调用方。
- 修改返回结构时优先新增字段，谨慎删除或重命名已有字段。
- 推荐、学习分析、课程管理等核心逻辑要保护现有行为，避免无验证重写。

## 编码与注释

- 使用 Java 17，4 空格缩进，包名保持 `com.sy.course_system`。
- 类名用 `PascalCase`，方法和字段用 `camelCase`。
- DTO、VO、Entity 保持明确后缀，例如 `CourseUpdateDTO`、`CourseDetailVO`。
- 注释默认使用中文，解释“为什么”、边界条件、兼容原因和业务语义。
- 不写无信息量注释，例如“查询数据库”“进入循环”“给变量赋值”。
- 复杂 SQL、Cypher、推荐/评分、缓存一致性、事务边界和外部服务调用应补必要说明。

## 构建与验证

常用命令：

- `./mvnw spring-boot:run`：本地启动服务。
- `./mvnw -q -DskipTests compile`：快速编译检查。
- `./mvnw test`：运行测试。
- `./mvnw clean package`：构建 JAR。
- `docker compose up -d`：启动本地依赖。

验证要求：

- 新增或修改业务分支时，优先补 service 层测试。
- 涉及 SQL、配置或启动链路时，至少执行编译检查。
- 新增测试放在 `src/test/java/com/sy/course_system`，目录结构尽量镜像生产代码。

## 提交规范

Commit message 使用 Conventional Commits：

```text
<type>(<scope>): <summary>
```

常用 `type`：

- `feat`：新增功能。
- `fix`：修复 bug。
- `refactor`：重构但不改变外部行为。
- `perf`：性能优化。
- `docs`：文档。
- `test`：测试。
- `build`、`ci`、`chore`：构建、CI 或维护性改动。

常用 `scope`：

- `auth`、`course`、`behavior`、`recommend`、`cold-start`
- `graph`、`cache`、`video`、`config`
- `mapper`、`repository`、`service`、`common`
- `docs`、`test`

规则：

- 标题使用英文祈使句，首字母默认小写，结尾不加句号。
- 根据 staged diff 选择准确的 `type` 和 `scope`，不要只根据需求猜测。
- 一个 commit 尽量只包含一类逻辑改动；小改动附带必要测试可以放在同一 commit。
- 破坏性变更必须使用 `!` 并在正文写 `BREAKING CHANGE:`。

示例：

```text
feat(recommend): add new course cold-start injection
fix(behavior): prevent duplicate finish records
test(recommend): cover hybrid score calculation
docs(recommend): describe cold-start flow
```

## PR 与安全

PR 描述应包含变更目的、影响模块、配置或表结构变化、验证方式。接口行为变化需说明兼容性影响。

严禁提交真实密钥、数据库密码、云服务地址和生产环境凭据。修改 JWT、CORS、上传目录、视频访问地址或 Neo4j 连接时，需要说明部署影响。
