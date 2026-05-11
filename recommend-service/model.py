from dataclasses import dataclass
from datetime import datetime, timezone
from threading import Lock, RLock
from typing import Dict, List, Optional, Set

import pandas as pd
from sqlalchemy import text
from surprise import Dataset, Reader, SVD


@dataclass(frozen=True)
class CourseRecommendation:
    """返回给后端的单个 CF 召回候选。"""

    courseId: int
    score: float


@dataclass(frozen=True)
class ModelStatusSnapshot:
    """对外暴露的模型状态快照，字段名保持 API 响应风格。"""

    available: bool
    trainedAt: Optional[str]
    modelVersion: int
    ratingCount: int
    userCount: int
    courseCount: int
    message: Optional[str] = None


@dataclass(frozen=True)
class _ModelState:
    """内存模型的完整状态。

    reload 成功后整体替换该对象，避免在线推荐读到半更新状态。
    """

    algo: Optional[SVD]
    all_items: List[int]
    learned_items_by_user: Dict[int, Set[int]]
    trained_at: Optional[datetime]
    model_version: int
    rating_count: int
    user_count: int
    course_count: int
    message: Optional[str] = None

    @property
    def available(self) -> bool:
        return self.algo is not None and self.rating_count > 0


class RecommendationModelStore:
    def __init__(self, engine):
        self._engine = engine
        # 状态锁只保护内存模型对象的读取和替换，避免长时间训练阻塞在线 recommend。
        self._lock = RLock()
        # reload 锁串行化完整训练流程，避免并发 reload 互相覆盖模型版本和训练结果。
        self._reload_lock = Lock()
        self._state = _ModelState(
            algo=None,
            all_items=[],
            learned_items_by_user={},
            trained_at=None,
            model_version=0,
            rating_count=0,
            user_count=0,
            course_count=0,
            message="model not trained",
        )

    def reload(self) -> ModelStatusSnapshot:
        with self._reload_lock:
            return self._reload_serialized()

    def _reload_serialized(self) -> ModelStatusSnapshot:
        # 先在锁外读取和训练，避免训练期间阻塞正在进行的在线推荐。
        df = self._load_score_frame()
        with self._lock:
            next_version = self._state.model_version + 1

        if df.empty:
            # 评分表为空不是异常，标记模型不可用即可，由后端走新课/热门兜底。
            new_state = _ModelState(
                algo=None,
                all_items=[],
                learned_items_by_user={},
                trained_at=None,
                model_version=next_version,
                rating_count=0,
                user_count=0,
                course_count=0,
                message="score snapshot table is empty",
            )
            with self._lock:
                self._state = new_state
            return self.status()

        reader = Reader(rating_scale=(0, 10))
        dataset = Dataset.load_from_df(df[["user", "item", "rating"]], reader)
        trainset = dataset.build_full_trainset()

        # 固定 random_state 让同一份快照训练结果更稳定，便于回归验证。
        algo = SVD(random_state=42)
        algo.fit(trainset)

        # 记录每个用户已经有评分的课程，在线预测时过滤掉这些已学习/已交互课程。
        learned_items_by_user = {
            int(user_id): set(group["item"].astype(int).tolist())
            for user_id, group in df.groupby("user")
        }
        new_state = _ModelState(
            algo=algo,
            all_items=sorted(df["item"].astype(int).unique().tolist()),
            learned_items_by_user=learned_items_by_user,
            trained_at=datetime.now(timezone.utc),
            model_version=next_version,
            rating_count=int(len(df)),
            user_count=int(df["user"].nunique()),
            course_count=int(df["item"].nunique()),
        )
        with self._lock:
            # 只有训练完整成功后才替换状态；训练失败会抛出异常并保留旧模型。
            self._state = new_state
        return self.status()

    def recommend(self, target_user_id: int, top_n: int = 100) -> List[CourseRecommendation]:
        safe_top_n = max(0, top_n)
        if safe_top_n == 0:
            return []

        with self._lock:
            state = self._state

        # 未训练、空表或目标用户不在训练集中时返回空列表，让 backend 统一降级。
        if not state.available or target_user_id not in state.learned_items_by_user:
            return []

        learned_items = state.learned_items_by_user[target_user_id]
        predictions = []
        for item in state.all_items:
            if item in learned_items:
                continue
            # Surprise 可直接用原始 user/item id 做预测，内部会映射到训练集 id。
            pred = state.algo.predict(target_user_id, item)
            predictions.append(CourseRecommendation(courseId=int(item), score=float(pred.est)))

        predictions.sort(key=lambda item: item.score, reverse=True)
        return predictions[:safe_top_n]

    def status(self) -> ModelStatusSnapshot:
        with self._lock:
            state = self._state
        trained_at = state.trained_at.isoformat() if state.trained_at else None
        return ModelStatusSnapshot(
            available=state.available,
            trainedAt=trained_at,
            modelVersion=state.model_version,
            ratingCount=state.rating_count,
            userCount=state.user_count,
            courseCount=state.course_count,
            message=state.message,
        )

    def _load_score_frame(self) -> pd.DataFrame:
        # 推荐服务只读后端维护好的归一化评分，不再接收请求体里的全量矩阵。
        query = text("""
            SELECT user_id AS user, course_id AS item, score AS rating
            FROM recommend_user_course_score
        """)
        with self._engine.connect() as conn:
            rows = conn.execute(query).mappings().all()

        if not rows:
            return pd.DataFrame(columns=["user", "item", "rating"])
        return pd.DataFrame([dict(row) for row in rows], columns=["user", "item", "rating"])
