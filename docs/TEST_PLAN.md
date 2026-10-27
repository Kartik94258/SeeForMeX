# MVP test plan

## Backend automated checks
- Health endpoint responds without a provider key.
- Malformed Base64 is rejected before any provider request.
- Non-JPEG data is rejected.
- Ask mode requires a non-empty question.
- A missing API key returns a safe configuration error.
- Hindi mode changes the response-language instruction.
- Read-text prompt asks the model to report uncertainty rather than guessing.

Run: cd backend && pytest -q

## Android manual checks
| Scenario | Expected result |
|---|---|
| Fresh install | Camera is not opened before permission grant |
| Deny camera permission | Clear explanation without crash |
| Capture photo | One still photo; no continuous recording or automatic upload |
| Describe | Short description is shown and spoken |
| Read text | Unclear text is flagged, not reconstructed |
| Ask with blank question | Request is blocked with helpful prompt |
| Hindi/English toggle | Speech input/output and model instruction change |
| Backend offline | Clear error; app remains usable and can retry |
| Stop speech | Text-to-speech stops promptly |
| Retake | Previous cached capture is deleted and answer cleared |
| TalkBack | Controls have labels and logical reading order |
| Font scaling | Main actions remain reachable by scrolling |

## Accessibility user testing
Test with people who are blind or have low vision before a public pilot. Ask participants to complete everyday tasks without sighted guidance, record failures and confusing phrasing, and let them stop narration. Do not test hazard claims by asking participants to navigate unsafe environments.

## Release gates
- Configure an HTTPS API endpoint.
- Disable cleartext traffic in release.
- Add server-side authentication, rate limiting, secret management, and abuse controls before public deployment.
- Verify provider data controls and update the privacy notice.
- Conduct security and accessibility reviews.
