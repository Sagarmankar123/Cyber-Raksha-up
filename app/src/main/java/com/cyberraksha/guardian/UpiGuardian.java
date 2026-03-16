package com.cyberraksha.guardian;

import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * UPI Guardian
 * Add this logic inside SafeSearchService.java
 * Detects UPI collect requests, overlay attacks, AnyDesk scams.
 *
 * HOW TO INTEGRATE:
 * In SafeSearchService.onAccessibilityEvent(), call:
 *   UpiGuardian.check(event, getApplicationContext());
 */
public class UpiGuardian {

    private static final Set<String> BANKING_APPS = new HashSet<>(Arrays.asList(
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.sbi.lotusintouch",
        "com.axis.mobile",
        "com.kotak.mobile.knetbanking"
    ));

    private static final Set<String> REMOTE_ACCESS_APPS = new HashSet<>(Arrays.asList(
        "com.anydesk.anydeskandroid",
        "com.teamviewer.teamviewer",
        "com.realvnc.viewer.android",
        "com.logmein.rescuereceiver"
    ));

    // UPI scam keywords
    private static final String[] COLLECT_KEYWORDS = {
        "collect request", "collect ₹", "collect rs",
        "approve request", "authorize payment",
        "enter pin to receive", "enter upi pin to receive",
        "pay to receive", "mandate", "auto debit",
        "standing instruction"
    };

    private static final String[] PHISHING_KEYWORDS = {
        "enter your otp", "verify your account",
        "account suspended", "account blocked",
        "update your kyc", "kyc pending", "kyc update",
        "enter cvv", "confirm your password",
        "your account will be closed"
    };

    private static long lastAlertTime = 0;
    private static final long COOLDOWN_MS = 8000;

    public static void check(AccessibilityEvent event, Context context) {
        if (event == null) return;

        String pkg = event.getPackageName() != null
                ? event.getPackageName().toString() : "";

        // Check for remote access + banking combo = HIGHEST RISK
        if (REMOTE_ACCESS_APPS.contains(pkg) && isAnyBankingAppRunning(context)) {
            fireAlert(context, "SCREEN SHARE SCAM",
                    "AnyDesk/TeamViewer detected while banking app is open!\n\n" +
                    "स्क्रीन शेयर बंद करो — यह स्कैम है!",
                    ResponseEngine.ACTION_OVERLAY_ATTACK, 95);
            return;
        }

        // Only deep-check banking apps
        if (!BANKING_APPS.contains(pkg)) return;

        String screenText = extractText(event.getSource()).toLowerCase();
        if (screenText.isEmpty()) return;

        // Check UPI collect request
        for (String keyword : COLLECT_KEYWORDS) {
            if (screenText.contains(keyword)) {
                fireAlert(context, "⚠️ UPI COLLECT REQUEST",
                        "यह COLLECT REQUEST है!\n\nपैसे तुम्हारे खाते से जाएंगे — आएंगे नहीं!\n\n" +
                        "DECLINE करो अभी!",
                        ResponseEngine.ACTION_UPI_SCAM, 80);
                return;
            }
        }

        // Check phishing patterns
        int phishCount = 0;
        for (String kw : PHISHING_KEYWORDS) {
            if (screenText.contains(kw)) phishCount++;
        }
        if (phishCount >= 2) {
            fireAlert(context, "🚨 PHISHING DETECTED",
                    "This page is collecting sensitive information!\n\n" +
                    "कोई भी OTP या password enter मत करो!",
                    ResponseEngine.ACTION_THREAT_DETECTED, 70);
        }
    }

    private static void fireAlert(Context ctx, String title, String message,
                                   String action, int riskScore) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime < COOLDOWN_MS) return;
        lastAlertTime = now;

        // Add to threat engine
        ThreatEngine.getInstance(ctx).addScore(riskScore, title);

        // Update Behavioral DNA — mark this as a threat event
        // BehaviorService tracks that the user encountered this pattern
        try {
            BehaviorService behavior = new BehaviorService(ctx);
            // If it's a UPI scam, extract UPI ID from title and check it
            if (action.equals(ResponseEngine.ACTION_UPI_SCAM)) {
                // The title contains the UPI pattern — log it as suspicious
                behavior.checkUpiAnomaly("scam_" + now);
            }
        } catch (Exception ignored) {}

        // Alert through IslandController — priority-aware, won't interrupt calls
        boolean isUpi = action.equals(ResponseEngine.ACTION_UPI_SCAM);
        IslandController.getInstance(ctx).post(
            IslandController.securityEvent(title, message, "upi_guardian", riskScore, isUpi)
        );

        // Broadcast for UI + overlay
        Intent intent = new Intent(action);
        intent.putExtra(ResponseEngine.EXTRA_RISK_SCORE, riskScore);
        intent.putExtra(ResponseEngine.EXTRA_REASON, title + ": " + message);
        ctx.sendBroadcast(intent);

        // Log evidence
        new ResponseEngine(ctx).triggerResponse(title, "UPI", riskScore);
    }

    private static boolean isAnyBankingAppRunning(Context ctx) {
        try {
            android.app.usage.UsageStatsManager usm =
                (android.app.usage.UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            java.util.List<android.app.usage.UsageStats> stats =
                usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    now - 60_000, now); // last 60 seconds
            if (stats != null) {
                for (android.app.usage.UsageStats s : stats) {
                    if (s.getLastTimeUsed() > now - 60_000) {
                        if (BANKING_APPS.contains(s.getPackageName())) return true;
                    }
                }
            }
        } catch (Exception e) {
            return true; // fallback: assume banking app could be running
        }
        return false;
    }

    private static String extractText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText()).append(" ");
        if (node.getContentDescription() != null) sb.append(node.getContentDescription()).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(extractText(node.getChild(i)));
        }
        return sb.toString();
    }
}
