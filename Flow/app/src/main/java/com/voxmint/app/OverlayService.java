package com.voxmint.app;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.WindowManager;
import java.util.ArrayList;

public class OverlayService extends Service implements OverlayPanel.Listener {
    private static final int NOTIFICATION_ID = 42;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private OverlayPanel panel;
    private Handler handler;
    private SpeechRecognizer recognizer;
    private boolean recording;
    private boolean stopRequested;
    private String latestTranscript = "";
    private String recordingFocusToken;
    private long lastKeyboardPoll;

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        startForeground(NOTIFICATION_ID, buildServiceNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler.post(pollKeyboard);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    private final Runnable pollKeyboard = new Runnable() {
        @Override public void run() {
            InputAccessibilityService access = InputAccessibilityService.getInstance();
            boolean keyboard = access != null && access.isKeyboardVisible();
            if (keyboard) showIdleIfNeeded(); else hideForKeyboardClose();
            handler.postDelayed(this, 220);
        }
    };

    private void showIdleIfNeeded() {
        if (panel != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        panel = new OverlayPanel(this); panel.setListener(this); panel.setAlpha(0f);
        params = new WindowManager.LayoutParams(dp(64), dp(64), Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getPreferences(0).getInt("x", dp(12)); params.y = getPreferences(0).getInt("y", dp(260));
        try { windowManager.addView(panel, params); panel.animate().alpha(0.5f).setDuration(180).start(); } catch (Throwable ignored) { panel=null; }
    }

    private void hideForKeyboardClose() {
        if (recording) cancelRecording();
        removePanel();
    }

    private void removePanel() {
        if (panel == null) return;
        try { windowManager.removeView(panel); } catch (Throwable ignored) { }
        panel = null;
    }

    @Override public void onTap() { if (!recording) beginRecording(); }

    private void beginRecording() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toast("Microphone permission is required"); return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("No speech recognition service is available"); return; }
        recording = true; stopRequested=false; latestTranscript="";
        InputAccessibilityService access = InputAccessibilityService.getInstance();
        recordingFocusToken = access == null ? null : access.currentFocusToken();
        if (panel != null) { panel.setRecording(true); panel.setAlpha(1f); resizePanel(dp(320), dp(150)); }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) { }
            public void onBeginningOfSpeech() { }
            public void onRmsChanged(float rmsdB) { if (panel != null) panel.setRms(rmsdB); }
            public void onBufferReceived(byte[] b) { }
            public void onEndOfSpeech() { }
            public void onError(int error) {
                if (stopRequested && latestTranscript.length() > 2) finalizePrompt();
                else if (error != SpeechRecognizer.ERROR_CLIENT) toast("Speech recognition stopped — try again");
            }
            public void onResults(Bundle results) { updateResults(results); if (stopRequested) finalizePrompt(); }
            public void onPartialResults(Bundle results) { updateResults(results); }
            public void onEvent(int type, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String language = getSharedPreferences("voxmint", MODE_PRIVATE).getString("language", "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { recognizer.startListening(intent); } catch (Throwable t) { recording=false; toast("Could not start microphone"); }
    }

    private void updateResults(Bundle results) {
        ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values != null && !values.isEmpty()) latestTranscript = values.get(0);
    }

    @Override public void onSave() {
        if (!recording) return;
        stopRequested=true;
        if (recognizer != null) { try { recognizer.stopListening(); } catch (Throwable ignored) { } }
        handler.postDelayed(() -> { if (recording && stopRequested) finalizePrompt(); }, 1100);
    }

    @Override public void onCancel() { cancelRecording(); }

    private void cancelRecording() {
        stopRequested=false;
        if (recognizer != null) { try { recognizer.cancel(); } catch (Throwable ignored) { } recognizer.destroy(); recognizer=null; }
        recording=false; latestTranscript=""; recordingFocusToken=null;
        if (panel != null) { panel.setRecording(false); panel.setAlpha(0.5f); resizePanel(dp(64), dp(64)); }
    }

    private void finalizePrompt() {
        if (!recording) return;
        String raw = latestTranscript;
        if (raw == null || PromptEnhancer.clean(raw).length() < 3) { toast("That was too short — please try again"); cancelRecording(); return; }
        String prompt = PromptEnhancer.enhance(raw, getSharedPreferences("voxmint", MODE_PRIVATE).getBoolean("enhance", true));
        if (recognizer != null) { recognizer.destroy(); recognizer=null; }
        recording=false; stopRequested=false;
        InputAccessibilityService access = InputAccessibilityService.getInstance();
        boolean inserted = access != null && access.isSameFocusedField(recordingFocusToken) && access.insertAtCursor(prompt);
        if (!inserted) showCopyNotification(prompt);
        if (panel != null) { panel.setRecording(false); panel.setAlpha(0.5f); resizePanel(dp(64), dp(64)); }
    }

    private void showCopyNotification(String prompt) {
        Intent copy = new Intent(this, CopyPromptReceiver.class).putExtra(CopyPromptReceiver.EXTRA_PROMPT, prompt);
        PendingIntent pending = PendingIntent.getBroadcast(this, 5, copy, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "voxmint")
                .setSmallIcon(R.drawable.ic_stat_mic).setContentTitle("Flow prompt ready")
                .setContentText("The focused field was unavailable. Tap Copy to paste it yourself.")
                .setStyle(new Notification.BigTextStyle().bigText(prompt)).addAction(new Notification.Action.Builder(null, "Copy", pending).build()).setAutoCancel(true).build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(43, n);
    }

    private void resizePanel(int width, int height) { if (panel == null || params == null) return; params.width=width; params.height=height; try { windowManager.updateViewLayout(panel, params); } catch (Throwable ignored) { } }
    @Override public void onDrag(float dx, float dy) { if (params == null || panel == null) return; params.x += (int)dx; params.y += (int)dy; try { windowManager.updateViewLayout(panel, params); } catch (Throwable ignored) { } getPreferences(0).edit().putInt("x",params.x).putInt("y",params.y).apply(); }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String text) { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show(); }
    private void createChannel() { if (Build.VERSION.SDK_INT >= 26) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("voxmint", "Flow", NotificationManager.IMPORTANCE_LOW)); }
    private Notification buildServiceNotification() { return new Notification.Builder(this, "voxmint").setSmallIcon(R.drawable.ic_stat_mic).setContentTitle("Flow is ready").setContentText("Floating control appears only while a keyboard is visible").setOngoing(true).build(); }
    private android.content.SharedPreferences getPreferences(int mode) { return getSharedPreferences("overlay", mode); }

    @Override public void onDestroy() { if (handler != null) handler.removeCallbacksAndMessages(null); cancelRecording(); removePanel(); super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
