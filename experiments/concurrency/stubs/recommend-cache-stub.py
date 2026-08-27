#!/usr/bin/env python3
"""CON-04 可控延迟推荐 Stub，仅使用 Python 标准库。"""

from __future__ import annotations

import json
import os
import random
import threading
import time
from collections import Counter
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


def read_int(name: str, default: int, minimum: int = 0) -> int:
    raw = os.getenv(name, str(default))
    try:
        value = int(raw)
    except ValueError as exc:
        raise SystemExit(f"{name} 必须是整数，当前值：{raw}") from exc
    if value < minimum:
        raise SystemExit(f"{name} 不能小于 {minimum}，当前值：{value}")
    return value


def read_probability(name: str, default: float = 0.0) -> float:
    raw = os.getenv(name, str(default))
    try:
        value = float(raw)
    except ValueError as exc:
        raise SystemExit(f"{name} 必须是 0-1 之间的小数，当前值：{raw}") from exc
    if value < 0.0 or value > 1.0:
        raise SystemExit(f"{name} 必须是 0-1 之间的小数，当前值：{value}")
    return value


HOST = os.getenv("STUB_HOST", "127.0.0.1")
PORT = read_int("STUB_PORT", 18000, 1)
DELAY_MS = read_int("STUB_DELAY_MS", 100)
ERROR_RATE = read_probability("STUB_ERROR_RATE")
RANDOM_SEED = read_int("STUB_RANDOM_SEED", 20260826)
LOG_REQUESTS = os.getenv("STUB_LOG_REQUESTS", "false").strip().lower() in {"1", "true", "yes"}


class StubStats:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._random = random.Random(RANDOM_SEED)
        self._started_at = self._now()
        self._request_total = 0
        self._success_total = 0
        self._failure_total = 0
        self._active_requests = 0
        self._max_active_requests = 0
        self._per_user: Counter[str] = Counter()

    @staticmethod
    def _now() -> str:
        return datetime.now(timezone.utc).isoformat()

    def begin(self, user_id: int) -> bool:
        with self._lock:
            self._request_total += 1
            self._active_requests += 1
            self._max_active_requests = max(self._max_active_requests, self._active_requests)
            self._per_user[str(user_id)] += 1
            return self._random.random() < ERROR_RATE

    def finish(self, failed: bool) -> None:
        with self._lock:
            self._active_requests -= 1
            if failed:
                self._failure_total += 1
            else:
                self._success_total += 1

    def reset(self) -> dict[str, Any]:
        with self._lock:
            if self._active_requests != 0:
                raise RuntimeError("仍有请求执行中，不能重置统计")
            self._started_at = self._now()
            self._request_total = 0
            self._success_total = 0
            self._failure_total = 0
            self._max_active_requests = 0
            self._per_user.clear()
            return self._snapshot_unlocked()

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return self._snapshot_unlocked()

    def _snapshot_unlocked(self) -> dict[str, Any]:
        return {
            "startedAt": self._started_at,
            "requestTotal": self._request_total,
            "successTotal": self._success_total,
            "failureTotal": self._failure_total,
            "activeRequests": self._active_requests,
            "maxActiveRequests": self._max_active_requests,
            "delayMs": DELAY_MS,
            "errorRate": ERROR_RATE,
            "perUser": dict(sorted(self._per_user.items(), key=lambda item: int(item[0]))),
        }


STATS = StubStats()


class RecommendStubHandler(BaseHTTPRequestHandler):
    server_version = "CON04RecommendStub/1.0"

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self._write_json(HTTPStatus.OK, {"status": "UP", "delayMs": DELAY_MS})
            return
        if self.path == "/stats":
            self._write_json(HTTPStatus.OK, STATS.snapshot())
            return
        self._write_json(HTTPStatus.NOT_FOUND, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        if self.path == "/stats/reset":
            try:
                snapshot = STATS.reset()
            except RuntimeError as exc:
                self._write_json(HTTPStatus.CONFLICT, {"error": str(exc)})
                return
            self._write_json(HTTPStatus.OK, snapshot)
            return
        if self.path != "/recommend":
            self._write_json(HTTPStatus.NOT_FOUND, {"error": "not found"})
            return

        payload = self._read_json()
        if payload is None:
            return
        if LOG_REQUESTS:
            print(f"[con04-stub] request={payload}", flush=True)
        user_id = payload.get("targetUserId")
        if not isinstance(user_id, int) or user_id <= 0:
            self._write_json(HTTPStatus.BAD_REQUEST, {"error": "targetUserId must be positive integer"})
            return

        should_fail = STATS.begin(user_id)
        try:
            time.sleep(DELAY_MS / 1000.0)
            if should_fail:
                self._write_json(HTTPStatus.SERVICE_UNAVAILABLE, {"error": "injected stub failure"})
            else:
                self._write_json(HTTPStatus.OK, {"userId": user_id, "items": []})
        finally:
            STATS.finish(should_fail)

    def _read_json(self) -> dict[str, Any] | None:
        try:
            payload = json.loads(self._read_request_body() or b"{}")
        except (ValueError, json.JSONDecodeError):
            self._write_json(HTTPStatus.BAD_REQUEST, {"error": "invalid JSON"})
            return None
        if not isinstance(payload, dict):
            self._write_json(HTTPStatus.BAD_REQUEST, {"error": "JSON object required"})
            return None
        return payload

    def _read_request_body(self) -> bytes:
        transfer_encoding = self.headers.get("Transfer-Encoding", "").lower()
        if "chunked" not in transfer_encoding:
            length = int(self.headers.get("Content-Length", "0"))
            return self.rfile.read(length)

        chunks: list[bytes] = []
        while True:
            size_line = self.rfile.readline()
            if not size_line:
                raise ValueError("unexpected EOF in chunked request")
            size = int(size_line.split(b";", 1)[0].strip(), 16)
            if size == 0:
                while self.rfile.readline() not in {b"\r\n", b"\n", b""}:
                    pass
                return b"".join(chunks)
            chunks.append(self.rfile.read(size))
            if self.rfile.read(2) != b"\r\n":
                raise ValueError("invalid chunk delimiter")

    def _write_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format_string: str, *args: Any) -> None:
        timestamp = datetime.now().astimezone().isoformat(timespec="seconds")
        print(f"[{timestamp}] {self.client_address[0]} {format_string % args}", flush=True)


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), RecommendStubHandler)
    server.daemon_threads = True
    print(
        f"[con04-stub] listening=http://{HOST}:{PORT} delayMs={DELAY_MS} errorRate={ERROR_RATE}",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[con04-stub] stopping", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
