import unittest
from threading import Event, Lock, Thread

from model import RecommendationModelStore


class FakeResult:
    """模拟 SQLAlchemy Result.mappings().all() 的最小行为。"""

    def __init__(self, rows):
        self.rows = rows

    def mappings(self):
        return self

    def all(self):
        return self.rows


class FakeConnection:
    """模拟数据库连接，也可以注入异常来验证 reload 失败路径。"""

    def __init__(self, rows_or_error):
        self.rows_or_error = rows_or_error

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def execute(self, query):
        if isinstance(self.rows_or_error, Exception):
            raise self.rows_or_error
        return FakeResult(self.rows_or_error)


class FakeEngine:
    """按批次返回 fake connection，方便模拟多次 reload 的不同数据库状态。"""

    def __init__(self, batches):
        self.batches = list(batches)

    def connect(self):
        if not self.batches:
            raise AssertionError("no fake DB batch configured")
        return FakeConnection(self.batches.pop(0))


class BlockingFakeEngine:
    """阻塞第一次连接，用来验证并发 reload 会被串行化。"""

    def __init__(self, batches):
        self.batches = list(batches)
        self.first_connect_entered = Event()
        self.second_connect_entered = Event()
        self.release_first = Event()
        self.connect_count = 0
        self._lock = Lock()

    def connect(self):
        with self._lock:
            if not self.batches:
                raise AssertionError("no fake DB batch configured")
            rows = self.batches.pop(0)
            connect_index = self.connect_count
            self.connect_count += 1

        if connect_index == 0:
            self.first_connect_entered.set()
            if not self.release_first.wait(timeout=5):
                raise AssertionError("first reload was not released")
        elif connect_index == 1:
            self.second_connect_entered.set()

        return FakeConnection(rows)


class RecommendationModelStoreTest(unittest.TestCase):
    def test_reload_should_train_model_and_recommend_unlearned_courses(self):
        # 用户 1 已学习 101，因此推荐结果只能来自 102/103。
        store = RecommendationModelStore(FakeEngine([[
            {"user": 1, "item": 101, "rating": 9.0},
            {"user": 2, "item": 101, "rating": 8.0},
            {"user": 2, "item": 102, "rating": 7.0},
            {"user": 3, "item": 102, "rating": 8.0},
            {"user": 3, "item": 103, "rating": 9.0},
        ]]))

        status = store.reload()
        items = store.recommend(1, top_n=10)

        self.assertTrue(status.available)
        self.assertEqual(1, status.modelVersion)
        self.assertEqual(5, status.ratingCount)
        self.assertNotIn(101, [item.courseId for item in items])
        self.assertTrue({102, 103}.issuperset({item.courseId for item in items}))

    def test_reload_should_mark_model_unavailable_when_score_table_is_empty(self):
        store = RecommendationModelStore(FakeEngine([[]]))

        status = store.reload()

        self.assertFalse(status.available)
        self.assertEqual(1, status.modelVersion)
        self.assertEqual(0, status.ratingCount)
        self.assertEqual([], store.recommend(1, top_n=10))

    def test_recommend_should_return_empty_when_target_user_is_unknown(self):
        store = RecommendationModelStore(FakeEngine([[
            {"user": 1, "item": 101, "rating": 9.0},
            {"user": 2, "item": 102, "rating": 8.0},
        ]]))
        store.reload()

        self.assertEqual([], store.recommend(99, top_n=10))

    def test_reload_failure_should_keep_previous_model(self):
        # 第二次 reload 抛错时，旧模型版本和评分规模都应保持不变。
        store = RecommendationModelStore(FakeEngine([
            [
                {"user": 1, "item": 101, "rating": 9.0},
                {"user": 2, "item": 102, "rating": 8.0},
            ],
            RuntimeError("db unavailable"),
        ]))
        initial_status = store.reload()

        with self.assertRaises(RuntimeError):
            store.reload()

        current_status = store.status()
        self.assertTrue(current_status.available)
        self.assertEqual(initial_status.modelVersion, current_status.modelVersion)
        self.assertEqual(initial_status.ratingCount, current_status.ratingCount)

    def test_concurrent_reload_should_run_serially(self):
        engine = BlockingFakeEngine([
            [
                {"user": 1, "item": 101, "rating": 9.0},
                {"user": 2, "item": 102, "rating": 8.0},
            ],
            [
                {"user": 1, "item": 101, "rating": 9.0},
                {"user": 2, "item": 102, "rating": 8.0},
                {"user": 3, "item": 103, "rating": 7.0},
            ],
        ])
        store = RecommendationModelStore(engine)
        statuses = []
        errors = []

        def run_reload():
            try:
                statuses.append(store.reload())
            except Exception as exc:
                errors.append(exc)

        first = Thread(target=run_reload)
        first.start()
        self.assertTrue(engine.first_connect_entered.wait(timeout=2))

        second = Thread(target=run_reload)
        second.start()
        # 第二次 reload 应该卡在 reload 锁上，直到第一次 reload 完整结束。
        self.assertFalse(engine.second_connect_entered.wait(timeout=0.2))

        engine.release_first.set()
        first.join(timeout=5)
        second.join(timeout=5)

        self.assertFalse(first.is_alive())
        self.assertFalse(second.is_alive())
        self.assertEqual([], errors)
        self.assertEqual([1, 2], sorted(status.modelVersion for status in statuses))
        self.assertEqual(2, store.status().modelVersion)


if __name__ == "__main__":
    unittest.main()
