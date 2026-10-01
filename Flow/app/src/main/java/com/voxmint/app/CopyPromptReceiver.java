package com.voxmint.app;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class CopyPromptReceiver extends BroadcastReceiver {
    public static final String EXTRA_PROMPT = "prompt";
    @Override public void onReceive(Context context, Intent intent) {
        String prompt = intent.getStringExtra(EXTRA_PROMPT);
        if (prompt == null || prompt.isEmpty()) return;
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("Flow prompt", prompt));
        Toast.makeText(context, "Prompt copied", Toast.LENGTH_SHORT).show();
    }
}
