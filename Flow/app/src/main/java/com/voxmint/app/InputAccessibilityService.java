package com.voxmint.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.Bundle;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/** Minimal, purpose-limited bridge for keyboard visibility and cursor insertion. */
public class InputAccessibilityService extends AccessibilityService {
    private static volatile InputAccessibilityService instance;
    private volatile String lastFocusedPackage;
    private volatile String lastFocusedViewId;

    public static InputAccessibilityService getInstance() { return instance; }

    @Override public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source != null && source.isEditable()) {
            lastFocusedPackage = source.getPackageName() == null ? null : source.getPackageName().toString();
            lastFocusedViewId = source.getViewIdResourceName();
        }
        if (source != null) source.recycle();
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public boolean isKeyboardVisible() {
        try {
            for (AccessibilityWindowInfo window : getWindows()) {
                if (window != null && window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD && window.isVisible()) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }


    /** Stable-enough identity for the field that was focused when capture began. */
    public String currentFocusToken() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo node = null;
        try { node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT); } catch (Throwable ignored) { }
        if (node == null || !node.isEditable()) { if (node != null) node.recycle(); root.recycle(); return null; }
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        String token = String.valueOf(node.getPackageName()) + "|" + String.valueOf(node.getViewIdResourceName()) + "|" + String.valueOf(node.getClassName()) + "|" + bounds.toShortString();
        node.recycle(); root.recycle();
        return token;
    }

    public boolean isSameFocusedField(String token) {
        return token != null && token.equals(currentFocusToken());
    }

    /** Inserts at the current cursor only if a focused editable field still exists. */
    public boolean insertAtCursor(String prompt) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo node = null;
        try { node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT); } catch (Throwable ignored) { }
        if (node == null || !node.isEditable()) { if (node != null) node.recycle(); root.recycle(); return false; }
        CharSequence current = node.getText();
        String original = current == null ? "" : current.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || end < 0 || start > original.length() || end > original.length()) {
            start = end = original.length();
        }
        String updated = original.substring(0, Math.min(start, end)) + prompt + original.substring(Math.max(start, end));
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
        boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        node.recycle(); root.recycle();
        return success;
    }

}
