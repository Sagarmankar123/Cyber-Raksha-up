package com.cyberraksha.guardian;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;


public class MainActivity extends AppCompatActivity {

    private static final int PHONE_PERMS_CODE = 201;

    // Normal mode views
    private View normalScrollView;
    private View headerSection;
    private TextView tvHealthScore;
    private TextView btnIslandVisibility;
    private MaterialButton btnOptimize, btnActivateShield;
    private CardView cardEmergencyShield, cardSecurityScan;
    private CardView cardSafeSearch, cardAppManager, cardSpaceCleanup;

    // Dynamic bar
    private LinearLayout dynamicBar;
    private TextView dynBtnNormal, dynBtnTurbo;
    private View dynStatusDot;

    // Turbo mode views
    private VideoView turboVideo;
    private CyberWebView cyberWebCanvas;
    private LinearLayout turboOverlay;
    private TextView btnExitTurbo, tvConsole;
    private TextView chipSafe, chipSuspicious, chipHighRisk;
    private View turboCardRadar, turboCardFraud, turboCardGuardian, turboCardLockdown;
    private TextView tvGlassCommunityStatus, tvGlassGuardianStatus;

    // Bottom nav
    private BottomNavigationView bottomNavigation;

    // Intelligence views (shown in bottom nav tab)
    private View intelligenceView;


    // State
    private boolean isTurboMode = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Engines
    private ThreatEngine threatEngine;
    private BehaviorService behaviorService;
    private CommunityThreatDB communityDB;
    private TurboManager turboManager;

    // Broadcast receiver
    private final BroadcastReceiver securityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;
            String action = intent.getAction();

            // APK install event → feed to BehaviorService
            if ("com.cyberraksha.guardian.APK_INSTALLED".equals(action)) {
                String pkg = intent.getStringExtra("package_name");
                int risk = intent.getIntExtra("risk_score", 0);
                if (risk > 0) {
                    appendConsole("⚠ Suspicious install: " + (pkg != null ? pkg : "?"));
                }
                refreshTurboCards();
                return;
            }

            // Turbo toggle from island
            if (DynamicBarService.ACTION_TURBO_ON.equals(action)) {
                if (!isTurboMode) activateTurboMode();
                return;
            }
            if (DynamicBarService.ACTION_TURBO_OFF.equals(action)) {
                if (isTurboMode) deactivateTurboMode();
                return;
            }

            // TurboModeEngine log lines → show in turbo console
            if (TurboModeEngine.ACTION_TURBO_LOG.equals(action)) {
                String line = intent.getStringExtra(TurboModeEngine.EXTRA_LOG_LINE);
                if (line != null) appendConsole(line);
                return;
            }

            // TurboManager status updates
            if ("com.cyberraksha.guardian.TURBO_STATUS".equals(action)) {
                String key = intent.getStringExtra("key");
                String message = intent.getStringExtra("message");
                if (message != null && isTurboMode) {
                    appendConsole(message);
                }
                return;
            }

            // Threat detected
            if ("com.cyberraksha.guardian.THREAT_DETECTED".equals(action)) {
                String type = intent.getStringExtra("type");
                String source = intent.getStringExtra("source");
                String details = intent.getStringExtra("details");
                if (isTurboMode) {
                    appendConsole("🚨 " + (type != null ? type : "Threat") + " from " + (source != null ? source : "unknown"));
                }
                refreshTurboCards();
                return;
            }

            // Intelligence feature updates → refresh IntelligenceActivity cards
            if ("com.cyberraksha.guardian.INTEL_UPDATE".equals(action)) {
                String key = intent.getStringExtra("key");
                String val = intent.getStringExtra("value");
                handleIntelUpdate(key, val);
                return;
            }

            int score = intent.getIntExtra(ResponseEngine.EXTRA_RISK_SCORE, 0);
            String reason = intent.getStringExtra(ResponseEngine.EXTRA_REASON);
            onThreatDetected(score, reason);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpamDatabase.getInstance(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        Window window = getWindow();
        new WindowInsetsControllerCompat(window, window.getDecorView())
                .setAppearanceLightStatusBars(false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        threatEngine    = ThreatEngine.getInstance(this);
        behaviorService = new BehaviorService(this);
        behaviorService.seedFromInstalledAppsIfNeeded(getPackageManager());
        communityDB     = CommunityThreatDB.getInstance(this);
        turboManager    = TurboManager.getInstance(this);

        // Check if first time — show onboarding
        boolean firstTime = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .getBoolean("onboarding_done", false);
        if (!firstTime) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }

        initViews();
        setupInsets();
        setupClickListeners();
        registerReceiver();
        checkPermissions();
        refreshScore();
    }



