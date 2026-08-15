# Spring Security 鉴权技术学习报告

## 1. 模块作用

本项目使用 Spring Security 统一保护 JSON API、管理员接口和本地视频资源。它解决的核心问题是：在请求进入 Controller 或静态资源处理器之前，先确认请求携带了哪一种可信凭证，再判断该身份是否有权访问目标资源。

当前实现区分两类凭证：

- 登录 JWT：代表用户身份，放在 `Authorization: Bearer ...` 请求头中，用于 `/api/v1/**`。
- 视频播放凭证：代表访问某一个视频文件的临时能力，放在 `/videos/**?token=...` 查询参数中，只拥有 `VIDEO_PLAYBACK` 权限。

这种拆分避免把长期登录凭证写入视频 URL，也避免原生 `<video>` 因无法像 Axios 一样统一添加请求头而依赖认证 Cookie。

需要先区分两个概念：

- 认证（Authentication）：确认请求是谁，或它持有什么访问能力。项目中的两个 `OncePerRequestFilter` 负责这一步。
- 授权（Authorization）：判断已经建立的身份是否允许访问当前 URL。`SecurityConfig` 中的 `authorizeHttpRequests` 负责这一步。

`SecurityConfig` 本身不是每个请求都直接调用的鉴权函数。它在应用启动时构建 `SecurityFilterChain`；运行期间真正执行的是过滤链中的过滤器和授权组件。

## 2. 入口位置

### 2.1 后端入口

- 安全总装配：[`SecurityConfig`](../backend/src/main/java/com/sy/course_system/config/SecurityConfig.java)
- 登录认证过滤器：[`JwtAuthenticationFilter`](../backend/src/main/java/com/sy/course_system/config/JwtAuthenticationFilter.java)
- 视频认证过滤器：[`PlaybackTokenAuthenticationFilter`](../backend/src/main/java/com/sy/course_system/config/PlaybackTokenAuthenticationFilter.java)
- 登录 JWT 工具：[`JwtUtil`](../backend/src/main/java/com/sy/course_system/common/util/JwtUtil.java)
- 视频凭证服务：[`PlaybackTokenService`](../backend/src/main/java/com/sy/course_system/service/PlaybackTokenService.java)
- 登录入口：`POST /api/v1/auth/login`
- 播放地址入口：`POST /api/v1/courses/{courseId}/playback`
- 视频读取入口：`GET|HEAD /videos/**?token=...`

### 2.2 前端入口

- Axios 实例：[`frontend/src/api/request.js`](../frontend/src/api/request.js)
- 播放地址 API：[`frontend/src/api/course.js`](../frontend/src/api/course.js) 中的 `createCoursePlayback`
- 课程详情容器：[`CourseDetail.vue`](../frontend/src/views/user/CourseDetail.vue)
- 原生播放器组件：[`CourseMediaPlayer.vue`](../frontend/src/features/course-detail/components/CourseMediaPlayer.vue)
- 页面级路由检查：[`frontend/src/router/index.js`](../frontend/src/router/index.js)
- 登录状态：[`frontend/src/store/user.js`](../frontend/src/store/user.js)

前端路由守卫主要改善页面导航体验，不构成后端安全边界。用户可以绕过前端直接调用 API，所以最终权限必须以后端 Spring Security 的判断为准。

## 3. 核心流程

### 3.1 应用启动与过滤链建立

1. Spring Boot 扫描到 `SecurityConfig`。
2. `securityFilterChain(HttpSecurity)` 配置无状态会话、CORS、异常处理、URL 授权规则和自定义过滤器顺序。
3. `http.build()` 生成 `SecurityFilterChain` Bean。
4. 请求运行时由 Servlet 容器中的 Spring Security 代理进入 `FilterChainProxy`，再进入这条安全过滤链。
5. 认证和授权通过后，请求才会到达 `DispatcherServlet`、Controller 或视频静态资源处理器。

