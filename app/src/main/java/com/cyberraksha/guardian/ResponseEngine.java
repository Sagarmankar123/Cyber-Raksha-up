package com.cyberraksha.guardian;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Response Engine
 * When threat detected — captures evidence, logs it, triggers alert.
 * This is what makes CyberRaksha ACTIVE defense, not just warnings.
 */
public class ResponseEngine {

    public static final String ACTION_THREAT_DETECTED = "com.cyberraksha.guardian.THREAT_DETECTED";
    public static final String ACTION_UPI_SCAM        = "com.cyberraksha.guardian.UPI_SCAM";
    public static final String ACTION_SCAM_CALL       = "com.cyberraksha.guardian.SCAM_CALL";
    public static final String ACTION_OVERLAY_ATTACK  = "com.cyberraksha.guardian.OVERLAY_ATTACK";
    public static final String ACTION_APK_INSTALL     = "com.cyberraksha.guardian.APK_INSTALL";

    public static final String EXTRA_RISK_SCORE  = "risk_score";
    public static final String EXTRA_REASON      = "reason";
    public static final String EXTRA_PACKAGE     = "package_name";

    private static final String LOG_FILE = "cyberraksha_evidence.json";

    private final Context context;
    private final ThreatEngine threatEngine;

    public ResponseEngine(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.threatEngine = ThreatEngine.getInstance(ctx);
    }

    /**
     * Main response trigger — call this when any threat is detected.
     * Captures evidence + broadcasts event for UI to react.
     */
    public void triggerResponse(String reason, String packageName, int riskScore) {
        // 1. Capture evidence
        captureEvidence(reason, packageName, riskScore);

        // 2. Broadcast to UI
        Intent intent = new Intent(ACTION_THREAT_DETECTED);
        intent.putExtra(EXTRA_RISK_SCORE, riskScore);
        intent.putExtra(EXTRA_REASON, reason);
        intent.putExtra(EXTRA_PACKAGE, packageName);
        context.sendBroadcast(intent);
    }

    public void triggerUpiScam(String upiId, int riskScore) {
        captureEvidence("UPI Scam: " + upiId, "UPI", riskScore);
        // Route through IslandController — won't interrupt a live call
        IslandController.getInstance(context).post(
            IslandController.securityEvent(upiId, "Flagged in Community Threat DB. Do NOT send money!", "UPI", riskScore, true)
        );
        // Also send raw broadcast for any other listeners (e.g. IntelligenceActivity)
        Intent intent = new Intent(ACTION_UPI_SCAM);
        intent.putExtra(EXTRA_RISK_SCORE, riskScore);
        intent.putExtra(EXTRA_REASON, upiId);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public void triggerScamCall(String number, int riskScore) {
        captureEvidence("Scam Call: " + number, "Phone", riskScore);
        // Route through IslandController — priority 2, auto-expands
        IslandController.getInstance(context).post(
            IslandController.scamCallEvent(number, riskScore)
        );
        // Raw broadcast for other listeners
        Intent intent = new Intent(ACTION_SCAM_CALL);
        intent.putExtra(EXTRA_RISK_SCORE, riskScore);
        intent.putExtra(EXTRA_REASON, number);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    public void triggerOverlayAttack(String packageName) {
        int score = threatEngine.addScore(ThreatEngine.SCORE_OVERLAY_ATTACK, "Overlay: " + packageName);
        captureEvidence("Overlay Attack by: " + packageName, packageName, score);
        // Route through IslandController — priority 3
        IslandController.getInstance(context).post(
            IslandController.securityEvent(packageName, "App drawing over screen. Screen-share scam technique.", packageName, score, false)
        );
        Intent intent = new Intent(ACTION_OVERLAY_ATTACK);
        intent.putExtra(EXTRA_PACKAGE, packageName);
        intent.putExtra(EXTRA_RISK_SCORE, score);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    // ── EVIDENCE CAPTURE ──────────────────────────────────────────────────────

    private void captureEvidence(String reason, String packageName, int riskScore) {
        new Thread(() -> {
            try {
                JSONObject entry = new JSONObject();
                entry.put("timestamp", new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                entry.put("reason", reason);
                entry.put("package", packageName);
                entry.put("risk_score", riskScore);
                entry.put("risk_level", threatEngine.getRiskLabel());

                File logFile = new File(context.getFilesDir(), LOG_FILE);
                JSONArray existing = new JSONArray();

                if (logFile.exists()) {
                    try {
                        String content = readFile(logFile);
                        existing = new JSONArray(content);
                    } catch (Exception ignored) {}
                }

                existing.put(entry);
                writeFile(logFile, existing.toString(2));

            } catch (Exception e) {
                // Silent fail — evidence capture is best-effort
            }
        }).start();
    }

    public String getEvidenceLog() {
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE);
            if (!logFile.exists()) return "No evidence logged yet.";
            return readFile(logFile);
        } catch (Exception e) {
            return "Error reading log.";
        }
    }

    public int getEvidenceCount() {
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE);
            if (!logFile.exists()) return 0;
            return new JSONArray(readFile(logFile)).length();
        } catch (Exception e) { return 0; }
    }

    public void clearEvidence() {
        new File(context.getFilesDir(), LOG_FILE).delete();
    }

    // ── FILE UTILS ────────────────────────────────────────────────────────────

    private String readFile(File f) throws Exception {
        byte[] buf = new byte[(int) f.length()];
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        fis.read(buf);
        fis.close();
        return new String(buf);
    }

    private void writeFile(File f, String content) throws Exception {
        FileWriter fw = new FileWriter(f);
        fw.write(content);
        fw.close();
    }
}
