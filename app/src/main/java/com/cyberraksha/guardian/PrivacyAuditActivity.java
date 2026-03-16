package com.cyberraksha.guardian;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrivacyAuditActivity extends AppCompatActivity {

    private RecyclerView rvPrivacy;
    private TextView tvRiskCount, tvSafeCount, tvSummary;
    private TabLayout tabLayout;
    private List<PrivacyItem> highRisk  = new ArrayList<>();
    private List<PrivacyItem> medRisk   = new ArrayList<>();
    private List<PrivacyItem> safeApps  = new ArrayList<>();
    private List<PrivacyItem> current   = new ArrayList<>();

    // Dangerous permissions grouped by category
    private static final Map<String, String[]> PERM_GROUPS = new HashMap<String, String[]>() {{
        put("📍 Location",   new String[]{"ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION"});
        put("📷 Camera",     new String[]{"CAMERA"});
        put("🎤 Microphone", new String[]{"RECORD_AUDIO"});
        put("📇 Contacts",   new String[]{"READ_CONTACTS", "WRITE_CONTACTS"});
        put("📞 Phone",      new String[]{"READ_CALL_LOG", "WRITE_CALL_LOG", "READ_PHONE_STATE", "CALL_PHONE"});
        put("💬 SMS",        new String[]{"READ_SMS", "SEND_SMS", "RECEIVE_SMS"});
        put("🗂 Storage",    new String[]{"READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"});
        put("🔔 Overlay",    new String[]{"SYSTEM_ALERT_WINDOW"});
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_audit);

        MaterialToolbar toolbar = findViewById(R.id.privacyToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvRiskCount = findViewById(R.id.tvRiskCount);
        tvSafeCount = findViewById(R.id.tvSafeCount);
        tvSummary   = findViewById(R.id.tvPrivacySummary);
        tabLayout   = findViewById(R.id.privacyTabLayout);
        rvPrivacy   = findViewById(R.id.rvPrivacyApps);
        rvPrivacy.setLayoutManager(new LinearLayoutManager(this));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                current.clear();
                switch (tab.getPosition()) {
                    case 0: current.addAll(highRisk); break;
                    case 1: current.addAll(medRisk);  break;
                    case 2: current.addAll(safeApps); break;
                }
                rvPrivacy.getAdapter().notifyDataSetChanged();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        rvPrivacy.setAdapter(new PrivacyAdapter(current));
        runAudit();
    }

    private void runAudit() {
        tvSummary.setText("🔍 Scanning all installed apps...");
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            for (PackageInfo pkg : packages) {
                if (pkg.applicationInfo == null) continue;
                boolean isSystem = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (isSystem) continue; // skip system apps

                String appName = pkg.applicationInfo.loadLabel(pm).toString();
                List<String> foundPerms = new ArrayList<>();
                List<String> permLabels = new ArrayList<>();

                if (pkg.requestedPermissions != null) {
                    for (String perm : pkg.requestedPermissions) {
                        String shortPerm = perm.contains(".") ? perm.substring(perm.lastIndexOf(".") + 1) : perm;
                        for (Map.Entry<String, String[]> entry : PERM_GROUPS.entrySet()) {
                            for (String p : entry.getValue()) {
                                if (shortPerm.equals(p)) {
                                    if (!foundPerms.contains(shortPerm)) {
                                        foundPerms.add(shortPerm);
                                        if (!permLabels.contains(entry.getKey())) permLabels.add(entry.getKey());
                                    }
                                }
                            }
                        }
                    }
                }

                PrivacyItem item = new PrivacyItem(appName, pkg.packageName, permLabels);
                if (permLabels.size() >= 4) highRisk.add(item);
                else if (permLabels.size() >= 2) medRisk.add(item);
                else safeApps.add(item);
            }

            runOnUiThread(() -> {
                tvRiskCount.setText("⚠️ High Risk: " + highRisk.size());
                tvRiskCount.setTextColor(Color.parseColor("#FF005C"));
                tvSafeCount.setText("✅ Safe: " + safeApps.size());
                tvSafeCount.setTextColor(Color.parseColor("#1FAF8B"));
                tvSummary.setText("Audit complete. Tap tabs to explore results.");

                // Update tab titles
                TabLayout.Tab t0 = tabLayout.getTabAt(0);
                TabLayout.Tab t1 = tabLayout.getTabAt(1);
                TabLayout.Tab t2 = tabLayout.getTabAt(2);
                if (t0 != null) t0.setText("High Risk (" + highRisk.size() + ")");
                if (t1 != null) t1.setText("Medium (" + medRisk.size() + ")");
                if (t2 != null) t2.setText("Safe (" + safeApps.size() + ")");

                current.clear();
                current.addAll(highRisk);
                rvPrivacy.getAdapter().notifyDataSetChanged();
            });
        }).start();
    }

    static class PrivacyItem {
        String name, packageName;
        List<String> permLabels;
        PrivacyItem(String name, String pkg, List<String> perms) {
            this.name = name; this.packageName = pkg; this.permLabels = perms;
        }
    }

    class PrivacyAdapter extends RecyclerView.Adapter<PrivacyAdapter.VH> {
        private final List<PrivacyItem> list;
        PrivacyAdapter(List<PrivacyItem> list) { this.list = list; }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(32, 20, 32, 20);
            root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView tvName = new TextView(parent.getContext()); tvName.setTextSize(14); tvName.setTag("name"); tvName.setTextColor(Color.parseColor("#2D3436"));
            TextView tvPerms = new TextView(parent.getContext()); tvPerms.setTextSize(11); tvPerms.setTag("perms"); tvPerms.setTextColor(Color.parseColor("#636E72"));
            Button btnManage = new Button(parent.getContext()); btnManage.setTag("btn"); btnManage.setText("Manage Permissions");
            btnManage.setTextSize(11); btnManage.setBackgroundColor(Color.parseColor("#1FAF8B")); btnManage.setTextColor(Color.WHITE);
            root.addView(tvName); root.addView(tvPerms); root.addView(btnManage);
            return new VH(root);
        }
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            PrivacyItem item = list.get(position);
            ((TextView) holder.itemView.findViewWithTag("name")).setText("📱 " + item.name);
            String permsText = item.permLabels.isEmpty() ? "No sensitive permissions" : String.join("  ", item.permLabels);
            ((TextView) holder.itemView.findViewWithTag("perms")).setText(permsText);
            Button btn = (Button) holder.itemView.findViewWithTag("btn");
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + item.packageName));
                startActivity(intent);
            });
        }
        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
