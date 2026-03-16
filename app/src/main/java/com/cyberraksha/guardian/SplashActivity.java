package com.cyberraksha.guardian;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_splash);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        new WindowInsetsControllerCompat(w, w.getDecorView()).setAppearanceLightStatusBars(false);

        // Try to start Lottie if it exists, but don't crash if it doesn't
        try {
            com.airbnb.lottie.LottieAnimationView anim = findViewById(R.id.splashAnimation);
            if (anim != null) anim.playAnimation();
        } catch (Exception ignored) {}

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE);
            Class<?> next = prefs.getBoolean("onboarding_done", false) ? MainActivity.class : OnboardingActivity.class;
            startActivity(new Intent(this, next));
            finish();
        }, 2500);
    }
}
