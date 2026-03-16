package com.cyberraksha.guardian;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks detected threats across all modules
 */
public class ThreatTracker {
    private static final String TAG = "ThreatTracker";
    private static final String PREFS = "cyberraksha_threats";
    private static final String KEY_THREATS = "threat_list";
    private static final int MAX_STORED_THREATS = 100;

    public static class Threat {
        public final String id;
        public final String type;
        public final String source;
        public final String details;
        public final long timestamp;
        public boolean isActive;

        public Threat(String type, String source, String details) {
            this.id = System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
            this.type = type;
            this.source = source;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
            this.isActive = true;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("type", type);
                obj.put("source", source);
                obj.put("details", details);
                obj.put("timestamp", timestamp);
                obj.put("isActive", isActive);
            } catch (Exception e) {
                Log.e(TAG, "Error converting threat to JSON", e);
            }
            return obj;
        }

        public static Threat fromJson(JSONObject obj) {
            try {
                Threat t = new Threat(
                    obj.optString("type", "unknown"),
                    obj.optString("source", "unknown"),
                    obj.optString("details", "")
                );
                // Override the generated id with stored one
                java.lang.reflect.Field idField = Threat.class.getField("id");
                idField.setAccessible(true);
                // id is final, can't change it - that's ok
                t.isActive = obj.optBoolean("isActive", true);
                return t;
            } catch (Exception e) {
                Log.e(TAG, "Error parsing threat from JSON", e);
                return null;
            }
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final List<Threat> activeThreats;

    public ThreatTracker(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.activeThreats = new CopyOnWriteArrayList<>();
        loadThreats();
    }

    /**
     * Add a new threat
     */
    public synchronized void addThreat(String type, String source, String details) {
        Threat threat = new Threat(type, source, details);
        activeThreats.add(0, threat); // Add to beginning (newest first)

        // Keep only recent threats
        if (activeThreats.size() > MAX_STORED_THREATS) {
            activeThreats.remove(activeThreats.size() - 1);
        }

        saveThreats();
        Log.d(TAG, "Added threat: " + type + " from " + source);
    }

    /**
     * Get count of active threats
     */
    public int getActiveThreatCount() {
        int count = 0;
        for (Threat t : activeThreats) {
            if (t.isActive) count++;
        }
        return count;
    }

    /**
     * Get total threats (including resolved)
     */
    public int getTotalThreatCount() {
        return activeThreats.size();
    }

    /**
     * Get all threats
     */
    public List<Threat> getAllThreats() {
        return new ArrayList<>(activeThreats);
    }

    /**
     * Mark threat as resolved
     */
    public void resolveThreat(String threatId) {
        for (Threat t : activeThreats) {
            if (t.id.equals(threatId)) {
                t.isActive = false;
                saveThreats();
                Log.d(TAG, "Resolved threat: " + threatId);
                return;
            }
        }
    }

    /**
     * Clear all threats
     */
    public void clearThreats() {
        activeThreats.clear();
        prefs.edit().remove(KEY_THREATS).apply();
        Log.d(TAG, "All threats cleared");
    }

    /**
     * Get threats by type
     */
    public List<Threat> getThreatsByType(String type) {
        List<Threat> result = new ArrayList<>();
        for (Threat t : activeThreats) {
            if (t.type.equalsIgnoreCase(type) && t.isActive) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Get summary of threats by category
     */
    public ThreatSummary getSummary() {
        int scamSMS = 0, suspiciousCalls = 0, flaggedApps = 0, maliciousLinks = 0;

        for (Threat t : activeThreats) {
            if (!t.isActive) continue;

            switch (t.type.toLowerCase()) {
                case "scam_sms":
                case "fraud_sms":
                case "phishing_sms":
                    scamSMS++;
                    break;
                case "scam_call":
                case "suspicious_call":
                case "spam_call":
                    suspiciousCalls++;
                    break;
                case "malicious_app":
                case "flagged_app":
                case "risky_app":
                    flaggedApps++;
                    break;
                case "malicious_url":
                case "phishing_url":
                case "unsafe_link":
                    maliciousLinks++;
                    break;
            }
        }

        return new ThreatSummary(scamSMS, suspiciousCalls, flaggedApps, maliciousLinks);
    }

    public static class ThreatSummary {
        public final int scamSMS;
        public final int suspiciousCalls;
        public final int flaggedApps;
        public final int maliciousLinks;
        public final int total;

        public ThreatSummary(int scamSMS, int suspiciousCalls, int flaggedApps, int maliciousLinks) {
            this.scamSMS = scamSMS;
            this.suspiciousCalls = suspiciousCalls;
            this.flaggedApps = flaggedApps;
            this.maliciousLinks = maliciousLinks;
            this.total = scamSMS + suspiciousCalls + flaggedApps + maliciousLinks;
        }
    }

    private void saveThreats() {
        try {
            JSONArray array = new JSONArray();
            for (Threat t : activeThreats) {
                array.put(t.toJson());
            }
            prefs.edit().putString(KEY_THREATS, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving threats", e);
        }
    }

    private void loadThreats() {
        try {
            String json = prefs.getString(KEY_THREATS, null);
            if (json == null) return;

            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    // Create threat manually since id is final
                    String type = obj.optString("type", "unknown");
                    String source = obj.optString("source", "unknown");
                    String details = obj.optString("details", "");
                    Threat t = new Threat(type, source, details);
                    t.isActive = obj.optBoolean("isActive", true);
                    activeThreats.add(t);
                }
            }
            Log.d(TAG, "Loaded " + activeThreats.size() + " threats from storage");
        } catch (Exception e) {
            Log.e(TAG, "Error loading threats", e);
        }
    }
}
