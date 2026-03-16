package com.cyberraksha.guardian;
import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;

public class SpamDatabase {
    private static SpamDatabase instance;
    private final HashMap<String, String[]> spamMap = new HashMap<>();
    private volatile boolean loaded = false;

    public static SpamDatabase getInstance(Context context) {
        if (instance == null) { synchronized (SpamDatabase.class) { if (instance == null) instance = new SpamDatabase(context.getApplicationContext()); } }
        return instance;
    }
    private SpamDatabase(Context context) {
        new Thread(() -> {
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(context.getAssets().open("spam_numbers.csv")));
                String line; boolean first = true;
                while ((line = r.readLine()) != null) {
                    if (first) { first = false; continue; }
                    String[] p = line.split(",");
                    if (p.length >= 3) { String n = norm(p[0].trim()); if (!n.isEmpty()) spamMap.put(n, new String[]{p[1].trim(), p[2].trim()}); }
                }
                r.close(); loaded = true;
                Log.d("SpamDB", "✅ Loaded " + spamMap.size() + " entries");
            } catch (Exception e) { Log.e("SpamDB", "❌ " + e.getMessage()); }
        }).start();
    }
    public boolean isLoaded() { return loaded; }
    private String norm(String n) {
        if (n == null) return "";
        n = n.replaceAll("[\\s\\-\\(\\)\\+]", "");
        if (n.startsWith("91") && n.length() == 12) n = n.substring(2);
        else if (n.startsWith("0091")) n = n.substring(4);
        else if (n.startsWith("0") && n.length() == 11) n = n.substring(1);
        if (n.length() > 10) n = n.substring(n.length() - 10);
        return n;
    }
    public boolean isSpam(String number) { return loaded && spamMap.containsKey(norm(number)); }
    public String getSpamLabel(String number) {
        String[] d = spamMap.get(norm(number));
        if (d == null) return "✅ SAFE";
        int s = 0; try { s = Integer.parseInt(d[1]); } catch (Exception ignored) {}
        String l = d[0].equals("SCM")?"Scam":d[0].equals("FRD")?"Fraud":d[0].equals("BOT")?"Robocall":d[0].equals("TLM")?"Telemarketing":d[0].equals("LNS")?"Loan Spam":d[0].equals("INS")?"Insurance":d[0].equals("PHI")?"Phishing":d[0].equals("HAR")?"Harassment":d[0];
        return (s >= 80 ? "🔴 HIGH RISK · " : "⚠️ SPAM · ") + l;
    }
    public String getRiskLevel(String number) {
        String[] d = spamMap.get(norm(number));
        if (d == null) return "SAFE";
        int s = 0; try { s = Integer.parseInt(d[1]); } catch (Exception ignored) {}
        return s >= 80 ? "HIGH" : "MEDIUM";
    }
}