    @SuppressLint("UnsafeImplicitIntentLaunch")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(securityReceiver); } catch (Exception ignored) {}
        if (cyberWebCanvas != null) cyberWebCanvas.stop();
        sendBroadcast(new Intent(DynamicBarService.ACTION_TURBO_OFF));
        handler.removeCallbacksAndMessages(null);
    }

    private void initViews() {
        normalScrollView   = findViewById(R.id.normalScrollView);
        headerSection      = findViewById(R.id.headerSection);
        tvHealthScore      = findViewById(R.id.tvHealthScore);
        btnIslandVisibility = findViewById(R.id.btnIslandVisibility);
        btnOptimize        = findViewById(R.id.btnOptimize);
        btnActivateShield  = findViewById(R.id.btnActivateShield);
        cardEmergencyShield= findViewById(R.id.cardEmergencyShield);
        cardSecurityScan   = findViewById(R.id.cardSecurityScan);
        cardSafeSearch     = findViewById(R.id.cardSafeSearch);
        cardAppManager     = findViewById(R.id.cardAppManager);
        cardSpaceCleanup   = findViewById(R.id.cardSpaceCleanup);

        // Dynamic bar
        dynamicBar    = findViewById(R.id.dynamicBar);
        dynBtnNormal  = findViewById(R.id.dynBtnNormal);
        dynBtnTurbo   = findViewById(R.id.dynBtnTurbo);
        dynStatusDot  = findViewById(R.id.dynStatusDot);

        // Turbo
        turboVideo           = findViewById(R.id.turboVideo);
        cyberWebCanvas       = findViewById(R.id.cyberWebCanvas);
        turboOverlay         = findViewById(R.id.turboOverlay);
        btnExitTurbo         = findViewById(R.id.btnExitTurbo);
        tvConsole            = findViewById(R.id.tvConsole);
        chipSafe             = findViewById(R.id.chipSafe);
        chipSuspicious       = findViewById(R.id.chipSuspicious);
        chipHighRisk         = findViewById(R.id.chipHighRisk);
        turboCardRadar       = findViewById(R.id.turboCardRadar);
        turboCardFraud       = findViewById(R.id.turboCardFraud);
        turboCardGuardian    = findViewById(R.id.turboCardGuardian);
        turboCardLockdown    = findViewById(R.id.turboCardLockdown);
        tvGlassCommunityStatus = findViewById(R.id.tvGlassCommunityStatus);
        tvGlassGuardianStatus  = findViewById(R.id.tvGlassGuardianStatus);

        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void setupInsets() {
        if (headerSection != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerSection, (v, insets) -> {
                int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
                v.setPadding(24, top + 16, 24, 32);
                return insets;
            });
        }
        if (bottomNavigation != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (v, insets) -> {
                v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
                return insets;
            });
        }
    }

    // ── TURBO MODE ────────────────────────────────────────────────────────────
    private void activateTurboMode() {
        if (isTurboMode) return;
        isTurboMode = true;

        // Save turbo state so it persists across restarts
        getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .edit().putBoolean("turbo_mode", true).apply();

        // Fade out normal dashboard
        normalScrollView.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> normalScrollView.setVisibility(View.GONE)).start();

        // Show spider video background
        turboVideo.setVisibility(View.VISIBLE);
        playVideo();

        // Show neon spider web canvas
        cyberWebCanvas.setVisibility(View.VISIBLE);
        cyberWebCanvas.start();

        // Show turbo Intelligence overlay
        turboOverlay.setVisibility(View.VISIBLE);
        turboOverlay.setAlpha(0f);
        turboOverlay.animate().alpha(1f).setDuration(500).start();

        // Start typewriter with real service status
        handler.postDelayed(this::runTypewriter, 400);

        // Tell island overlay to switch to turbo pill style
        Intent turboOn = new Intent(DynamicBarService.ACTION_TURBO_ON);
        turboOn.setPackage(getPackageName());
        sendBroadcast(turboOn);

        // Populate turbo cards with real live data
        refreshTurboCards();

        // START Turbo Mode via TurboManager - activates all protection services
        turboManager.startTurboMode();
    }

    private void deactivateTurboMode() {
        if (!isTurboMode) return;
        isTurboMode = false;

        // Save normal state
        getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .edit().putBoolean("turbo_mode", false).apply();

        // Animate turbo overlay out
        turboOverlay.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> turboOverlay.setVisibility(View.GONE)).start();

        // Stop video + canvas
        try { if (turboVideo.isPlaying()) turboVideo.stopPlayback(); } catch (Exception ignored) {}
        turboVideo.setVisibility(View.GONE);
        cyberWebCanvas.stop();
        cyberWebCanvas.setVisibility(View.GONE);

        // Bring back normal dashboard
        normalScrollView.setVisibility(View.VISIBLE);
        normalScrollView.setAlpha(0f);
        normalScrollView.animate().alpha(1f).setDuration(400).start();

        // Tell island overlay to revert to normal pill
        Intent turboOff = new Intent(DynamicBarService.ACTION_TURBO_OFF);
        turboOff.setPackage(getPackageName());
        sendBroadcast(turboOff);

        // STOP Turbo Mode via TurboManager
        turboManager.stopTurboMode();
        handler.removeCallbacksAndMessages(null);
    }

    private void playVideo() {
        try {
            Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.spider_turbo);
            turboVideo.setVideoURI(uri);
            turboVideo.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                turboVideo.start();
            });
            turboVideo.setOnErrorListener((mp, w, e) -> true);
        } catch (Exception ignored) {}
    }

    private void runTypewriter() {
        if (!isTurboMode) return;

        // Get dynamic threat count and service status
        int threatCount = turboManager.getDynamicThreatCount();
        TurboManager.ServiceState[] states = turboManager.getAllServiceStates();
        int activeServices = 0;
        for (TurboManager.ServiceState state : states) {
            if (state.status == TurboManager.ServiceStatus.RUNNING) activeServices++;
        }

        String[] lines = {
            "CYBERRAKSHA TURBO MODE ACTIVATED",
            "Threat Engine Online...",
            "Guardian Active [" + threatCount + " threats]",
            "Active Modules: " + activeServices + "/" + states.length,
            "[ ALL SYSTEMS OPERATIONAL ]"
        };
        tvConsole.setText("");
        final int[] li = {0}, ci = {0};
        final StringBuilder sb = new StringBuilder();
        handler.post(new Runnable() {
            @Override public void run() {
                if (!isTurboMode || li[0] >= lines.length) return;
                String line = lines[li[0]];
                if (ci[0] < line.length()) {
                    sb.append(line.charAt(ci[0]++));
                    tvConsole.setText(sb + "_");
                    handler.postDelayed(this, 45);
                } else {
                    sb.append("\n"); li[0]++; ci[0] = 0;
                    handler.postDelayed(this, 80);
                }
            }
        });
    }

    private void refreshTurboCards() {
        // Get dynamic threat count from TurboManager
        int threatCount = turboManager.getDynamicThreatCount();

        if (tvGlassCommunityStatus != null)
            tvGlassCommunityStatus.setText(threatCount + " active threats");
        if (tvGlassGuardianStatus != null) {
            boolean on = isAccessibilityEnabled();
            tvGlassGuardianStatus.setText(on ? "● Watching" : "⚠ Enable");
        }
        // Show Behavioral DNA stats in console
        if (isTurboMode) {
            int knownApps = behaviorService.getKnownAppCount();
            int knownUpi  = behaviorService.getKnownUpiCount();
            appendConsole("DNA: " + knownApps + " apps | " + knownUpi + " UPI IDs learned");
            appendConsole("Threats: " + threatCount + " detected | Community: " + communityDB.getTotalReports() + " loaded");
        }
        updateThreatMeter();
    }

    private void updateThreatMeter() {
        int score = threatEngine.getCurrentScore();
        // Keep island pill score in sync
        Intent upd = new Intent(DynamicBarService.ACTION_UPDATE_SCORE);
        upd.putExtra(DynamicBarService.EXTRA_SCORE, score);
        upd.setPackage(getPackageName());
        sendBroadcast(upd);
    }

    private void onThreatDetected(int score, String reason) {
        if (score > 0) threatEngine.addScore(score, reason != null ? reason : "");
        refreshThreatMeter();
        if (reason != null) {
            appendConsole("⚠ " + reason);
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshThreatMeter() {
    }

    private void appendConsole(String msg) {
        if (tvConsole == null || !isTurboMode) return;
        String cur = tvConsole.getText().toString().replace("_", "");
        String[] lines = cur.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.length - 3);
        for (int i = start; i < lines.length; i++) sb.append(lines[i]).append("\n");
        sb.append("> ").append(msg);
        tvConsole.setText(sb.toString());
    }

    private void handleIntelUpdate(String key, String val) {
        if (key == null || val == null) return;
        // Update turbo card labels with live Intelligence data
        switch (key) {
            case "dna_apps":
                if (isTurboMode) appendConsole("🧬 Known apps: " + val);
                break;
            case "dna_upi":
                if (isTurboMode && !"0".equals(val)) appendConsole("🧬 Known UPI IDs: " + val);
                break;
            case "community_count":
                if (tvGlassCommunityStatus != null)
                    tvGlassCommunityStatus.setText(val + " threats");
                break;
            case "guardian_status":
                if (tvGlassGuardianStatus != null) {
                    boolean armed = "armed".equals(val);
                    tvGlassGuardianStatus.setText(armed ? "● Armed" : "⚠ Limited");
                    tvGlassGuardianStatus.setTextColor(
                        android.graphics.Color.parseColor(armed ? "#B400FF" : "#FFD600"));
                }
                break;
        }
    }

    private void refreshScore() {
        int score = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .getInt("last_security_score", 97);
        if (tvHealthScore != null) tvHealthScore.setText(String.valueOf(score));
    }

    // ── BOTTOM NAV — Switch between Dashboard and Intelligence ────────────────
    private void showDashboard() {
        normalScrollView.setVisibility(isTurboMode ? View.GONE : View.VISIBLE);
        if (intelligenceView != null) intelligenceView.setVisibility(View.GONE);
    }

    private void showIntelligence() {
        normalScrollView.setVisibility(View.GONE);
        if (turboOverlay != null && isTurboMode) turboOverlay.setVisibility(View.GONE);
        if (intelligenceView != null) {
            intelligenceView.setVisibility(View.VISIBLE);
        } else {
            // Load intelligence fragment/view
            startActivity(new Intent(this, IntelligenceActivity.class));
            if (bottomNavigation != null) bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
        }
    }

    // ── CLICK LISTENERS ───────────────────────────────────────────────────────
    private void setupClickListeners() {
        // Dynamic bar
        if (dynBtnNormal != null) dynBtnNormal.setOnClickListener(v -> deactivateTurboMode());
        if (dynBtnTurbo != null)  dynBtnTurbo.setOnClickListener(v -> activateTurboMode());
        if (btnExitTurbo != null) btnExitTurbo.setOnClickListener(v -> deactivateTurboMode());

        // Dashboard
        if (btnOptimize != null)
            btnOptimize.setOnClickListener(v -> startActivity(new Intent(this, ScanListActivity.class)));
        if (btnActivateShield != null)
            btnActivateShield.setOnClickListener(v -> startActivity(new Intent(this, PanicModeActivity.class)));
        if (cardEmergencyShield != null)
            cardEmergencyShield.setOnClickListener(v -> startActivity(new Intent(this, PanicModeActivity.class)));
        if (cardSecurityScan != null)
            cardSecurityScan.setOnClickListener(v -> startActivity(new Intent(this, NetworkSecurityActivity.class)));
        if (cardSafeSearch != null)
            cardSafeSearch.setOnClickListener(v -> startActivity(new Intent(this, SafeSearchActivity.class)));
        if (cardAppManager != null)
            cardAppManager.setOnClickListener(v -> startActivity(new Intent(this, AiNewsSelectionActivity.class)));
        if (cardSpaceCleanup != null)
            cardSpaceCleanup.setOnClickListener(v -> startActivity(new Intent(this, ChatbotActivity.class)));

        // Turbo tools — wired to real Intelligence features
        if (turboCardRadar != null) turboCardRadar.setOnClickListener(v -> {
            // Threat Radar → triggers live app scan + shows in island via IslandController
            IslandController.getInstance(this).post(IslandController.scanEvent(0, 42));
            startActivity(new Intent(this, ScanListActivity.class));
        });
        if (turboCardFraud != null) turboCardFraud.setOnClickListener(v -> {
            // Live Fraud Detection → IntelligenceActivity
            startActivity(new Intent(this, IntelligenceActivity.class));
        });
        if (turboCardGuardian != null) turboCardGuardian.setOnClickListener(v -> {
            // Guardian Monitor → V2 Security Engine
            startActivity(new Intent(this, V2SecurityEngineActivity.class));
        });
        if (turboCardLockdown != null) turboCardLockdown.setOnClickListener(v -> {
            // Emergency Lockdown → Panic Mode
            startActivity(new Intent(this, PanicModeActivity.class));
        });

        // Long-press health score → toggle island mode toggle visibility (user preference)
        if (tvHealthScore != null) {
            tvHealthScore.setOnLongClickListener(v -> {
                boolean showToggle = !getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                        .getBoolean("show_island_toggle", true);
                getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                        .edit().putBoolean("show_island_toggle", showToggle).apply();
                // Notify island service to show/hide toggle row
                Intent pref = new Intent("com.cyberraksha.guardian.ISLAND_PREF");
                pref.putExtra("show_toggle", showToggle);
                pref.setPackage(getPackageName());
                sendBroadcast(pref);
                Toast.makeText(this,
                    showToggle ? "Island toggle shown" : "Island toggle hidden",
                    Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // Island visibility toggle button — hide or restore the floating island
        if (btnIslandVisibility != null) {
            // Set correct initial label from saved state
            boolean isVisible = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                    .getBoolean("island_visible", true);
            btnIslandVisibility.setText(isVisible ? "🫥  Hide Island" : "✅  Show Island");

            btnIslandVisibility.setOnClickListener(v -> {
                boolean currentlyVisible = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                        .getBoolean("island_visible", true);
                if (currentlyVisible) {
                    // Hide it
                    Intent hide = new Intent(DynamicBarService.ACTION_HIDE_ISLAND);
                    hide.setPackage(getPackageName());
                    sendBroadcast(hide);
                    btnIslandVisibility.setText("✅  Show Island");
                    Toast.makeText(this, "Island hidden. Tap 'Show Island' to restore.", Toast.LENGTH_SHORT).show();
                } else {
                    // Show it
                    Intent show = new Intent(DynamicBarService.ACTION_SHOW_ISLAND);
                    show.setPackage(getPackageName());
                    sendBroadcast(show);
                    btnIslandVisibility.setText("🫥  Hide Island");
                    Toast.makeText(this, "Island restored!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Bottom nav
        if (bottomNavigation != null) {
            bottomNavigation.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_dashboard) { showDashboard(); return true; }
                if (id == R.id.nav_intelligence) { showIntelligence(); return true; }
                return false;
            });
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.cyberraksha.guardian.APK_INSTALLED");
        filter.addAction(DynamicBarService.ACTION_TURBO_ON);
        filter.addAction(DynamicBarService.ACTION_TURBO_OFF);
        filter.addAction(TurboModeEngine.ACTION_TURBO_LOG);
        filter.addAction("com.cyberraksha.guardian.TURBO_STATUS");
        filter.addAction("com.cyberraksha.guardian.THREAT_DETECTED");
        filter.addAction("com.cyberraksha.guardian.INTEL_UPDATE");
        filter.addAction(ResponseEngine.ACTION_THREAT_DETECTED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(securityReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(securityReceiver, filter);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("CyberRaksha needs 'Display over other apps' permission to show the Dynamic Control Bar and fraud alerts.")
                .setPositiveButton("Grant", (d, w) -> startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Later", null).show();
        } else {
            startDynamicBarService();
        }
        String[] perms = {Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG};
        boolean needs = false;
        for (String p : perms)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needs = true; break;
            }
        if (needs) ActivityCompat.requestPermissions(this, perms, PHONE_PERMS_CODE);
        else startService(new Intent(this, CallDetectionService.class));
    }

    private boolean isAccessibilityEnabled() {
        try {
            String svc = getPackageName() + "/" + SafeSearchService.class.getCanonicalName();
            String en = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return en != null && en.contains(svc);
        } catch (Exception e) { return false; }
    }

    public static void saveSecurityScore(Context ctx, int score) {
        ctx.getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .edit().putInt("last_security_score", score).apply();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PHONE_PERMS_CODE && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED)
            startService(new Intent(this, CallDetectionService.class));
    }

    private void startDynamicBarService() {
        try {
            Intent serviceIntent = new Intent(this, DynamicBarService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(serviceIntent);
            else
                startService(serviceIntent);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshScore();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            startDynamicBarService();
        }
        // Restore turbo state if app was killed while in turbo mode
        boolean wasTurbo = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .getBoolean("turbo_mode", false);
        if (wasTurbo && !isTurboMode) {
            handler.postDelayed(this::activateTurboMode, 500);
        }
    }

    /**
     * Test all API keys - call this method to verify API connectivity
     * Add this to any button click or call directly for testing
     */
    private void testAllApiKeys() {
        Toast.makeText(this, "Testing API keys... Check logs for results", Toast.LENGTH_SHORT).show();
        ApiKeyTestRunner.runAllTests();
    }
    
    /**
     * Quick test for essential providers only
     */
    private void testEssentialApiKeys() {
        Toast.makeText(this, "Testing essential API keys... Check logs", Toast.LENGTH_SHORT).show();
        ApiKeyTestRunner.runEssentialTests();
    }
    
    /**
     * Quick test for chat API key only
     */
    private void testChatApiKey() {
        Toast.makeText(this, "Testing Chat API key... Check logs", Toast.LENGTH_SHORT).show();
        DirectApiKeyTest.testOpenRouterKey();
    }


}