整体位置可以记成：

```text
浏览器 / Axios / <video>
        ↓
Vite 或 Nginx 代理
        ↓
Tomcat Servlet Filter
        ↓
Spring Security FilterChainProxy
        ↓
JwtAuthenticationFilter
        ↓
PlaybackTokenAuthenticationFilter
        ↓
Spring Security 授权判断
        ↓
Controller 或 WebConfig 静态资源处理器
```

`CourseSystemApplication` 排除了 `UserDetailsServiceAutoConfiguration`。项目不使用 Spring Boot 自动生成的内存用户，而是只接受本项目签发的 JWT。

### 3.2 用户登录和登录 JWT 签发

1. 前端调用 `POST /api/v1/auth/login`，登录接口在 `SecurityConfig` 中配置为 `permitAll`。
2. `JwtAuthenticationFilter` 仍会进入，但 `resolveToken()` 识别到公开接口后不解析可能存在的旧 Authorization Header。
3. `AuthController.login()` 调用 `UserService.login()`。
4. `UserServiceImpl` 根据用户名读取 `user` 表并校验当前存量密码。
5. 登录成功后，将 `userId`、`username`、`role` 写入 claims。
6. `JwtUtil` 使用 HS256 和 `JWT_SECRET_BASE64` 签名，登录 JWT 有效期为 24 小时。
7. 前端把 JWT 保存到 `localStorage.token`，Pinia store 解码身份信息。
8. 后续 Axios 请求由请求拦截器自动添加：

```http
Authorization: Bearer <login-jwt>
```

当前代码仍使用明文密码比较。它与 Spring Security 过滤链迁移是两个独立问题，后续应单独迁移到 BCrypt，并处理存量密码升级。

### 3.3 普通 API 请求认证

以学生读取课程详情为例：

```http
GET /api/v1/courses/7
Authorization: Bearer <login-jwt>
```

1. `JwtAuthenticationFilter` 在请求开始时清理 `UserContext`。
2. `resolveToken()` 从 Authorization Header 提取 Bearer Token。
3. `JwtUtil.parseToken()` 校验 HS256 签名和过期时间。
4. `parseUserInfo()`要求 claims 中必须存在有效的 `userId`、`username`、`role`。
5. 角色被映射为 `ROLE_<role>`，例如 `STUDENT` 变成 `ROLE_STUDENT`。
6. 过滤器创建 `UsernamePasswordAuthenticationToken`，写入 Spring Security 的 `SecurityContext`。
7. 同一个 `UserInfo` 还被写入项目原有的 `UserContext`，供现有 Controller 和 Service 读取。
8. `PlaybackTokenAuthenticationFilter.shouldNotFilter()` 判断这不是视频请求，因此完全跳过。
9. `SecurityConfig` 的 `/api/v1/**.authenticated()` 规则确认已经存在认证身份，请求进入 Controller。
10. 请求结束后，JWT 过滤器在 `finally` 中清理 `UserContext`，防止 Tomcat 线程复用造成身份泄漏。

### 3.4 管理员接口授权

管理员接口规则位于普通 API 规则之前：

```java
.requestMatchers(ApiPaths.ADMIN, ApiPaths.ADMIN + "/**").hasRole("ADMIN")
.requestMatchers(ApiPaths.API_V1, ApiPaths.API_V1 + "/**").authenticated()
```

执行过程：

1. JWT 过滤器先验证登录身份并建立角色权限。
2. `hasRole("ADMIN")` 实际要求认证对象具有 `ROLE_ADMIN`。
3. 学生或教师虽然已登录，但缺少该权限，因此返回 HTTP 403。
4. 管理员具有 `ROLE_ADMIN`，请求才会进入后台 Controller。

规则顺序不能颠倒。如果先写宽泛的 `/api/v1/**.authenticated()`，管理员 URL 可能先匹配到“只需登录”的规则，导致角色限制失效。

