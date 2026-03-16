package com.cyberraksha.guardian;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class SafeSearchActivity extends AppCompatActivity {

    private TextView tvStatus, tvResultIcon, tvResultTitle, tvResultDesc;
    private CardView cardResult;
    private TextInputEditText etUrlInput;
    private MaterialButton btnCheckUrl, btnEnableSafeSearch;

    private static final Set<String> SAFE_DOMAINS = new HashSet<>(Arrays.asList(
        "google.com","youtube.com","facebook.com","instagram.com","whatsapp.com",
        "linkedin.com","gmail.com","twitter.com","x.com","github.com","android.com",
        "bing.com","yahoo.com","microsoft.com","apple.com","wikipedia.org",
        "duckduckgo.com","amazon.com","flipkart.com","paytm.com","phonepe.com",
        "sbi.co.in","hdfcbank.com","icicibank.com","irctc.co.in","zomato.com","swiggy.com"
    ));

    private AiService aiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_safe_search);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        new WindowInsetsControllerCompat(w, w.getDecorView()).setAppearanceLightStatusBars(true);

        MaterialToolbar toolbar = findViewById(R.id.safeSearchToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvStatus = findViewById(R.id.tvStatus);
        tvResultIcon = findViewById(R.id.tvResultIcon);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultDesc = findViewById(R.id.tvResultDesc);
        cardResult = findViewById(R.id.cardResult);
        etUrlInput = findViewById(R.id.etUrlInput);
        btnCheckUrl = findViewById(R.id.btnCheckUrl);
        btnEnableSafeSearch = findViewById(R.id.btnEnableSafeSearch);

        // Build AI service
        aiService = new Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(AiService.class);

        btnCheckUrl.setOnClickListener(v -> checkUrlManually());

        btnEnableSafeSearch.setOnClickListener(v -> {
            if (isAccessibilityEnabled()) {
                // Test the warning system
                testWarningOverlay();
            } else {
                Toast.makeText(this, "Enable CyberRaksha in Accessibility settings", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAccessibilityEnabled()) {
            tvStatus.setText("AI Shield Active ✅");
            btnEnableSafeSearch.setText("✅ Safe Search Enabled");
            btnEnableSafeSearch.setEnabled(false);
        } else {
            tvStatus.setText("Tap below to enable");
            btnEnableSafeSearch.setText("Enable Safe Search");
            btnEnableSafeSearch.setEnabled(true);
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo s : services) {
            if (s.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) return true;
        }
        return false;
    }

    private void checkUrlManually() {
        String url = etUrlInput.getText() != null ? etUrlInput.getText().toString().trim().toLowerCase() : "";
        if (url.isEmpty()) { Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show(); return; }
        if (!url.startsWith("http")) url = "https://" + url;

        btnCheckUrl.setText("Checking...");
        btnCheckUrl.setEnabled(false);
        cardResult.setVisibility(View.GONE);

        final String finalUrl = url;
        String clean = url.replace("https://","").replace("http://","").replace("www.","");

        // Check safe domains first
        for (String s : SAFE_DOMAINS) {
            if (clean.startsWith(s) || clean.contains("."+s)) {
                showResult("✅", "Safe Website", "This is a trusted and well-known domain.", "#34C759");
                return;
            }
        }

        // Check malicious keywords
        if (clean.contains("mod-apk")||clean.contains("cracked")||clean.contains("nulled")) { showResult("☣️","Malware Risk","This URL shows signs of malicious software distribution.","#FF3B30"); return; }
        if (clean.contains("verify-account")||clean.contains("update-kyc")||clean.contains("aadhar-verify")||clean.contains("banking-alert")) { showResult("🎣","Phishing Site","This URL looks like a phishing attempt to steal credentials.","#FF3B30"); return; }
        if (clean.contains("guaranteed-profit")||clean.contains("crypto-earn")||clean.contains("doubler")) { showResult("💰","Scam Site","This URL shows signs of financial fraud.","#FF9500"); return; }

        // Ask AI
        List<AiMsg> msgs = new ArrayList<>();
        msgs.add(new AiMsg("user", "Is this URL safe for an Indian internet user?\nURL: '" + finalUrl + "'\nReply ONLY:\nSAFE\nor\nDANGEROUS | one line reason"));
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.URL_CHECK);
        aiService.complete(ApiKeyManager.getBearerToken(ApiKeyManager.UseCase.URL_CHECK), new AiReq(config.modelId, msgs))
            .enqueue(new Callback<AiResp>() {
                @Override public void onResponse(@NonNull Call<AiResp> call, @NonNull Response<AiResp> r) {
                    if (!r.isSuccessful()||r.body()==null||r.body().choices==null||r.body().choices.isEmpty()) {
                        showResult("⚠️","Unknown","Could not verify this URL. Proceed with caution.","#FF9500"); return;
                    }
                    String res = r.body().choices.get(0).message.content.trim().toUpperCase();
                    if (res.startsWith("DANGEROUS")) {
                        String reason = res.contains("|") ? res.substring(res.indexOf("|")+1).trim() : "Flagged as potentially unsafe.";
                        reason = reason.substring(0,1).toUpperCase()+reason.substring(1).toLowerCase();
                        showResult("🚨","Dangerous Site", reason,"#FF3B30");
                    } else {
                        showResult("✅","Safe Website","No threats detected by AI analysis.","#34C759");
                    }
                }
                @Override public void onFailure(@NonNull Call<AiResp> call, @NonNull Throwable t) {
                    showResult("⚠️","Unknown","Could not reach AI. Check your internet connection.","#FF9500");
                }
            });
    }

    private void testWarningOverlay() {
        // Send broadcast to test the warning popup
        Intent testIntent = new Intent("com.cyberraksha.guardian.TEST_WARNING");
        testIntent.putExtra("url", "verify-account-test.com");
        testIntent.putExtra("title", "🎣 Phishing Site");
        testIntent.putExtra("reason", "This is a TEST warning to verify Safe Search is working!");
        sendBroadcast(testIntent);
        Toast.makeText(this, "Test warning sent! Check for popup.", Toast.LENGTH_SHORT).show();
    }

    private void showResult(String icon, String title, String desc, String color) {
        new Handler(Looper.getMainLooper()).post(() -> {
            btnCheckUrl.setText("🔍 Check This URL");
            btnCheckUrl.setEnabled(true);
            tvResultIcon.setText(icon);
            tvResultTitle.setText(title);
            tvResultTitle.setTextColor(Color.parseColor(color));
            tvResultDesc.setText(desc);
            cardResult.setVisibility(View.VISIBLE);
        });
    }

    interface AiService { @POST("chat/completions") Call<AiResp> complete(@Header("Authorization") String auth, @Body AiReq body); }
    static class AiReq { String model; List<AiMsg> messages; AiReq(String m, List<AiMsg> ms){model=m;messages=ms;} }
    static class AiMsg { String role,content; AiMsg(String r,String c){role=r;content=c;} }
    static class AiResp { List<Choice> choices; static class Choice { AiMsg message; } }
}
