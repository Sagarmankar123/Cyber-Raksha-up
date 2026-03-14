package com.avish.sheidhero;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TrueNewsActivity extends AppCompatActivity {

    private ImageView selectedImage;
    private View placeholderLayout;
    private Button btnVerify;
    private ProgressBar progressBar;
    private MaterialCardView resultCard;
    private TextView resultContent;
    
    private Bitmap currentBitmap;
    private GeminiService geminiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_true_news);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true); // Light background

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        AppBarLayout appBarLayout = toolbar.getParent() instanceof AppBarLayout ? (AppBarLayout) toolbar.getParent() : null;
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        selectedImage = findViewById(R.id.selectedImage);
        placeholderLayout = findViewById(R.id.placeholderLayout);
        btnVerify = findViewById(R.id.btnVerify);
        progressBar = findViewById(R.id.progressBar);
        resultCard = findViewById(R.id.resultCard);
        resultContent = findViewById(R.id.resultContent);

        initGeminiService();

        ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            currentBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
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
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnVerify.setOnClickListener(v -> {
            if (currentBitmap != null) {
                analyzeImage();
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

    private void analyzeImage() {
        progressBar.setVisibility(View.VISIBLE);
        btnVerify.setEnabled(false);
        resultCard.setVisibility(View.GONE);

        String base64Image = encodeImage(currentBitmap);
        String prompt = "Carefully analyze this image. Tell me if you think it is AI-generated (like DALL-E, Midjourney, etc.) or a real photograph. " +
                "Look for AI artifacts like weird textures, unnatural fingers, or inconsistent lighting. " +
                "Also check if this image is being used for fake news or misinformation. Provide a concise verdict.";

        MultiModalGeminiRequest request = new MultiModalGeminiRequest(prompt, base64Image);
        String authHeader = "Bearer " + Config.OPENROUTER_API_KEY;

        geminiService.analyzeImage(authHeader, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                progressBar.setVisibility(View.GONE);
                btnVerify.setEnabled(true);
                
                if (response.isSuccessful() && response.body() != null) {
                    resultCard.setVisibility(View.VISIBLE);
                    resultContent.setText(response.body().getResponseText());
                } else {
                    String errorMsg = "Analysis failed: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += "\n" + response.errorBody().string();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    resultCard.setVisibility(View.VISIBLE);
                    resultContent.setText(errorMsg);
                    Toast.makeText(TrueNewsActivity.this, "Analysis failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnVerify.setEnabled(true);
                Toast.makeText(TrueNewsActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int maxWidth = 800;
        int maxHeight = 800;
        float ratio = Math.min((float) maxWidth / bitmap.getWidth(), (float) maxHeight / bitmap.getHeight());
        int width = Math.round(ratio * bitmap.getWidth());
        int height = Math.round(ratio * bitmap.getHeight());
        
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }
}
