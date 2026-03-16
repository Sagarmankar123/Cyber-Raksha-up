package com.cyberraksha.guardian;

import android.content.Context;
import android.content.SharedPreferences;

public class ThreatEngine {

    public static final int RISK_SAFE       = 0;   // 0-39
    public static final int RISK_SUSPICIOUS = 1;   // 40-69
    public static final int RISK_HIGH       = 2;   // 70+

    // Score weights
    public static final int SCORE_SCAM_CALL        = 40;
    public static final int SCORE_COLLECT_REQUEST  = 30;
    public static final int SCORE_UNKNOWN_UPI      = 20;
    public static final int SCORE_COMMUNITY_REPORT = 30;
    public static final int SCORE_OVERLAY_ATTACK   = 50;
    public static final int SCORE_REMOTE_ACCESS    = 45;
    public static final int SCORE_MALICIOUS_APK    = 35;

    private static ThreatEngine instance;
    private int currentScore = 0;
    private final Context context;

    private ThreatEngine(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    public static ThreatEngine getInstance(Context ctx) {
        if (instance == null) instance = new ThreatEngine(ctx);
        return instance;
    }

    // Add score for a threat signal
    public int addScore(int points, String reason) {
        currentScore = Math.min(100, currentScore + points);
        logThreat(reason, points);
        return currentScore;
    }

    // Calculate risk from multiple signals
    public static int calculate(
            boolean scamCall,
            boolean collectRequest,
            boolean unknownUpi,
            boolean communityReport,
            boolean overlayAttack,
            boolean remoteAccess) {

        int risk = 0;
        if (scamCall)       risk += SCORE_SCAM_CALL;
        if (collectRequest) risk += SCORE_COLLECT_REQUEST;
        if (unknownUpi)     risk += SCORE_UNKNOWN_UPI;
        if (communityReport)risk += SCORE_COMMUNITY_REPORT;
        if (overlayAttack)  risk += SCORE_OVERLAY_ATTACK;
        if (remoteAccess)   risk += SCORE_REMOTE_ACCESS;
        return Math.min(risk, 100);
    }

    public int getCurrentScore() { return currentScore; }

    public void resetScore() { currentScore = 0; }

    public int getRiskLevel() {
        if (currentScore >= 70) return RISK_HIGH;
        if (currentScore >= 40) return RISK_SUSPICIOUS;
        return RISK_SAFE;
    }

    public String getRiskLabel() {
        switch (getRiskLevel()) {
            case RISK_HIGH:       return "HIGH RISK";
            case RISK_SUSPICIOUS: return "SUSPICIOUS";
            default:              return "SAFE";
        }
    }

    public String getRiskHindi() {
        switch (getRiskLevel()) {
            case RISK_HIGH:       return "खतरा — तुरंत रुकें!";
            case RISK_SUSPICIOUS: return "संदिग्ध गतिविधि";
            default:              return "सुरक्षित";
        }
    }

    private void logThreat(String reason, int score) {
        SharedPreferences prefs = context.getSharedPreferences("threat_log", Context.MODE_PRIVATE);
        String existing = prefs.getString("log", "");
        String entry = System.currentTimeMillis() + "|" + score + "|" + reason + "\n";
        prefs.edit().putString("log", entry + existing).apply();
    }

    public String getThreatLog() {
        return context.getSharedPreferences("threat_log", Context.MODE_PRIVATE)
                .getString("log", "No threats logged.");
    }
}
