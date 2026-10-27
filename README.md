# SeeForMe X — AI Visual Copilot

Native Android MVP for voice-first visual assistance. This starter is intentionally narrow: capture a photo, ask the vision model to describe the scene/read text/answer a question, and speak the answer aloud.

> **Safety note:** Prototype only. Do not rely on it for collision avoidance, crossing roads, medication decisions, emergency response, or independent navigation. Vision models can miss objects, misread text, or invent details.

## MVP scope

- CameraX photo capture (no continuous recording)
- Three analysis modes: Describe, Read text, Ask a question
- Hindi/English speech input through Android's speech-recognition activity (availability depends on device/service)
- Android TextToSpeech output with a stop control
- FastAPI backend; OpenAI API key stays server-side
- Stateless processing; no image or conversation persistence by this application
- Accessible labels, large touch targets, visible status and error messages

Still to build for the advanced vision shown in the concept: continuous Android camera sampling, recent visual memory, contextual voice follow-ups, and an Android live-mode UI. Automatic hazard avoidance, GPS routing, and identity tracking are intentionally not promised.

## Architecture

```text
Android (Kotlin / Compose)
  ├─ CameraX preview + explicit capture
  ├─ Speech recognizer + Android TextToSpeech
  └─ HTTPS JSON request (JPEG base64, mode, optional question)
          │
          ▼
FastAPI service
  ├─ Request validation / size cap
  ├─ No image persistence or request-body logging
  └─ OpenAI Responses API (store=false)
          │
          ▼
Text answer → Android TextToSpeech
```

## Start backend locally

Requirements: Python 3.11+ recommended.

```bash
cd backend
python -m venv .venv
# Windows: .venv\\Scripts\\activate
source .venv/bin/activate
pip install -r requirements.txt
cp ../.env.example .env  # Windows: copy ..\\.env.example .env
# Edit .env and set OPENAI_API_KEY. Keep it private.
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Health check: `http://127.0.0.1:8000/health`

Run backend tests:

```bash
cd backend
pytest -q
```

## Android setup

1. Open the `android/` folder in Android Studio (current stable version recommended).
2. Allow Gradle to sync and install the Android SDK platform/build tools requested by the project.
3. **Wrapper note:** this archive does not include the generated Gradle wrapper scripts/JAR, and Android compilation was not available in the scaffold environment. If Android Studio cannot sync the folder as-is, create a new Empty Activity (Compose) project in Android Studio using package `com.seeforme.x` and minimum SDK 26. Keep the newly generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/` files, then copy this archive's `app/src/` and `app/build.gradle.kts` into that project and apply the root Gradle plugin versions/settings from this archive.
4. Start the backend on your computer.
5. **Android Emulator:** the default backend URL is `http://10.0.2.2:8000/`.
6. **Physical phone:** make sure both devices are on the same trusted network and set the API base URL to your computer's LAN IP, e.g. build with `-PSEEFORME_API_BASE_URL=http://192.168.1.20:8000/` once the Gradle wrapper is available. Change the sample IP. Debug builds permit HTTP for local development; release builds default to HTTPS-only.
7. Run on a device with a camera. Grant camera access. Capture a photo, choose Describe / Read text / Ask, and press Analyze. For voice input, use the microphone control.

The backend defaults to `OPENAI_MODEL=gpt-4.1-mini`, a vision-capable model; change it in the backend environment if you use another model available to your API project. The Android project points at the emulator URL by default. Do not ship a build using a local development URL.


## Advanced perception checkpoint (backend preview)

The backend now includes an **optional** YOLO object-perception endpoint. This is a foundation for live mode, not the finished live assistant:

- `POST /v1/perception` accepts a JPEG frame plus a random client-generated `session_id`.
- Returns common object labels, confidence values, normalized bounding boxes, and best-effort track IDs across nearby frames.
- Tracking state is in memory, expires after inactivity, and is local to one backend process. IDs can switch or reset.
- Android now has an explicit **Start live mode / Stop live mode** control that samples a frame roughly every 2.5 seconds and sends it to this endpoint until stopped. Frame requests are sequential; labels are spoken only when the summary changes.
- The endpoint does not estimate real-world distance or determine that a path is clear. Do not use these labels for collision avoidance or independent mobility.

To enable it locally, from `backend/` after installing the normal requirements:

