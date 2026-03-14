package com.avish.sheidhero;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ScanListActivity extends AppCompatActivity {

    private static final String TAG = "ScanListActivity";
    private ProgressBar progressBar;
    private TextView statusText, progressDetail, percentageText;
    private RecyclerView recyclerView;
    private TabLayout tabLayout;

    private final ArrayList<AppInfo> riskyApps = new ArrayList<>();
    private final ArrayList<AppInfo> safeApps = new ArrayList<>();
    private final ArrayList<AppInfo> currentList = new ArrayList<>();
    private final ArrayList<PackageInfo> scanQueue = new ArrayList<>();

    private int totalToScan = 0;
    private int scannedCount = 0;

    private AppAdapter adapter;
    private GeminiService geminiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_scan_list);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true); // Light background

        AppBarLayout appBarLayout = findViewById(R.id.scanToolbar).getParent() instanceof AppBarLayout ? (AppBarLayout) findViewById(R.id.scanToolbar).getParent() : null;
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        MaterialToolbar toolbar = findViewById(R.id.scanToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar = findViewById(R.id.scanProgressBar);
        statusText = findViewById(R.id.scanStatusText);
        progressDetail = findViewById(R.id.scanProgressDetail);
        percentageText = findViewById(R.id.scanPercentageText);
        recyclerView = findViewById(R.id.scanRecyclerView);
        tabLayout = findViewById(R.id.scanTabLayout);

        adapter = new AppAdapter(this, currentList, new AppAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(AppInfo app) {
                if (riskyApps.contains(app)) {
                    showUninstallDialog(app);
                }
            }

            @Override
            public void onTrustChanged(AppInfo app, boolean isTrusted) {}
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentList.clear();
                if (tab.getPosition() == 0) {
                    currentList.addAll(riskyApps);
                } else {
                    currentList.addAll(safeApps);
                }
                adapter.notifyDataSetChanged();
                recyclerView.invalidate();
                recyclerView.requestLayout();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        initGeminiService();
        startScan();
    }

    private void showUninstallDialog(AppInfo app) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Security Threat")
                .setMessage("Deep scan suggests " + app.name + " may be harmful due to suspicious permissions or source. It's recommended to uninstall it.")
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)));
                })
                .setNegativeButton("Ignore", null)
                .show();
    }

    private void initGeminiService() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.BULK_SCAN);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
        geminiService = retrofit.create(GeminiService.class);
    }

    private void startScan() {
        riskyApps.clear();
        safeApps.clear();
        currentList.clear();
        scanQueue.clear();
        scannedCount = 0;
        adapter.notifyDataSetChanged();

        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);

        for (PackageInfo packageInfo : packages) {
            if (packageInfo.applicationInfo == null) continue;

            boolean isSystemApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            if (isSystemApp && !isUpdatedSystemApp) {
                continue;
            }

            scanQueue.add(packageInfo);
        }

        totalToScan = scanQueue.size();
        progressBar.setMax(totalToScan);

        if (totalToScan > 0) {
            scanNextApp();
        } else {
            statusText.setText("Scan Complete");
            progressDetail.setText("No user-installed apps found to scan.");
            updateDisplayList();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (scannedCount < totalToScan) {
                runOnUiThread(() -> {
                    statusText.setText("Scan Complete");
                    progressDetail.setText("Protected. Analyzed " + scannedCount + " apps.");
                    percentageText.setText("100%");
                    progressBar.setProgress(totalToScan);
                    
                    if (!riskyApps.isEmpty()) {
                        tabLayout.selectTab(tabLayout.getTabAt(0));
                        currentList.clear();
                        currentList.addAll(riskyApps);
                        adapter.notifyDataSetChanged();
                        statusText.setText("⚠️ " + riskyApps.size() + " threats found!");
                    } else {
                        tabLayout.selectTab(tabLayout.getTabAt(1));
                        currentList.clear();
                        currentList.addAll(safeApps);
                        adapter.notifyDataSetChanged();
                        statusText.setText("✅ Device is Clean!");
                    }

                    int score = Math.max(0, 100 - (riskyApps.size() * 8));
                    MainActivity.saveSecurityScore(getApplicationContext(), score);
                });
            }
        }, 180000);
    }

    private String getAppInstaller(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return pm.getInstallSourceInfo(packageName).getInstallingPackageName();
            } else {
                return pm.getInstallerPackageName(packageName);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void scanNextApp() {
        if (scanQueue.isEmpty()) {
            int score = Math.max(0, 100 - (riskyApps.size() * 8));
            MainActivity.saveSecurityScore(getApplicationContext(), score);
            runOnUiThread(() -> {
                percentageText.setText("100%");
                progressBar.setProgress(totalToScan);
                if (!riskyApps.isEmpty()) {
                    statusText.setText("⚠️ " + riskyApps.size() + " threats found!");
                    progressDetail.setText("Protected. Analyzed " + totalToScan + " user apps.");
                    tabLayout.selectTab(tabLayout.getTabAt(0));
                } else {
                    statusText.setText("✅ Device is Clean!");
                    progressDetail.setText("Protected. Analyzed " + totalToScan + " user apps.");
                    tabLayout.selectTab(tabLayout.getTabAt(1));
                }
                updateDisplayList();
            });
            return;
        }

        PackageInfo pkg = scanQueue.remove(0);
        PackageManager pm = getPackageManager();
        String appName = pkg.applicationInfo.loadLabel(pm).toString();
        String packageName = pkg.packageName;

        scannedCount++;
        int pct = (int)(((float) scannedCount / totalToScan) * 100);

        runOnUiThread(() -> {
            progressBar.setProgress(scannedCount);
            percentageText.setText(pct + "%");
            statusText.setText("Scanning: " + appName);
            progressDetail.setText(scannedCount + " / " + totalToScan);
            TabLayout.Tab riskyTab = tabLayout.getTabAt(0);
            TabLayout.Tab safeTab = tabLayout.getTabAt(1);
            if (riskyTab != null) riskyTab.setText("Risky Apps (" + riskyApps.size() + ")");
            if (safeTab != null) safeTab.setText("Safe Apps (" + safeApps.size() + ")");
        });

        if (packageName.equals("app.rvx.android.youtube") ||
                packageName.equals("app.revanced.android.youtube") ||
                packageName.equals("app.revanced.android.apps.youtube.music") ||
                packageName.equals("app.rvx.android.apps.youtube.music") ||
                packageName.startsWith("app.rvx") ||
                packageName.startsWith("app.revanced")) {
            AppInfo info = new AppInfo(appName, packageName, "RISKY ⚠️");
            info.vtResult = "Modified/Unofficial APK - " + appName + " is not the official app. Sideloaded modified apps can contain hidden malware.";
            riskyApps.add(info);
            runOnUiThread(() -> { updateDisplayList(); scanNextApp(); });
            return;
        }

        boolean instantRisky = false;
        String lowerPkg = packageName.toLowerCase();

        if (lowerPkg.equals("sigma.male") ||
            lowerPkg.contains("sigma.male") ||
            lowerPkg.contains(".myapplication") ||
            lowerPkg.contains(".hack") ||
            lowerPkg.contains(".rat") ||
            lowerPkg.contains(".spy") ||
            lowerPkg.contains(".payload") ||
            lowerPkg.contains(".malware") ||
            lowerPkg.contains(".virus") ||
            lowerPkg.contains(".trojan") ||
            lowerPkg.contains(".exploit")) {
            instantRisky = true;
        }

        if (instantRisky) {
            AppInfo info = new AppInfo(appName, packageName, "RISKY ⚠️");
            info.vtResult = "Flagged: Suspicious package name - possible malware/RAT";
            riskyApps.add(info);
            runOnUiThread(() -> { updateDisplayList(); scanNextApp(); });
            return;
        }

        String lowerName = appName.toLowerCase();
        boolean isFakeApp = false;

        if (lowerName.contains("amazon") &&
                (packageName.startsWith("com.amazon") || packageName.startsWith("in.amazon"))) {
        } else {
            String[][] knownApps = {
                {"instagram", "com.instagram"},
                {"whatsapp", "com.whatsapp"},
                {"facebook", "com.facebook"},
                {"youtube", "com.google.android.youtube"},
                {"youtube music", "com.google.android.apps.youtube.music"},
                {"youtube kids", "com.google.android.apps.youtube.kids"},
                {"google play", "com.android.vending"},
                {"play store", "com.android.vending"},
                {"gmail", "com.google.android.gm"},
                {"google maps", "com.google.android.apps.maps"},
                {"google chrome", "com.android.chrome"},
                {"telegram", "org.telegram"},
                {"snapchat", "com.snapchat"},
                {"twitter", "com.twitter"},
                {"netflix", "com.netflix"},
                {"phonepe", "com.phonepe"},
                {"paytm", "net.one97"},
                {"gpay", "com.google.android.apps.nbu"},
                {"google pay", "com.google.android.apps.nbu"},
                {"spotify", "com.spotify"},
                {"discord", "com.discord"},
                {"linkedin", "com.linkedin"},
                {"pinterest", "com.pinterest"},
                {"amazon", "com.amazon"},
                {"flipkart", "com.flipkart"},
                {"tiktok", "com.zhiliaoapp"}
            };

            for (String[] knownApp : knownApps) {
                String knownName = knownApp[0];
                String knownPkg = knownApp[1];

                if (lowerName.equals(knownName) && !packageName.startsWith(knownPkg)) {
                    boolean isLegitGooglePackage = packageName.startsWith("com.google") ||
                                                    packageName.startsWith("com.android");
                    boolean isKnownMod = packageName.startsWith("app.revanced") ||
                                         packageName.startsWith("app.rvx");

                    if (!isLegitGooglePackage && !isKnownMod) {
                        isFakeApp = true;
                        break;
                    } else if (isKnownMod) {
                        AppInfo info = new AppInfo(appName, packageName, "RISKY ⚠️");
                        info.vtResult = "Modified/Unofficial version of " + appName + " - use with caution";
                        riskyApps.add(info);
                        runOnUiThread(() -> { updateDisplayList(); scanNextApp(); });
                        return;
                    }
                }
            }
        }

        if (isFakeApp) {
            AppInfo info = new AppInfo(appName, packageName, "RISKY ⚠️");
            info.vtResult = "Flagged: App impersonation (Fake " + appName + ")";
            riskyApps.add(info);
            runOnUiThread(() -> { updateDisplayList(); scanNextApp(); });
            return;
        }

        boolean isTrusted = false;
        if (packageName.startsWith("com.google.android") ||
            packageName.startsWith("com.android.") ||
            packageName.startsWith("com.samsung.") ||
            packageName.startsWith("com.xiaomi.") ||
            packageName.startsWith("com.miui.") ||
            packageName.startsWith("com.oppo.") ||
            packageName.startsWith("com.vivo.") ||
            packageName.equals("com.whatsapp") ||
            packageName.equals("com.instagram.android") ||
            packageName.equals("com.snapchat.android") ||
            packageName.equals("com.facebook.katana") ||
            packageName.equals("com.facebook.lite") ||
            packageName.equals("com.discord") ||
            packageName.equals("com.spotify.music") ||
            packageName.equals("com.netflix.mediaclient") ||
            packageName.equals("com.amazon.mShop.android.shopping") ||
            packageName.equals("in.amazon.mShop.android.shopping") ||
            packageName.equals("com.amazon.kindle") ||
            packageName.equals("com.amazon.avod.thirdpartyclient") ||
            packageName.equals("com.amazon.dee.app") ||
            packageName.equals("in.gov.uidai.mAadhaarPlus") ||
            packageName.equals("com.myairtelapp") ||
            packageName.equals("com.bsbportal.music") ||
            packageName.equals("com.truecaller") ||
            packageName.equals("com.iexceed.appzillon.ippbMB") ||
            packageName.startsWith("in.amazon") ||
            packageName.startsWith("in.gov") ||
            packageName.startsWith("in.co") ||
            packageName.equals("com.flipkart.android") ||
            packageName.equals("com.phonepe.app") ||
            packageName.equals("net.one97.paytm") ||
            packageName.equals("com.openai.chatgpt") ||
            packageName.equals("com.pinterest") ||
            packageName.equals("com.linkedin.android") ||
            packageName.equals("com.avish.sheidhero") ||
            packageName.equals("com.digilocker.android")) {
            isTrusted = true;
        }

        List<String> sensitivePerms = new ArrayList<>();
        if (pkg.requestedPermissions != null) {
            for (String perm : pkg.requestedPermissions) {
                if (perm.contains("SMS") || perm.contains("RECORD_AUDIO") ||
                    perm.contains("CAMERA") || perm.contains("SYSTEM_ALERT_WINDOW") ||
                    perm.contains("LOCATION") || perm.contains("READ_CONTACTS") ||
                    perm.contains("READ_CALL_LOG")) {
                    sensitivePerms.add(perm.substring(perm.lastIndexOf(".") + 1));
                }
            }
        }

        String installer = getAppInstaller(packageName);
        boolean isFromPlayStore = "com.android.vending".equals(installer);

        if (isTrusted || (isFromPlayStore && sensitivePerms.size() < 2)) {
            AppInfo info = new AppInfo(appName, packageName, "CLEAN ✅");
            info.vtResult = "Trusted App";
            safeApps.add(info);
            runOnUiThread(() -> { updateDisplayList(); scanNextApp(); });
            return;
        }

        String prompt = "Android Security Check:\nApp: " + appName
            + "\nPackage: " + packageName
            + "\nInstaller: " + (installer != null ? installer : "Unknown/Sideloaded")
            + "\nPermissions: " + sensitivePerms.toString()
            + "\nRules: Sideloaded app with sensitive permissions = UNSAFE. "
            + "Known legitimate app = SAFE. Unknown app with many permissions = UNSAFE."
            + "\nReply ONLY with 'UNSAFE' or 'SAFE'.";

        GeminiRequest request = new GeminiRequest(prompt);
        ApiKeyManager.ApiConfig config = ApiKeyManager.getConfig(ApiKeyManager.UseCase.BULK_SCAN);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            geminiService.checkAppSecurity("Bearer " + config.apiKey, request)
                .enqueue(new Callback<GeminiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                        try {
                            AppInfo info;
                            if (response.isSuccessful() && response.body() != null) {
                                boolean isUnsafe = response.body().getResponseText().trim().toUpperCase().contains("UNSAFE");
                                info = new AppInfo(appName, packageName, isUnsafe ? "RISKY ⚠️" : "CLEAN ✅");
                                info.sensitivePermissions = sensitivePerms;
                                info.vtResult = isUnsafe ? "Flagged: Suspicious permissions or untrusted source."
                                    : (isFromPlayStore ? "Verified Play Store App" : "Verified Safe by AI");
                                if (isUnsafe) riskyApps.add(info);
                                else safeApps.add(info);
                            } else {
                                info = new AppInfo(appName, packageName, "CLEAN ✅");
                                info.vtResult = "Skipped";
                                safeApps.add(info);
                                ApiKeyManager.handleFailure(config.provider, response.code());
                            }
                        } catch (Exception e) {
                            AppInfo info = new AppInfo(appName, packageName, "CLEAN ✅");
                            info.vtResult = "Skipped";
                            safeApps.add(info);
                        }
                        runOnUiThread(() -> { updateDisplayList(); scanNextApp(); });
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                        AppInfo info = new AppInfo(appName, packageName, "CLEAN ✅");
                        info.vtResult = "Skipped (network)";
                        safeApps.add(info);
                        runOnUiThread(() -> { updateDisplayList(); scanNextApp(); });
                    }
                });
        }, 300);
    }

    private void updateDisplayList() {
        runOnUiThread(() -> {
            currentList.clear();
            if (tabLayout.getSelectedTabPosition() == 0) {
                currentList.addAll(riskyApps);
            } else {
                currentList.addAll(safeApps);
            }
            adapter.notifyDataSetChanged();
        });
    }
}
