"""Optional YOLO object detection and session-local, short-lived track IDs.

Tracking IDs are convenience labels across adjacent frames, not identities. They can
reset after missed detections, process restarts, or when requests reach another worker.
"""
from __future__ import annotations

import os
import threading
import time
from collections import OrderedDict
from dataclasses import dataclass, field
from functools import lru_cache
from io import BytesIO
from typing import Any



@dataclass
class Detection:
    class_name: str
    confidence: float
    bbox: list[float]  # normalized x1, y1, x2, y2 in [0, 1]
    track_id: int | None = None


@dataclass
class Track:
    track_id: int
    class_name: str
    center_x: float
    center_y: float
    misses: int = 0


@dataclass
class SessionTracker:
    next_id: int = 1
    last_access: float = field(default_factory=time.monotonic)
    tracks: list[Track] = field(default_factory=list)


class ObjectTrackerPool:
    """Best-effort centroid tracker scoped to caller-provided random session IDs."""

    def __init__(self, max_sessions: int = 128, expire_seconds: int = 600, max_misses: int = 3):
        self.max_sessions = max_sessions
        self.expire_seconds = expire_seconds
        self.max_misses = max_misses
        self._sessions: OrderedDict[str, SessionTracker] = OrderedDict()
        self._lock = threading.Lock()

    def update(self, session_id: str, detections: list[Detection]) -> list[Detection]:
        now = time.monotonic()
        with self._lock:
            expired = [
                key for key, value in self._sessions.items()
                if now - value.last_access > self.expire_seconds
            ]
            for key in expired:
                self._sessions.pop(key, None)

            tracker = self._sessions.pop(session_id, None) or SessionTracker()
            tracker.last_access = now
            old_tracks = tracker.tracks
            used: set[int] = set()
            updated: list[Track] = []

            for detection in detections:
                x1, y1, x2, y2 = detection.bbox
                cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
                candidates = [
                    (i, track, ((track.center_x - cx) ** 2 + (track.center_y - cy) ** 2) ** 0.5)
                    for i, track in enumerate(old_tracks)
                    if i not in used and track.class_name == detection.class_name
                ]
                match = min(candidates, key=lambda candidate: candidate[2], default=None)
                if match is not None and match[2] <= 0.16:
                    index, previous, _ = match
                    used.add(index)
                    track_id = previous.track_id
                else:
                    track_id = tracker.next_id
                    tracker.next_id += 1

                detection.track_id = track_id
                updated.append(Track(track_id, detection.class_name, cx, cy))

            # Keep a track for a few missed frames so brief detector flicker doesn't
            # immediately change its ID. IDs are still only best-effort associations.
            for index, previous in enumerate(old_tracks):
                if index not in used and previous.misses < self.max_misses:
                    updated.append(
                        Track(
                            previous.track_id,
                            previous.class_name,
                            previous.center_x,
                            previous.center_y,
                            previous.misses + 1,
                        )
                    )

            tracker.tracks = updated
            self._sessions[session_id] = tracker
            while len(self._sessions) > self.max_sessions:
                self._sessions.popitem(last=False)
            return detections


class YoloObjectDetector:
    """Loads Ultralytics lazily so photo-only installations need no model dependency."""

    def __init__(self) -> None:
        model_path = os.getenv("YOLO_MODEL_PATH", "yolov8n.pt")
        try:
            from ultralytics import YOLO
        except ImportError as exc:
            raise RuntimeError(
                "YOLO dependencies are not installed. Install backend/requirements-perception.txt."
            ) from exc
        try:
            self.model: Any = YOLO(model_path)
        except Exception as exc:
            raise RuntimeError(
                "YOLO model could not be loaded. Check YOLO_MODEL_PATH and model access."
            ) from exc

    def detect(self, jpeg_bytes: bytes) -> tuple[int, int, list[Detection]]:
        try:
            from PIL import Image
            image = Image.open(BytesIO(jpeg_bytes)).convert("RGB")
        except Exception as exc:
            raise ValueError("Could not decode the JPEG frame.") from exc

        width, height = image.size
        try:
            results = self.model.predict(image, verbose=False, conf=0.35, imgsz=640)
            result = results[0]
            boxes = result.boxes
            if boxes is None:
                return width, height, []
            xyxy = boxes.xyxy.cpu().tolist()
            confidences = boxes.conf.cpu().tolist()
            classes = boxes.cls.cpu().tolist()
            names = result.names
            detections = []
            for coords, confidence, class_index in zip(xyxy, confidences, classes):
                x1, y1, x2, y2 = coords
                detections.append(
                    Detection(
                        class_name=str(names.get(int(class_index), int(class_index))),
                        confidence=round(float(confidence), 3),
                        bbox=[
                            max(0.0, min(1.0, float(x1) / width)),
                            max(0.0, min(1.0, float(y1) / height)),
                            max(0.0, min(1.0, float(x2) / width)),
                            max(0.0, min(1.0, float(y2) / height)),
                        ],
                    )
                )
            return width, height, detections
        except Exception as exc:
            raise RuntimeError("YOLO inference failed.") from exc


@lru_cache(maxsize=1)
def get_object_detector() -> YoloObjectDetector:
    return YoloObjectDetector()


tracker_pool = ObjectTrackerPool()
