# Flow architecture

- `MainActivity`: first-run setup, permissions, settings, microphone test.
- `OverlayService`: foreground service, permission-gated `TYPE_APPLICATION_OVERLAY`, keyboard polling, speech lifecycle, overlay positioning, fallback notification.
- `OverlayPanel`: custom Canvas UI for the idle bubble and recording panel. `onRmsChanged` from `SpeechRecognizer` drives the waveform.
- `InputAccessibilityService`: only keyboard detection, focused editable lookup, and cursor insertion. No click automation, screenshots, or content persistence.
- `PromptEnhancer`: offline deterministic text cleanup/enhancement. The app can work without an API key or network.
- `CopyPromptReceiver`: one-tap clipboard fallback from the notification.

The overlay is deliberately not focusable, so tapping it does not steal focus from the original text field. The service snapshots the active field through the current accessibility root at save time and refuses to insert when no editable input is focused.
