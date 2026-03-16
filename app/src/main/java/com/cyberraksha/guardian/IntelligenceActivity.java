package com.cyberraksha.guardian;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class IntelligenceActivity extends AppCompatActivity {

    private ThreatEngine threatEngine;
    private BehaviorService behaviorService;
    private CommunityThreatDB communityDB;
    private ResponseEngine responseEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intelligence);

        threatEngine   = ThreatEngine.getInstance(this);
        behaviorService = new BehaviorService(this);
        communityDB    = CommunityThreatDB.getInstance(this);
        responseEngine = new ResponseEngine(this);

        refreshAll();
        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void refreshAll() {
        // DNA stats
        TextView tvDnaUpi = findViewById(R.id.tvDnaUpiCount);
        TextView tvDnaApp = findViewById(R.id.tvDnaAppCount);
        TextView tvDnaStatus = findViewById(R.id.tvDnaStatus);
        if (tvDnaUpi != null) tvDnaUpi.setText(String.valueOf(behaviorService.getKnownUpiCount()));
        if (tvDnaApp != null) tvDnaApp.setText(String.valueOf(behaviorService.getKnownAppCount()));
        if (tvDnaStatus != null) tvDnaStatus.setText("● Active");

        // Community stats
        TextView tvCommCount = findViewById(R.id.tvCommThreatCount);
        TextView tvCommStatus = findViewById(R.id.tvCommunityStatus);
        if (tvCommCount != null) tvCommCount.setText(String.valueOf(communityDB.getTotalReports()));
        if (tvCommStatus != null) tvCommStatus.setText("● Live");

        // Guardian status
        TextView tvGuardian = findViewById(R.id.tvGuardianStatus);
        if (tvGuardian != null) {
            boolean on = isAccessibilityEnabled();
            tvGuardian.setText(on ? "● Watching" : "⚠ Enable in Settings");
            tvGuardian.setTextColor(Color.parseColor(on ? "#B400FF" : "#FFD600"));
        }

        // Threat engine
        int score = threatEngine.getCurrentScore();
        TextView tvScore = findViewById(R.id.tvThreatScore);
        ProgressBar meter = findViewById(R.id.intelThreatMeter);
        if (tvScore != null) {
            tvScore.setText(score + "%");
            tvScore.setTextColor(Color.parseColor(
                score >= 70 ? "#FF005C" : score >= 40 ? "#FFD600" : "#00FF94"));
        }
        if (meter != null) {
            meter.setProgress(score);
            meter.getProgressDrawable().setColorFilter(
                Color.parseColor(score >= 70 ? "#FF005C" : score >= 40 ? "#FFD600" : "#00FF94"),
                android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    private void setupButtons() {
        MaterialButton btnReport = findViewById(R.id.btnReportFraud);
        if (btnReport != null) {
            btnReport.setOnClickListener(v -> {
                communityDB.reportNumber("reported_by_user");
                threatEngine.resetScore();
                Toast.makeText(this, "✅ Fraud reported to community database", Toast.LENGTH_SHORT).show();
                refreshAll();
            });
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String svc = getPackageName() + "/" + SafeSearchService.class.getCanonicalName();
            String en = android.provider.Settings.Secure.getString(getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return en != null && en.contains(svc);
        } catch (Exception e) { return false; }
    }
}
