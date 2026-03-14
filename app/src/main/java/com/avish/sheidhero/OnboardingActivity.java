package com.avish.sheidhero;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private LinearLayout cardContainer;
    private int currentStep = 1;
    private static final String PREFS_NAME = "cyberraksha_prefs";
    private static final String KEY_SETUP_COMPLETE = "setup_complete";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SETUP_COMPLETE, false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_onboarding);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true); // Light background

        cardContainer = findViewById(R.id.cardContainer);
        showNextCard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentStep == 4 && isAccessibilityServiceEnabled()) {
            currentStep++;
            showNextCard();
        } else if (currentStep == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            currentStep++;
            showNextCard();
        }
    }

    private void showNextCard() {
        cardContainer.removeAllViews();
        View cardView = getLayoutInflater().inflate(R.layout.item_onboarding_card, cardContainer, false);
        
        ImageView icon = cardView.findViewById(R.id.stepIcon);
        TextView title = cardView.findViewById(R.id.stepTitle);
        TextView desc = cardView.findViewById(R.id.stepDesc);
        Button btn = cardView.findViewById(R.id.stepButton);

        switch (currentStep) {
            case 1:
                icon.setImageResource(android.R.drawable.ic_menu_manage);
                title.setText("🪟 Overlay Permission");
                desc.setText("CyberRaksha needs to show alerts over other apps to protect you from fraud in real-time.");
                btn.setText("GRANT OVERLAY");
                btn.setOnClickListener(v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (!Settings.canDrawOverlays(this)) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, 201);
                        } else {
                            currentStep++;
                            showNextCard();
                        }
                    } else {
                        currentStep++;
                        showNextCard();
                    }
                });
                break;

            case 2:
                icon.setImageResource(android.R.drawable.ic_menu_call);
                title.setText("📞 Default Dialer");
                desc.setText("Set CyberRaksha as your default dialer to identify spam and fraud calls instantly.");
                btn.setText("SET AS DEFAULT DIALER");
                btn.setOnClickListener(v -> requestDefaultDialer());
                break;

            case 3:
                icon.setImageResource(android.R.drawable.stat_notify_chat);
                title.setText("💬 Default SMS App");
                desc.setText("Protect your inbox from phishing links and fake bank alerts.");
                btn.setText("SET AS DEFAULT SMS");
                btn.setOnClickListener(v -> {
                    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                    startActivityForResult(intent, 203);
                });
                break;

            case 4:
                icon.setImageResource(android.R.drawable.ic_menu_zoom);
                title.setText("♿ Accessibility");
                desc.setText("CyberRaksha needs Accessibility permission to detect spam calls in real-time.");
                btn.setText("ENABLE ACCESSIBILITY");
                btn.setOnClickListener(v -> checkAndRequestAccessibility());
                break;

            case 5:
                icon.setImageResource(android.R.drawable.checkbox_on_background);
                title.setText("✅ All Set!");
                desc.setText("CyberRaksha is fully activated and ready to protect you.");
                btn.setText("GET STARTED");
                btn.setOnClickListener(v -> finishOnboarding());
                break;
        }
        cardContainer.addView(cardView);
    }

    private void requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                startActivityForResult(intent, 202);
            } else {
                currentStep++;
                showNextCard();
            }
        } else {
            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
            startActivityForResult(intent, 202);
        }
    }

    private void checkAndRequestAccessibility() {
        if (isAccessibilityServiceEnabled()) {
            currentStep++;
            showNextCard();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("CyberRaksha needs Accessibility permission to detect spam calls in real-time. Tap OK to enable it.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivityForResult(intent, 204);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 201) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                currentStep++;
                showNextCard();
            }
        } else if (requestCode == 202 || requestCode == 203) {
            currentStep++;
            showNextCard();
        } else if (requestCode == 204) {
            if (isAccessibilityServiceEnabled()) {
                currentStep++;
                showNextCard();
            } else {
                Toast.makeText(this, "Accessibility permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void finishOnboarding() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_done", true)
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
