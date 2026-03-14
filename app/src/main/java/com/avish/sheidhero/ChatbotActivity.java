package com.avish.sheidhero;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class ChatbotActivity extends AppCompatActivity {

    private EditText etMessage;
    private LinearLayout chatMessagesContainer;
    private ScrollView chatScrollView;
    private OpenAIService deepSeekService;

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

        AppBarLayout appBarLayout = findViewById(R.id.chatbotToolbar).getParent() instanceof AppBarLayout ? (AppBarLayout) findViewById(R.id.chatbotToolbar).getParent() : null;
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }
        
        // Input area should respect navigation bar
        View inputArea = findViewById(R.id.inputArea);
        if (inputArea != null) {
            ViewCompat.setOnApplyWindowInsetsListener(inputArea, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Math.max(systemBars.bottom, ime.bottom));
                return insets;
            });
        }

        MaterialToolbar toolbar = findViewById(R.id.chatbotToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etMessage = findViewById(R.id.etMessage);
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer);
        FloatingActionButton btnSend = findViewById(R.id.btnSend);
        chatScrollView = findViewById(R.id.chatScrollView);

        initDeepSeekService();

        addToChat("Hello! I'm your ShieldHero AI assistant. How can I help you with your device security today?", false);

        btnSend.setOnClickListener(v -> {
            String question = etMessage.getText().toString().trim();
            if (!question.isEmpty()) {
                addToChat(question, true);
                etMessage.setText("");
                getResponse(question);
            } else {
                Toast.makeText(this, "Enter message", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initDeepSeekService() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        deepSeekService = retrofit.create(OpenAIService.class);
    }

    private void addToChat(String text, boolean isUser) {
        runOnUiThread(() -> {
            View messageView;
            if (isUser) {
                messageView = getLayoutInflater().inflate(R.layout.item_message_user, chatMessagesContainer, false);
            } else {
                messageView = getLayoutInflater().inflate(R.layout.item_message_bot, chatMessagesContainer, false);
            }

            TextView tvMessage = messageView.findViewById(R.id.tvMessage);
            tvMessage.setText(text);

            chatMessagesContainer.addView(messageView);
            
            // Auto scroll to bottom
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void getResponse(String question) {
        List<OpenAIRequest.Message> messages = new ArrayList<>();
        messages.add(new OpenAIRequest.Message("user", question));

        OpenAIRequest request = new OpenAIRequest("google/gemini-2.0-flash-001", messages);
        String authHeader = "Bearer " + Config.OPENROUTER_API_KEY;

        deepSeekService.getChatCompletion(authHeader, request).enqueue(new Callback<OpenAIResponse>() {
            @Override
            public void onResponse(@NonNull Call<OpenAIResponse> call, @NonNull Response<OpenAIResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getChoices() != null && !response.body().getChoices().isEmpty()) {
                    String resultText = response.body().getChoices().get(0).getMessage().getContent();
                    addToChat(resultText.trim(), false);
                } else {
                    String errorMsg = "AI Error: Service unavailable.";
                    if (response.code() == 400) errorMsg = "AI Error: Invalid Model or Request.";
                    if (response.code() == 401) errorMsg = "AI Error: Invalid API Key.";
                    if (response.code() == 402) errorMsg = "AI Error: Out of credits.";
                    if (response.code() == 404) errorMsg = "AI Error: Model not found.";
                    if (response.code() == 429) errorMsg = "AI Error: Too many requests.";
                    
                    try {
                        if (response.errorBody() != null) {
                            Log.e("Chatbot", "Error: " + response.errorBody().string());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    addToChat(errorMsg, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<OpenAIResponse> call, @NonNull Throwable t) {
                addToChat("AI Error: Connection failed.", false);
                Log.e("Chatbot", "Failure", t);
            }
        });
    }

    interface OpenAIService {
        @POST("chat/completions")
        Call<OpenAIResponse> getChatCompletion(@Header("Authorization") String authHeader, @Body OpenAIRequest request);
    }

    static class OpenAIRequest {
        String model;
        List<Message> messages;
        OpenAIRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
        }
        static class Message {
            String role;
            String content;
            Message(String role, String content) {
                this.role = role;
                this.content = content;
            }
        }
    }

    static class OpenAIResponse {
        List<Choice> choices;
        List<Choice> getChoices() { 
            return choices;
        }
        static class Choice {
            Message message;
            Message getMessage() { return message; }
        }
        static class Message {
            String content;
            String getContent() { return content != null ? content : ""; }
        }
    }
}
