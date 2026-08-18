# 实验观测

本目录用于保存并发实验的指标采集说明、Prometheus 配置和 Grafana 面板。

首批计划观测：

- HTTP QPS、p95、p99 和业务错误码。
- Tomcat busy threads。
- Hikari active、idle、pending 和连接获取耗时。
- 推荐线程池 active、queue size 和 rejected count。
- Redis 命令延迟、缓存命中与回源构建次数。
- MySQL 慢查询和锁等待。

监控组件确定后再增加实验专用 Compose 文件，避免提交一个不能采集项目指标的空监控栈。
