package com.avish.sheidhero;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NewsDetectionActivity extends AppCompatActivity {

    private TextInputEditText etNewsContent;
    private MaterialButton btnVerify;
    private ProgressBar progressBar;
    private MaterialCardView resultCard;
    private TextView resultContent;
    private GeminiService geminiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_detection);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etNewsContent = findViewById(R.id.etNewsContent);
        btnVerify = findViewById(R.id.btnVerifyNews);
        progressBar = findViewById(R.id.newsProgressBar);
        resultCard = findViewById(R.id.newsResultCard);
        resultContent = findViewById(R.id.newsResultContent);

        initGeminiService();

        btnVerify.setOnClickListener(v -> {
            String content = etNewsContent.getText().toString().trim();
            if (!content.isEmpty()) {
                verifyNews(content);
            } else {
                Toast.makeText(this, "Please paste some news content", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initGeminiService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        geminiService = retrofit.create(GeminiService.class);
    }

    private void verifyNews(String content) {
        progressBar.setVisibility(View.VISIBLE);
        btnVerify.setEnabled(false);
        resultCard.setVisibility(View.GONE);

        String prompt = "Analyze the following news content for authenticity. Check if it's fake news, misinformation, or highly biased. " +
                "Provide a clear verdict (REAL, FAKE, or SUSPICIOUS) and a brief explanation of why. Content: " + content;

        GeminiRequest request = new GeminiRequest(prompt);
        String authHeader = "Bearer " + Config.OPENROUTER_API_KEY;

        geminiService.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                progressBar.setVisibility(View.GONE);
                btnVerify.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    resultCard.setVisibility(View.VISIBLE);
                    resultContent.setText(response.body().getResponseText());
                } else {
                    Toast.makeText(NewsDetectionActivity.this, "Verification failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnVerify.setEnabled(true);
                Toast.makeText(NewsDetectionActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
