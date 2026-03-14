package com.avish.sheidhero;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    private static final int PHONE_PERMS_CODE = 201;

    private TextView tvHealthScore;
    private MaterialButton btnOptimize;
    private MaterialCardView cardSpaceCleanup, cardSecurityScan, cardDataManagement, cardAppManager, cardSafeSearch, cardEmergencyShield;
    private LinearLayout toolBattery, toolCooling, toolJunkCleaner, toolBooster;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        
        // Initialize Spam Database in background
        SpamDatabase.getInstance(this);

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        // Make status bar icons dark because we have a light background/gradient
        Window window = getWindow();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false); // Header is dark green, so icons should be white
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Initialize Views
        tvHealthScore = findViewById(R.id.tvHealthScore);
        btnOptimize = findViewById(R.id.btnOptimize);
        cardSpaceCleanup = findViewById(R.id.cardSpaceCleanup);
        cardSecurityScan = findViewById(R.id.cardSecurityScan);
        cardDataManagement = findViewById(R.id.cardDataManagement);
        cardAppManager = findViewById(R.id.cardAppManager);
        cardSafeSearch = findViewById(R.id.cardSafeSearch);
        cardEmergencyShield = findViewById(R.id.cardEmergencyShield);
        toolBattery = findViewById(R.id.toolBattery);
        toolCooling = findViewById(R.id.toolCooling);
        toolJunkCleaner = findViewById(R.id.toolJunkCleaner);
        toolBooster = findViewById(R.id.toolBooster);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Handle Window Insets for edge-to-edge
        View headerView = findViewById(R.id.tvAppName).getParent() instanceof View ? (View) findViewById(R.id.tvAppName).getParent() : null;
        if (headerView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top + 40, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        // Set initial health score from prefs
        int lastScore = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .getInt("last_security_score", 97);
        tvHealthScore.setText(String.valueOf(lastScore));

        setupClickListeners();
        checkPermissions();
    }

    private void setupClickListeners() {
        btnOptimize.setOnClickListener(v -> {
            Toast.makeText(this, "Optimizing system...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, ScanListActivity.class));
        });

        cardSecurityScan.setOnClickListener(v ->
                startActivity(new Intent(this, NetworkSecurityActivity.class)));

        cardAppManager.setOnClickListener(v ->
                startActivity(new Intent(this, AiNewsSelectionActivity.class)));

        cardSpaceCleanup.setOnClickListener(v ->
                Toast.makeText(this, "Cleaning junk files...", Toast.LENGTH_SHORT).show());

        cardDataManagement.setOnClickListener(v ->
                Toast.makeText(this, "Analyzing data usage...", Toast.LENGTH_SHORT).show());

        cardSafeSearch.setOnClickListener(v -> {
            startActivity(new Intent(this, SafeSearchActivity.class));
        });

        cardEmergencyShield.setOnClickListener(v ->
                startActivity(new Intent(this, PanicModeActivity.class)));

        // Tools
        View.OnClickListener toolListener = v -> 
                Toast.makeText(MainActivity.this, "Feature coming soon!", Toast.LENGTH_SHORT).show();
        
        toolBattery.setOnClickListener(toolListener);
        toolCooling.setOnClickListener(toolListener);
        toolJunkCleaner.setOnClickListener(toolListener);
        toolBooster.setOnClickListener(toolListener);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_management) {
                return true;
            } else if (id == R.id.nav_tools) {
                Toast.makeText(this, "Utilities Section", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("CyberRaksha needs 'Display over other apps' permission for full protection features.")
                .setPositiveButton("Grant", (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                })
                .setNegativeButton("Later", null)
                .show();
        }

        String[] phonePerms = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG
        };
        boolean needsPerms = false;
        for (String p : phonePerms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needsPerms = true;
                break;
            }
        }
        if (needsPerms) {
            ActivityCompat.requestPermissions(this, phonePerms, PHONE_PERMS_CODE);
        } else {
            startCallService();
        }
    }

    private void startCallService() {
        Intent i = new Intent(this, CallDetectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    public static void saveSecurityScore(Context ctx, int score) {
        ctx.getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .edit()
                .putInt("last_security_score", score)
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int lastScore = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .getInt("last_security_score", 97);
        tvHealthScore.setText(String.valueOf(lastScore));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_PERMS_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCallService();
        }
    }
}
