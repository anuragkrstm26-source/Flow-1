# Flow — Android voice → prompt assistant

Flow is an original, privacy-forward Android implementation of a floating voice-to-prompt workflow. It is not a WhisperFlow clone: it uses its own name, palette, layout, and dependency-free Android Views.

## Implemented flow

1. Onboarding explains microphone, overlay, and the purpose-limited Accessibility Service.
2. A foreground overlay service polls the Accessibility Service's interactive windows and shows the bubble **only when an `TYPE_INPUT_METHOD` window is visible**.
3. The bubble is movable and idle at 50% opacity. It expands into a recording panel with live RMS-driven waveform animation, timer, cancel, and save.
4. Android `SpeechRecognizer` provides English speech-to-text. No audio file is retained by Flow.
5. `PromptEnhancer` runs locally and deterministically: removes common filler words, preserves named details/numbers/technical terms, and adds a structured instruction wrapper when enhancement is enabled.
6. The Accessibility Service inserts at the current cursor in the focused editable field using `ACTION_SET_TEXT`. It never runs unrelated actions.
7. If there is no focused editable field or insertion is rejected, a notification offers one-tap Copy.
8. Keyboard close cancels any active capture and removes the overlay immediately.

## Build

Open this folder in Android Studio (Hedgehog or newer) and let Android Studio install the Android SDK / Gradle wrapper requested by the project. Build with:

```bash
./gradlew assembleDebug
```

This environment did not include an Android SDK or Gradle executable, so the source project was generated and statically reviewed but not compiled here.

## First-run test plan

- Install on Android 8.0+.
- Grant microphone, overlay, and **Flow input access** in Settings.
- Open any text field and verify the bubble appears only after the keyboard is visible.
- Hide the keyboard and verify the bubble is gone.
- Tap the bubble, speak a sentence, verify the status and waveform, then tap ✓.
- Verify insertion occurs at the cursor in the original field.
- Move focus to a non-editable view or dismiss the keyboard before saving; verify Copy notification fallback.
- Deny each permission and verify the main screen explains the missing capability.

## Privacy / platform notes

- Microphone is activated only after the user taps the bubble.
- The overlay service is foreground so Android can show the microphone-sensitive background operation.
- Android's selected speech recognition provider may process audio according to the device/provider privacy settings; this is stated in the in-app note.
- The Accessibility Service can read the focused editable node at insertion time because Android does not provide a general cross-app cursor-insertion API. Flow does not store that content or use the service for unrelated automation.
- Some secure or custom text fields reject `ACTION_SET_TEXT`; the notification fallback is intentional.