### 3.5 视频播放地址签发

课程详情页不会直接拼接未签名的视频路径，而是先调用：

```http
POST /api/v1/courses/7/playback
Authorization: Bearer <login-jwt>
```

1. 该请求属于普通 `/api/v1/**`，由 `JwtAuthenticationFilter` 验证登录 JWT。
2. `CourseController.createCoursePlayback()` 从 `UserContext` 取得当前用户 ID。
3. `VideoPlaybackService` 通过 `VideoService.getPlaybackSource()` 查询 `video` 表中的相对路径和时长。
4. `PlaybackTokenService` 将相对路径规范化为 `/videos/...`。
5. 播放凭证使用独立的 `PLAYBACK_TOKEN_SECRET_BASE64` 和 HS256 签名，不能复用登录 JWT 密钥。
6. 凭证包含：
   - `jti`：随机 UUID，保证同一秒续签也得到不同 URL；
   - `iss=course-system`；
   - `aud=video-playback`；
   - `sub`：唯一允许访问的视频路径；
   - `userId`、`courseId`；
   - `iat`、`exp`。
7. 有效期采用 `max(2 小时, 视频时长 + 30 分钟)`。
8. Controller 返回 `playbackUrl` 和 `expiresAt`，并设置 `Cache-Control: no-store`、`Pragma: no-cache`，避免凭证签发响应被缓存。

### 3.6 视频文件请求认证

浏览器把签名地址交给原生 `<video>` 后，会自行发出请求：

```http
GET /videos/7/course.mp4?token=<playback-token>
Range: bytes=0-1048575
```

1. Vite 或 Nginx 将 `/videos/**` 转发到后端，并保留查询参数和 Range 请求头。
2. `JwtAuthenticationFilter` 仍会执行 `UserContext` 的清理，但 `resolveToken()` 识别到视频请求后不解析登录 JWT。
3. `PlaybackTokenAuthenticationFilter` 匹配 `GET/HEAD /videos/**`，进入 `doFilterInternal()`。
4. 过滤器要求查询参数中恰好有一个非空 `token`，避免重复参数产生不同解析结果。
5. `PlaybackTokenService.validate()` 校验签名、issuer、audience、过期时间、身份声明和资源路径。
6. token 中的 `sub` 必须与当前请求路径完全相同。为一个视频签发的凭证不能访问另一个视频。
7. 验证成功后，过滤器创建只具有 `VIDEO_PLAYBACK` authority 的认证对象并写入 `SecurityContext`。
8. `SecurityConfig` 的视频规则要求 `hasAuthority("VIDEO_PLAYBACK")`，授权通过。
9. `WebConfig` 将 `/videos/**` 映射到 `VIDEO_DIR` 下的本地文件。
10. Spring MVC 静态资源处理器返回完整文件或 HTTP 206 Range 分段响应。

后续每一次 Range 请求都会重新验证播放凭证。登录 JWT 本身没有 `VIDEO_PLAYBACK`，不能直接读取视频；播放凭证也没有 `ROLE_STUDENT` 或 `ROLE_ADMIN`，不能调用普通 API。

### 3.7 视频凭证续签

1. 播放凭证过期、网络中断或文件读取失败时，原生 `<video>` 触发 `error` 事件。
2. `CourseMediaPlayer` 上报当前播放位置、累计有效观看时长和之前是否正在播放。
3. `CourseDetail` 首次收到错误时，再调用一次 `POST /courses/{courseId}/playback`。
4. 新凭证包含随机 `jti`，即使同一秒签发也会产生不同 URL，从而触发 Vue 更新 `<video src>`。
5. 播放器保留累计观看时长，加载新 URL 后定位到原位置，并在浏览器允许时恢复播放。
6. 第二次媒体错误不再无限续签，而是显示错误提示。

### 3.8 401、403 与恶意路径

