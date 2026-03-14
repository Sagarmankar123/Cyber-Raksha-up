package com.avish.sheidhero;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Patterns;
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
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class SafeSearchService extends AccessibilityService {

    private static final String TAG = "SafeSearchService";
    private static final String CHANNEL_ID = "SafeSearchAlerts";
    private static final int URL_THREAT_NOTIFICATION_ID = 2003;
    
    private OpenAIService openAIService;
    private WindowManager windowManager;
    private View popupView;
    
    private final Set<String> safeDomains = new HashSet<>(Arrays.asList(
            "google.com", "youtube.com", "facebook.com", "instagram.com", 
            "whatsapp.com", "linkedin.com", "gmail.com", "twitter.com", "github.com",
            "android.com", "bing.com", "yahoo.com", "cyberraksha.com", "microsoft.com", "apple.com",
            "wikipedia.org", "duckduckgo.com"
    ));

    private long lastCheckTime = 0;
    private String currentUrl = "";

    private static final Set<String> FRAUD_KEYWORDS = new HashSet<>(Arrays.asList("invest", "profit", "guaranteed", "ponzi", "pyramid", "deposit", "referral", "earning", "win-money", "doubler", "crypto-earn"));
    private static final Set<String> MALWARE_KEYWORDS = new HashSet<>(Arrays.asList("apk", "spyware", "ransomware", "trojan", "cracked", "mod-apk", "free-download", "keygen", "patcher", "malware"));
    private static final Set<String> ADULT_KEYWORDS = new HashSet<>(Arrays.asList("porn", "xvideos", "sex", "adult", "explicit", "nude", "casino", "betting"));

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "🛡️ Safe Search Service Connected");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        openAIService = retrofit.create(OpenAIService.class);
        
        createNotificationChannel();
        Toast.makeText(this, "🛡️ CyberRaksha Shield Active", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        CharSequence packageName = event.getPackageName();
        if (packageName != null && packageName.toString().equals(getPackageName())) {
            return;
        }

        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                if (scanNodeFast(source)) {
                    source.recycle();
                    return; 
                }
                source.recycle();
            }

            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                scanNodeFast(rootNode);
                rootNode.recycle();
            }
        }
    }

    private boolean scanNodeFast(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String viewId = node.getViewIdResourceName();
        if (viewId != null && (viewId.contains("url_bar") || viewId.contains("location_bar") || viewId.contains("address_bar") || viewId.contains("url_edit_text"))) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                processUrl(text.toString());
                return true; 
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (scanNodeFast(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        
        CharSequence text = node.getText();
        if (text != null && text.length() > 3) {
            String url = extractUrlFromText(text.toString());
            if (url != null) {
                processUrl(url);
                return true;
            }
        }

        return false;
    }

    private String extractUrlFromText(String input) {
        if (input == null) return null;
        Matcher matcher = Patterns.WEB_URL.matcher(input.trim());
        if (matcher.find()) return matcher.group();
        return null;
    }

    private void processUrl(String url) {
        String processedUrl = url.toLowerCase().trim();
        if (processedUrl.isEmpty() || processedUrl.startsWith("content://") || processedUrl.startsWith("chrome://")) return;
        
        String cleanUrl = processedUrl.replace("http://", "").replace("https://", "").replace("www.", "");

        long currentTime = System.currentTimeMillis();
        if (cleanUrl.equals(currentUrl) && (currentTime - lastCheckTime < 1000)) return;
        
        currentUrl = cleanUrl;
        lastCheckTime = currentTime;

        if (processedUrl.contains("phish-test.com") || processedUrl.contains("unsafe-site.com")) {
            showThreatPopup(processedUrl, "DANGEROUS", getString(R.string.threat_description_default));
            return;
        }

        for (String key : FRAUD_KEYWORDS) {
            if (processedUrl.contains(key)) {
                showThreatPopup(processedUrl, "DANGEROUS", "This website is flagged as a potential FRAUD/SCAM.");
                return;
            }
        }
        for (String key : MALWARE_KEYWORDS) {
            if (processedUrl.contains(key)) {
                showThreatPopup(processedUrl, "DANGEROUS", "This website is flagged as MALWARE or UNSAFE DOWNLOAD.");
                return;
            }
        }
        for (String key : ADULT_KEYWORDS) {
            if (processedUrl.contains(key)) {
                showThreatPopup(processedUrl, "DANGEROUS", "This website contains ADULT/EXPLICIT content.");
                return;
            }
        }

        for (String safe : safeDomains) {
            if (cleanUrl.startsWith(safe)) return; 
        }

        checkUrlWithAI(processedUrl);
    }

    private void checkUrlWithAI(String url) {
        String prompt = "Analyze URL: '" + url + "'. If Fraud/Phishing/Malware/Adult, reply: [DANGEROUS] | [REASON]. Else: [SAFE].";

        List<OpenAIRequest.Message> messages = new ArrayList<>();
        messages.add(new OpenAIRequest.Message("user", prompt));
        
        OpenAIRequest request = new OpenAIRequest("google/gemini-2.0-flash-001", messages);
        String authHeader = "Bearer " + Config.OPENROUTER_API_KEY;

        openAIService.getChatCompletion(authHeader, request).enqueue(new Callback<OpenAIResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenAIResponse> call, @NonNull Response<OpenAIResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().getChoices().isEmpty()) {
                    String result = response.body().getChoices().get(0).getMessage().getContent().trim().toUpperCase();
                    if (result.contains("DANGEROUS")) {
                        String reason = getString(R.string.threat_description_default);
                        if (result.contains("|")) reason = result.substring(result.indexOf("|") + 1).trim();
                        String finalReason = reason;
                        new Handler(Looper.getMainLooper()).post(() -> showThreatPopup(url, "DANGEROUS", finalReason));
                    }
                }
            }
            @Override public void onFailure(@NonNull Call<OpenAIResponse> call, @NonNull Throwable t) {}
        });
    }

    private void showThreatPopup(String url, String riskLevel, String reason) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (popupView != null) return; 

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;

            try {
                Context themedContext = new ContextThemeWrapper(this, R.style.Theme_CyberRaksha);
                popupView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_threat_alert, null);

                ((TextView) popupView.findViewById(R.id.tvAlertTitle)).setText(R.string.unsafe_website_blocked);
                ((TextView) popupView.findViewById(R.id.tvThreatUrl)).setText(url);
                ((TextView) popupView.findViewById(R.id.tvRiskDescription)).setText(reason);

                popupView.findViewById(R.id.btnBackToSafety).setOnClickListener(v -> {
                    // Navigate back to safe page or close tab
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    
                    // Add an extra back action just in case of redirects
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }, 200);

                    // Clear state immediately to avoid re-triggering during the back transition
                    currentUrl = "";
                    lastCheckTime = 0;
                    
                    removePopup();
                    
                    // If they are still in the browser on a dangerous page, this ensures we are out
                    Log.d(TAG, "🛡️ Go Back clicked: Executing safety navigation.");
                });

                popupView.findViewById(R.id.btnIgnore).setOnClickListener(v -> {
                    removePopup();
                });

                windowManager.addView(popupView, params);
            } catch (Exception e) {
                Log.e(TAG, "Popup Error: " + e.getMessage());
            }
        });
    }

    private void removePopup() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (popupView != null) {
                try { windowManager.removeView(popupView); } catch (Exception ignored) {}
                popupView = null;
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "CyberRaksha Alerts", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onInterrupt() {}

    interface OpenAIService {
        @POST("chat/completions")
        Call<OpenAIResponse> getChatCompletion(@Header("Authorization") String authHeader, @Body OpenAIRequest request);
    }

    static class OpenAIRequest {
        String model;
        List<Message> messages;
        OpenAIRequest(String model, List<Message> messages) { this.model = model; this.messages = messages; }
        static class Message {
            String role, content;
            Message(String role, String content) { this.role = role; this.content = content; }
        }
    }

    static class OpenAIResponse {
        List<Choice> choices;
        static class Choice {
            Message message;
            static class Message { String content; public String getContent() { return content; } }
            Message getMessage() { return message; }
        }
        List<Choice> getChoices() { return choices != null ? choices : new ArrayList<>(); }
    }
}
