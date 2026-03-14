package com.avish.sheidhero;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;

public class SpamDatabase {
    private static SpamDatabase instance;
    private final HashMap<String, String[]> spamMap = new HashMap<>();
    private boolean loaded = false;

    public static SpamDatabase getInstance(Context context) {
        if (instance == null) instance = new SpamDatabase(context);
        return instance;
    }

    private SpamDatabase(Context context) {
        new Thread(() -> {
            try {
                // Use the correct filename found in assets
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open("spam_numbers .csv")));
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (first) { first = false; continue; } // skip header
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        String num = normalizeNumber(parts[0].trim());
                        spamMap.put(num, new String[]{parts[1].trim(), parts[2].trim()});
                    }
                }
                reader.close();
                loaded = true;
                Log.d("SpamDatabase", "CSV loaded: " + spamMap.size() + " entries");
            } catch (Exception e) {
                Log.e("SpamDatabase", "Failed to load CSV", e);
            }
        }).start();
    }

    private String normalizeNumber(String number) {
        if (number == null) return "";
        number = number.replaceAll("[\\s\\-\\(\\)]", "");
        if (number.startsWith("+91")) number = number.substring(3);
        else if (number.startsWith("0091")) number = number.substring(4);
        else if (number.startsWith("0")) number = number.substring(1);
        if (number.length() > 10) number = number.substring(number.length() - 10);
        return number;
    }

    public boolean isSpam(String number) {
        return loaded && spamMap.containsKey(normalizeNumber(number));
    }

    public String getSpamLabel(String number) {
        String[] data = spamMap.get(normalizeNumber(number));
        if (data == null) return "✅ SAFE";
        int score = 0;
        try { score = Integer.parseInt(data[1]); } catch (Exception ignored) {}
        String cat = data[0];
        String label = cat.equals("SCM") ? "Scam" :
                       cat.equals("FRD") ? "Fraud" :
                       cat.equals("BOT") ? "Robocall" :
                       cat.equals("TLM") ? "Telemarketing" :
                       cat.equals("LNS") ? "Loan Spam" :
                       cat.equals("INS") ? "Insurance Spam" :
                       cat.equals("PHI") ? "Phishing" :
                       cat.equals("HAR") ? "Harassment" : cat;
        if (score >= 80) return "🔴 HIGH RISK · " + label;
        return "⚠️ SPAM · " + label;
    }

    public String getRiskLevel(String number) {
        String[] data = spamMap.get(normalizeNumber(number));
        if (data == null) return "UNKNOWN";
        int score = 0;
        try { score = Integer.parseInt(data[1]); } catch (Exception ignored) {}
        if (score >= 80) return "HIGH";
        return "MEDIUM";
    }
}
