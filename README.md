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

Out of scope for the first 1–2 weeks: continuous streaming, stored visual memory, GPS routing, object tracking across frames, automatic hazard avoidance, accounts, and cloud image storage.

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

The backend test suite is included and currently passes in the scaffold environment. Android compilation has not been verified in this environment because Android SDK/Gradle tooling is not installed here; Android Studio must sync dependencies and build the APK on your machine.
