# Course System Backend

智能课程系统的 Spring Boot 后端，负责核心业务、权限校验和推荐结果融合，默认运行在 `http://127.0.0.1:8080`。

## 功能概览

- 用户认证、课程与用户管理、选课和视频访问
- 学习行为、进度记录与学习分析
- Neo4j 知识图谱查询与知识点掌握度维护
- 协同过滤、冷启动、新课和热门推荐融合
- 基于学习数据生成只读建议的学习助手

学习数据与后续 Outbox 任务在同一 MySQL 事务中提交；热度、图谱掌握度和推荐数据由后台异步更新，允许短暂延迟。

## 技术与目录

Java 17、Spring Boot 3、Spring Security、MyBatis-Plus、Flyway；依赖 MySQL 8、Redis 7、Neo4j 5、ffprobe 和 FastAPI 推荐服务。

| 目录 | 用途 |
| --- | --- |
| `src/main/java/com/sy/course_system/controller` | 学生端与管理端接口 |
| `src/main/java/com/sy/course_system/service` | 业务规则 |
| `src/main/java/com/sy/course_system/mapper`、`repository` | MySQL 与 Neo4j 数据访问 |
| `src/main/java/com/sy/course_system/outbox` | 学习行为后续任务 |
| `src/main/resources` | 配置、Mapper XML 与 Flyway 迁移 |
| `src/test` | 单元与集成测试 |

## 本地运行

先按 [操作手册](../docs/OPERATION_MANUAL.md) 准备基础服务和根目录 `.env.local`，包括两个独立的登录与播放签名密钥。从仓库根目录执行：

```bash
set -a
source .env.local
set +a
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

健康检查：`GET /actuator/health`。在 `backend` 目录执行：

- `./mvnw -q -DskipTests compile`：编译
- `./mvnw test`：测试，包含需要 Docker 的集成测试
- `./mvnw clean package`：打包

## 详细文档

- [后端开发与接口手册](../docs/BACKEND_GUIDE.md)：配置入口、接口分组、构建和排障
- [总操作手册](../docs/OPERATION_MANUAL.md)：环境变量、数据库迁移与完整联调
- [Outbox 运行与恢复](../docs/OPERATION_MANUAL.md#学习行为-outbox-运行与恢复)
- [学习助手模块](../docs/agent-module.md)
- [Spring Security 鉴权说明](../docs/spring-security-authentication.md)
- [后端协作指南](AGENTS.md)
- [项目总览](../README.md)

## License

采用 MIT License，详见 [LICENSE](../LICENSE)。
