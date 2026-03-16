package com.cyberraksha.guardian;

import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Direct API Key Test - Tests the actual API key validity
 */
public class DirectApiKeyTest {
    private static final String TAG = "DirectApiKeyTest";
    
    public static void testOpenRouterKey() {
        Log.i(TAG, "🔍 Testing OpenRouter key directly...");
        
        try {
            ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.CHATBOT);
            GeminiService service = ApiKeyManager.buildRetrofit(config).create(GeminiService.class);
            
            GeminiRequest request = new GeminiRequest("Test message - respond with 'OK' if you receive this");
            String authHeader = ApiKeyManager.getBearerToken(ApiKeyManager.UseCase.CHATBOT);
            
            Log.i(TAG, "📤 Sending request with:");
            Log.i(TAG, "   URL: " + config.baseUrl);
            Log.i(TAG, "   Model: " + config.modelId);
            Log.i(TAG, "   Auth: " + authHeader.substring(0, Math.min(20, authHeader.length())) + "...");
            
            service.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
                @Override
                public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                    Log.i(TAG, "📥 Response received:");
                    Log.i(TAG, "   Code: " + response.code());
                    Log.i(TAG, "   Success: " + response.isSuccessful());
                    Log.i(TAG, "   Message: " + response.message());
                    
                    if (response.isSuccessful()) {
                        Log.i(TAG, "✅ API KEY IS VALID!");
                        if (response.body() != null) {
                            Log.i(TAG, "   Response: " + response.body().getResponseText());
                        }
                    } else {
                        Log.e(TAG, "❌ API KEY FAILED!");
                        try {
                            if (response.errorBody() != null) {
                                String errorBody = response.errorBody().string();
                                Log.e(TAG, "   Error: " + errorBody);
                                
                                if (errorBody.contains("invalid") || errorBody.contains("401")) {
                                    Log.e(TAG, "   🔑 ISSUE: Invalid API Key");
                                } else if (errorBody.contains("rate") || errorBody.contains("429")) {
                                    Log.e(TAG, "   ⏱️ ISSUE: Rate Limited");
                                } else if (errorBody.contains("model") || errorBody.contains("404")) {
                                    Log.e(TAG, "   🤖 ISSUE: Model Not Found");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "   Error reading error body", e);
                        }
                    }
                }
                
                @Override
                public void onFailure(Call<GeminiResponse> call, Throwable t) {
                    Log.e(TAG, "❌ NETWORK FAILED!");
                    Log.e(TAG, "   Error: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ TEST SETUP FAILED: " + e.getMessage(), e);
        }
    }
}
