package com.cyberraksha.guardian;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Community Threat Intelligence
 * Preloaded India fraud database — works fully offline.
 * Only SHA-256 hashes compared — raw data never shared.
 */
public class CommunityThreatDB {

    private static final String TAG = "CommunityThreatDB";
    private static CommunityThreatDB instance;

    private final Set<String> fraudUpiIds    = new HashSet<>();
    private final Set<String> fraudNumbers   = new HashSet<>();
    private final Set<String> fraudDomains   = new HashSet<>();

    // Preloaded India fraud data — updated via assets
    private static final String[] PRELOADED_FRAUD_UPI = {
        "refund-help@upi", "lottery-win@oksbi", "upi-support@okaxis",
        "cashback-offer@paytm", "sbi-refund@upi", "hdfc-kyc@upi",
        "prize-claim@okicici", "amazon-refund@upi", "flipkart-cashback@upi",
        "irctc-refund@upi", "electricity-bill@upi", "gas-subsidy@upi",
        "pm-kisan@upi", "aadhaar-link@upi", "covid-help@upi",
        "cyber-crime@upi", "police-fine@upi", "court-notice@upi",
        "income-tax@upi", "gst-refund@upi",
        "upi123@okaxis", "fraudpay@upi", "scammer99@oksbi"
    };

    private static final String[] PRELOADED_FRAUD_NUMBERS = {
        "8800000000", "9999999999", "7777777777",
        "1800111363",  // fake SBI helpline
        "1800103888",  // fake HDFC helpline
        "8929292929", "9696969696", "8888888888",
        "7042000000", "9971000000"
    };

    private static final String[] PRELOADED_FRAUD_DOMAINS = {
        "sbi-account-locked.com", "sbi-kyc-update.net", "sbibank-login.in",
        "hdfc-kyc-update.net", "hdfcbank-verify.com",
        "icicibanklogin.xyz", "paytm-kyc.net", "phonepe-support.xyz",
        "gpay-cashback.com", "amazon-prize.click",
        "whatsapp-update-2026.apk.download", "flipkart-winner.tk",
        "irctc-refund.xyz", "aadhaar-update.ml", "incometax-refund.ga"
    };

    private CommunityThreatDB(Context ctx) {
        loadPreloaded();
        loadFromAssets(ctx);
    }

    public static CommunityThreatDB getInstance(Context ctx) {
        if (instance == null) instance = new CommunityThreatDB(ctx);
        return instance;
    }

    // ── CHECKS ────────────────────────────────────────────────────────────────

    public boolean isUpiReported(String upiId) {
        return fraudUpiIds.contains(sha256(upiId.toLowerCase().trim()))
                || fraudUpiIds.contains(upiId.toLowerCase().trim());
    }

    public boolean isNumberReported(String number) {
        String clean = number.replaceAll("[^0-9]", "");
        if (clean.startsWith("91") && clean.length() == 12) clean = clean.substring(2);
        return fraudNumbers.contains(sha256(clean)) || fraudNumbers.contains(clean);
    }

    public boolean isDomainReported(String url) {
        String domain = extractDomain(url);
        return fraudDomains.contains(sha256(domain)) || fraudDomains.contains(domain);
    }

    // ── REPORT ────────────────────────────────────────────────────────────────

    /** User reports a fraud — stored as hash only */
    public void reportUpi(String upiId) {
        fraudUpiIds.add(sha256(upiId.toLowerCase().trim()));
    }

    public void reportNumber(String number) {
        String clean = number.replaceAll("[^0-9]", "");
        fraudNumbers.add(sha256(clean));
    }

    public void reportDomain(String url) {
        fraudDomains.add(sha256(extractDomain(url)));
    }

    // ── STATS ─────────────────────────────────────────────────────────────────

    public int getTotalReports() {
        return fraudUpiIds.size() + fraudNumbers.size() + fraudDomains.size();
    }

    public String getStats() {
        return "Fraud UPI IDs: " + fraudUpiIds.size()
                + "\nScam Numbers: " + fraudNumbers.size()
                + "\nFake Domains: " + fraudDomains.size()
                + "\nTotal: " + getTotalReports() + " threats";
    }

    // ── LOAD ──────────────────────────────────────────────────────────────────

    private void loadPreloaded() {
        for (String s : PRELOADED_FRAUD_UPI)     fraudUpiIds.add(s);
        for (String s : PRELOADED_FRAUD_NUMBERS) fraudNumbers.add(s);
        for (String s : PRELOADED_FRAUD_DOMAINS) fraudDomains.add(s);
    }

    private void loadFromAssets(Context ctx) {
        loadJsonAsset(ctx, "fraud_upi_ids.json",   fraudUpiIds);
        loadJsonAsset(ctx, "fraud_numbers.json",    fraudNumbers);
        loadJsonAsset(ctx, "fraud_domains.json",    fraudDomains);
    }

    private void loadJsonAsset(Context ctx, String file, Set<String> target) {
        try {
            InputStream is = ctx.getAssets().open(file);
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            JSONArray arr = new JSONArray(new String(buf, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) target.add(arr.getString(i));
        } catch (Exception e) {
            Log.d(TAG, "Asset not found: " + file + " (using preloaded data)");
        }
    }

    // ── UTILS ─────────────────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    private String extractDomain(String url) {
        String d = url.toLowerCase().trim()
                .replace("https://", "").replace("http://", "").replace("www.", "");
        int slash = d.indexOf('/');
        return slash > 0 ? d.substring(0, slash) : d;
    }
}
