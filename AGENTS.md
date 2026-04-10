# Repository Guidelines

## Project Structure & Module Organization
This repository is a Spring Boot 3 backend for a course learning system. Main code lives under `src/main/java/com/sy/course_system`, organized by responsibility: `controller` for APIs, `service` and `service/impl` for business logic, `mapper` and `repository` for MySQL/Neo4j access, `entity`/`dto`/`vo` for data models, and `config` for infrastructure setup. SQL mappers are in `src/main/resources/mapper`, and environment configs are in `src/main/resources/application*.yaml`. Deployment samples live in `deploy/`, and `course_db.sql` provides schema/bootstrap data.

## Build, Test, and Development Commands
- `./mvnw spring-boot:run`: run the service locally on port `8080`.
- `./mvnw test`: run the test suite.
- `./mvnw -q -DskipTests compile`: fast compile check for Java sources.
- `./mvnw clean package`: build the runnable JAR in `target/`.
- `docker compose up -d`: start local dependencies defined in `docker-compose.yml`.

Before running locally, ensure MySQL, Redis, and Neo4j match the defaults in `application.yaml` or override them with environment variables such as `DB_HOST` and `NEO4J_URI`.

## Coding Style & Naming Conventions
Use Java 17 and follow the existing 4-space indentation style. Keep packages under `com.sy.course_system`. Use `PascalCase` for classes, `camelCase` for methods and fields, and suffix DTO/VO/entity classes consistently, for example `CourseUpdateDTO`, `CourseDetailVO`. Keep controllers thin; place business rules in services. Prefer clear endpoint grouping such as `/course/*`, `/admin/course/*`, `/analysis/*`.

## Testing Guidelines
The project includes `spring-boot-starter-test`, but there is currently no `src/test` tree. Add tests under `src/test/java/com/sy/course_system` mirroring production packages. Name test classes `*Test`, for example `CourseServiceTest`. Prioritize service-layer tests for recommendation, learning analysis, and course management logic. Run `./mvnw test` before opening a PR.

## Commit & Pull Request Guidelines
Recent commits use short Chinese summaries describing completed work, for example `完成了用户管理的增删改查功能。`. Keep commits focused and descriptive; one logical change per commit is preferred. PRs should include a concise summary, affected modules, config or schema changes, and manual verification steps. For API or admin workflow changes, include request examples or screenshots if a frontend is impacted.

## Security & Configuration Tips
Do not commit real secrets. Keep credentials in environment variables and use `application-dev.yaml` or local overrides for development. Review JWT, CORS, upload path, and video/Neo4j settings before deployment, especially when changing `app.upload.video-dir` or public video URLs.
