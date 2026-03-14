package com.avish.sheidhero;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class SafeSearchActivity extends AppCompatActivity {

    private TextView tvStatus;
    private MaterialButton btnEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_safe_search);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);

        MaterialToolbar toolbar = findViewById(R.id.safeSearchToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Handle Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.safeSearchToolbar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        tvStatus = findViewById(R.id.tvStatus); // I need to add this to layout or use an existing one
        btnEnable = findViewById(R.id.btnEnableSafeSearch);

        btnEnable.setOnClickListener(v -> {
            if (isAccessibilityServiceEnabled(this, SafeSearchService.class)) {
                // Already enabled, maybe show a test
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://phish-test.com"));
                startActivity(intent);
                Toast.makeText(this, "Testing Shield Protection...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.enable_instruction), Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = isAccessibilityServiceEnabled(this, SafeSearchService.class);
        if (enabled) {
            btnEnable.setText(R.string.test_protection);
            btnEnable.setIconResource(android.R.drawable.ic_menu_view);
        } else {
            btnEnable.setText(R.string.enable_safe_search);
            btnEnable.setIconResource(android.R.drawable.ic_menu_add);
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> service) {
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (settingValue != null) {
            colonSplitter.setString(settingValue);
            while (colonSplitter.hasNext()) {
                String accessibilityService = colonSplitter.next();
                if (accessibilityService.equalsIgnoreCase(context.getPackageName() + "/" + service.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
