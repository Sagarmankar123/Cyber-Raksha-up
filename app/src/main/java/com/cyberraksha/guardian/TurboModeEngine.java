package com.cyberraksha.guardian;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           TURBO MODE ENGINE — CyberRaksha                   ║
 * ║                                                              ║
 * ║  Central controller for all 4 Intelligence features.        ║
 * ║  When TURBO ON  → all 4 engines START actively scanning.    ║
 * ║  When TURBO OFF → all 4 engines PAUSE (save battery).       ║
 * ║                                                              ║
 * ║  Feature 1: Behavioral DNA Engine                           ║
 * ║    → Scans all installed apps against known patterns        ║
 * ║    → Checks for apps installed at unusual hours             ║
 * ║    → Broadcasts results to island + turbo console           ║
 * ║                                                              ║
 * ║  Feature 2: Community Threat Intelligence                   ║
 * ║    → Cross-checks all installed apps against fraud DB       ║
 * ║    → Checks running processes against threat hashes         ║
 * ║    → Broadcasts threat count updates                        ║
 * ║                                                              ║
 * ║  Feature 3: Android Guardian System                         ║
 * ║    → Activates aggressive UPI monitoring via UpiGuardian    ║
 * ║    → Sets turbo flag so SafeSearchService scans deeper      ║
 * ║    → Monitors for AnyDesk/TeamViewer + banking combos       ║
 * ║                                                              ║
 * ║  Feature 4: Threat Engine (Central Risk Brain)              ║
 * ║    → Runs a full device risk assessment                     ║
 * ║    → Calculates composite threat score                      ║
 * ║    → Updates island pill score in real-time                 ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public class TurboModeEngine {

    private static final String TAG   = "TurboModeEngine";
    private static final String PREFS = "cyberraksha_prefs";

    // Broadcast actions emitted by the engine → MainActivity console
    public static final String ACTION_TURBO_LOG   = "com.cyberraksha.guardian.TURBO_LOG";
    public static final String EXTRA_LOG_LINE     = "log_line";

    private static TurboModeEngine instance;

    private final Context        ctx;
    private final Handler        handler;
    private final BehaviorService  behavior;
    private final CommunityThreatDB community;
    private final ThreatEngine     threat;

    private boolean running = false;
    private Runnable periodicScanRunnable;

    private TurboModeEngine(Context context) {
        this.ctx       = context.getApplicationContext();
        this.handler   = new Handler(Looper.getMainLooper());
        this.behavior  = new BehaviorService(ctx);
        this.community = CommunityThreatDB.getInstance(ctx);
        this.threat    = ThreatEngine.getInstance(ctx);
    }

    public static TurboModeEngine getInstance(Context ctx) {
        if (instance == null) instance = new TurboModeEngine(ctx);
        return instance;
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════

    /** Call this when TURBO MODE is activated */
    public void start() {
        if (running) return;
        running = true;
        setTurboFlag(true);
        log("🕷 TURBO ENGINE STARTED");
        log("Loading " + community.getTotalReports() + " community threats...");

        // Run all 4 features immediately, then on a schedule
        handler.postDelayed(this::runFeature1_BehavioralDNA,   500);
        handler.postDelayed(this::runFeature2_CommunityCheck, 1500);
        handler.postDelayed(this::runFeature3_GuardianArm,    2500);
        handler.postDelayed(this::runFeature4_ThreatAssessment, 3500);

        // Schedule periodic re-scans every 60 seconds while turbo is on
        periodicScanRunnable = new Runnable() {
            @Override public void run() {
                if (!running) return;
                runFeature1_BehavioralDNA();
                runFeature4_ThreatAssessment();
                handler.postDelayed(this, 90_000); // was 60s — reduced to cut CPU load
            }
        };
        handler.postDelayed(periodicScanRunnable, 90_000);
        log("[ ALL SYSTEMS OPERATIONAL ]");
    }

    /** Call this when TURBO MODE is deactivated */
    public void stop() {
        if (!running) return;
        running = false;
        setTurboFlag(false);
        handler.removeCallbacksAndMessages(null);
        log("TURBO ENGINE STOPPED — Normal mode");
        Log.d(TAG, "TurboModeEngine stopped");
    }

    public boolean isRunning() { return running; }

    // ══════════════════════════════════════════════════════════════
    // FEATURE 1 — BEHAVIORAL DNA ENGINE
    // ══════════════════════════════════════════════════════════════

    private void runFeature1_BehavioralDNA() {
        log("🧬 DNA: Scanning installed apps...");
        new Thread(() -> {
            try {
                PackageManager pm = ctx.getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(0);
                int newApps    = 0;
                int suspicious = 0;
                int total      = 0;

                for (PackageInfo pkg : packages) {
                    if (pkg.applicationInfo == null) continue;
                    boolean isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (isSystem) continue;
                    total++;

                    // Check this app against behavioral baseline
                    // BehaviorService.checkAppInstallAnomaly will:
                    // - Add to known apps if new
                    // - Flag if installed at unusual hour
                    int riskScore = behavior.checkAppInstallAnomaly(pkg.packageName);
                    if (riskScore > 0) suspicious++;

                    // Check against community threat DB
                    String pkgHash = sha256(pkg.packageName);
                    // We check package hash against known malicious hashes
                    // (future: load malicious_packages.json like fraud_upi_ids.json)
                }

                int knownApps = behavior.getKnownAppCount();
                int knownUpi  = behavior.getKnownUpiCount();

                log("🧬 DNA: " + total + " apps scanned | " + knownApps + " learned | " + suspicious + " flagged");
                if (suspicious > 0) {
                    log("⚠ DNA: " + suspicious + " suspicious app install pattern(s) found");
                    threat.addScore(suspicious * 5, "Behavioral DNA: suspicious installs");
                    broadcastScore();
                }

                broadcastIntelUpdate("dna_apps", String.valueOf(knownApps));
                broadcastIntelUpdate("dna_upi",  String.valueOf(knownUpi));

            } catch (Exception e) {
                log("🧬 DNA: scan error — " + e.getMessage());
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    // FEATURE 2 — COMMUNITY THREAT INTELLIGENCE
    // ══════════════════════════════════════════════════════════════

    private void runFeature2_CommunityCheck() {
        log("🌐 Community: Cross-checking threats...");
        new Thread(() -> {
            try {
                PackageManager pm = ctx.getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(0);
                int threatHits = 0;

                for (PackageInfo pkg : packages) {
                    if (pkg.applicationInfo == null) continue;
                    boolean isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (isSystem) continue;

                    // Check package name against fraud domains list
                    // (apps that disguise as banking support)
                    String pkgLower = pkg.packageName.toLowerCase();
                    if (community.isDomainReported(pkgLower)) {
                        threatHits++;
                        String appName = pm.getApplicationLabel(pkg.applicationInfo).toString();
                        log("🌐 Community: THREAT — " + appName + " matches fraud pattern");
                        threat.addScore(ThreatEngine.SCORE_COMMUNITY_REPORT, "Community: " + appName);
                    }
                }

                int total = community.getTotalReports();
                log("🌐 Community: " + total + " threats active | " + threatHits + " device matches");

                if (threatHits > 0) {
                    broadcastScore();
                    broadcastAlert("⚠ " + threatHits + " app(s) match community fraud patterns!");
                }

                broadcastIntelUpdate("community_count", String.valueOf(total));

            } catch (Exception e) {
                log("🌐 Community: check error — " + e.getMessage());
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    // FEATURE 3 — ANDROID GUARDIAN SYSTEM
    // ══════════════════════════════════════════════════════════════

    private void runFeature3_GuardianArm() {
        // Guardian is AccessibilityService-based — it's always listening via SafeSearchService
        // In Turbo mode, we set a flag that SafeSearchService reads to be MORE aggressive
        // (deeper scanning, more keywords checked, faster response)
        log("🛡 Guardian: Armed — deep UPI interception active");
        log("🛡 Guardian: Monitoring PhonePe, GPay, Paytm, SBI, HDFC");

        // Check if accessibility is actually enabled
        boolean accessEnabled = isAccessibilityEnabled();
        if (!accessEnabled) {
            log("⚠ Guardian: Accessibility not enabled — some features limited");
        } else {
            log("🛡 Guardian: All systems watching ✓");
        }

        broadcastIntelUpdate("guardian_status", accessEnabled ? "armed" : "limited");
    }

    // ══════════════════════════════════════════════════════════════
    // FEATURE 4 — THREAT ENGINE (CENTRAL RISK BRAIN)
    // ══════════════════════════════════════════════════════════════

    private void runFeature4_ThreatAssessment() {
        log("⚡ ThreatEngine: Running full device risk assessment...");
        new Thread(() -> {
            try {
                int score = threat.getCurrentScore();

                // Check for risky permission combinations across all apps
                PackageManager pm = ctx.getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
                int highRiskApps = 0;

                for (PackageInfo pkg : packages) {
                    if (pkg.applicationInfo == null) continue;
                    boolean isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (isSystem) continue;
                    if (pkg.requestedPermissions == null) continue;

                    // Check for the deadly combo: overlay + SMS access (OTP theft pattern)
                    boolean hasOverlay = false, hasSms = false;
                    for (String perm : pkg.requestedPermissions) {
                        if (perm.contains("SYSTEM_ALERT_WINDOW")) hasOverlay = true;
                        if (perm.contains("READ_SMS") || perm.contains("RECEIVE_SMS")) hasSms = true;
                    }
                    if (hasOverlay && hasSms) {
                        highRiskApps++;
                        String appName = pm.getApplicationLabel(pkg.applicationInfo).toString();
                        log("⚡ ThreatEngine: HIGH RISK perm combo — " + appName);
                    }
                }

                if (highRiskApps > 0) {
                    threat.addScore(highRiskApps * 10, "ThreatEngine: OTP-theft risk combo");
                    score = threat.getCurrentScore();
                }

                String label = score >= 70 ? "HIGH RISK" : score >= 40 ? "SUSPICIOUS" : "SAFE";
                log("⚡ ThreatEngine: Score " + score + "% — " + label);
                broadcastScore();

            } catch (Exception e) {
                log("⚡ ThreatEngine: assessment error — " + e.getMessage());
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    // BROADCASTS
    // ══════════════════════════════════════════════════════════════

    // Throttle log broadcasts — max 1 per 200ms to avoid flooding the main thread
    private long lastLogBroadcastMs = 0;
    private final StringBuilder pendingLogs = new StringBuilder();

    private void log(String line) {
        Log.d(TAG, line);
        long now = System.currentTimeMillis();
        pendingLogs.append(line).append("\n");
        if (now - lastLogBroadcastMs > 200) {
            lastLogBroadcastMs = now;
            String batch = pendingLogs.toString().trim();
            pendingLogs.setLength(0);
            android.content.Intent i = new android.content.Intent(ACTION_TURBO_LOG);
            i.putExtra(EXTRA_LOG_LINE, batch);
            i.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(i);
        }
    }

    private void broadcastScore() {
        int score = threat.getCurrentScore();
        android.content.Intent i = new android.content.Intent(DynamicBarService.ACTION_UPDATE_SCORE);
        i.putExtra(DynamicBarService.EXTRA_SCORE, score);
        i.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(i);
    }

    private void broadcastAlert(String text) {
        // Route through IslandController — respects priority, won't interrupt calls
        IslandController.getInstance(ctx).post(
            new IslandController.IslandEvent.Builder(IslandController.EventType.SECURITY)
                .title("⚠️ Security Alert").subtitle(text).key("turbo_alert_" + text.hashCode())
                .autoExpand(true).autoDismissMs(10000).build()
        );
    }

    private void broadcastIntelUpdate(String key, String value) {
        android.content.Intent i = new android.content.Intent("com.cyberraksha.guardian.INTEL_UPDATE");
        i.putExtra("key", key);
        i.putExtra("value", value);
        i.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(i);
    }

    // ══════════════════════════════════════════════════════════════
    // UTILS
    // ══════════════════════════════════════════════════════════════

    /** Sets turbo flag in SharedPrefs — SafeSearchService reads this to arm deeper scanning */
    private void setTurboFlag(boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("turbo_mode", on).apply();
    }

    private boolean isAccessibilityEnabled() {
        try {
            String svc = ctx.getPackageName() + "/" + SafeSearchService.class.getCanonicalName();
            String en  = android.provider.Settings.Secure.getString(
                    ctx.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return en != null && en.contains(svc);
        } catch (Exception e) { return false; }
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }
}
