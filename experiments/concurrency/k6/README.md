# k6 压测脚本

本目录保存高并发实验的 HTTP 压测脚本。

约定：

- 文件名使用实验编号，例如 `con-01-concurrent-enrollment.js`。
- 基础地址、账号、密码、课程 ID 通过环境变量传入，不把 Token 写入仓库。
- 每个实验使用编号前缀，例如 `CON01_USERNAME`，避免与 shell 的 `USERNAME` 等系统变量冲突。
- 登录和数据准备放在 `setup()`，不混入目标接口指标。
- 同时检查 HTTP 状态和响应体中的 `Result.code`。
- 使用自定义 Counter 区分业务成功、预期冲突和非预期异常。
- 默认不输出每次请求的响应体，避免日志 IO 干扰结果。

## 安装

当前项目不把 k6 作为 npm 依赖。macOS 可以使用 Homebrew 安装：

```bash
brew install k6
k6 version
```

其他系统或 Docker 方式见 [Grafana k6 官方安装文档](https://grafana.com/docs/k6/latest/set-up/install-k6/)。

## CON-01 并发选课

已实现脚本：

- [`con-01-concurrent-enrollment.js`](./con-01-concurrent-enrollment.js)
- 详细方案见 [CON-01 并发选课实验](../docs/01-concurrent-enrollment.md)。

运行前必须保证目标用户尚未选择目标课程。下面的命令会在 `setup()` 中登录一次，然后让 50 个 VU 各发送一次相同的选课请求：

```bash
CON01_BASE_URL=http://127.0.0.1:8080 \
CON01_USERNAME=stu_newbie \
CON01_PASSWORD=123456 \
CON01_COURSE_ID=10 \
CON01_VUS=50 \
k6 run experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

也可以传入已有 Token，跳过登录：

```bash
CON01_BASE_URL=http://127.0.0.1:8080 \
CON01_TOKEN=仅在当前终端保存的JWT \
CON01_COURSE_ID=10 \
CON01_VUS=50 \
k6 run experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

脚本内置正确性阈值：

- `enrollment_success == 1`
- `enrollment_duplicate == CON01_VUS - 1`
- `enrollment_unexpected == 0`
- 所有响应检查通过

任意条件不满足时，k6 会以非零状态退出。脚本不会自动删除选课关系，因为实验后还需要查询数据库验证最终状态；再次运行前必须换一个未选课程，或者只在实验数据库中清理关系。

## 双后端实例

通过 `CON01_BASE_URLS` 传入逗号分隔的后端地址：

```bash
CON01_BASE_URLS=http://127.0.0.1:8080,http://127.0.0.1:8081 \
CON01_USERNAME=stu_newbie \
CON01_PASSWORD=123456 \
CON01_COURSE_ID=14 \
CON01_VUS=100 \
k6 run experiments/concurrency/k6/con-01-concurrent-enrollment.js
```

登录请求发送到第一个地址；选课请求按照 VU 编号在所有地址间轮询。100 VU、两个地址时，每个实例应恰好收到 50 次选课请求。脚本会为每个 `backend_index` 增加 `http_reqs` 数量阈值，确保双实例不是“启动了两个、实际只请求了一个”。两个实例必须共享 MySQL 和 JWT 签名密钥。
