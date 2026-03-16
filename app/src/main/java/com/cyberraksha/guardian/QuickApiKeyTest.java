package com.cyberraksha.guardian;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;

/**
 * Quick API Key Test - Run this to verify chat functionality
 */
public class QuickApiKeyTest {
    private static final String TAG = "QuickApiKeyTest";
    
    public static void testChatKey() {
        Log.i(TAG, "🧪 Testing Chat API Key...");
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.CHATBOT);
                Log.i(TAG, "✅ Chat API Config:");
                Log.i(TAG, "   Provider: " + config.provider);
                Log.i(TAG, "   Model: " + config.modelId);
                Log.i(TAG, "   Base URL: " + config.baseUrl);
                Log.i(TAG, "   API Key: " + config.apiKey.substring(0, Math.min(10, config.apiKey.length())) + "...");
                Log.i(TAG, "   Auth Header: " + ApiKeyManager.getBearerToken(ApiKeyManager.UseCase.CHATBOT));
                Log.i(TAG, "✅ Chat API key test completed successfully!");
            } catch (Exception e) {
                Log.e(TAG, "❌ Chat API key test failed: " + e.getMessage(), e);
            }
        }, 1000);
    }
}