```bash
pip install -r requirements-perception.txt
# Optional: set YOLO_MODEL_PATH to a local compatible Ultralytics model file.
# By default, Ultralytics attempts to use yolov8n.pt and may download weights on first load.
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Example request body (use actual base64 JPEG data):

```json
{
  "image_base64": "<base64-encoded-jpeg>",
  "session_id": "a-random-session-id-created-by-the-client"
}
```

The endpoint is available at `POST /v1/perception`. Keep the API on a trusted development network; production use needs authentication, rate limiting, HTTPS, resource limits, and an explicit model-download/deployment plan.

## Build roadmap for the full concept

1. **Foundation (current):** Android camera capture, vision Q&A, OCR-style text reading, Hindi/English prompt selection, voice input/output, FastAPI and tests.
2. **Perception (backend preview):** YOLO boxes and approximate short-lived track IDs across supplied frames.
3. **Live mode (prototype):** sampled CameraX captures and explicit start/stop are implemented; still needs device tests, network cancellation review, battery/thermal testing, and tuning for frame rate.
4. **Visual memory (prototype):** opt-in text-only notes, bounded to 8 observations per session and 15 minutes, with a clear control; held in backend process memory only.
5. **Contextual conversation (prototype):** follow-up questions can use prior same-session notes as tentative context; this still needs real-device integration testing.
6. **Hardening:** authentication, rate limits, deployment secrets, HTTPS, device testing, TalkBack, low-network behavior, privacy review, and a documented demo.

Avoid claiming the system detects every obstacle. Do not rely on it for street crossing, stairs, traffic, or collision avoidance; retain a cane, guide dog, and other trusted mobility supports.

## Privacy model

- The camera is used only after the user grants permission.
- Photos are written temporarily to app cache for the explicit analyze flow and are not intentionally retained after process use; Android may preserve cache until it reclaims it. Future work should explicitly delete each captured file after the request completes and add a user-visible privacy control.
- The backend does not save the image or response to a database and does not log request bodies.
- Image bytes are sent to the configured model provider for analysis. Review provider data controls and your organization's policies before real-world use.
- Speech recognition is handed off to the Android recognizer; its on-device/cloud behavior depends on the installed recognition service and settings.
- Never put `OPENAI_API_KEY` in the Android app.

## Day-by-day plan (1–2 weeks)

- Day 1: project opens, backend health and API tests pass.
- Day 2–3: CameraX capture and backend request wired together.
- Day 4: response rendering and spoken output.
- Day 5: speech input, Hindi/English UX, errors and loading states.
- Day 6–7: device testing, screen-reader checks, low-network handling, privacy cleanup.
- Week 2 if available: quality improvements, curated test set, APK build, demo script, and safety review.

## Definition of done for MVP

- [ ] A fresh install requests camera permission only when needed.
- [ ] User can capture an image and select one analysis mode.
- [ ] API key remains on the backend.
- [ ] Invalid input and provider/network errors produce understandable UI messages.
- [ ] Answer can be spoken and stopped immediately.
- [ ] No image is retained by the backend.
- [ ] Tested on at least one physical Android device, including TalkBack/manual accessibility checks.
- [ ] README and demo explicitly disclose that outputs can be wrong and the app is not a navigation safety device.

## Technical references

- Android CameraX: https://developer.android.com/media/camera/camerax
- OpenAI image/vision inputs: https://developers.openai.com/api/docs/guides/images-vision
- OpenAI Responses API: https://developers.openai.com/api/reference/cli/resources/responses/methods/create

## Build verification status

- Backend test workflow: passed in GitHub Actions.
- Android debug APK: **built successfully** in GitHub Actions after fixing the debug manifest cleartext override.
- Download the APK from the [Android debug APK workflow run](https://github.com/Kartik94258/SeeForMeX/actions/runs/40579866494), then open the **Artifacts** section and download `seeforme-x-debug-apk`.
- This is a debug build configured for the Android emulator by default (`http://10.0.2.2:8000/`). For a physical phone, rebuild with the backend LAN/HTTPS URL configured. Do not use a public, untrusted network for the local HTTP debug endpoint.
- Build success confirms Android compilation/package creation; it does **not** confirm real-device behaviour. Camera capture, backend connectivity, voice input/output, TalkBack accessibility, and privacy flows still require device testing.
