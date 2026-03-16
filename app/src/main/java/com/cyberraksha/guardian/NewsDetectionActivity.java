package com.cyberraksha.guardian;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import androidx.cardview.widget.CardView;

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

public class NewsDetectionActivity extends AppCompatActivity {

    private static final String TAG = "NewsDetectionActivity";

    // Provider endpoints and models with fallback chain
    private static final String[][] PROVIDERS = {
        // { endpoint, bearerKey, model }
        // Groq — fastest, free
        { "https://api.groq.com/openai/v1/chat/completions", "GROQ", "llama-3.1-8b-instant" },
        // OpenRouter — free fallback
        { "https://openrouter.ai/api/v1/chat/completions", "OPENROUTER", "meta-llama/llama-3.1-8b-instruct:free" },
    };

    private TextInputEditText etNewsContent;
    private MaterialButton btnVerify;
    private ProgressBar progressBar;
    private CardView resultCard;
    private TextView resultContent;

    private OkHttpClient httpClient;
    private int providerIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_detection);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etNewsContent = findViewById(R.id.etNewsContent);
        btnVerify     = findViewById(R.id.btnVerifyNews);
        progressBar   = findViewById(R.id.newsProgressBar);
        resultCard    = findViewById(R.id.newsResultCard);
        resultContent = findViewById(R.id.newsResultContent);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        btnVerify.setOnClickListener(v -> {
            String content = etNewsContent.getText() != null
                    ? etNewsContent.getText().toString().trim() : "";
            if (!content.isEmpty()) {
                providerIndex = 0; // Reset fallback index
                verifyNews(content);
            } else {
                Toast.makeText(this, "Please paste some news content", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyNews(String newsText) {
        progressBar.setVisibility(View.VISIBLE);
        btnVerify.setEnabled(false);
        resultCard.setVisibility(View.GONE);

        String[] provider = PROVIDERS[providerIndex];
        String endpoint = provider[0];
        String providerType = provider[1];
        String model = provider[2];

        // Get correct API key
        String apiKey;
        if ("GROQ".equals(providerType)) {
            apiKey = ApiKeyManager.getConfig(ApiKeyManager.UseCase.BULK_SCAN).apiKey;
        } else {
            apiKey = ApiKeyManager.getConfig(ApiKeyManager.UseCase.IMAGE_ANALYSIS).apiKey; // OpenRouter
        }

        Log.d(TAG, "Using provider [" + providerType + "] model: " + model);

        String prompt = "You are a professional fact-checker. Analyze the following news content:\n\n"
                + newsText + "\n\n"
                + "Respond with:\n"
                + "VERDICT: REAL / FAKE / SUSPICIOUS\n"
                + "REASON: (1-2 sentences explaining why)\n"
                + "CONFIDENCE: HIGH / MEDIUM / LOW";

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 300);

            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            requestBody.put("messages", messages);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    requestBody.toString()
            );

            Request.Builder reqBuilder = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");

            if ("OPENROUTER".equals(providerType)) {
                reqBuilder.header("HTTP-Referer", "https://cyberraksha.app");
                reqBuilder.header("X-Title", "CyberRaksha News Detection");
            }

            httpClient.newCall(reqBuilder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Network error: " + e.getMessage());
                    tryFallback(newsText, "Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String responseStr = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Response [" + code + "]: "
                            + responseStr.substring(0, Math.min(300, responseStr.length())));

                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(responseStr);
                            String text = json.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            showResult(text);
                        } catch (JSONException e) {
                            showError("Could not parse AI response");
                        }
                    } else if (code == 429 || code == 401 || code == 403 || code == 404) {
                        // Rate limit or auth error — try next provider
                        tryFallback(newsText, "Provider failed (HTTP " + code + ")");
                    } else {
                        showError("Verification failed (" + code + "):\n" + responseStr);
                    }
                }
            });

        } catch (JSONException e) {
            showError("Request build failed: " + e.getMessage());
        }
    }

    private void tryFallback(String newsText, String reason) {
        if (providerIndex < PROVIDERS.length - 1) {
            providerIndex++;
            Log.w(TAG, "Falling back to provider[" + providerIndex + "] — reason: " + reason);
            runOnUiThread(() ->
                Toast.makeText(this, "Trying backup AI service...", Toast.LENGTH_SHORT).show());
            verifyNews(newsText);
        } else {
            showError("All AI services unavailable. Please try again later.\n(" + reason + ")");
        }
    }

    private void showResult(String text) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            resultCard.setVisibility(View.VISIBLE);
            resultContent.setText(text);
        });
    }

    private void showError(String error) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnVerify.setEnabled(true);
            resultCard.setVisibility(View.VISIBLE);
            resultContent.setText("Error: " + error);
            Toast.makeText(this, "Failed: " + error, Toast.LENGTH_LONG).show();
        });
    }
}
