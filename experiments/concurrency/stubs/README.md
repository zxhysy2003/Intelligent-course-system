# 故障模拟服务

本目录保存实验专用 Stub，例如：

- 可配置延迟、超时和错误率的推荐服务。
- 模拟连接中断或返回畸形 JSON 的上游服务。
- Agent 慢模型响应模拟器。

故障行为必须通过参数显式开启，默认启动时应处于安全、可预测状态。Stub 不进入正式服务镜像。

## CON-04 推荐缓存 Stub

[`recommend-cache-stub.py`](./recommend-cache-stub.py) 仅使用 Python 标准库，提供：

- `POST /recommend`：返回空 CF 候选，可通过 `STUB_DELAY_MS` 控制延迟。
- `GET /health`：健康检查。
- `GET /stats`：返回总请求数、成功/失败数和最大同时请求数。
- `POST /stats/reset`：在没有活动请求时清零统计。

示例：

```bash
STUB_DELAY_MS=800 python3 experiments/concurrency/stubs/recommend-cache-stub.py
```

可选 `STUB_ERROR_RATE=0.1` 用于注入确定种子的随机错误；CON-04 击穿和雪崩主轮次保持 `0`，错误与超时留给 CON-05。
