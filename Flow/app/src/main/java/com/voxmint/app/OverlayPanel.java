package com.voxmint.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Custom-drawn overlay keeps the floating control lightweight and dependency-free. */
public class OverlayPanel extends View {
    public interface Listener { void onTap(); void onSave(); void onCancel(); void onDrag(float dx, float dy); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap logo;
    private Listener listener;
    private boolean recording;
    private float rms;
    private long startedAt;
    private float downX, downY, lastX, lastY;
    private boolean moved;

    public OverlayPanel(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_reference);
        textPaint.setTypeface(Typeface.create("sans", Typeface.BOLD));
    }
    public void setListener(Listener l) { listener = l; }
    public void setRecording(boolean value) { recording = value; startedAt = System.currentTimeMillis(); invalidate(); }
    public void setRms(float value) { rms = Math.max(0, Math.min(1, (value + 2f) / 12f)); invalidate(); }
    public boolean isRecording() { return recording; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (!recording) { drawIdle(c, w, h); return; }
        drawRecording(c, w, h);
    }

    private void drawIdle(Canvas c, float w, float h) {
        paint.setShadowLayer(dp(10), 0, dp(3), 0xAA7EE7D1);
        paint.setShader(new LinearGradient(0, 0, w, h, 0xEE252532, 0xEE101017, Shader.TileMode.CLAMP));
        c.drawCircle(w/2, h/2, Math.min(w,h)/2 - dp(2), paint);
        paint.clearShadowLayer(); paint.setShader(null);
        drawLogo(c, new RectF(dp(8), dp(8), w-dp(8), h-dp(8)));
    }

    private void drawRecording(Canvas c, float w, float h) {
        paint.setShadowLayer(dp(16), 0, dp(6), 0x887EE7D1);
        paint.setColor(0xF21A1A24); c.drawRoundRect(new RectF(0,0,w,h), dp(24), dp(24), paint); paint.clearShadowLayer();
        textPaint.setColor(Color.WHITE); textPaint.setTextSize(dp(14));
        c.drawText("LIVE CAPTURE", dp(18), dp(24), textPaint);
        textPaint.setColor(0xFF7EE7D1); textPaint.setTextSize(dp(11));
        c.drawText("English · private until you save", dp(18), dp(42), textPaint);
        paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(0xFFB6A4FF);
        float base = dp(70), left = dp(18), width = w-dp(36);
        for (int i=0;i<30;i++) {
            float x = left + i * width / 29f;
            float wave = (float)Math.sin((System.currentTimeMillis()/120.0)+i*0.7) * (dp(10)+rms*dp(28)) * (0.35f + 0.65f*(float)Math.sin(i*0.4+1));
            c.drawLine(x, base-wave, x, base+wave, paint);
        }
        textPaint.setColor(Color.WHITE); textPaint.setTextSize(dp(13));
        long seconds = Math.max(0, (System.currentTimeMillis()-startedAt)/1000);
        c.drawText("🎙  Recording  " + String.format(java.util.Locale.US, "%02d:%02d", seconds/60, seconds%60), dp(18), dp(106), textPaint);
        drawButton(c, dp(72), h-dp(38), 0xFFFF8393, "×");
        drawButton(c, w-dp(72), h-dp(38), 0xFF7EE7D1, "✓");
        postInvalidateDelayed(80);
    }

    private void drawButton(Canvas c, float x, float y, int color, String label) {
        paint.setColor(color); c.drawCircle(x,y,dp(21),paint);
        textPaint.setColor(0xFF101017); textPaint.setTextSize(dp(22)); textPaint.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, x, y+dp(8), textPaint); textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawLogo(Canvas c, RectF dst) {
        if (logo == null) return;
        Path clip = new Path(); clip.addCircle(dst.centerX(), dst.centerY(), Math.min(dst.width(),dst.height())/2, Path.Direction.CW);
        c.save(); c.clipPath(clip); c.drawBitmap(logo, null, dst, paint); c.restore();
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: downX=lastX=e.getRawX(); downY=lastY=e.getRawY(); moved=false; return true;
            case MotionEvent.ACTION_MOVE:
                float dx=e.getRawX()-lastX, dy=e.getRawY()-lastY;
                if (Math.hypot(e.getRawX()-downX,e.getRawY()-downY) > dp(7)) moved=true;
                if (listener != null) listener.onDrag(dx,dy); lastX=e.getRawX(); lastY=e.getRawY(); return true;
            case MotionEvent.ACTION_UP:
                if (!moved && listener != null) {
                    if (!recording) listener.onTap();
                    else if (e.getX() < getWidth()/2f) listener.onCancel(); else listener.onSave();
                }
                return true;
        }
        return true;
    }
}
