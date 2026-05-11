from pydantic import BaseModel
from typing import List, Optional


class RecommendRequest(BaseModel):
    """后端调用 CF 召回服务的请求体，不再携带评分矩阵。"""

    targetUserId: int
    topN: int = 100


class CourseRecommendation(BaseModel):
    """单个课程候选及其模型预测分。"""

    courseId: int
    score: float


class RecommendResponse(BaseModel):
    userId: int
    items: List[CourseRecommendation]


class ModelStatus(BaseModel):
    """模型状态查询和 reload 接口共用的响应体。"""

    available: bool
    trainedAt: Optional[str] = None
    modelVersion: int = 0
    ratingCount: int = 0
    userCount: int = 0
    courseCount: int = 0
    message: Optional[str] = None
