package com.cyberraksha.guardian;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import android.view.ViewGroup;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JunkCleanerActivity extends AppCompatActivity {

    private TextView tvTotalJunk, tvFreeSpace, tvTotalSpace, tvScanStatus, tvCleanResult;
    private ProgressBar scanProgress;
    private Button btnScan, btnClean;
    private RecyclerView rvJunkItems;
    private List<JunkItem> junkList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_junk_cleaner);

        MaterialToolbar toolbar = findViewById(R.id.junkToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTotalJunk  = findViewById(R.id.tvTotalJunk);
        tvFreeSpace  = findViewById(R.id.tvFreeSpace);
        tvTotalSpace = findViewById(R.id.tvTotalSpace);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvCleanResult= findViewById(R.id.tvCleanResult);
        scanProgress = findViewById(R.id.junkScanProgress);
        btnScan      = findViewById(R.id.btnScanJunk);
        btnClean     = findViewById(R.id.btnCleanJunk);
        rvJunkItems  = findViewById(R.id.rvJunkItems);

        rvJunkItems.setLayoutManager(new LinearLayoutManager(this));
        updateStorageInfo();

        btnScan.setOnClickListener(v -> startScan());
        btnClean.setOnClickListener(v -> cleanJunk());
    }

    private void updateStorageInfo() {
        try {
            StatFs stat = new StatFs(getFilesDir().getPath());
            long blockSize  = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availBlocks = stat.getAvailableBlocksLong();
            long total = (totalBlocks * blockSize) / (1024 * 1024);
            long free  = (availBlocks * blockSize) / (1024 * 1024);
            tvTotalSpace.setText("Total Storage: " + total + " MB");
            tvFreeSpace.setText("Free: " + free + " MB");
        } catch (Exception e) {
            tvFreeSpace.setText("Storage info unavailable");
        }
    }

    private void startScan() {
        junkList.clear();
        btnScan.setEnabled(false);
        btnClean.setVisibility(View.GONE);
        tvScanStatus.setText("🔍 Scanning for junk files...");
        scanProgress.setVisibility(View.VISIBLE);
        scanProgress.setProgress(0);

        new Thread(() -> {
            // Scan app cache
            scanCacheDir(getCacheDir(), "App Cache");
            scanCacheDir(getExternalCacheDir(), "External Cache");

            // Scan app-specific caches
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            int i = 0;
            for (ApplicationInfo app : apps) {
                i++;
                final int prog = (int)(((float)i / apps.size()) * 80);
                final String appName;
                try { appName = pm.getApplicationLabel(app).toString(); } catch (Exception e) { continue; }
                runOnUiThread(() -> {
                    scanProgress.setProgress(prog);
                    tvScanStatus.setText("🔍 Scanning: " + appName);
                });
                try {
                    Context appCtx = createPackageContext(app.packageName, 0);
                    File cache = appCtx.getCacheDir();
                    if (cache != null && cache.exists()) {
                        long size = getDirSize(cache);
                        if (size > 0) {
                            JunkItem item = new JunkItem(appName + " cache", formatSize(size), cache);
                            junkList.add(item);
                        }
                    }
                } catch (Exception ignored) {}
            }

            long totalJunk = 0;
            for (JunkItem item : junkList) totalJunk += item.sizeBytes;
            final long finalTotal = totalJunk;
            runOnUiThread(() -> {
                scanProgress.setProgress(100);
                tvScanStatus.setText("✅ Scan complete — " + junkList.size() + " items found");
                tvTotalJunk.setText("Junk Found: " + formatSize(finalTotal));
                tvTotalJunk.setTextColor(finalTotal > 0 ? Color.parseColor("#FF005C") : Color.parseColor("#1FAF8B"));
                rvJunkItems.setAdapter(new JunkAdapter(junkList));
                btnScan.setEnabled(true);
                if (!junkList.isEmpty()) btnClean.setVisibility(View.VISIBLE);
                scanProgress.setVisibility(View.GONE);
            });
        }).start();
    }

    private void scanCacheDir(File dir, String label) {
        if (dir == null || !dir.exists()) return;
        long size = getDirSize(dir);
        if (size > 0) junkList.add(new JunkItem(label, formatSize(size), dir));
    }

    private void cleanJunk() {
        btnClean.setEnabled(false);
        tvCleanResult.setVisibility(View.VISIBLE);
        tvCleanResult.setText("🧹 Cleaning...");
        long totalCleaned = 0;
        for (JunkItem item : junkList) {
            if (item.file != null) {
                totalCleaned += getDirSize(item.file);
                deleteDir(item.file);
            }
        }
        final long cleaned = totalCleaned;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            tvCleanResult.setText("✅ Cleaned " + formatSize(cleaned) + " of junk!");
            tvCleanResult.setTextColor(Color.parseColor("#1FAF8B"));
            tvTotalJunk.setText("Junk Found: 0 B");
            junkList.clear();
            rvJunkItems.setAdapter(new JunkAdapter(junkList));
            btnClean.setVisibility(View.GONE);
            btnClean.setEnabled(true);
            updateStorageInfo();
            Toast.makeText(this, "Junk cleaned successfully!", Toast.LENGTH_SHORT).show();
        }, 1500);
    }

    private long getDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) size += f.isDirectory() ? getDirSize(f) : f.length();
        return size;
    }

    private boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return false;
        if (dir.isDirectory()) { File[] children = dir.listFiles(); if (children != null) for (File c : children) deleteDir(c); }
        return dir.delete();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static class JunkItem {
        String name, sizeStr;
        File file;
        long sizeBytes;
        JunkItem(String name, String sizeStr, File file) {
            this.name = name; this.sizeStr = sizeStr; this.file = file;
            this.sizeBytes = file != null ? file.length() : 0;
        }
    }

    static class JunkAdapter extends RecyclerView.Adapter<JunkAdapter.VH> {
        private final List<JunkItem> items;
        JunkAdapter(List<JunkItem> items) { this.items = new ArrayList<>(items); }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(parent.getContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(32, 16, 32, 16);
            row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tv.setTextColor(Color.parseColor("#2D3436"));
            TextView tvSize = new TextView(parent.getContext());
            tvSize.setTextColor(Color.parseColor("#FF005C"));
            tvSize.setTextSize(12);
            row.addView(tv); row.addView(tvSize);
            return new VH(row, tv, tvSize);
        }
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.tv.setText("🗑️ " + items.get(position).name);
            holder.tvSize.setText(items.get(position).sizeStr);
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { TextView tv, tvSize; VH(View v, TextView t, TextView s) { super(v); tv=t; tvSize=s; } }
    }
}
