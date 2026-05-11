import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from database import create_mysql_engine
from model import RecommendationModelStore
from schemas import ModelStatus, RecommendRequest, RecommendResponse

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

model_store = RecommendationModelStore(create_mysql_engine())


@asynccontextmanager
async def lifespan(_: FastAPI):
    # 服务启动时尝试训练一次；失败不阻断进程，在线请求会返回空 CF 结果给后端兜底。
    try:
        status = model_store.reload()
        log.info("recommend model startup reload finished: %s", status)
    except Exception:
        log.exception("recommend model startup reload failed; service will return empty CF results")
    yield


app = FastAPI(title="Course Recommendation Service", lifespan=lifespan)


@app.post("/recommend", response_model=RecommendResponse)
def recommend_api(req: RecommendRequest):
    # 在线请求只传 userId/topN，模型和评分矩阵常驻在推荐服务内存中。
    recommendations = model_store.recommend(
        target_user_id=req.targetUserId,
        top_n=req.topN,
    )
    return RecommendResponse(
        userId=req.targetUserId,
        items=[item.__dict__ for item in recommendations],
    )


@app.get("/model/status", response_model=ModelStatus)
def model_status():
    # 便于后端联调时确认模型是否已经训练、训练数据规模是否符合预期。
    return ModelStatus(**model_store.status().__dict__)


@app.post("/model/reload", response_model=ModelStatus)
def reload_model():
    try:
        status = model_store.reload()
    except Exception as exc:
        # reload 失败时 RecommendationModelStore 不会替换旧状态，因此旧模型仍可服务。
        log.exception("recommend model reload failed; keeping previous model")
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return ModelStatus(**status.__dict__)
