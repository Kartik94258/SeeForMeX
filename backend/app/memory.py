"""Small, opt-in in-memory visual notes. Images are never stored here."""
from __future__ import annotations

import threading
import time
from collections import OrderedDict
from dataclasses import dataclass


@dataclass(frozen=True)
class Observation:
    mode: str
    text: str
    created_at: float


class EphemeralMemory:
    def __init__(
        self,
        max_sessions: int = 256,
        max_observations: int = 8,
        ttl_seconds: int = 900,
    ) -> None:
        self.max_sessions = max_sessions
        self.max_observations = max_observations
        self.ttl_seconds = ttl_seconds
        self._sessions: OrderedDict[str, list[Observation]] = OrderedDict()
        self._lock = threading.Lock()

    def _expire(self, now: float) -> None:
        empty_or_stale = []
        for session_id, observations in self._sessions.items():
            fresh = [item for item in observations if now - item.created_at <= self.ttl_seconds]
            if fresh:
                self._sessions[session_id] = fresh
            else:
                empty_or_stale.append(session_id)
        for session_id in empty_or_stale:
            self._sessions.pop(session_id, None)

    def remember(self, session_id: str, mode: str, text: str) -> None:
        clean_text = " ".join(text.split())[:1200]
        if not clean_text:
            return
        now = time.monotonic()
        with self._lock:
            self._expire(now)
            observations = self._sessions.pop(session_id, [])
            observations.append(Observation(mode=mode, text=clean_text, created_at=now))
            self._sessions[session_id] = observations[-self.max_observations :]
            while len(self._sessions) > self.max_sessions:
                self._sessions.popitem(last=False)

    def recent(self, session_id: str) -> list[Observation]:
        now = time.monotonic()
        with self._lock:
            self._expire(now)
            observations = self._sessions.pop(session_id, [])
            if observations:
                self._sessions[session_id] = observations
            return list(observations)

    def clear(self, session_id: str) -> None:
        with self._lock:
            self._sessions.pop(session_id, None)


memory_store = EphemeralMemory()
