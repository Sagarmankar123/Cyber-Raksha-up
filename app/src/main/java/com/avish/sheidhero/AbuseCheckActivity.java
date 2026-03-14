package com.avish.sheidhero;

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

public class AbuseCheckActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView recyclerView;
    private MaterialButton btnStart;
    
    private ArrayList<AppInfo> abusiveApps = new ArrayList<>();
    private ArrayList<PackageInfo> scanQueue = new ArrayList<>();
    private AppAdapter adapter;
    private GeminiService geminiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_abuse_check);

        MaterialToolbar toolbar = findViewById(R.id.abuseToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.abuseProgressBar);
        statusText = findViewById(R.id.abuseStatusText);
        recyclerView = findViewById(R.id.abuseRecyclerView);
        btnStart = findViewById(R.id.btnStartAbuseCheck);

        adapter = new AppAdapter(this, abusiveApps, new AppAdapter.OnItemClickListener() {
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

        btnStart.setOnClickListener(v -> startAbuseScan());
    }

    private void initGeminiService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://openrouter.ai/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        geminiService = retrofit.create(GeminiService.class);
    }

    private void startAbuseScan() {
        abusiveApps.clear();
        adapter.notifyDataSetChanged();
        scanQueue.clear();
        
        btnStart.setEnabled(false);
        btnStart.setText("SCANNING...");
        progressBar.setVisibility(View.VISIBLE);

        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);

        for (PackageInfo packageInfo : packages) {
            if (packageInfo.applicationInfo == null) continue;
            boolean isSystemApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystemApp && !isUpdatedSystemApp) continue;
            scanQueue.add(packageInfo);
        }

        if (!scanQueue.isEmpty()) {
            scanNext();
        } else {
            finishScan();
        }
    }

    private void scanNext() {
        if (scanQueue.isEmpty()) {
            finishScan();
            return;
        }

        PackageInfo packageInfo = scanQueue.remove(0);
        PackageManager pm = getPackageManager();
        String appName = packageInfo.applicationInfo.loadLabel(pm).toString();
        
        statusText.setText("Analyzing: " + appName);

        StringBuilder permissions = new StringBuilder();
        if (packageInfo.requestedPermissions != null) {
            for (String perm : packageInfo.requestedPermissions) {
                permissions.append(perm.replace("android.permission.", "")).append(", ");
            }
        }
        
        int serviceCount = packageInfo.services != null ? packageInfo.services.length : 0;

        String prompt = "Analyze app for ABUSE:\nName: " + appName + "\nPkg: " + packageInfo.packageName + 
                "\nPerms: [" + permissions.toString() + "]\nServices: " + serviceCount + 
                "\nRespond ONLY 'UNSAFE' if it has unusual permissions/background activity, else 'SAFE'.";

        GeminiRequest request = new GeminiRequest(prompt);
        String authHeader = "Bearer " + Config.OPENROUTER_API_KEY;

        geminiService.checkAppSecurity(authHeader, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getResponseText().trim().equalsIgnoreCase("UNSAFE")) {
                        AppInfo info = new AppInfo(appName, packageInfo.packageName, "ABUSIVE \u26a0\ufe0f");
                        abusiveApps.add(info);
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
            btnStart.setText("START DETECTION");
            statusText.setText("Scan Complete. Found " + abusiveApps.size() + " suspicious apps.");
        });
    }

    private void showUninstallDialog(AppInfo app) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ High Risk App")
                .setMessage("AI analysis suggests " + app.name + " might be abusive. Uninstall?")
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
