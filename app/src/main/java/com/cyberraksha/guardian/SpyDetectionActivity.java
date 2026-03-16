package com.cyberraksha.guardian;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SpyDetectionActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView recyclerView;
    private MaterialButton btnStart;
    
    private ArrayList<AppInfo> spyApps = new ArrayList<>();
    private ArrayList<PackageInfo> scanQueue = new ArrayList<>();
    private AppAdapter adapter;
    private GeminiService geminiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spy_detection);

        MaterialToolbar toolbar = findViewById(R.id.spyToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.spyProgressBar);
        statusText = findViewById(R.id.spyStatusText);
        recyclerView = findViewById(R.id.spyRecyclerView);
        btnStart = findViewById(R.id.btnStartSpyDetection);

        adapter = new AppAdapter(this, spyApps, new AppAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(AppInfo app) {
                showUninstallDialog(app);
            }

            @Override
            public void onTrustChanged(AppInfo app, boolean isTrusted) {}
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        initGeminiService();

        btnStart.setOnClickListener(v -> startSpyScan());
    }

    private void initGeminiService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        geminiService = retrofit.create(GeminiService.class);
    }

    private void startSpyScan() {
        spyApps.clear();
        adapter.notifyDataSetChanged();
        scanQueue.clear();

        btnStart.setEnabled(false);
        btnStart.setText("SCANNING FOR SPYWARE...");
        progressBar.setVisibility(View.VISIBLE);

        // Background thread — prevents UI freeze during package enumeration
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);

            for (PackageInfo packageInfo : packages) {
                if (packageInfo.applicationInfo == null) continue;
                boolean isSystemApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isUpdatedSystemApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (isSystemApp && !isUpdatedSystemApp) continue;
                if (hasSpyTraits(packageInfo)) {
                    scanQueue.add(packageInfo);
                }
            }

            runOnUiThread(() -> {
                if (!scanQueue.isEmpty()) {
                    scanNext();
                } else {
                    finishScan();
                }
            });
        }).start();
    }

    private boolean hasSpyTraits(PackageInfo pkg) {
        if (pkg.requestedPermissions == null) return false;
        
        boolean hasMic = false;
        boolean hasCamera = false;
        boolean hasLocation = false;
        boolean hasSms = false;
        
        for (String perm : pkg.requestedPermissions) {
            if (perm.contains("RECORD_AUDIO")) hasMic = true;
            if (perm.contains("CAMERA")) hasCamera = true;
            if (perm.contains("ACCESS_FINE_LOCATION")) hasLocation = true;
            if (perm.contains("READ_SMS") || perm.contains("RECEIVE_SMS")) hasSms = true;
        }
        
        // Flag apps that have multiple intrusive permissions
        int intrusiveCount = (hasMic ? 1 : 0) + (hasCamera ? 1 : 0) + (hasLocation ? 1 : 0) + (hasSms ? 1 : 0);
        return intrusiveCount >= 2;
    }

    private void scanNext() {
        if (scanQueue.isEmpty()) {
            finishScan();
            return;
        }

        PackageInfo packageInfo = scanQueue.remove(0);
        PackageManager pm = getPackageManager();
        String appName = packageInfo.applicationInfo.loadLabel(pm).toString();
        
        statusText.setText("Analyzing " + appName + " for spyware activity...");

        StringBuilder permissions = new StringBuilder();
        if (packageInfo.requestedPermissions != null) {
            for (String perm : packageInfo.requestedPermissions) {
                permissions.append(perm.replace("android.permission.", "")).append(", ");
            }
        }

        String prompt = "Spyware Analysis Task:\n" +
                "App: " + appName + "\n" +
                "Package: " + packageInfo.packageName + "\n" +
                "Permissions: [" + permissions.toString() + "]\n\n" +
                "Evaluate if this app behaves like SPYWARE or STALKERWARE. " +
                "Does it have an unusual combination of Mic/Camera/Location/SMS access for its apparent purpose? " +
                "Reply ONLY 'UNSAFE' if it is highly suspicious/spyware, else 'SAFE'.";

        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.BULK_SCAN);
        String authHeader = ApiKeyManager.getBearerToken(ApiKeyManager.UseCase.BULK_SCAN);
        GeminiRequest request = new GeminiRequest(prompt);
        request.setModel(config.modelId);

        geminiService.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getResponseText().trim().equalsIgnoreCase("UNSAFE")) {
                        AppInfo info = new AppInfo(appName, packageInfo.packageName, "SPYWARE DETECTED \u26a0\ufe0f");
                        spyApps.add(info);
                        runOnUiThread(() -> adapter.notifyDataSetChanged());
                    }
                }
                scanNext();
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                scanNext();
            }
        });
    }

    private void finishScan() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnStart.setEnabled(true);
            btnStart.setText("START SPY SCAN");
            statusText.setText("Spy Scan Complete. Found " + spyApps.size() + " threats.");
        });
    }

    private void showUninstallDialog(AppInfo app) {
        new AlertDialog.Builder(this)
                .setTitle("\u26a0\ufe0f Spyware Alert")
                .setMessage("AI analysis suggests " + app.name + " may be spying on you. It is highly recommended to uninstall it.")
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)));
                })
                .setNegativeButton("Ignore", null)
                .show();
    }
}
