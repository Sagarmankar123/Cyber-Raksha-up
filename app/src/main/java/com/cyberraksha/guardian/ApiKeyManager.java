package com.cyberraksha.guardian;

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
            "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",
    };

    // Cloud API keys (Anthropic Claude, etc.)
    private static final String[] CLOUD_KEYS = {
            "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",
    };

    // NVIDIA API keys (Build-Autogen-46)
    private static final String[] NVIDIA_KEYS = {
            "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",
    };

    // Google AI Studio API keys (direct Google access)
    private static final String[] GOOGLE_AI_KEYS = {
            "AIzaSyCVpOW-sdiDEKjKO6o4_dZltVMpIYwYa4g",
    };

    // Groq keys (free tier — great for bulk scanning)
    private static final String[] GROQ_KEYS = {
            "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",
            "REPLACE_WITH_REAL_GROQ_KEY_2",
            "REPLACE_WITH_REAL_GROQ_KEY_3",
            "REPLACE_WITH_REAL_GROQ_KEY_4",
    };

    // Sarvam AI keys (Hindi STT/TTS/translation — Indian language support)
    private static final String[] SARVAM_KEYS = {
            "sk_8gi2ep9n_HQW5OgUHj1zFnM8TM8NEtaWV",
    };

    // ─────────────────────────────────────────────────────────────────────────

    public enum ApiProvider { OPENROUTER, GROQ, SARVAM, CLOUD, NVIDIA, GOOGLE_AI }
    public enum UseCase {
        BULK_SCAN,          // → Groq (fastest, cheapest)
        PANIC_MODE,         // → Groq (better rate limits)
        OTP_ANALYSIS,       // → Groq (speed critical)
        URL_CHECK,          // → Groq (speed critical)
        HINDI_RESPONSE,     // → Sarvam AI
        CHATBOT,            // → Groq (better rate limits), fallback to OpenRouter
        IMAGE_ANALYSIS,     // → OpenRouter Vision Model (for fake image detection)
    }

    // Track which key index we're currently on for each provider
    private static final AtomicInteger openrouterIndex = new AtomicInteger(0);
    private static final AtomicInteger groqIndex       = new AtomicInteger(0);
    private static final AtomicInteger sarvamIndex     = new AtomicInteger(0);
    private static final AtomicInteger cloudIndex      = new AtomicInteger(0);
    private static final AtomicInteger nvidiaIndex     = new AtomicInteger(0);
    private static final AtomicInteger googleAiIndex   = new AtomicInteger(0);

    // Track failure counts to implement backoff
    private static final int[] openrouterFailures = new int[OPENROUTER_KEYS.length];
    private static final int[] groqFailures        = new int[GROQ_KEYS.length];
    private static final int[] cloudFailures       = new int[CLOUD_KEYS.length];
    private static final int[] nvidiaFailures       = new int[NVIDIA_KEYS.length];
    private static final int[] googleAiFailures     = new int[GOOGLE_AI_KEYS.length];

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
            case CHATBOT:
            case PANIC_MODE:
            case OTP_ANALYSIS:
                return getGroqConfig(); // Groq: fast, free, reliable
            case HINDI_RESPONSE:
                return getSarvamConfig();
            case IMAGE_ANALYSIS:
                return getVisionConfig(); // OpenRouter vision model for image analysis
            default:
                return getGroqConfig();
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
                // Skip placeholder keys
                while (next < GROQ_KEYS.length && GROQ_KEYS[next].startsWith("REPLACE_WITH_")) {
                    next = (next + 1) % GROQ_KEYS.length;
                }
                if (next == 0 && current != 0) {
                    Log.w(TAG, "All Groq keys exhausted, falling back to OpenRouter.");
                    return false;
                }
                groqIndex.set(next);
                Log.d(TAG, "Groq rotated to key index " + next);
                return true;
            }
            case CLOUD: {
                int current = cloudIndex.get();
                cloudFailures[current]++;
                int next = (current + 1) % CLOUD_KEYS.length;
                if (next == 0 && current != 0) {
                    Log.w(TAG, "All Cloud keys exhausted.");
                    return false;
                }
                cloudIndex.set(next);
                Log.d(TAG, "Cloud rotated to key index " + next);
                return true;
            }
            case NVIDIA: {
                int current = nvidiaIndex.get();
                nvidiaFailures[current]++;
                int next = (current + 1) % NVIDIA_KEYS.length;
                if (next == 0 && current != 0) {
                    Log.w(TAG, "All NVIDIA keys exhausted.");
                    return false;
                }
                nvidiaIndex.set(next);
                Log.d(TAG, "NVIDIA rotated to key index " + next);
                return true;
            }
            case GOOGLE_AI: {
                int current = googleAiIndex.get();
                googleAiFailures[current]++;
                int next = (current + 1) % GOOGLE_AI_KEYS.length;
                if (next == 0 && current != 0) {
                    Log.w(TAG, "All Google AI keys exhausted.");
                    return false;
                }
                googleAiIndex.set(next);
                Log.d(TAG, "Google AI rotated to key index " + next);
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

    /**
     * Returns Google AI Studio token (no Bearer prefix)
     * Google AI uses direct API key in URL, not Bearer token
     */
    public static String getGoogleAiToken(UseCase useCase) {
        ApiKeyManager.ApiConfig config = getConfig(useCase);
        if (config.provider == ApiProvider.GOOGLE_AI) {
            return config.apiKey;
        }
        return config.apiKey;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private static ApiConfig getGoogleAiConfig() {
        // Google AI native format — used by GeminiClient.java directly (NOT via Retrofit GeminiService)
        String key = GOOGLE_AI_KEYS[googleAiIndex.get() % GOOGLE_AI_KEYS.length];
        return new ApiConfig(
                ApiProvider.GOOGLE_AI,
                key,
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + key,
                "gemini-2.0-flash"   // Google AI Studio - latest working model
        );
    }

    private static ApiConfig getVisionConfig() {
        // OpenRouter vision — used by TrueNewsActivity via Retrofit GeminiService (chat/completions format)
        // Fallback chain: try each free vision model
        String key = OPENROUTER_KEYS[openrouterIndex.get() % OPENROUTER_KEYS.length];
        return new ApiConfig(
                ApiProvider.OPENROUTER,
                key,
                "https://openrouter.ai/api/v1/",
                "qwen/qwen2.5-vl-72b-instruct:free"   // Free vision model on OpenRouter (updated March 2026)
        );
    }

    private static ApiConfig getOpenRouterConfig() {
        String key = OPENROUTER_KEYS[openrouterIndex.get() % OPENROUTER_KEYS.length];
        return new ApiConfig(
                ApiProvider.OPENROUTER,
                key,
                "https://openrouter.ai/api/v1/",
                "meta-llama/llama-3.1-8b-instruct:free"   // Free, working model on OpenRouter
        );
    }

    private static ApiConfig getGroqConfig() {
        String key = GROQ_KEYS[groqIndex.get() % GROQ_KEYS.length];
        // Skip placeholder keys
        while (key.startsWith("REPLACE_WITH_")) {
            int next = (groqIndex.incrementAndGet()) % GROQ_KEYS.length;
            key = GROQ_KEYS[next];
        }
        return new ApiConfig(
                ApiProvider.GROQ,
                key,
                "https://api.groq.com/openai/v1/",
                "llama-3.1-8b-instant"  // Current fast free model on Groq (llama3-8b-8192 is deprecated)
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

    private static ApiConfig getCloudConfig() {
        String key = CLOUD_KEYS[cloudIndex.get() % CLOUD_KEYS.length];
        return new ApiConfig(
                ApiProvider.CLOUD,
                key,
                "https://api.anthropic.com/v1/",
                "claude-3-5-sonnet-20241022"
        );
    }

    private static ApiConfig getNvidiaConfig() {
        String key = NVIDIA_KEYS[nvidiaIndex.get() % NVIDIA_KEYS.length];
        return new ApiConfig(
                ApiProvider.NVIDIA,
                key,
                "https://integrate.api.nvidia.com/v1/",
                "nvidia/llama-3.1-nemotron-70b-instruct"
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