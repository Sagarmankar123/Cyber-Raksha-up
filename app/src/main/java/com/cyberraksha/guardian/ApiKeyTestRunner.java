package com.cyberraksha.guardian;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;

/**
 * Standalone API Key Test Runner
 * Call this from anywhere to test all API keys
 */
public class ApiKeyTestRunner {
    private static final String TAG = "ApiKeyTestRunner";
    
    /**
     * Run comprehensive API key tests
     * This method can be called from any Activity or Service
     */
    public static void runAllTests() {
        Log.i(TAG, "🚀 Starting comprehensive API key testing...");
        
        // Test all providers with a small delay between each to avoid rate limiting
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ApiKeyTester.testOpenRouterKey();
        }, 0);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ApiKeyTester.testGroqKey();
        }, 1000);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ApiKeyTester.testSarvamKey();
        }, 2000);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ApiKeyTester.testCloudKey();
        }, 3000);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ApiKeyTester.testNvidiaKey();
        }, 4000);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.i(TAG, "✅ All API key tests completed. Check logs for individual results.");
            Log.i(TAG, "📋 Summary Report:");
            Log.i(TAG, "   - OpenRouter: Gemini Flash model");
            Log.i(TAG, "   - Groq: Llama3-8b model");
            Log.i(TAG, "   - Sarvam: sarvam-2b model");
            Log.i(TAG, "   - Cloud: Claude-3-5-Sonnet model");
            Log.i(TAG, "   - NVIDIA: Nemotron-70b model");
        }, 5000);
    }
    
    /**
     * Quick test for essential providers only
     */
    public static void runEssentialTests() {
        Log.i(TAG, "⚡ Running essential API key tests...");
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ApiKeyTester.testOpenRouterKey();
        }, 0);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ApiKeyTester.testGroqKey();
        }, 1000);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.i(TAG, "✅ Essential tests completed. Check logs for results.");
        }, 2000);
    }
    
    /**
     * Test specific provider
     */
    public static void testProvider(String providerName) {
        switch (providerName.toLowerCase()) {
            case "openrouter":
                ApiKeyTester.testOpenRouterKey();
                break;
            case "groq":
                ApiKeyTester.testGroqKey();
                break;
            case "sarvam":
                ApiKeyTester.testSarvamKey();
                break;
            case "cloud":
                ApiKeyTester.testCloudKey();
                break;
            case "nvidia":
                ApiKeyTester.testNvidiaKey();
                break;
            default:
                Log.e(TAG, "Unknown provider: " + providerName);
                Log.i(TAG, "Available providers: openrouter, groq, sarvam, cloud, nvidia");
        }
    }
}
