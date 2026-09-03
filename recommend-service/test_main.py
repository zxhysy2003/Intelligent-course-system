import unittest
from threading import Event, Thread
from unittest.mock import patch

from fastapi import HTTPException

import main
from schemas import RecommendRequest


class RecommendConcurrencyGateTest(unittest.TestCase):
    def test_over_capacity_should_return_503_with_retry_after(self):
        gate = main.RecommendConcurrencyGate(1, 10)
        entered = Event()
        release = Event()

        def blocking_recommend(*_args, **_kwargs):
            entered.set()
            release.wait(timeout=2)
            return []

        request = RecommendRequest(targetUserId=1, topN=10)
        first_result = []

        with patch.object(main, "recommend_gate", gate), patch.object(
            main.model_store, "recommend", side_effect=blocking_recommend
        ):
            first = Thread(target=lambda: first_result.append(main.recommend_api(request)))
            first.start()
            self.assertTrue(entered.wait(timeout=1))

            with self.assertRaises(HTTPException) as raised:
                main.recommend_api(request)

            self.assertEqual(503, raised.exception.status_code)
            self.assertEqual("1", raised.exception.headers["Retry-After"])
            release.set()
            first.join(timeout=2)

        self.assertFalse(first.is_alive())
        self.assertEqual(1, len(first_result))

    def test_permit_should_be_released_when_recommendation_fails(self):
        gate = main.RecommendConcurrencyGate(1, 0)
        request = RecommendRequest(targetUserId=1, topN=10)

        with patch.object(main, "recommend_gate", gate), patch.object(
            main.model_store, "recommend", side_effect=RuntimeError("failed")
        ):
            with self.assertRaises(RuntimeError):
                main.recommend_api(request)

        self.assertTrue(gate.acquire())
        gate.release()


if __name__ == "__main__":
    unittest.main()
