package com.cyberraksha.guardian;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import java.util.*;
import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.*;

public class SafeSearchService extends AccessibilityService {
    private static final String TAG = "SafeSearchService";

    // ONLY these packages trigger scanning - expanded list
    private static final Set<String> BROWSERS = new HashSet<>(Arrays.asList(
        "com.android.chrome","com.chrome.beta","com.chrome.dev","com.chrome.canary",
        "org.mozilla.firefox","org.mozilla.firefox_beta","org.mozilla.fenix","org.mozilla.focus",
        "com.microsoft.emmx","com.microsoft.emmx.beta",
        "com.opera.browser","com.opera.browser.beta","com.opera.mini.native",
        "com.brave.browser","com.brave.browser_beta","com.brave.browser_nightly",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser","com.sec.android.app.sbrowser.beta",
        "com.UCMobile.intl","com.uc.browser.en","com.UCMobile",
        "org.mozilla.focus","com.vivaldi.browser","com.kiwibrowser.browser",
        "com.android.browser","com.google.android.apps.chrome","com.google.android.apps.chrome_dev",
        "com.yandex.browser","com.coccoc.trinhduyet","com.hsv.freeadblocker.browser"
    ));

    private static final Set<String> SAFE_DOMAINS = new HashSet<>(Arrays.asList(
        "google.com","youtube.com","facebook.com","instagram.com","whatsapp.com",
        "linkedin.com","gmail.com","twitter.com","x.com","github.com","android.com",
        "bing.com","yahoo.com","microsoft.com","apple.com","wikipedia.org",
        "duckduckgo.com","amazon.com","flipkart.com","paytm.com","phonepe.com",
        "sbi.co.in","hdfcbank.com","icicibank.com","irctc.co.in","zomato.com","swiggy.com"
    ));

    private static final Set<String> PHISHING = new HashSet<>(Arrays.asList(
        "verify-account","update-kyc","aadhar-verify","pan-update","sbi-alert",
        "hdfc-secure","paytm-kyc","account-suspended","banking-alert","secure-login-update"
    ));
    private static final Set<String> FRAUD = new HashSet<>(Arrays.asList(
        "guaranteed-profit","ponzi","crypto-earn","doubler","win-money","free-bitcoin","pyramid-scheme"
    ));
    private static final Set<String> MALWARE = new HashSet<>(Arrays.asList(
        "mod-apk","modapk","cracked-apk","spyware","ransomware","trojan","keygen","nulled","warez",
        "getmodsapk","modded","modapk","hacked","unlocked","premium-mod"
    ));
    private static final Set<String> ADULT = new HashSet<>(Arrays.asList(
        "xvideos.com","pornhub.com","xnxx.com","xhamster.com"
    ));

    private OpenAIService aiService;
    private WindowManager windowManager;
    private View popupView;
    private String lastUrl = "";
    private long lastCheckMs = 0;
    private static final long COOLDOWN = 5_000L; // 5 seconds instead of 30
    private BroadcastReceiver testReceiver;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        aiService = new retrofit2.Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(OpenAIService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("SafeSearch","CyberRaksha Alerts",NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        
        // Register test receiver
        testReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String url = intent.getStringExtra("url");
                String title = intent.getStringExtra("title");
                String reason = intent.getStringExtra("reason");
                if (url != null && title != null && reason != null) {
                    warn(url, title, reason);
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.cyberraksha.guardian.TEST_WARNING");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(testReceiver, filter);
        }
        
        Toast.makeText(this,"🛡️ CyberRaksha Shield Active",Toast.LENGTH_SHORT).show();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        int eventType = event.getEventType();
        
        Log.d(TAG, "Event from: " + pkg + " type: " + eventType);

        // V2: UPI Guardian — runs always, but deeper in Turbo mode
        boolean isTurbo = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .getBoolean("turbo_mode", false);
        UpiGuardian.check(event, getApplicationContext());

        // TURBO EXTRA: In turbo mode, also scan ALL apps for phishing
        if (isTurbo) {
            if (pkg.contains("bank") || pkg.contains("pay") || pkg.contains("finance")
                    || pkg.contains("wallet") || pkg.contains("upi")) {
                android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    String url = findUrl(root);
                    root.recycle();
                    if (url != null && !url.isEmpty()) analyze(url);
                }
            }
        }

        // GATE 1: browsers only
        boolean isBrowser = BROWSERS.contains(pkg) || 
                           pkg.contains("chrome") || pkg.contains("browser") ||
                           pkg.contains("firefox") || pkg.contains("opera");
        if (!isBrowser) {
            Log.d(TAG, "Not a browser: " + pkg);
            return;
        }
        
        Log.d(TAG, "Browser detected: " + pkg);
        
        // GATE 2: navigation or content changes
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            Log.d(TAG, "Not a relevant event type: " + eventType);
            return;
        }
        
