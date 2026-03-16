package com.cyberraksha.guardian;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

/**
 * Behavioral DNA Engine
 * Learns user's normal behavior and flags anomalies.
 * All data stored locally — zero cloud tracking.
 */
public class BehaviorService {

    private static final String PREFS_NAME     = "behavioral_dna";
    private static final String KEY_UPI_IDS    = "known_upi_ids";
    private static final String KEY_APPS       = "known_apps";
    private static final String KEY_LAST_BANK  = "last_banking_hour";

    private final SharedPreferences prefs;
    private final ThreatEngine threatEngine;

    public BehaviorService(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        threatEngine = ThreatEngine.getInstance(ctx);
    }

    // ── UPI ID TRUST ──────────────────────────────────────────────────────────

    /** Call this after a successful payment — marks UPI ID as trusted */
    public void markUpiTrusted(String upiId) {
        Set<String> known = getKnownUpiIds();
        known.add(upiId.toLowerCase().trim());
        saveSet(KEY_UPI_IDS, known);
    }

    /** Returns true if this UPI ID has been seen before */
    public boolean isKnownUpiId(String upiId) {
        return getKnownUpiIds().contains(upiId.toLowerCase().trim());
    }

    /**
     * Checks a UPI ID against behavioral baseline.
     * Returns risk score added (0 if trusted, 20 if unknown).
     */
    public int checkUpiAnomaly(String upiId) {
        if (!isKnownUpiId(upiId)) {
            threatEngine.addScore(ThreatEngine.SCORE_UNKNOWN_UPI,
                    "Unknown UPI ID: " + upiId);
            return ThreatEngine.SCORE_UNKNOWN_UPI;
        }
        return 0;
    }

    // ── APP INSTALL ANOMALY ───────────────────────────────────────────────────

    /** Call when any app is installed — checks if it's unusual */
    public int checkAppInstallAnomaly(String packageName) {
        Set<String> known = getKnownApps();
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);

        // New app + installed at unusual hour (10PM - 5AM) = suspicious
        if (!known.contains(packageName) && (hour >= 22 || hour <= 5)) {
            threatEngine.addScore(ThreatEngine.SCORE_MALICIOUS_APK,
                    "App installed at unusual hour: " + packageName);
            return ThreatEngine.SCORE_MALICIOUS_APK;
        }

        known.add(packageName);
        saveSet(KEY_APPS, known);
        return 0;
    }

    // ── BANKING TIME PATTERN ──────────────────────────────────────────────────

    /** Call when banking app opens — checks if time is unusual */
    public int checkBankingAnomaly() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int lastHour = prefs.getInt(KEY_LAST_BANK, -1);

        prefs.edit().putInt(KEY_LAST_BANK, hour).apply();

        // Banking at 1AM - 4AM and never done before = flag
        if ((hour >= 1 && hour <= 4) && lastHour == -1) {
            threatEngine.addScore(20, "Banking at unusual hour: " + hour + ":00");
            return 20;
        }
        return 0;
    }

    // ── STATS ─────────────────────────────────────────────────────────────────

    public int getKnownUpiCount()  { return getKnownUpiIds().size(); }
    public int getKnownAppCount()  { return getKnownApps().size(); }

    public String getBehaviorSummary() {
        return "Known UPI IDs: " + getKnownUpiCount()
                + "\nKnown Apps: " + getKnownAppCount()
                + "\nDNA Status: Active";
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private Set<String> getKnownUpiIds() { return loadSet(KEY_UPI_IDS); }
    private Set<String> getKnownApps()   { return loadSet(KEY_APPS); }

    private Set<String> loadSet(String key) {
        Set<String> result = new HashSet<>();
        String json = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) result.add(arr.getString(i));
        } catch (JSONException ignored) {}
        return result;
    }

    private void saveSet(String key, Set<String> set) {
        JSONArray arr = new JSONArray();
        for (String s : set) arr.put(s);
        prefs.edit().putString(key, arr.toString()).apply();
    }

    // ── SEED ON FIRST LAUNCH ──────────────────────────────────────────────────
    private static final String KEY_SEEDED = "dna_seeded_v1";

    /**
     * Seeds Known Apps from all currently installed non-system apps on first run.
     * Fixes "Known Apps = 0" counter. Runs on background thread — safe from onCreate.
     */
    public void seedFromInstalledAppsIfNeeded(android.content.pm.PackageManager pm) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return;
        new Thread(() -> {
            try {
                java.util.List<android.content.pm.PackageInfo> pkgs = pm.getInstalledPackages(0);
                Set<String> known = getKnownApps();
                int added = 0;
                for (android.content.pm.PackageInfo p : pkgs) {
                    if (p.applicationInfo == null) continue;
                    boolean sys = (p.applicationInfo.flags
                            & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (!sys) { known.add(p.packageName); added++; }
                }
                saveSet(KEY_APPS, known);
                prefs.edit().putBoolean(KEY_SEEDED, true).apply();
                android.util.Log.d("BehaviorService", "DNA seeded: " + added + " apps");
            } catch (Exception e) {
                android.util.Log.e("BehaviorService", "Seed error: " + e.getMessage());
            }
        }).start();
    }
}
