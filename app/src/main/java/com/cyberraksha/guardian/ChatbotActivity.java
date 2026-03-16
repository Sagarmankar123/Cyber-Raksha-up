package com.cyberraksha.guardian;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatbotActivity extends AppCompatActivity {

    private EditText etMessage;
    private LinearLayout chatMessagesContainer;
    private ScrollView chatScrollView;
    private GeminiService geminiService;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_chatbot);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true); // Light background

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.chatbotToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etMessage = findViewById(R.id.etMessage);
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer);
        chatScrollView = findViewById(R.id.chatScrollView);

        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String question = etMessage.getText().toString().trim();
            if (!question.isEmpty()) {
                addToChat(question, true);
                etMessage.setText("");
                getResponse(question);
            } else {
                Toast.makeText(this, "Enter message", Toast.LENGTH_SHORT).show();
            }
        });
        
        initDeepSeekService();
    }

    private void initDeepSeekService() {
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.CHATBOT);
        geminiService = ApiKeyManager.buildRetrofit(config).create(GeminiService.class);
    }

    private void addToChat(String text, boolean isUser) {
        runOnUiThread(() -> {
            View messageView;
            if (isUser) {
                messageView = getLayoutInflater().inflate(R.layout.item_message_user, chatMessagesContainer, false);
            } else {
                messageView = getLayoutInflater().inflate(R.layout.item_message_bot, chatMessagesContainer, false);
            }
            
            android.widget.TextView messageTextView = messageView.findViewById(R.id.tvMessage);
            messageTextView.setText(text);
            
            chatMessagesContainer.addView(messageView);
            chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void getResponse(String question) {
        getResponseWithRetry(question, 0);
    }

    private void getResponseWithRetry(String question, int retryCount) {
        if (retryCount >= 6) { // Increased retry limit for fallback
            addToChat("AI Error: All API keys exhausted. Please try again later.", false);
            return;
        }

        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.CHATBOT);
        String authHeader = ApiKeyManager.getBearerToken(ApiKeyManager.UseCase.CHATBOT);
        GeminiRequest request = new GeminiRequest("You are CyberRaksha AI, an expert Indian cyber security assistant built into the CyberRaksha app. " +
            "You help Indian users with: UPI fraud, scam calls, phishing, OTP theft, cyber crime reporting, " +
            "safe internet usage, and device security. " +
            "Always reply in the same language as user writes in (Hindi or English). " +
            "Be concise, friendly, and give practical actionable advice. " +
            "For emergencies, always mention: call 1930 (National Cyber Crime Helpline) or visit cybercrime.gov.in.\n\nUser: " + question);
        request.setModel(config.modelId);

        geminiService.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String resultText = response.body().getResponseText();
                    addToChat(resultText.trim(), false);
                } else {
                    String errorMsg = "AI Error: Service unavailable.";
                    if (response.code() == 400) errorMsg = "AI Error: Invalid Model or Request.";
                    if (response.code() == 401) {
                        errorMsg = "AI Error: Invalid API Key. Rotating key...";
                        boolean hasMoreKeys = ApiKeyManager.handleFailure(config.provider, response.code());
                        if (hasMoreKeys) {
                            handler.postDelayed(() -> getResponseWithRetry(question, retryCount + 1), 1000);
                        } else {
                            addToChat(errorMsg, false);
                        }
                        return;
                    }
                    if (response.code() == 402) errorMsg = "AI Error: Out of credits.";
                    if (response.code() == 404) errorMsg = "AI Error: Model not found.";
                    if (response.code() == 429) {
                        errorMsg = "AI Error: Too many requests. Retrying with new key...";
                        boolean hasMoreKeys = ApiKeyManager.handleFailure(config.provider, response.code());
                        if (hasMoreKeys) {
                            handler.postDelayed(() -> getResponseWithRetry(question, retryCount + 1), 1000);
                        } else {
                            addToChat(errorMsg, false);
                        }
                        return;
                    }
                    
                    addToChat(errorMsg, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                addToChat("AI Error: Connection failed.", false);
                Log.e("Chatbot", "Failure", t);
            }
        });
    }

    private void initOpenRouterFallback() {
        ApiKeyManager.ApiConfig config = new ApiKeyManager.ApiConfig(
            ApiKeyManager.ApiProvider.OPENROUTER,
            "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",
            "https://openrouter.ai/api/v1/",
            "meta-llama/llama-3.2-3b-instruct:free"
        );
        geminiService = ApiKeyManager.buildRetrofit(config).create(GeminiService.class);
    }
}