        // GATE 3: find URL bar
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.d(TAG, "Root is null");
            return;
        }
        String url = findUrl(root); 
        root.recycle();
        if (url == null || url.isEmpty()) {
            Log.d(TAG, "No URL found in window");
            return;
        }
        
        Log.d(TAG, "URL found: " + url);
        
        // GATE 4: cooldown only for SAME URL
        long now = System.currentTimeMillis();
        if (url.equals(lastUrl) && (now - lastCheckMs) < COOLDOWN) {
            Log.d(TAG, "Cooldown active for same URL: " + url);
            return;
        }
        
        // Reset cooldown for new URL, let warn() set it when popup shows
        Log.d(TAG, "Analyzing URL: " + url);
        analyze(url);
    }

    private String findUrl(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String id = node.getViewIdResourceName();
        CharSequence text = node.getText();
        
        // Check if this node has URL-like text
        if (text != null && text.length() > 4) {
            String txt = text.toString().trim().toLowerCase();
            // URL detection: contains dot, no spaces, looks like a URL
            if (txt.contains(".") && !txt.contains(" ") && 
                (txt.startsWith("http") || txt.startsWith("www.") || 
                 (txt.contains(".") && txt.length() > 6 && !txt.startsWith("chrome://") && !txt.startsWith("content://") && !txt.startsWith("about:")))) {
                // Check if it's in an editable field or URL bar
                if (id != null && (id.contains("url") || id.contains("address") || id.contains("omnibox") ||
                                   id.contains("edit") || id.contains("text"))) {
                    Log.d(TAG, "Found URL in field: " + id + " = " + txt);
                    return txt;
                }
                // Also accept if node is editable (EditText)
                if (node.isEditable() || node.isFocusable()) {
                    Log.d(TAG, "Found URL in editable/focusable: " + txt);
                    return txt;
                }
            }
        }
        
        // Search children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) { 
                String f = findUrl(c); 
                c.recycle(); 
                if (f != null) return f; 
            }
        }
        return null;
    }

    private void analyze(String url) {
        String clean = url.replace("https://","").replace("http://","").replace("www.","");
        for (String s : SAFE_DOMAINS) { if (clean.startsWith(s)||clean.contains("."+s)) return; }
        for (String k : PHISHING) { if (url.contains(k)) { warn(url,"🎣 Phishing Site","This site may steal your banking or personal credentials."); return; } }
        for (String k : FRAUD) { if (url.contains(k)) { warn(url,"💰 Scam Site","This site shows signs of investment fraud."); return; } }
        for (String k : MALWARE) { if (url.contains(k)) { warn(url,"☣️ Malware Risk","This download may contain malicious software."); return; } }
        for (String k : ADULT) { if (url.contains(k)) { warn(url,"🔞 Adult Content","This site contains explicit adult content."); return; } }
        checkAI(url);
    }

    private void checkAI(String url) {
        List<Msg> msgs = new ArrayList<>();
        msgs.add(new Msg("user","Is this URL safe for an Indian user?\nURL: '"+url+"'\nReply ONLY:\nSAFE\nor\nDANGEROUS | reason"));
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.URL_CHECK);
        aiService.complete(ApiKeyManager.getBearerToken(ApiKeyManager.UseCase.URL_CHECK), new Req(config.modelId, msgs))
            .enqueue(new Callback<Resp>() {
                @Override public void onResponse(@NonNull Call<Resp> call, @NonNull Response<Resp> r) {
                    if (!r.isSuccessful()||r.body()==null||r.body().choices==null||r.body().choices.isEmpty()) return;
                    String res = r.body().choices.get(0).message.content.trim().toUpperCase();
                    if (res.startsWith("DANGEROUS")) {
                        String reason = res.contains("|") ? res.substring(res.indexOf("|")+1).trim() : "Flagged as unsafe by AI.";
                        reason = reason.substring(0,1).toUpperCase()+reason.substring(1).toLowerCase();
                        final String fr = reason;
                        new Handler(Looper.getMainLooper()).post(()->warn(url,"⚠️ Unsafe Site",fr));
                    }
                }
                @Override public void onFailure(@NonNull Call<Resp> call, @NonNull Throwable t) { Log.w(TAG,"AI fail: "+t.getMessage()); }
            });
    }

    private void warn(String url, String title, String reason) {
        new Handler(Looper.getMainLooper()).post(() -> {
            // Allow new warning if it's a different URL, otherwise skip if same URL already showing
            if (popupView != null && lastUrl.equals(url)) {
                Log.d(TAG, "Popup already showing for same URL: " + url);
                return;
            }
            // Remove old popup if showing different URL
            if (popupView != null) {
                removePopup();
            }
            
            Log.d(TAG, "Showing warning for: " + url);
            Toast.makeText(SafeSearchService.this, "🛡️ " + title + " detected!", Toast.LENGTH_SHORT).show();
            try {
                // TRULY MODAL - blocks interaction with background
                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                    PixelFormat.TRANSLUCENT);
                p.gravity = Gravity.CENTER;
                p.dimAmount = 0.7f; // Darken background
                Context ctx = new ContextThemeWrapper(this, R.style.Theme_CyberRaksha);
                popupView = LayoutInflater.from(ctx).inflate(R.layout.dialog_threat_alert, null);
                ((TextView)popupView.findViewById(R.id.tvAlertTitle)).setText(title);
                ((TextView)popupView.findViewById(R.id.tvThreatUrl)).setText(url);
                ((TextView)popupView.findViewById(R.id.tvRiskDescription)).setText(reason);
                popupView.findViewById(R.id.btnBackToSafety).setOnClickListener(v -> {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    new Handler(Looper.getMainLooper()).postDelayed(()->performGlobalAction(GLOBAL_ACTION_BACK),300);
                    removePopup();
                });
                popupView.findViewById(R.id.btnIgnore).setOnClickListener(v -> {
                    removePopup();
                });
                windowManager.addView(popupView, p);
                Log.d(TAG, "Popup shown successfully");
                // Set cooldown only after successfully showing
                lastUrl = url;
                lastCheckMs = System.currentTimeMillis();
            } catch (Exception e) { 
                Log.e(TAG,"popup error: "+e.getMessage(), e);
                Toast.makeText(SafeSearchService.this, "Security Alert: "+title, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void removePopup() { if (popupView!=null) { try { windowManager.removeView(popupView); } catch(Exception ignored){} popupView=null; } }
    @Override public void onInterrupt() { removePopup(); }
    @Override public void onDestroy() { 
        super.onDestroy(); 
        removePopup(); 
        if (testReceiver != null) {
            try { unregisterReceiver(testReceiver); } catch (Exception ignored) {}
        }
    }

    interface OpenAIService { @POST("chat/completions") Call<Resp> complete(@Header("Authorization") String auth, @Body Req body); }
    static class Req { String model; List<Msg> messages; Req(String m,List<Msg> ms){model=m;messages=ms;} }
    static class Msg { String role,content; Msg(String r,String c){role=r;content=c;} }
    static class Resp { List<Choice> choices; static class Choice { Msg message; } }
}
