import base64

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_is_available_without_provider_key():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_invalid_base64_is_rejected_before_provider_call():
    response = client.post("/v1/analyze", json={"image_base64": "not base64!", "mode": "describe"})
    assert response.status_code == 400
    assert "base64" in response.json()["detail"].lower()


def test_non_jpeg_is_rejected_before_provider_call():
    payload = base64.b64encode(b"not a jpeg image").decode("ascii")
    response = client.post("/v1/analyze", json={"image_base64": payload, "mode": "describe"})
    assert response.status_code == 415


def test_ask_mode_requires_question():
    payload = base64.b64encode(b"\xff\xd8\xff\xd9").decode("ascii")
    response = client.post("/v1/analyze", json={"image_base64": payload, "mode": "ask", "question": "  "})
    assert response.status_code == 422
    assert "question" in response.json()["detail"].lower()


def test_valid_jpeg_reports_missing_provider_configuration(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    from app.main import _client
    _client.cache_clear()
    payload = base64.b64encode(b"\xff\xd8\xff\xd9").decode("ascii")
    response = client.post("/v1/analyze", json={"image_base64": payload, "mode": "describe"})
    assert response.status_code == 503
    assert "not configured" in response.json()["detail"].lower()


def test_hindi_prompt_uses_selected_response_language():
    from app.main import AnalysisMode, ResponseLanguage, _prompt
    prompt = _prompt(AnalysisMode.DESCRIBE, "", ResponseLanguage.HINDI)
    assert "Speak to the user in Hindi" in prompt


def test_read_text_prompt_warns_about_unclear_text():
    from app.main import AnalysisMode, ResponseLanguage, _prompt
    prompt = _prompt(AnalysisMode.READ_TEXT, "", ResponseLanguage.ENGLISH)
    assert "say so rather than reconstructing it" in prompt
