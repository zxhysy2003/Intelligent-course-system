# Course Recommendation Service

`recommend-service` 是智能课程学习系统的 FastAPI 推荐服务，负责接收后端传入的用户课程评分数据，使用协同过滤算法生成课程推荐候选列表。

当前项目采用同仓多服务结构：

- 推荐服务：`recommend-service`
- 后端服务：[`../backend`](../backend)
- 前端服务：[`../frontend`](../frontend)
- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)

推荐服务默认运行在 `http://127.0.0.1:8000`。前端不直接调用该服务，后端会通过 `RECOMMEND_SERVICE_URL` 调用 `POST /recommend`，再结合 MySQL、Redis 和 Neo4j 中的业务数据生成最终推荐结果。

## 功能概览

- 提供 `POST /recommend` 推荐接口
- 接收用户、课程、隐式评分组成的评分矩阵
- 使用 `scikit-surprise` 的 `SVD` 算法训练协同过滤模型
- 过滤目标用户已学习课程
- 按预测评分降序返回未学习课程候选
- 通过 FastAPI 自动提供 Swagger 文档和 OpenAPI 描述

## 技术栈

- Python 3.9
- FastAPI 0.116.1
- Uvicorn 0.35.0
- Pydantic 2.11.7
- Pandas 2.3.1
- NumPy 1.26.4
- SciPy 1.13.1
- scikit-surprise 1.1.4
- Conda

## 目录结构

```text
recommend-service
├── environment.yml     # Conda 环境和 Python 依赖
├── main.py             # FastAPI 应用入口
├── model.py            # SVD 推荐算法
├── schemas.py          # 请求和响应模型
└── README.md
```

## 环境要求

- Conda 或兼容的 Python 环境管理工具
- Python 3.9
- 完整联调时需要可访问的后端服务，默认地址为 `http://127.0.0.1:8080`

依赖定义在 [`environment.yml`](environment.yml)，默认环境名为 `lab_autumn`。

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
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

启动成功后可访问：

- `http://127.0.0.1:8000/docs`
- `http://127.0.0.1:8000/openapi.json`

### 4. 联调后端

后端默认读取：

```text
RECOMMEND_SERVICE_URL=http://localhost:8000
```

如果推荐服务监听地址发生变化，需要同步修改后端启动环境变量：

```bash
cd ../backend
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 ./mvnw spring-boot:run
```

如果另开终端并从仓库根目录启动后端，则执行：

```bash
cd backend
RECOMMEND_SERVICE_URL=http://127.0.0.1:8000 ./mvnw spring-boot:run
```

## API 说明

### POST /recommend

请求体：

```json
{
  "targetUserId": 1,
  "topN": 5,
  "data": [
    {
      "userId": 1,
      "courseId": 101,
      "score": 9.0
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `targetUserId` | integer | 需要生成推荐结果的目标用户 ID |
| `topN` | integer | 返回候选课程数量，默认 `5` |
| `data` | array | 用户课程评分矩阵 |
| `data[].userId` | integer | 用户 ID |
| `data[].courseId` | integer | 课程 ID |
| `data[].score` | number | 隐式评分，模型按 `0` 到 `10` 评分区间读取 |

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

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | integer | 目标用户 ID |
| `items` | array | 推荐候选课程列表 |
| `items[].courseId` | integer | 推荐课程 ID |
| `items[].score` | number | SVD 预测评分 |

当 `data` 为空时，接口会返回：

```json
{
  "userId": -1,
  "items": []
}
```

## 接口测试

```bash
curl -X POST "http://127.0.0.1:8000/recommend" \
  -H "Content-Type: application/json" \
  -d '{
    "targetUserId": 1,
    "topN": 3,
    "data": [
      { "userId": 1, "courseId": 1, "score": 9.0 },
      { "userId": 1, "courseId": 2, "score": 7.5 },
      { "userId": 2, "courseId": 2, "score": 8.0 },
      { "userId": 2, "courseId": 3, "score": 9.5 },
      { "userId": 3, "courseId": 3, "score": 8.5 },
      { "userId": 3, "courseId": 4, "score": 9.0 }
    ]
  }'
```

正常情况下响应会包含 `userId` 和 `items`。具体 `score` 会随 SVD 模型训练结果变化，不建议在调用方依赖固定分值。

## 算法说明

核心逻辑位于 [`model.py`](model.py)：

1. 将请求中的 `data` 转换为 Pandas DataFrame
2. 使用 Surprise `Reader(rating_scale=(0, 10))` 读取评分矩阵
3. 基于完整评分矩阵构建训练集
4. 使用 `SVD()` 训练协同过滤模型
5. 遍历评分矩阵中出现过的课程
6. 排除目标用户已学习课程
7. 对未学习课程进行预测并按评分降序返回

当前实现会在每次请求时即时训练模型，不做模型持久化，也不直接访问数据库。评分数据由后端聚合后传入。

## 与后端的关系

后端调用链路：

```text
backend RecommendService
  -> POST ${RECOMMEND_SERVICE_URL}/recommend
  -> recommend-service SVD 候选课程
  -> backend HybridRecommendService 融合课程信息、知识图谱和新课策略
  -> frontend /recommend 页面展示
```

因此推荐服务只负责协同过滤候选生成，不负责：

- 用户登录鉴权
- 课程详情查询
- 知识图谱先修关系计算
- 新课曝光策略
- 前端展示分和推荐理由

这些能力由 `../backend` 完成。

## 开发约定

- FastAPI 应用入口保留在 `main.py`
- 请求和响应模型放在 `schemas.py`
- 推荐算法逻辑放在 `model.py`
- 接口路径保持为 `POST /recommend`，避免后端 `RECOMMEND_SERVICE_URL` 调用链路失效
- 修改请求或响应字段时，需要同步更新后端 DTO 和调用逻辑

## 常见问题

### Conda 环境创建失败

检查 Conda 是否可用，以及当前网络是否能访问 `environment.yml` 中配置的镜像源。`scikit-surprise` 依赖编译环境，如果安装失败，优先使用新的 Conda 环境重新创建。

### 后端推荐接口报错

确认推荐服务已经启动在 `http://127.0.0.1:8000`，并且后端 `RECOMMEND_SERVICE_URL` 与实际地址一致。后端默认会调用 `${RECOMMEND_SERVICE_URL}/recommend`。

### 返回 items 为空

常见原因包括：

- `data` 为空
- 目标用户已经学习了评分矩阵中的所有课程
- 评分矩阵中课程数量过少，无法产生未学习候选

### 分数每次略有变化

当前 `SVD()` 未固定随机种子，并且每次请求都会重新训练模型，因此预测分数可能出现轻微波动。调用方应按排序和候选列表使用结果，不应依赖固定分值。

## 更多文档

- 总操作手册：[`../docs/OPERATION_MANUAL.md`](../docs/OPERATION_MANUAL.md)
- 后端说明：[`../backend/README.md`](../backend/README.md)
- 前端说明：[`../frontend/README.md`](../frontend/README.md)

## License

本推荐服务采用 MIT License，详见根目录 [`../LICENSE`](../LICENSE)。
