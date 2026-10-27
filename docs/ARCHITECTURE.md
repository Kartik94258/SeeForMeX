# Architecture decisions (ADR-001)

## Native Android for the MVP
Use Kotlin, Jetpack Compose, CameraX, and Android TextToSpeech. The app captures a still image only after a user action; it does not stream continuously.

## Server-side provider key
The Android app never receives the OpenAI API key. FastAPI validates request shape and size before making a provider call. Production deployment needs HTTPS, authentication/rate limiting, secret management, and abuse protection.

## Stateless inference
No database is used in the first milestone. The request sets store=false, and the application itself does not persist image input or output. Provider-side handling depends on the account's settings and policies; this is not a claim of zero retention by the provider.

## Vision response safety
The prompt tells the model to state uncertainty, not to present image interpretation as guaranteed obstacle clearance or medical advice, and to avoid identifying people.

## Advanced perception preview

The optional `/v1/perception` route runs Ultralytics YOLO when `backend/requirements-perception.txt` is installed. It returns normalized boxes and best-effort, short-lived track IDs keyed by a random session ID. The tracker is in-process memory only and is not shared between workers or restarts. The Android app does not yet continuously upload camera frames to this route.

This endpoint labels objects; it does not estimate calibrated distance, determine traversability, or provide collision warnings. Do not translate object labels into a statement that the path is safe.

## Not in first release
- No navigation commands or turn-by-turn instructions.
- No claim that estimated depth is a collision sensor.
- No persistent visual memory; a future implementation should be opt-in, text-only by default, time-bounded, and explicitly clearable.
- No continuous camera recording.

## Next architecture evolution
Once capture/analyze/speak is stable, consider optional on-device OCR, local short-lived notes with timestamps, request cancellation, server auth, metrics without sensitive payloads, and device-level accessibility tests.
