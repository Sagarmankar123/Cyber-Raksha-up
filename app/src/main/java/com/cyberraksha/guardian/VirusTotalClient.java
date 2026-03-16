package com.cyberraksha.guardian;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * VirusTotal API Client
 * Scans file hashes against VirusTotal database for malware detection.
 * API Key: 0ded96a109be489fcb37212e9efbdbb690d7463eda10d919a4252a34db1817fe
 */
public class VirusTotalClient {

    private static final String TAG = "VirusTotal";
    private static final String API_KEY = "0ded96a109be489fcb37212e9efbdbb690d7463eda10d919a4252a34db1817fe";
    private static final String BASE_URL = "https://www.virustotal.com/api/v3/";

    private final VirusTotalService service;
    private final Context context;

    public VirusTotalClient(Context ctx) {
        this.context = ctx.getApplicationContext();
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        this.service = retrofit.create(VirusTotalService.class);
    }

    /**
     * Scan a file by its SHA-256 hash
     */
    public void scanFileHash(String filePath, VirusTotalCallback callback) {
        String hash = sha256(filePath);
        if (hash == null) {
            callback.onError("Failed to calculate file hash");
            return;
        }

        service.getFileReport(API_KEY, hash).enqueue(new Callback<VirusTotalResponse>() {
            @Override
            public void onResponse(Call<VirusTotalResponse> call, Response<VirusTotalResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    VirusTotalResponse vtResponse = response.body();
                    ScanResult result = parseResponse(vtResponse);
                    callback.onResult(result);
                } else {
                    // File not found in VT database or API error
                    if (response.code() == 404) {
                        callback.onResult(new ScanResult(hash, 0, 0, "Unknown - Not in database", false));
                    } else {
                        callback.onError("API Error: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<VirusTotalResponse> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage());
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    private ScanResult parseResponse(VirusTotalResponse response) {
        if (response.data == null || response.data.attributes == null 
            || response.data.attributes.last_analysis_stats == null) {
            return new ScanResult("", 0, 0, "Invalid response", false);
        }

        VirusTotalResponse.LastAnalysisStats stats = response.data.attributes.last_analysis_stats;
        int malicious = stats.malicious;
        int suspicious = stats.suspicious;
        int total = stats.malicious + stats.suspicious + stats.undetected + stats.harmless;
        
        String status;
        boolean isThreat;
        if (malicious > 0) {
            status = "MALICIOUS (" + malicious + "/" + total + " detections)";
            isThreat = true;
        } else if (suspicious > 0) {
            status = "SUSPICIOUS (" + suspicious + "/" + total + " flags)";
            isThreat = true;
        } else {
            status = "CLEAN (" + stats.harmless + "/" + total + " clean)";
            isThreat = false;
        }

        return new ScanResult("", malicious + suspicious, total, status, isThreat);
    }

    private String sha256(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return null;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();

            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Hash error: " + e.getMessage());
            return null;
        }
    }

    public interface VirusTotalCallback {
        void onResult(ScanResult result);
        void onError(String error);
    }

    public static class ScanResult {
        public final String hash;
        public final int detections;
        public final int totalEngines;
        public final String status;
        public final boolean isThreat;

        public ScanResult(String hash, int detections, int totalEngines, String status, boolean isThreat) {
            this.hash = hash;
            this.detections = detections;
            this.totalEngines = totalEngines;
            this.status = status;
            this.isThreat = isThreat;
        }
    }
}
