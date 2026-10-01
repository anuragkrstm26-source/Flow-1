package com.voxmint.app;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView micStatus, overlayStatus, accessStatus;
    private CheckBox enhance;
    private final int MIC_REQUEST=20;
    private int lavender, mint, muted, white;

    @Override public void onCreate(Bundle saved) { super.onCreate(saved); getWindow().setStatusBarColor(Color.rgb(11,11,16)); lavender=getColor(R.color.lavender); mint=getColor(R.color.mint); muted=getColor(R.color.muted); white=getColor(R.color.white); render(); }
    @Override protected void onResume() { super.onResume(); if (root != null) refreshStatuses(); }

    private void render() {
        boolean setup = getSharedPreferences("voxmint", MODE_PRIVATE).getBoolean("setup_seen", false);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(24), dp(22), dp(20)); root.setBackgroundColor(getColor(R.color.ink));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        if (!setup) renderOnboarding(); else renderHome();
    }

    private void renderOnboarding() {
        addBrand(); addTitle("Voice → Prompt", "Say what you mean. Flow turns it into a clear prompt and drops it into the field you were using.");
        addCard("Why these permissions?", "Microphone · records only after you tap the floating control.\n\nOverlay · shows the small control above your keyboard.\n\nAccessibility · used only to detect the keyboard, locate the focused text field, and insert at the cursor. No unrelated actions, no saved field contents.");
        Button mic = button("Grant microphone", v -> requestMic()); root.addView(mic);
        Button overlay = button("Allow display over other apps", v -> openOverlaySettings()); root.addView(overlay);
        Button access = button("Enable Flow input access", v -> openAccessibilitySettings()); root.addView(access);
        Button done = button("Continue", v -> { getSharedPreferences("voxmint", MODE_PRIVATE).edit().putBoolean("setup_seen",true).apply(); render(); }); root.addView(done);
        addSmall("Speech recognition uses Android's selected speech provider. Flow does not keep an audio recording.");
    }

    private void renderHome() {
        addBrand(); addTitle("Voice → Prompt", "A quiet shortcut for the text field already in focus.");
        addCard("The floating control", "The button appears only when Android reports a visible soft keyboard. Tap it, speak naturally, then save. If the keyboard closes, the overlay disappears and any active capture is safely cancelled.");
        addSection("ACCESS & PRIVACY");
        micStatus=addStatus("Microphone", false); overlayStatus=addStatus("Overlay", false); accessStatus=addStatus("Keyboard + input access", false);
        Button mic = button("Manage microphone", v -> requestMic()); root.addView(mic);
        Button overlay = button("Manage overlay", v -> openOverlaySettings()); root.addView(overlay);
        Button access = button("Manage input access", v -> openAccessibilitySettings()); root.addView(access);
        addSection("BEHAVIOUR");
        enhance = new CheckBox(this); enhance.setText("Enhance spoken instructions into structured prompts"); enhance.setTextColor(white); enhance.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{mint,muted})); enhance.setChecked(getSharedPreferences("voxmint",MODE_PRIVATE).getBoolean("enhance",true)); enhance.setOnCheckedChangeListener((b,c)->getSharedPreferences("voxmint",MODE_PRIVATE).edit().putBoolean("enhance",c).apply()); root.addView(enhance, lp(WRAP, dp(52)));
        addSpinner();
        Button test = button("Test microphone", v -> testMicrophone()); root.addView(test);
        addCard("Privacy note", "Recording is explicit and visible. Audio is not saved by Flow. Android's speech recognizer may process audio according to the provider selected on this device. When insertion is unavailable, the result stays in a notification with a one-tap Copy action.");
        refreshStatuses(); ensureOverlayService();
    }

    private void addBrand() { LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); ImageView icon=new ImageView(this); icon.setImageResource(R.drawable.logo_reference); icon.setScaleType(ImageView.ScaleType.CENTER_CROP); row.addView(icon,lp(dp(52),dp(52))); TextView name=text("FLOW",20,white); name.setLetterSpacing(.18f); name.setPadding(dp(14),0,0,0); row.addView(name,lp(WRAP,dp(60))); root.addView(row); }
    private void addTitle(String title,String subtitle) { TextView t=text(title,32,white); t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); t.setPadding(0,dp(26),0,dp(5)); root.addView(t); TextView s=text(subtitle,16,muted); s.setPadding(0,0,0,dp(22)); root.addView(s); }
    private void addSection(String title) { TextView s=text(title,11,mint); s.setLetterSpacing(.16f); s.setPadding(0,dp(18),0,dp(8)); root.addView(s); }
    private void addCard(String title,String body) { LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(15),dp(16),dp(15)); GradientDrawable bg=new GradientDrawable(); bg.setColor(getColor(R.color.panel)); bg.setCornerRadius(dp(18)); card.setBackground(bg); TextView h=text(title,16,white); h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); card.addView(h); TextView b=text(body,14,muted); b.setPadding(0,dp(8),0,0); card.addView(b); root.addView(card,lp(MATCH,WRAP,0,dp(8))); }
    private TextView addStatus(String label, boolean ok) { TextView v=text("•  "+label,15,muted); v.setPadding(dp(4),dp(6),0,dp(6)); root.addView(v); return v; }
    private void addSpinner() { Spinner spinner=new Spinner(this); String[] values={"Output language · English (US)","Output language · English (UK)"}; ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){ @Override public View getView(int p,View c,android.view.ViewGroup parent){ TextView v=(TextView)super.getView(p,c,parent);v.setTextColor(white);v.setPadding(dp(12),0,0,0);return v;} }; spinner.setAdapter(a); spinner.setSelection(getSharedPreferences("voxmint",MODE_PRIVATE).getInt("language_index",0)); spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id){ getSharedPreferences("voxmint",MODE_PRIVATE).edit().putInt("language_index",pos).putString("language",pos==1?"en-GB":"en-US").apply(); }}); root.addView(spinner,lp(MATCH,dp(52),0,dp(5))); }
    private void addSmall(String s) { TextView v=text(s,12,muted); v.setPadding(0,dp(12),0,0); root.addView(v); }

    private Button button(String label, View.OnClickListener click) { Button b=new Button(this); b.setText(label); b.setTextColor(getColor(R.color.ink)); b.setTextSize(14); b.setAllCaps(false); b.setOnClickListener(click); GradientDrawable bg=new GradientDrawable();bg.setColor(mint);bg.setCornerRadius(dp(14));b.setBackground(bg);b.setPadding(dp(14),0,dp(14),0); return b; }
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    private void requestMic(){ if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST); }
    private void openOverlaySettings(){ if(Build.VERSION.SDK_INT>=23)startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))); }
    private void openAccessibilitySettings(){ startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
    private boolean accessibilityEnabled(){String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return enabled!=null && enabled.toLowerCase().contains(getPackageName().toLowerCase()+"/"+InputAccessibilityService.class.getName().toLowerCase());}
    private void refreshStatuses(){ if(micStatus==null)return; boolean m=Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED; boolean o=Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(this); boolean a=accessibilityEnabled(); setStatus(micStatus,"Microphone",m);setStatus(overlayStatus,"Overlay",o);setStatus(accessStatus,"Keyboard + input access",a); if(m&&o&&a)ensureOverlayService(); }
    private void setStatus(TextView v,String label,boolean ok){v.setText((ok?"✓  ":"•  ")+label+(ok?"  ready":"  needs setup"));v.setTextColor(ok?mint:muted);}
    private void ensureOverlayService(){if(Build.VERSION.SDK_INT>=23 && Settings.canDrawOverlays(this) && accessibilityEnabled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){Intent i=new Intent(this,OverlayService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}}
    private void testMicrophone(){ if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestMic();return;} File file=new File(getCacheDir(),"mic-test.3gp"); MediaRecorder r=new MediaRecorder();try{r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);r.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);r.setOutputFile(file.getAbsolutePath());r.prepare();r.start();new Handler().postDelayed(()->{try{r.stop();}catch(Throwable ignored){}r.release();file.delete();Toast.makeText(this,"Microphone is working",Toast.LENGTH_SHORT).show();},700);}catch(Throwable e){try{r.release();}catch(Throwable ignored){}Toast.makeText(this,"Microphone test failed",Toast.LENGTH_SHORT).show();}}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(0,l,0,b);return p;}
    private static final int MATCH=LinearLayout.LayoutParams.MATCH_PARENT, WRAP=LinearLayout.LayoutParams.WRAP_CONTENT;
}
