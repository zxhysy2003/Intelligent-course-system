# Course Recommendation Service

`recommend-service` 是智能课程学习系统的 FastAPI 协同过滤召回服务。第二阶段重构后，它不再接收后端传来的全量评分矩阵，而是直连 MySQL 读取 `recommend_user_course_score` 快照表，在启动或手动 reload 时训练内存 SVD 模型；在线推荐请求只传 `targetUserId` 和 `topN`。

当前项目采用同仓多服务结构：

- 推荐服务：`recommend-service`
- 后端服务：[`../backend`](../backend)
- 前端服务：[`../frontend`](../frontend)
- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)

推荐服务默认运行在 `http://127.0.0.1:8000`。前端不直接调用该服务，后端通过 `RECOMMEND_SERVICE_URL` 调用 `POST /recommend`，再结合 MySQL、Redis 和 Neo4j 中的业务数据生成最终推荐结果。

## 功能概览

- 启动时从 MySQL `recommend_user_course_score` 读取评分数据并训练 SVD 模型
- 提供 `POST /model/reload` 手动重载模型，失败时保留旧模型
- 提供 `GET /model/status` 查看模型状态、版本和训练数据规模
- `POST /recommend` 只接收目标用户和候选数量，返回 CF 候选课程
- 模型不可用、评分表为空或目标用户不在训练集中时返回空 `items`，由后端走新课/热门兜底

## 技术栈

- Python 3.9
- FastAPI 0.116.1
- Uvicorn 0.35.0
- Pydantic 2.11.7
- Pandas 2.3.1
- SQLAlchemy 2.0.36
- PyMySQL 1.1.1
- scikit-surprise 1.1.4
- Conda

## 目录结构

```text
recommend-service
├── database.py          # MySQL 连接配置
├── environment.yml     # Conda 环境和 Python 依赖
├── main.py             # FastAPI 应用入口
├── model.py            # SVD 模型加载、训练、预测和状态管理
├── schemas.py          # 请求和响应模型
└── README.md
```

## 环境要求

- Conda 或兼容的 Python 环境管理工具
- Python 3.9
- 可访问的 MySQL，且已存在后端维护的 `recommend_user_course_score` 表

依赖定义在 [`environment.yml`](environment.yml)，默认环境名为 `lab_autumn`。

推荐服务复用后端数据库环境变量：

| 变量名 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` | MySQL 地址 | `localhost` |
| `DB_PORT` | MySQL 端口 | `3306` |
| `DB_NAME` | 数据库名 | `course_db` |
| `DB_USERNAME` | MySQL 用户名 | `dev` |
| `DB_PASSWORD` | MySQL 密码 | `dev123` |

## 本地开发

下面创建和启动推荐服务的命令默认从仓库根目录执行。

### 1. 创建环境

```bash
cd recommend-service
conda env create -f environment.yml
```

### 2. 激活环境

```bash
conda activate lab_autumn
```

### 3. 启动服务

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=course_db \
DB_USERNAME=dev \
DB_PASSWORD=dev123 \
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

启动成功后可访问：

- `http://127.0.0.1:8000/docs`
- `http://127.0.0.1:8000/openapi.json`
- `http://127.0.0.1:8000/model/status`

如果后端刚完成快照重建，可以手动刷新模型：

```bash
curl -X POST "http://127.0.0.1:8000/model/reload"
```

## API 说明

### POST /recommend

请求体：

```json
{
  "targetUserId": 1,
  "topN": 100
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `targetUserId` | integer | 需要生成 CF 候选的目标用户 ID |
| `topN` | integer | 返回候选课程数量，默认 `100` |

响应体：

```json
{
  "userId": 1,
  "items": [
    {
      "courseId": 103,
      "score": 8.72
    }
  ]
}
```

当模型不可用、评分表为空、目标用户不在训练集中，或目标用户已经学习了训练集中所有课程时，接口返回：

```json
{
  "userId": 1,
  "items": []
}
```

### GET /model/status

返回模型状态：

```json
{
  "available": true,
  "trainedAt": "2026-05-10T10:00:00+00:00",
  "modelVersion": 1,
  "ratingCount": 1200,
  "userCount": 120,
  "courseCount": 80,
  "message": null
}
```

### POST /model/reload

从 MySQL 重新加载快照表并训练新模型。训练成功后原子替换内存模型；如果读取或训练失败，接口返回 `500`，旧模型继续可用。

## 接口测试

```bash
curl -X POST "http://127.0.0.1:8000/recommend" \
  -H "Content-Type: application/json" \
  -d '{
    "targetUserId": 1,
    "topN": 3
  }'
```

正常情况下响应会包含 `userId` 和 `items`。具体 `score` 会随 SVD 模型训练结果变化，不建议在调用方依赖固定分值。

## 算法说明

核心逻辑位于 [`model.py`](model.py)：

1. 从 `recommend_user_course_score` 读取 `user_id`、`course_id`、`score`
2. 使用 Surprise `Reader(rating_scale=(0, 10))` 读取评分数据
3. 基于完整快照表构建训练集
4. 使用固定随机种子的 `SVD` 训练协同过滤模型
5. 在内存中保存模型、课程全集、用户已评分课程集合和模型状态
6. 在线请求遍历训练集中出现过的课程，排除目标用户已评分课程
7. 对未评分课程进行预测并按评分降序返回

推荐服务只负责 CF 候选召回，不负责：

- 用户登录鉴权
- 课程详情查询
- 知识图谱先修关系计算
- 新课曝光策略
- 热门课程兜底
- 前端展示分和推荐理由

这些能力由 `../backend` 完成。

## 开发约定

- FastAPI 应用入口保留在 `main.py`
- 请求和响应模型放在 `schemas.py`
- 推荐算法逻辑放在 `model.py`
- 数据库连接配置放在 `database.py`
- 接口路径保持为 `POST /recommend`，避免后端 `RECOMMEND_SERVICE_URL` 调用链路失效
- 修改请求或响应字段时，需要同步更新后端 DTO、调用逻辑和文档

## 常见问题

### Conda 环境创建失败

检查 Conda 是否可用，以及当前网络是否能访问 `environment.yml` 中配置的镜像源。`scikit-surprise` 依赖编译环境，如果安装失败，优先使用新的 Conda 环境重新创建。

### 后端推荐接口报错

确认推荐服务已经启动在 `http://127.0.0.1:8000`，并且后端 `RECOMMEND_SERVICE_URL` 与实际地址一致。后端默认会调用 `${RECOMMEND_SERVICE_URL}/recommend`。

### 返回 items 为空

常见原因包括：

- `recommend_user_course_score` 为空
- 模型尚未训练成功
- 目标用户不在训练集中
- 目标用户已经学习了训练集中的所有课程

如果后端刚重建过快照，调用 `POST /model/reload` 即可让推荐服务加载新数据。

## 更多文档

- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)
- 后端说明：[`../backend/README.md`](../backend/README.md)
- 前端说明：[`../frontend/README.md`](../frontend/README.md)

## License

本推荐服务采用 MIT License，详见根目录 [`../LICENSE`](../LICENSE)。
