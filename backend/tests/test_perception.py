import base64

from fastapi.testclient import TestClient

from app.main import app
from app.perception import Detection, ObjectTrackerPool

client = TestClient(app)


class FakeDetector:
    def __init__(self):
        self.frames = 0

    def detect(self, _jpeg_bytes):
        self.frames += 1
        shift = 0.01 * (self.frames - 1)
        return 100, 100, [
            Detection(
                class_name="person",
                confidence=0.91,
                bbox=[0.10 + shift, 0.10, 0.40 + shift, 0.90],
            )
        ]


def test_tracker_preserves_track_id_for_small_frame_motion():
    tracker = ObjectTrackerPool()
    first = [Detection("person", 0.91, [0.1, 0.1, 0.4, 0.9])]
    second = [Detection("person", 0.90, [0.11, 0.1, 0.41, 0.9])]

    tracker.update("session-123456", first)
    tracker.update("session-123456", second)

    assert first[0].track_id == second[0].track_id


def test_perception_endpoint_tracks_objects_across_frames(monkeypatch):
    from app import main as main_module

    detector = FakeDetector()
    monkeypatch.setattr(main_module, "get_object_detector", lambda: detector)
    monkeypatch.setattr(main_module, "tracker_pool", ObjectTrackerPool())
    image_b64 = base64.b64encode(b"\xff\xd8\xff\xd9").decode("ascii")
    payload = {"image_base64": image_b64, "session_id": "demo-session-123"}

    first = client.post("/v1/perception", json=payload)
    second = client.post("/v1/perception", json=payload)

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["objects"][0]["class_name"] == "person"
    assert first.json()["objects"][0]["track_id"] == second.json()["objects"][0]["track_id"]
    assert "does not measure distance" in first.json()["limitation"]


def test_perception_requires_session_id():
    image_b64 = base64.b64encode(b"\xff\xd8\xff\xd9").decode("ascii")
    response = client.post("/v1/perception", json={"image_base64": image_b64})
    assert response.status_code == 422
