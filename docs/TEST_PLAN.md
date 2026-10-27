# MVP and advanced prototype test plan

## Backend automated checks
- Health endpoint responds without a provider key.
- Malformed Base64 is rejected before any provider request.
- Non-JPEG data is rejected.
- Ask mode requires a non-empty question.
- A missing API key returns a safe configuration error.
- Hindi mode changes the response-language instruction.
- Read-text prompt asks the model to report uncertainty rather than guessing.
- Perception endpoint rejects malformed frames and requires a session ID.
- Tracking IDs remain stable for small adjacent-frame movements in controlled tests.
- Temporary memory is off by default, bounded, clearable, and included only for the same session when enabled.

Run: cd backend && pytest -q

## Android manual checks
| Scenario | Expected result |
|---|---|
| Fresh install | Camera is not opened before permission grant |
| Deny camera permission | Clear explanation without crash |
| Photo mode | One still photo; upload occurs only after Analyze is pressed |
| Start live mode | Sampled frames go to the configured backend until Stop is pressed |
| Stop live mode | Frame sampling ends; verify that no new frames arrive |
| Optional YOLO dependency missing | Clear setup error; regular photo Q&A still works |
| YOLO perception | Labels and approximate track IDs appear; do not interpret missing labels as an empty/clear path |
| Live response | Spoken summary changes only when label summary changes |
| Describe | Short description is shown and spoken |
| Read text | Unclear text is flagged, not reconstructed |
| Ask with blank question | Request is blocked with helpful prompt |
| Hindi/English toggle | Speech input/output and model instruction change |
| Backend offline | Clear error; app remains usable and can retry |
| Stop speech | Text-to-speech stops promptly |
| Retake | Previous cached capture is deleted and answer cleared |
| Memory default | Memory is off on a fresh app session |
| Enable memory | Text-only AI answers may inform same-session follow-up questions |
| Clear memory | Server removes notes for this session and reports success |
| Server restart | Temporary memory is lost |
| Leave live mode running | Stop it explicitly; check camera/network activity stops and temporary frame files are removed |
| TalkBack | Controls have labels and logical reading order |
| Font scaling | Main actions remain reachable by scrolling |

## Accessibility user testing
Test with people who are blind or have low vision before a public pilot. Ask participants to complete everyday tasks without sighted guidance, record failures and confusing phrasing, and let them stop narration. Do not test hazard claims by asking participants to navigate unsafe environments.

## Safety, privacy, and performance checks
- Test live mode only in a controlled, stationary environment; do not walk into traffic/stairs to test it.
- Verify repeated/false object detections and ID switches are surfaced as uncertain, never as a guarantee of clearance.
- Measure CPU, battery, thermal behavior, network use, and latency on a physical device before increasing frame rate.
- Confirm the UI clearly indicates when camera frames are being sent to the configured backend.

## Release gates
- Configure an HTTPS API endpoint.
- Disable cleartext traffic in release.
- Add server-side authentication, rate limiting, secret management, and abuse controls before public deployment.
- Verify provider data controls and update the privacy notice.
- Conduct security and accessibility reviews.
