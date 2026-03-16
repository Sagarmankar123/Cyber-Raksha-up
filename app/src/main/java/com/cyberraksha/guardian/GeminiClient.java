package com.cyberraksha.guardian;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Gemini AI Client
 * Provides AI explanations for security threats and news verification.
 * API Key: AIzaSyADRsyWutVGYN1eXX70ANOuK2-_KKnaiBE
 */
public class GeminiClient {

    private static final String TAG = "GeminiClient";
    private static final String API_KEY = "AIzaSyADRsyWutVGYN1eXX70ANOuK2-_KKnaiBE";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final OkHttpClient httpClient;
    private final Context context;

    public GeminiClient(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Get AI explanation for security threat
     */
    public void explainThreat(String threatType, String details, GeminiCallback callback) {
        String prompt = "You are CyberRaksha Security Assistant. Explain this threat in simple terms:\n\n" +
                "Threat: " + threatType + "\n" +
                "Details: " + details + "\n\n" +
                "Provide:\n1. What this means (1 sentence)\n2. Why it's dangerous (1 sentence)\n3. What to do right now (actionable advice)\n" +
                "Keep response under 100 words. Be direct and helpful.";
        
        generateContent(prompt, callback);
    }

    /**
     * Analyze news article for misinformation
     */
    public void checkNews(String headline, String content, GeminiCallback callback) {
        String prompt = "You are a fact-checking AI. Analyze this news:\n\n" +
                "Headline: " + headline + "\n" +
                "Content: " + content + "\n\n" +
                "Respond with ONLY one of these labels:\n" +
                "- TRUE (verified facts)\n" +
                "- MISLEADING (twisted context)\n" +
                "- FALSE (completely fake)\n" +
                "- UNVERIFIED (cannot confirm)\n\n" +
                "Then explain in 1-2 sentences why.";
        
        generateContent(prompt, callback);
    }

    /**
     * Generate security guidance after panic mode scan
     */
    public void generateSecurityGuidance(int threatCount, String threatSummary, GeminiCallback callback) {
        String prompt = "You are CyberRaksha AI Guardian. The user has " + threatCount + " security threats detected:\n" +
                threatSummary + "\n\n" +
                "Generate a 2-sentence explanation in simple English of what these threats mean. " +
                "Then add one line in Hindi explaining the same thing. " +
                "Be reassuring but serious. Keep it under 80 words total.";
        
        generateContent(prompt, callback);
    }

    /**
     * Analyze a base64-encoded image using Gemini's vision capability.
     * Uses the Google AI multimodal format (inline_data) — works with gemini-2.0-flash.
     */
    public void analyzeImageBase64(String base64Image, String prompt, GeminiCallback callback) {
        String url = BASE_URL + "?key=" + API_KEY;

        try {
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();

            // Text part
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);

            // Image part (inline base64)
            JSONObject imagePart = new JSONObject();
            JSONObject inlineData = new JSONObject();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", base64Image);
            imagePart.put("inline_data", inlineData);
            parts.put(imagePart);

            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            // Generation config
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("maxOutputTokens", 512);
            generationConfig.put("temperature", 0.4);
            requestBody.put("generationConfig", generationConfig);

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
            );

            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Image analysis failed: " + e.getMessage());
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        Log.e(TAG, "Image API error: " + response.code() + " - " + errorBody);
                        callback.onError("API error " + response.code() + ": " + errorBody);
                        return;
                    }
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        String text = extractText(json);
                        if (text.isEmpty()) {
                            callback.onError("Empty response from AI");
                        } else {
                            callback.onSuccess(text);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        callback.onError("Failed to parse response");
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Request build error: " + e.getMessage());
            callback.onError("Request failed");
        }
    }

    private void generateContent(String prompt, GeminiCallback callback) {
        String url = BASE_URL + "?key=" + API_KEY;

        try {
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);
            
            // Safety settings - allow all content since we're analyzing potentially harmful stuff
            JSONArray safetySettings = new JSONArray();
            safetySettings.put(createSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"));
            safetySettings.put(createSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"));
            safetySettings.put(createSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"));
            safetySettings.put(createSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE"));
            requestBody.put("safetySettings", safetySettings);
            
            // Generation config
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("maxOutputTokens", 800);
            generationConfig.put("temperature", 0.7);
            requestBody.put("generationConfig", generationConfig);

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
            );

            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API call failed: " + e.getMessage());
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        Log.e(TAG, "API error: " + response.code() + " - " + errorBody);
                        callback.onError("API error: " + response.code());
                        return;
                    }

                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        String text = extractText(json);
                        callback.onSuccess(text);
                    } catch (JSONException e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        callback.onError("Failed to parse response");
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Request build error: " + e.getMessage());
            callback.onError("Request failed");
        }
    }

    private JSONObject createSafetySetting(String category, String threshold) throws JSONException {
        JSONObject setting = new JSONObject();
        setting.put("category", category);
        setting.put("threshold", threshold);
        return setting;
    }

    private String extractText(JSONObject json) throws JSONException {
        if (!json.has("candidates")) return "";
        
        JSONArray candidates = json.getJSONArray("candidates");
        if (candidates.length() == 0) return "";
        
        JSONObject candidate = candidates.getJSONObject(0);
        if (!candidate.has("content")) return "";
        
        JSONObject content = candidate.getJSONObject("content");
        if (!content.has("parts")) return "";
        
        JSONArray parts = content.getJSONArray("parts");
        StringBuilder text = new StringBuilder();
        
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.has("text")) {
                text.append(part.getString("text"));
            }
        }
        
        return text.toString().trim();
    }

    public interface GeminiCallback {
        void onSuccess(String response);
        void onError(String error);
    }
}