- 缺少登录身份访问普通 API：统一认证入口返回 HTTP 401。
- 登录 JWT 无效、过期或缺少必要 claims：JWT 过滤器直接返回 HTTP 401。
- 已登录但角色不足：`AccessDeniedHandler` 返回 HTTP 403。
- 视频 token 缺失、无效、过期或路径不匹配：播放过滤器直接返回 HTTP 401。
- 带分号或矩阵参数等非规范 URL：Spring Security 默认严格防火墙在 Controller 之前拒绝，测试期望 HTTP 400。
- 未被任何规则允许的请求：`anyRequest().denyAll()` 默认拒绝。

## 4. 关键类与职责

| 类 / 文件 | 作用 |
|---|---|
| `CourseSystemApplication` | 排除默认内存用户自动配置，启动项目自己的安全体系 |
| `SecurityConfig` | 构建无状态安全过滤链、注册认证过滤器、配置 URL 授权和 401/403 处理 |
| `JwtAuthenticationFilter` | 解析登录 JWT，建立角色身份，同时维护 `UserContext` 生命周期 |
| `PlaybackTokenAuthenticationFilter` | 只处理 `GET/HEAD /videos/**`，验证路径绑定的播放凭证 |
| `JwtUtil` | 使用登录密钥签发、解析 24 小时登录 JWT |
| `PlaybackTokenService` | 使用独立密钥签发、验证视频播放 JWT，计算有效期并规范化视频路径 |
| `VideoPlaybackService` | 查询视频路径和时长，将视频元数据交给凭证服务 |
| `CourseController` | 暴露播放地址签发接口，设置禁止缓存响应头 |
| `UserContext` | 通过 ThreadLocal 兼容现有业务代码读取当前登录用户 |
| `CorConfig` | 提供接入 Spring Security 的 `CorsConfigurationSource` |
| `WebConfig` | 将 `/videos/**` 映射到本地 `VIDEO_DIR`，实际提供 Range 文件响应 |
| `frontend/src/api/request.js` | 为 Axios JSON API 请求自动添加登录 Bearer Token |
| `CourseDetail.vue` | 申请播放地址、持有业务状态并处理一次自动续签 |
| `CourseMediaPlayer.vue` | 使用原生 `<video>` 加载签名 URL，维护 DOM 局部播放状态 |
| `JwtSecurityIntegrationTest` | 通过真实安全过滤链覆盖公开接口、角色、视频、CORS 和恶意路径 |

## 5. 涉及的数据表或外部组件

### 5.1 数据表

- `user`：登录时查询用户、密码和角色，用于生成登录 JWT。
- `video`：签发播放地址时查询课程对应的视频相对路径和时长。
- `user_course_relation`：不直接参与 Spring Security 认证，但课程详情离开时使用当前登录用户保存学习进度。

当前播放地址的签发条件是“用户已经通过登录认证且课程存在视频记录”，没有额外要求用户必须已经选课。

### 5.2 外部组件和运行环境

- Tomcat：运行 Servlet Filter，线程会复用，因此 `UserContext` 必须清理。
- Nginx：生产环境代理 `/api` 和 `/videos`；视频 location 关闭 access log，避免查询参数中的播放凭证进入常规访问日志。
- Vite proxy：本机开发时代理 `/api` 和 `/videos` 到后端。
- 浏览器原生 `<video>`：直接消费签名 URL并自动发起 Range 请求，不能复用 Axios 请求拦截器。

### 5.3 必要环境变量

- `JWT_SECRET_BASE64`：登录 JWT 的 Base64 密钥，解码后至少 32 字节。
- `PLAYBACK_TOKEN_SECRET_BASE64`：播放凭证的独立 Base64 密钥，解码后至少 32 字节，不能与登录密钥相同。
- `VIDEO_DIR`：视频文件在后端机器或容器中的存储根目录。
- `CORS_ALLOWED_ORIGIN_PATTERNS`：允许调用后端的前端来源模式。

