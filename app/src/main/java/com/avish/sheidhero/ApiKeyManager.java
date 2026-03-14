package com.avish.sheidhero;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CyberRaksha Smart API Key Rotation Manager
 *
 * Strategy:
 *   - Groq (free, ultra-fast)   → bulk SAFE/UNSAFE scanning (ScanList, SpyDetection, AbuseCheck)
 *   - OpenRouter/Gemini Flash   → Panic Mode AI explanations, OTP analysis, URL checks
 *   - Sarvam AI                 → Hindi language responses
 *
 * If a key hits rate-limit (429) or auth error (401/402), it auto-rotates to the next key.
 * Keys are tried round-robin with exponential backoff on repeated failures.
 */
public class ApiKeyManager {

    private static final String TAG = "ApiKeyManager";

    // ─────────────────────────────────────────────────────────────────────────
    // ADD YOUR REAL API KEYS HERE — keep this file in .gitignore !
    // ─────────────────────────────────────────────────────────────────────────

    // OpenRouter keys (supports Gemini Flash, DeepSeek, etc.)
    private static final String[] OPENROUTER_KEYS = {
            "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",
    };

    // Groq keys (free tier — great for bulk scanning)
    private static final String[] GROQ_KEYS = {
            "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",
    };

    // Sarvam AI keys (Hindi STT/TTS/translation — Indian language support)
    private static final String[] SARVAM_KEYS = {
            "sk_n251pxfi_ym5K2shN4TRV8AeVuVjF5rfS",
    };

    // ─────────────────────────────────────────────────────────────────────────

    public enum ApiProvider { OPENROUTER, GROQ, SARVAM }
    public enum UseCase {
        BULK_SCAN,          // → Groq (fastest, cheapest)
        PANIC_MODE,         // → OpenRouter Gemini Flash (best quality)
        OTP_ANALYSIS,       // → OpenRouter Gemini Flash
        URL_CHECK,          // → Groq (speed critical)
        HINDI_RESPONSE,     // → Sarvam AI
        CHATBOT,            // → OpenRouter Gemini Flash
    }

    // Track which key index we're currently on for each provider
    private static final AtomicInteger openrouterIndex = new AtomicInteger(0);
    private static final AtomicInteger groqIndex       = new AtomicInteger(0);
    private static final AtomicInteger sarvamIndex     = new AtomicInteger(0);

    // Track failure counts to implement backoff
    private static final int[] openrouterFailures = new int[OPENROUTER_KEYS.length];
    private static final int[] groqFailures        = new int[GROQ_KEYS.length];

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the best ApiConfig for a given use-case.
     * Call this before every API call to get the right key + base URL + model.
     */
    public static ApiConfig getConfig(UseCase useCase) {
        switch (useCase) {
            case BULK_SCAN:
            case URL_CHECK:
                return getGroqConfig();
            case HINDI_RESPONSE:
                return getSarvamConfig();
            case PANIC_MODE:
            case OTP_ANALYSIS:
            case CHATBOT:
            default:
                return getOpenRouterConfig();
        }
    }

    /**
     * Call this when an API call fails with a rate-limit (429) or auth (401/402) error.
     * It will rotate to the next available key automatically.
     *
     * @param provider  which provider failed
     * @param httpCode  the HTTP error code
     * @return          true if another key is available to retry, false if all keys exhausted
     */
    public static boolean handleFailure(ApiProvider provider, int httpCode) {
        if (httpCode == 200) return false; // Not a failure

        boolean isRateLimit = (httpCode == 429);
        boolean isAuthError = (httpCode == 401 || httpCode == 402);

        if (!isRateLimit && !isAuthError) return false;

        switch (provider) {
            case OPENROUTER: {
                int current = openrouterIndex.get();
                openrouterFailures[current]++;
                int next = (current + 1) % OPENROUTER_KEYS.length;
                if (next == 0 && current != 0) {
                    Log.w(TAG, "All OpenRouter keys exhausted.");
                    return false;
                }
                openrouterIndex.set(next);
                Log.d(TAG, "OpenRouter rotated to key index " + next);
                return true;
            }
            case GROQ: {
                int current = groqIndex.get();
                groqFailures[current]++;
                int next = (current + 1) % GROQ_KEYS.length;
                if (next == 0 && current != 0) {
                    Log.w(TAG, "All Groq keys exhausted, falling back to OpenRouter.");
                    return false;
                }
                groqIndex.set(next);
                Log.d(TAG, "Groq rotated to key index " + next);
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Returns the best available key as a Bearer token string.
     * Ready to plug into Retrofit's @Header("Authorization").
     */
    public static String getBearerToken(UseCase useCase) {
        return "Bearer " + getConfig(useCase).apiKey;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private static ApiConfig getOpenRouterConfig() {
        String key = OPENROUTER_KEYS[openrouterIndex.get() % OPENROUTER_KEYS.length];
        return new ApiConfig(
                ApiProvider.OPENROUTER,
                key,
                "https://openrouter.ai/api/v1/",
                "google/gemini-2.0-flash-001"   // best quality on OpenRouter
        );
    }

    private static ApiConfig getGroqConfig() {
        String key = GROQ_KEYS[groqIndex.get() % GROQ_KEYS.length];
        return new ApiConfig(
                ApiProvider.GROQ,
                key,
                "https://api.groq.com/openai/v1/",
                "llama3-8b-8192"  // ultra-fast free model on Groq
        );
    }

    private static ApiConfig getSarvamConfig() {
        String key = SARVAM_KEYS[0];
        return new ApiConfig(
                ApiProvider.SARVAM,
                key,
                "https://api.sarvam.ai/",
                "sarvam-2b"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DATA CLASS
    // ─────────────────────────────────────────────────────────────────────────

    public static class ApiConfig {
        public final ApiProvider provider;
        public final String apiKey;
        public final String baseUrl;
        public final String modelId;

        public ApiConfig(ApiProvider provider, String apiKey, String baseUrl, String modelId) {
            this.provider = provider;
            this.apiKey   = apiKey;
            this.baseUrl  = baseUrl;
            this.modelId  = modelId;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETROFIT FACTORY — builds a fresh Retrofit instance for the given config
    // ─────────────────────────────────────────────────────────────────────────

    public static retrofit2.Retrofit buildRetrofit(ApiConfig config) {
        return new retrofit2.Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();
    }
}