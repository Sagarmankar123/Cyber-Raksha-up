package com.cyberraksha.guardian;

import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiKeyTester {
    private static final String TAG = "ApiKeyTester";
    
    /**
     * Test all API keys - call this method to verify all providers are working
     */
    public static void testAllApiKeys() {
        Log.i(TAG, "🔍 Starting comprehensive API key testing...");
        
        testOpenRouterKey();
        testGroqKey();
        testSarvamKey();
        testCloudKey();
        testNvidiaKey();
        
        Log.i(TAG, "✅ All API key tests initiated. Check logs for results.");
    }
    
    public static void testOpenRouterKey() {
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.PANIC_MODE);
        GeminiService service = ApiKeyManager.buildRetrofit(config).create(GeminiService.class);
        
        GeminiRequest request = new GeminiRequest("Test message - respond with 'OK' if you receive this");
        String authHeader = "Bearer " + config.apiKey;
        
        service.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "✅ OpenRouter API Key is VALID");
                    if (response.body() != null) {
                        Log.d(TAG, "Response: " + response.body().getResponseText());
                    }
                } else {
                    Log.e(TAG, "❌ OpenRouter API Key FAILED - Code: " + response.code());
                    try {
                        if (response.errorBody() != null) {
                            Log.e(TAG, "Error: " + response.errorBody().string());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading error body", e);
                    }
                }
            }
            
            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                Log.e(TAG, "❌ OpenRouter API Network FAILED: " + t.getMessage());
            }
        });
    }
    
    public static void testGroqKey() {
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.BULK_SCAN);
        testApiProvider("Groq", config);
    }
    
    public static void testSarvamKey() {
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.HINDI_RESPONSE);
        testApiProvider("Sarvam", config);
    }
    
    public static void testCloudKey() {
        // Create a Cloud config directly since it's not in UseCase enum yet
        ApiKeyManager.ApiConfig config = new ApiKeyManager.ApiConfig(
            ApiKeyManager.ApiProvider.CLOUD,
            "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",
            "https://api.anthropic.com/v1/",
            "claude-3-5-sonnet-20241022"
        );
        testApiProvider("Cloud", config);
    }
    
    public static void testNvidiaKey() {
        // Create an NVIDIA config directly since it's not in UseCase enum yet
        ApiKeyManager.ApiConfig config = new ApiKeyManager.ApiConfig(
            ApiKeyManager.ApiProvider.NVIDIA,
            "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",
            "https://integrate.api.nvidia.com/v1/",
            "nvidia/llama-3.1-nemotron-70b-instruct"
        );
        testApiProvider("NVIDIA", config);
    }
    
    /**
     * Generic method to test any API provider
     */
    private static void testApiProvider(String providerName, ApiKeyManager.ApiConfig config) {
        Log.i(TAG, "🧪 Testing " + providerName + " API Key...");
        
        try {
            GeminiService service = ApiKeyManager.buildRetrofit(config).create(GeminiService.class);
            GeminiRequest request = createTestRequest(config.modelId);
            String authHeader = getAuthHeader(config.provider, config.apiKey);
            
            service.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
                @Override
                public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                    if (response.isSuccessful()) {
                        Log.i(TAG, "✅ " + providerName + " API Key is VALID");
                        if (response.body() != null && !response.body().getResponseText().isEmpty()) {
                            Log.d(TAG, providerName + " Response: " + response.body().getResponseText());
                        } else {
                            Log.w(TAG, providerName + " Response was empty but request succeeded");
                        }
                    } else {
                        Log.e(TAG, "❌ " + providerName + " API Key FAILED - Code: " + response.code());
                        try {
                            if (response.errorBody() != null) {
                                String errorBody = response.errorBody().string();
                                Log.e(TAG, providerName + " Error: " + errorBody);
                                
                                // Analyze common error patterns
                                if (errorBody.contains("401") || errorBody.contains("unauthorized")) {
                                    Log.e(TAG, providerName + " Issue: Invalid API key");
                                } else if (errorBody.contains("429") || errorBody.contains("rate limit")) {
                                    Log.e(TAG, providerName + " Issue: Rate limit exceeded");
                                } else if (errorBody.contains("403") || errorBody.contains("forbidden")) {
                                    Log.e(TAG, providerName + " Issue: Access forbidden - check permissions");
                                } else if (errorBody.contains("404") || errorBody.contains("not found")) {
                                    Log.e(TAG, providerName + " Issue: Model not found or invalid endpoint");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading " + providerName + " error body", e);
                        }
                    }
                }
                
                @Override
                public void onFailure(Call<GeminiResponse> call, Throwable t) {
                    Log.e(TAG, "❌ " + providerName + " API Network FAILED: " + t.getMessage());
                    if (t.getMessage() != null) {
                        if (t.getMessage().contains("timeout")) {
                            Log.e(TAG, providerName + " Issue: Request timeout");
                        } else if (t.getMessage().contains("UnknownHost")) {
                            Log.e(TAG, providerName + " Issue: DNS resolution failed - check internet");
                        } else if (t.getMessage().contains("SSL")) {
                            Log.e(TAG, providerName + " Issue: SSL/TLS error");
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ " + providerName + " Test setup failed: " + e.getMessage());
        }
    }
    
    /**
     * Create appropriate test request based on model
     */
    private static GeminiRequest createTestRequest(String modelId) {
        String testPrompt = "Test message - respond with 'OK' if you receive this";
        
        // Adjust prompt for different providers if needed
        if (modelId.contains("claude")) {
            testPrompt = "Test message - respond with just 'OK' if you receive this";
        } else if (modelId.contains("nemotron")) {
            testPrompt = "Test message - respond with 'OK' if you receive this";
        }
        
        GeminiRequest request = new GeminiRequest(testPrompt);
        request.model = modelId; // Override the default model
        return request;
    }
    
    /**
     * Get appropriate auth header format based on provider
     */
    private static String getAuthHeader(ApiKeyManager.ApiProvider provider, String apiKey) {
        switch (provider) {
            case CLOUD:
                // Anthropic uses different format
                return apiKey; // No Bearer prefix for Anthropic
            case NVIDIA:
                return "Bearer " + apiKey;
            default:
                return "Bearer " + apiKey;
        }
    }
}