生产环境的所有后端实例必须共享同一组稳定密钥。轮换登录密钥会使现有登录 JWT 失效；轮换播放密钥会使尚未过期的播放 URL 失效。

## 6. 核心规则与特殊处理

### 6.1 无状态安全模型

项目使用 `SessionCreationPolicy.STATELESS`，同时关闭表单登录、HTTP Basic、请求缓存和 Spring Security logout。服务器不保存登录 Session，每个 API 请求都必须携带自己的登录 JWT。

CSRF 被关闭的前提是：写 API 只接受 Authorization Header；视频 token 只允许用于只读的 GET/HEAD。不能在未重新评估 CSRF 风险的情况下把播放 token 扩展到写接口。

### 6.2 过滤器顺序

当前顺序由以下配置表达：

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(playbackTokenAuthenticationFilter, JwtAuthenticationFilter.class)
```

它表示：

```text
JwtAuthenticationFilter
    < PlaybackTokenAuthenticationFilter
    < UsernamePasswordAuthenticationFilter 的标准位置
```

两个自定义过滤器都在最终 URL 授权判断之前执行。`addFilterAfter` 只表示播放过滤器位于 JWT 过滤器之后，不表示它位于整个鉴权过程之后。

### 6.3 `resolveToken()` 与 `shouldNotFilter()`

JWT 过滤器对视频和公开请求使用 `resolveToken()` 返回 `null`：过滤器仍然执行，只是不解析登录凭证，因此请求前后的 `UserContext.clear()` 仍会运行。

播放过滤器使用 `shouldNotFilter()` 跳过非视频请求：框架不会调用该过滤器的 `doFilterInternal()`。这是因为播放过滤器没有全局 ThreadLocal 清理职责，对普通 API 完全无关。

### 6.4 防止 Filter 执行两次

两个过滤器既是 Spring Bean，又被显式加入 Spring Security。如果让 Spring Boot 同时把它们注册成普通 Servlet Filter，可能绕过预期顺序或执行两次。

因此 `SecurityConfig` 为两个 Filter 都提供禁用状态的 `FilterRegistrationBean`：

```java
registration.setEnabled(false);
```

这不是关闭过滤器，而是关闭 Servlet 容器的独立注册，只允许 Spring Security 管理它们。

### 6.5 规则顺序和默认拒绝

规则从具体到宽泛排列：公开接口、管理员 API、普通 API、视频 GET/HEAD，最后 `anyRequest().denyAll()`。新增接口时必须确认它会匹配到哪一条规则，不要只看 Controller 注解。

### 6.6 登录身份与播放能力隔离

登录 JWT 和播放 token 使用不同密钥、不同载体和不同 authority：

| 对比项 | 登录 JWT | 播放 token |
|---|---|---|
| 携带位置 | Authorization Header | URL 查询参数 |
| 典型权限 | `ROLE_STUDENT`、`ROLE_ADMIN` | `VIDEO_PLAYBACK` |
| 访问范围 | 业务 API | 一个确定的视频路径 |
| 是否写入 `UserContext` | 是 | 否 |
| 默认有效期 | 24 小时 | 至少 2 小时，或视频时长加 30 分钟 |

### 6.7 CORS 与预检请求

`CorConfig` 生成 `CorsConfigurationSource` 并由 `.cors(Customizer.withDefaults())` 接入安全过滤链。合法来源的 OPTIONS 预检会在认证判断前得到处理；未配置来源不会被放行。

## 7. 容易忘记或容易出错的点

1. 前端路由守卫不是安全边界。管理员权限必须由后端 `/api/v1/admin/**.hasRole("ADMIN")` 保证。
2. `hasRole("ADMIN")` 自动检查 `ROLE_ADMIN`；`hasAuthority("VIDEO_PLAYBACK")` 不会自动增加 `ROLE_` 前缀。
3. 管理员规则必须写在宽泛的 `/api/v1/**` 规则之前。
4. 登录和注册虽然 `permitAll`，JWT 过滤器仍需忽略旧 Authorization Header，避免过期登录状态阻断重新登录。
5. `UserContext` 是 ThreadLocal，任何新增的提前返回和异常分支都不能破坏 `finally` 清理。
6. 视频请求不能重新接受登录 JWT 或认证 Cookie，否则会破坏“登录身份与播放能力分离”的安全边界。
7. 播放 token 必须绑定实际请求路径，不能只检查签名和过期时间。
8. 播放 JWT 需要随机 `jti`。JWT 时间以秒编码，不加随机声明时，同一秒续签可能产生完全相同的 URL，导致播放器不重新加载。
9. 签名 URL 含有凭证。不要在通知、调试日志、Nginx access log 或异常信息中输出完整 URL。
10. Nginx 和 Vite 必须保留 `/videos` 路径、查询参数以及 `Range`、`If-Range` 请求头。
11. `POST /courses/{courseId}/playback` 必须禁止缓存，避免中间层复用签发响应。
12. Spring Security 默认严格防火墙负责拒绝分号、矩阵参数等非规范路径，不要为了兼容异常 URL 随意放宽 `HttpFirewall`。
13. `.env.local`、`.env` 和真实密钥不能提交；示例文件只能保留空值和生成说明。
14. 播放 token 在签发后独立有效，用户退出前端登录不会立即撤销它，直到过期或播放密钥轮换。
15. 当前没有刷新令牌、Token 黑名单或逐请求数据库查用户。用户角色或状态改变后，已经签发的登录 JWT 会继续携带旧 claims，直到 24 小时过期或密钥轮换。

## 8. 后续维护建议

### 8.1 修改 URL 权限规则时

从 `SecurityConfig.authorizeHttpRequests()` 开始，按“更具体规则在前、默认拒绝在后”的原则修改，并在 `JwtSecurityIntegrationTest` 中使用真实过滤链增加对应的 200、401、403 测试。

### 8.2 修改登录 JWT 时

同时检查：

- `UserServiceImpl.login()` 写入的 claims；
- `JwtUtil` 的签发和解析；
- `JwtAuthenticationFilter.parseUserInfo()` 的必要声明；
- 前端 `user` store 的解码字段；
- 测试专用 token 构造代码。

不要只修改 JWT 中的一端，否则可能出现“登录能签发、业务请求却全部 401”的情况。

### 8.3 修改视频播放鉴权时

同时检查：

- `CourseController.createCoursePlayback()`；
- `VideoPlaybackService` 和 `PlaybackTokenService`；
- `PlaybackTokenAuthenticationFilter`；
- `SecurityConfig` 的 `VIDEO_PLAYBACK` 规则；
- `WebConfig` 的视频目录映射；
- Nginx/Vite 对 `/videos` 和 Range 的代理；
- `CourseDetail` 的一次续签和 `CourseMediaPlayer` 的位置恢复。

### 8.4 推荐保留的验证命令

```bash
cd backend
./mvnw test

cd ../frontend
npm run check

cd ..
git diff --check
```

安全变更还应手动验证：

1. 无 Token 访问普通 API 返回 401。
2. 学生访问管理员 API 返回 403。
3. 管理员访问管理员 API 成功。
4. 登录 JWT 不能直接访问 `/videos/**`。
5. 正确播放 token 支持 GET、HEAD 和 Range。
6. 播放 token 不能访问其他视频路径。
7. 视频凭证过期后前端只自动续签一次，并从原位置恢复。
8. `/api/v1/admin;x=1/...` 等非规范路径在 Controller 前被拒绝。

如果后续要引入刷新令牌、服务端撤销、方法级权限、OAuth2 Resource Server 或 CDN 签名，应先明确新的威胁模型和客户端范围，再决定是否替换当前最小实现，避免在没有实际需求时同时维护多套安全机制。
