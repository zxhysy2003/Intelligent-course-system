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
- Commit 信息建议使用简洁中文，准确描述完成的变更。
- 一个 commit 尽量只包含一类逻辑改动。
- PR 描述应包含：变更目的、影响模块、配置或表结构变化、验证方式。
- 如果改动影响接口行为，附上请求示例、返回示例，或说明兼容性影响。

## 安全注意事项
- 严禁提交真实密钥、数据库密码、云服务地址和生产环境凭据。
- 生产配置优先使用环境变量，不要把生产值硬编码进 `application-prod.yaml`。
- 修改 JWT、CORS、上传目录、视频访问地址、Neo4j 连接时，需明确说明部署影响。
