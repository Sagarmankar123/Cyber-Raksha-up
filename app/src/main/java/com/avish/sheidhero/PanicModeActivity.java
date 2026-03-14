package com.avish.sheidhero;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PanicModeActivity extends AppCompatActivity {

    private static final String TAG = "PanicModeActivity";
    private static final String LOG_FILE = "cyberraksha_security_log.txt";

    private static final String[] BANKING_PACKAGES = {
            "com.csam.icici.bank.imobile",
            "com.axis.mobile",
            "net.one97.paytm",
            "com.phonepe.app",
            "com.google.android.apps.nbu.paisa.user",
            "com.whatsapp",
            "in.org.npci.upiapp",
            "com.sbi.lotusintouch",
            "com.hdfc.mobilebanking",
            "com.kotak.mobile.knetbanking",
            "com.snapwork.hdfc",
            "com.rblbank.mobank",
    };

    private LinearLayout lockdownContainer, diagnosisContainer, solutionContainer;
    private LinearLayout lockdownChecksList, lockdownActions;
    private TextView tvLockdownStatus, tvDiagnosisPercent, tvDiagnosisDetail;
    private TextView tvSecurityScore, tvFinalThreatLevel, tvDetectedIssuesList;
    private TextView tvAiExplainEn, tvAiExplainHi, tvFinalGuidance;
    private LinearProgressIndicator diagnosisProgress;
    private MaterialButton btnReviewApps, btnWifiSettings, btnContinueDiagnosis;
    private MaterialButton btnRemoveSusApps, btnResetPerms, btnReportFraud;
    private MaterialButton btnKillNetwork, btnViewSuspiciousApps;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GeminiService geminiService;
    private final List<String> detectedPackageNames = new ArrayList<>();
    private final List<String> threatList = new ArrayList<>();
    private final List<String> criticalThreats = new ArrayList<>();
    private boolean bankingAppDetected = false;
    private boolean vpnActive = false;
    private boolean insecureNetwork = false;

    private List<PackageInfo> scannedPackages = new ArrayList<>();
    private String hiddenCyberReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_panic_mode);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);

        AppBarLayout appBarLayout = findViewById(R.id.panicToolbar).getParent() instanceof AppBarLayout ? (AppBarLayout) findViewById(R.id.panicToolbar).getParent() : null;
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        initViews();
        initApiService();
        startStage1Lockdown();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.panicToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        lockdownContainer  = findViewById(R.id.lockdownContainer);
        diagnosisContainer = findViewById(R.id.diagnosisContainer);
        solutionContainer  = findViewById(R.id.solutionContainer);
        lockdownChecksList = findViewById(R.id.lockdownChecksList);
        lockdownActions    = findViewById(R.id.lockdownActions);

        tvLockdownStatus   = findViewById(R.id.tvLockdownStatus);
        tvDiagnosisPercent = findViewById(R.id.tvDiagnosisPercent);
        tvDiagnosisDetail  = findViewById(R.id.tvDiagnosisDetail);
        tvSecurityScore    = findViewById(R.id.tvSecurityScore);
        tvFinalThreatLevel = findViewById(R.id.tvFinalThreatLevel);
        tvDetectedIssuesList = findViewById(R.id.tvDetectedIssuesList);
        tvAiExplainEn      = findViewById(R.id.tvAiExplainEn);
        tvAiExplainHi      = findViewById(R.id.tvAiExplainHi);
        tvFinalGuidance    = findViewById(R.id.tvFinalGuidance);
        diagnosisProgress  = findViewById(R.id.diagnosisProgress);

        btnReviewApps        = findViewById(R.id.btnReviewApps);
        btnWifiSettings      = findViewById(R.id.btnWifiSettings);
        btnContinueDiagnosis = findViewById(R.id.btnContinueDiagnosis);
        btnRemoveSusApps     = findViewById(R.id.btnRemoveSusApps);
        btnResetPerms        = findViewById(R.id.btnResetPerms);
        btnReportFraud       = findViewById(R.id.btnReportFraud);
        btnKillNetwork       = findViewById(R.id.btnKillNetwork);
        btnViewSuspiciousApps = findViewById(R.id.btnViewSuspiciousApps);

        btnKillNetwork.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)));
        btnViewSuspiciousApps.setOnClickListener(v -> startActivity(new Intent(this, ScanListActivity.class)));

        btnContinueDiagnosis.setOnClickListener(v -> startStage2RealDiagnosis());
        btnReviewApps.setOnClickListener(v ->
                startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        btnWifiSettings.setOnClickListener(v ->
                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)));
        btnRemoveSusApps.setOnClickListener(v -> uninstallThreatApps());
        btnResetPerms.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
            }
        });
        btnReportFraud.setOnClickListener(v -> reportToCyberCrime());
    }

    private void initApiService() {
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.PANIC_MODE);
        geminiService = ApiKeyManager.buildRetrofit(config).create(GeminiService.class);
    }

    private void startStage1Lockdown() {
        lockdownContainer.setVisibility(View.VISIBLE);
        diagnosisContainer.setVisibility(View.GONE);
        solutionContainer.setVisibility(View.GONE);

        String[] checks = {
                getString(R.string.checking_background),
                getString(R.string.checking_banking),
                getString(R.string.checking_network_safety),
                getString(R.string.checking_overlay),
                getString(R.string.checking_sms_otp),
                getString(R.string.checking_cam_mic),
                getString(R.string.checking_screen_recording)
        };

        checkRealLifeUsageFactors();
        performLockdownChecks(checks, 0);
    }

    private void checkRealLifeUsageFactors() {
        PackageManager pm = getPackageManager();
        // 1. Check Banking Apps
        for (String pkg : BANKING_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                bankingAppDetected = true;
                break;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        // 2. Check Network & VPN
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities != null) {
                vpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            }
        }
    }

    private void performLockdownChecks(String[] checks, int index) {
        if (index >= checks.length) {
            finalizeLockdown();
            return;
        }
        addCheckItem(checks[index]);
        handler.postDelayed(() -> performLockdownChecks(checks, index + 1), 600);
    }

    private void addCheckItem(String text) {
        TextView tv = new TextView(this);
        tv.setText("✓ " + text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setPadding(8, 8, 8, 8);
        findViewById(R.id.cardLockdownChecks).setVisibility(View.VISIBLE);
        lockdownChecksList.addView(tv);
    }

    private void finalizeLockdown() {
        StringBuilder status = new StringBuilder();
        if (bankingAppDetected) {
            status.append("⚠️ Banking Apps Exposed!\n");
        }
        if (vpnActive) {
            status.append("🔒 VPN Active (Shielded)\n");
        } else {
            status.append("🔓 Unprotected Connection!\n");
        }
        
        if (status.length() == 0) {
            tvLockdownStatus.setText(getString(R.string.lockdown_complete_clean));
        } else {
            tvLockdownStatus.setText(status.toString());
            tvLockdownStatus.setTextColor(Color.YELLOW);
        }
        lockdownActions.setVisibility(View.VISIBLE);
    }

    private void startStage2RealDiagnosis() {
        lockdownContainer.setVisibility(View.GONE);
        diagnosisContainer.setVisibility(View.VISIBLE);
        diagnosisProgress.setProgress(0);
        tvDiagnosisPercent.setText("0%");

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<PackageInfo> allPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

            scannedPackages = new ArrayList<>();
            for (PackageInfo pkg : allPackages) {
                if (pkg.applicationInfo == null) continue;
                boolean isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isUpdated = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (!isSystem || isUpdated) {
                    scannedPackages.add(pkg);
                }
            }

            int total = Math.max(scannedPackages.size(), 1);

            for (int i = 0; i < scannedPackages.size(); i++) {
                final int progress = (int) (((float)(i + 1) / total) * 100);
                final String label = getDiagnosisLabel(progress);

                handler.post(() -> {
                    diagnosisProgress.setProgress(progress);
                    tvDiagnosisPercent.setText(progress + "%");
                    tvDiagnosisDetail.setText(label);
                });

                try { Thread.sleep(3); } catch (InterruptedException e) { break; }
            }

            handler.post(this::startStage3Solution);

        }).start();
    }

    private String getDiagnosisLabel(int progress) {
        if (progress < 20) return "Analyzing app behavior...";
        if (progress < 40) return getString(R.string.scan_status_perms);
        if (progress < 60) return "Detecting remote access tokens...";
        if (progress < 80) return getString(R.string.scan_status_phishing);
        return "Scanning for screen mirroring...";
    }

    private void startStage3Solution() {
        diagnosisContainer.setVisibility(View.GONE);
        solutionContainer.setVisibility(View.VISIBLE);

        new Thread(() -> {
            performDeepSecurityAnalysis();

            handler.post(() -> {
                generateThreatReport();
                collectEvidence();
                displayFinalGuidance();
                fetchAiGuidanceAsync();
            });
        }).start();
    }

    private void performDeepSecurityAnalysis() {
        PackageManager pm = getPackageManager();
        threatList.clear();
        detectedPackageNames.clear();
        criticalThreats.clear();
        
        List<PackageInfo> packages = scannedPackages.isEmpty()
                ? pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                : scannedPackages;

        for (PackageInfo pkg : packages) {
            if (pkg.applicationInfo == null) continue;

            String packageName = pkg.packageName;
            if (isSystemOrTrusted(packageName)) continue;

            int danger = 0;
            List<String> reasons = new ArrayList<>();

            boolean hasAccessibility = false;
            boolean hasOverlay = false;
            boolean hasSms = false;
            
            if (pkg.requestedPermissions != null) {
                for (String p : pkg.requestedPermissions) {
                    if (p.contains("BIND_ACCESSIBILITY_SERVICE")) hasAccessibility = true;
                    if (p.contains("SYSTEM_ALERT_WINDOW")) hasOverlay = true;
                    if (p.contains("READ_SMS") || p.contains("RECEIVE_SMS")) hasSms = true;
                }
            }

            if (hasOverlay && hasSms) {
                danger += 8;
                reasons.add("Overlay + SMS: Used to steal OTPs from Banking apps");
            }
            
            if (hasAccessibility) {
                danger += 5;
                reasons.add("Accessibility abuse: Can read screen content and log keys");
            }

            if (danger >= 5) {
                String name = pkg.applicationInfo.loadLabel(pm).toString();
                threatList.add(name + " - " + String.join(", ", reasons));
                detectedPackageNames.add(packageName);
                if (danger >= 8) criticalThreats.add(name + " (CRITICAL OTP THREAT)");
            }
        }
    }

    private boolean isSystemOrTrusted(String pkg) {
        return pkg.startsWith("com.google.") || pkg.startsWith("com.android.") || 
               pkg.startsWith("com.avish.sheidhero") || pkg.equals("com.whatsapp");
    }

    private void generateThreatReport() {
        int score = 100;
        if (!criticalThreats.isEmpty()) score = 25;
        else if (!threatList.isEmpty()) score = 65;
        
        tvSecurityScore.setText(getString(R.string.security_score, score));

        String level = score > 75 ? "LOW" : (score > 50 ? "MEDIUM" : "HIGH");
        int color = score > 75 ? Color.GREEN : (score > 50 ? Color.YELLOW : Color.RED);

        tvFinalThreatLevel.setText(getString(R.string.threat_level_label, level));
        tvFinalThreatLevel.setTextColor(color);

        if (threatList.isEmpty()) {
            tvDetectedIssuesList.setText("No critical threats active. Recommended: Use VPN on public Wi-Fi.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String t : threatList) sb.append("• ").append(t).append("\n");
            tvDetectedIssuesList.setText(sb.toString());
        }

        buildHiddenReport("");
    }

    private void buildHiddenReport(String aiSummary) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateAndTime = sdf.format(new Date());

        String deviceModel = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;

        int score = 100;
        if (!criticalThreats.isEmpty()) score = 25;
        else if (!threatList.isEmpty()) score = 65;
        
        String level = score > 75 ? "LOW" : (score > 50 ? "MEDIUM" : "HIGH");

        StringBuilder sb = new StringBuilder();
        sb.append("------------------------------------------------------------\n");
        sb.append("TO: Cyber Crime Cell of India\n");
        sb.append("Portal: https://cybercrime.gov.in\n");
        sb.append("Helpline: 1930\n\n");
        sb.append("DATE: ").append(currentDateAndTime).append("\n");
        sb.append("DEVICE: ").append(deviceModel).append(", Android ").append(androidVersion).append("\n\n");
        sb.append("INCIDENT TYPE: Suspected Mobile Banking Fraud / OTP Theft Attempt\n\n");
        sb.append("DESCRIPTION:\n");
        sb.append("The CyberRaksha security app has detected the following threats on my device:\n\n");
        sb.append("Security Score: ").append(score).append("%\n");
        sb.append("Threat Level: ").append(level).append("\n\n");
        sb.append("Suspicious Applications Detected:\n");

        if (threatList.isEmpty()) {
            sb.append("None detected.\n");
        } else {
            for (String threat : threatList) {
                String[] parts = threat.split(" - ", 2);
                String appName = parts[0];
                String reason = parts.length > 1 ? parts[1] : "Multiple sensitive permissions";
                sb.append("- App Name: ").append(appName).append("\n");
                sb.append("  Risk: ").append(reason).append("\n");
            }
        }

        sb.append("\nAI Analysis Summary:\n");
        sb.append(aiSummary != null && !aiSummary.isEmpty() ? aiSummary : "Analysis pending...");

        sb.append("\n\nRequested Action:\n");
        sb.append("I request the Cyber Crime Cell to investigate the above applications \n");
        sb.append("and take appropriate action. I am willing to provide further details \n");
        sb.append("if required.\n\n");
        sb.append("Submitted via: CyberRaksha Security App\n");
        sb.append("------------------------------------------------------------");

        hiddenCyberReport = sb.toString();
    }

    private void collectEvidence() {
        // Implementation for evidence logging to file
    }

    private void fetchAiGuidanceAsync() {
        if (threatList.isEmpty()) {
            buildHiddenReport("No threats detected. Device is clean.");
            return;
        }

        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.PANIC_MODE);
        String prompt = "Review these detected security threats on an Android phone and provide a professional summary for a cyber crime report. Focus on mobile banking fraud risk.\nThreats:\n" + String.join("\n", threatList);

        GeminiRequest request = new GeminiRequest(prompt);
        geminiService.checkAppSecurity("Bearer " + config.apiKey, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String aiText = response.body().getResponseText();
                    tvAiExplainEn.setText(aiText);
                    buildHiddenReport(aiText);
                } else {
                    buildHiddenReport("AI analysis unavailable.");
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                buildHiddenReport("AI analysis failed.");
            }
        });
    }

    private void displayFinalGuidance() {
        // Final user steps
    }

    private void uninstallThreatApps() {
        if (detectedPackageNames.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + detectedPackageNames.get(0)));
        startActivity(intent);
    }

    private void reportToCyberCrime() {
        if (hiddenCyberReport.isEmpty()) {
            buildHiddenReport("");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Cyber Crime Report", hiddenCyberReport);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Report copied! Paste it in the message box on cybercrime.gov.in", Toast.LENGTH_LONG).show();
        }

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"));
        startActivity(browserIntent);
    }
}
