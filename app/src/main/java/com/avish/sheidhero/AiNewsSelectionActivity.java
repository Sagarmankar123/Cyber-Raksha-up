package com.avish.sheidhero;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

public class AiNewsSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_ai_news_selection);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);

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

        MaterialCardView btnAiDetection = findViewById(R.id.btnAiDetection);
        MaterialCardView btnNewsDetection = findViewById(R.id.btnNewsDetection);

        btnAiDetection.setOnClickListener(v -> {
            Intent intent = new Intent(AiNewsSelectionActivity.this, TrueNewsActivity.class);
            startActivity(intent);
        });

        btnNewsDetection.setOnClickListener(v -> {
            Intent intent = new Intent(AiNewsSelectionActivity.this, NewsDetectionActivity.class);
            startActivity(intent);
        });
    }
}
