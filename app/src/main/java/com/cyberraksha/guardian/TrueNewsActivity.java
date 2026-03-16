package com.cyberraksha.guardian;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TrueNewsActivity extends AppCompatActivity {

    private static final String TAG = "TrueNewsActivity";

    // OpenRouter endpoint — supports vision via chat/completions format
    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    // Free vision models to try in order (fallback chain) — updated March 2026
    private static final String[] VISION_MODELS = {
        "qwen/qwen2.5-vl-72b-instruct:free",
        "qwen/qwen2.5-vl-32b-instruct:free",
        "mistralai/mistral-small-3.1-24b-instruct:free",
        "google/gemma-3-27b-it:free"
    };

    private ImageView selectedImage;
    private View placeholderLayout;
    private Button btnVerify;
    private ProgressBar progressBar;
    private CardView resultCard;
    private TextView resultContent;

    private Bitmap currentBitmap;
    private OkHttpClient httpClient;
    private int currentModelIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_true_news);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        AppBarLayout appBarLayout = toolbar.getParent() instanceof AppBarLayout
                ? (AppBarLayout) toolbar.getParent() : null;
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        selectedImage    = findViewById(R.id.selectedImage);
        placeholderLayout = findViewById(R.id.placeholderLayout);
        btnVerify        = findViewById(R.id.btnVerify);
        progressBar      = findViewById(R.id.progressBar);
        resultCard       = findViewById(R.id.resultCard);
        resultContent    = findViewById(R.id.resultContent);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            currentBitmap = MediaStore.Images.Media.getBitmap(
                                    this.getContentResolver(), imageUri);
                            selectedImage.setImageBitmap(currentBitmap);
                            placeholderLayout.setVisibility(View.GONE);
                            btnVerify.setEnabled(true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        findViewById(R.id.imageContainer).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnVerify.setOnClickListener(v -> {
            if (currentBitmap != null) {
                currentModelIndex = 0; // Reset to first model each new attempt
                analyzeImage();
            }
        });
    }

    private void analyzeImage() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            btnVerify.setEnabled(false);
            resultCard.setVisibility(View.GONE);
        });

        String base64Image = encodeImage(currentBitmap);
        String model = VISION_MODELS[currentModelIndex];
        Log.d(TAG, "Trying vision model: " + model);

        // Get OpenRouter API key
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.IMAGE_ANALYSIS);
        String apiKey = config.apiKey;

        try {
            // Build OpenRouter chat/completions request with image_url (base64 data URI)
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 512);

            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");

            JSONArray contentArr = new JSONArray();

            // Text part
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text",
                "Carefully analyze this image. Determine if it is AI-generated (made by DALL-E, " +
                "Midjourney, Stable Diffusion, etc.) or a real photograph. Look for artifacts " +
                "like unnatural fingers, blurry edges, weird textures, inconsistent lighting, or " +
                "impossible backgrounds. Also check if this image might be used for fake news or " +
                "misinformation. Give a clear verdict and brief explanation.");
            contentArr.put(textPart);

            // Image part — OpenRouter accepts data URI format
            JSONObject imagePart = new JSONObject();
            imagePart.put("type", "image_url");
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
            imagePart.put("image_url", imageUrl);
            contentArr.put(imagePart);

            message.put("content", contentArr);
            messages.put(message);
            requestBody.put("messages", messages);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    requestBody.toString()
            );

            Request request = new Request.Builder()
                    .url(OPENROUTER_URL)
                    .post(body)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://cyberraksha.app")
                    .header("X-Title", "CyberRaksha AI Detection")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Network error: " + e.getMessage());
                    showError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String responseStr = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Response [" + code + "]: " + responseStr.substring(0, Math.min(200, responseStr.length())));

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
                    } else if ((code == 429 || code == 404 || code == 400) &&
                               currentModelIndex < VISION_MODELS.length - 1) {
                        // Quota hit or model not found — try next model in fallback chain
                        currentModelIndex++;
                        Log.w(TAG, "Model failed (" + code + "), trying fallback: " +
                              VISION_MODELS[currentModelIndex]);
                        analyzeImage(); // Retry with next model
                    } else {
                        showError("Analysis failed (" + code + "):\n" + responseStr);
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Build error: " + e.getMessage());
            showError("Could not build request: " + e.getMessage());
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
            resultContent.setText("Analysis failed:\n" + error);
            Toast.makeText(TrueNewsActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
        });
    }

    private String encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int maxSize = 768; // Smaller = faster, less quota usage
        float ratio = Math.min((float) maxSize / bitmap.getWidth(),
                               (float) maxSize / bitmap.getHeight());
        int width  = Math.round(ratio * bitmap.getWidth());
        int height = Math.round(ratio * bitmap.getHeight());
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        scaled.compress(Bitmap.CompressFormat.JPEG, 65, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }
}
