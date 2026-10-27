import base64
from types import SimpleNamespace

from fastapi.testclient import TestClient

from app import main as main_module
from app.main import app
from app.memory import EphemeralMemory, memory_store

client = TestClient(app)


def test_ephemeral_memory_is_bounded_and_clearable():
    memory = EphemeralMemory(max_observations=2)
    memory.remember("session-123456", "describe", "The table is near the window.")
    memory.remember("session-123456", "read_text", "A sign says Exit.")
    memory.remember("session-123456", "ask", "The cup was beside the book.")

    recent = memory.recent("session-123456")
    assert len(recent) == 2
    assert recent[0].text == "A sign says Exit."
    memory.clear("session-123456")
    assert memory.recent("session-123456") == []


def test_memory_is_opt_in_and_context_is_used_for_follow_up(monkeypatch):
    class FakeResponses:
        def __init__(self):
            self.calls = []

        def create(self, **kwargs):
            self.calls.append(kwargs)
            answer = "Earlier note: a red cup was beside the book." if len(self.calls) == 1 else "The earlier note said the cup was beside the book."
            return SimpleNamespace(output_text=answer)

    class FakeClient:
        def __init__(self):
            self.responses = FakeResponses()

    fake = FakeClient()
    monkeypatch.setattr(main_module, "_client", lambda: fake)
    session_id = "memory-demo-123"
    memory_store.clear(session_id)
    image_b64 = base64.b64encode(b"\xff\xd8\xff\xd9").decode("ascii")

    first = client.post(
        "/v1/analyze",
        json={
            "image_base64": image_b64,
            "mode": "describe",
            "session_id": session_id,
            "remember": True,
        },
    )
    second = client.post(
        "/v1/analyze",
        json={
            "image_base64": image_b64,
            "mode": "ask",
            "question": "Where was the cup in the earlier observation?",
            "session_id": session_id,
            "remember": True,
        },
    )

    assert first.status_code == 200
    assert second.status_code == 200
    prompt_text = fake.responses.calls[1]["input"][0]["content"][0]["text"]
    assert "Earlier note: a red cup was beside the book." in prompt_text
    assert "possibly wrong/outdated" in prompt_text

    cleared = client.post("/v1/memory/clear", json={"session_id": session_id})
    assert cleared.status_code == 200
    assert cleared.json()["status"] == "cleared"


def test_memory_requires_session_id():
    image_b64 = base64.b64encode(b"\xff\xd8\xff\xd9").decode("ascii")
    response = client.post(
        "/v1/analyze",
        json={"image_base64": image_b64, "remember": True, "mode": "describe"},
    )
    assert response.status_code == 422
    assert "session_id" in response.json()["detail"]
