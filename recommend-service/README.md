# Course Recommendation Service

智能课程系统的 FastAPI 协同过滤候选服务，默认运行在 `http://127.0.0.1:8000`。服务从 MySQL 评分快照训练内存 SVD 模型，为后端提供候选课程；最终过滤、冷启动、热门兜底和展示分由后端处理。

## 主要能力

- 启动时训练模型，支持手动重载与状态查询
- 根据目标用户返回尚未评分的课程候选
- 模型或用户数据不可用时返回空候选，由后端兜底
- 限制进程内推荐并发，过载时返回 `503`

## 技术与目录

Python 3.9、FastAPI、Surprise SVD、SQLAlchemy / PyMySQL；依赖定义见 [environment.yml](environment.yml)。

| 文件 | 用途 |
| --- | --- |
| `main.py` | HTTP 接口与并发控制 |
| `model.py` | 训练、预测与模型状态 |
| `database.py` | MySQL 连接 |
| `schemas.py` | 请求和响应结构 |

## 本地运行

先启动后端，等待 Flyway 建表与评分快照初始化；数据库连接通过 `DB_*` 环境变量配置，见 [推荐服务手册](../docs/RECOMMEND_SERVICE_GUIDE.md#环境要求)。从仓库根目录执行：

```bash
cd recommend-service
conda env create -f environment.yml
conda activate lab_autumn
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

环境只需创建一次。通过 `GET /model/status` 检查模型状态，交互式接口文档位于 `/docs`。快照更新后使用 `POST /model/reload` 重新训练。

## 详细文档

- [推荐服务开发与接口手册](../docs/RECOMMEND_SERVICE_GUIDE.md)：配置、API 示例、算法与排障
- [总操作手册](../docs/OPERATION_MANUAL.md)：基础依赖与完整联调
- [后端概览](../backend/README.md)
- [项目总览](../README.md)

## License

采用 MIT License，详见 [LICENSE](../LICENSE)。
