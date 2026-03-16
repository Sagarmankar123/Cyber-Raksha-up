package com.cyberraksha.guardian;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class DataUsageActivity extends AppCompatActivity {

    private RecyclerView rvDataUsage;
    private TextView tvTotalMobile, tvTotalWifi, tvPeriod;
    private MaterialButton btnRefresh;
    private List<AppDataInfo> dataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_usage);

        MaterialToolbar toolbar = findViewById(R.id.dataToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTotalMobile = findViewById(R.id.tvTotalMobile);
        tvTotalWifi   = findViewById(R.id.tvTotalWifi);
        tvPeriod      = findViewById(R.id.tvPeriod);
        btnRefresh    = findViewById(R.id.btnRefreshData);
        rvDataUsage   = findViewById(R.id.rvDataUsage);
        rvDataUsage.setLayoutManager(new LinearLayoutManager(this));

        tvPeriod.setText("Data usage — This Month");
        btnRefresh.setOnClickListener(v -> loadDataUsage());
        loadDataUsage();
    }

    private boolean hasUsagePermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void loadDataUsage() {
        if (!hasUsagePermission()) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("CyberRaksha needs 'Usage Access' permission to show data usage per app.\n\nTap OK to grant it.")
                .setPositiveButton("Grant Permission", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                .setNegativeButton("Cancel", null).show();
            return;
        }

        dataList.clear();
        new Thread(() -> {
            try {
                NetworkStatsManager nsm = (NetworkStatsManager) getSystemService(Context.NETWORK_STATS_SERVICE);
                PackageManager pm = getPackageManager();

                // Get this month's range
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0);
                long startTime = cal.getTimeInMillis();
                long endTime   = System.currentTimeMillis();

                long totalMobile = 0, totalWifi = 0;

                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                for (ApplicationInfo app : apps) {
                    try {
                        int uid = app.uid;
                        long mobileRx = 0, mobileTx = 0, wifiRx = 0, wifiTx = 0;

                        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                        NetworkStats stats;

                        // Mobile data
                        try {
                            stats = nsm.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, startTime, endTime, uid);
                            while (stats.hasNextBucket()) { stats.getNextBucket(bucket); mobileRx += bucket.getRxBytes(); mobileTx += bucket.getTxBytes(); }
                            stats.close();
                        } catch (Exception ignored) {}

                        // WiFi
                        try {
                            stats = nsm.queryDetailsForUid(ConnectivityManager.TYPE_WIFI, null, startTime, endTime, uid);
                            while (stats.hasNextBucket()) { stats.getNextBucket(bucket); wifiRx += bucket.getRxBytes(); wifiTx += bucket.getTxBytes(); }
                            stats.close();
                        } catch (Exception ignored) {}

                        long total = mobileRx + mobileTx + wifiRx + wifiTx;
                        if (total > 0) {
                            String label = pm.getApplicationLabel(app).toString();
                            dataList.add(new AppDataInfo(label, mobileRx + mobileTx, wifiRx + wifiTx));
                            totalMobile += mobileRx + mobileTx;
                            totalWifi   += wifiRx + wifiTx;
                        }
                    } catch (Exception ignored) {}
                }

                Collections.sort(dataList, (a, b) -> Long.compare(b.total(), a.total()));
                final long finalMobile = totalMobile, finalWifi = totalWifi;

                runOnUiThread(() -> {
                    tvTotalMobile.setText("📶 Mobile: " + formatBytes(finalMobile));
                    tvTotalWifi.setText("📡 WiFi: " + formatBytes(finalWifi));
                    rvDataUsage.setAdapter(new DataAdapter(dataList));
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not load data: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    static class AppDataInfo {
        String name; long mobile, wifi;
        AppDataInfo(String n, long m, long w) { name = n; mobile = m; wifi = w; }
        long total() { return mobile + wifi; }
    }

    class DataAdapter extends RecyclerView.Adapter<DataAdapter.VH> {
        private final List<AppDataInfo> list;
        DataAdapter(List<AppDataInfo> list) { this.list = new ArrayList<>(list); }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(parent.getContext());
            row.setOrientation(android.widget.LinearLayout.VERTICAL);
            row.setPadding(32, 20, 32, 20);
            row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = new TextView(parent.getContext()); name.setTextColor(Color.parseColor("#2D3436")); name.setTextSize(14); name.setTag("name");
            TextView detail = new TextView(parent.getContext()); detail.setTextColor(Color.parseColor("#636E72")); detail.setTextSize(12); detail.setTag("detail");
            row.addView(name); row.addView(detail);
            return new VH(row);
        }
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AppDataInfo item = list.get(position);
            ((TextView) holder.itemView.findViewWithTag("name")).setText("📱 " + item.name + "  —  " + formatBytes(item.total()));
            ((TextView) holder.itemView.findViewWithTag("detail")).setText("Mobile: " + formatBytes(item.mobile) + "  |  WiFi: " + formatBytes(item.wifi));
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
