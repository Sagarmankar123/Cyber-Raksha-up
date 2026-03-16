package com.cyberraksha.guardian;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class V2SecurityEngineActivity extends AppCompatActivity {

    // UI
    private View rootLayout;
    private SwitchMaterial turboSwitch;
    private TextView tvModeLabel, tvThreatScore, tvRiskLevel, tvRiskHindi;
    private TextView tvBehaviorSummary, tvCommunityStats, tvEvidenceCount;
    private TextView tvConsoleOutput;
    private ProgressBar threatMeter;
    private MaterialButton btnReportFraud, btnClearEvidence;
    private FrameLayout turboVideoContainer;
    private CardView cardBehavior, cardCommunity, cardGuardian, cardResponse;

    // Engines
    private ThreatEngine threatEngine;
    private BehaviorService behaviorService;
    private CommunityThreatDB communityDB;
    private ResponseEngine responseEngine;

    private boolean isTurboMode = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Threat broadcast receiver
    private final BroadcastReceiver threatReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int score = intent.getIntExtra(ResponseEngine.EXTRA_RISK_SCORE, 0);
            String reason = intent.getStringExtra(ResponseEngine.EXTRA_REASON);
            updateThreatUI(score, reason);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_v2_security_engine);

        // Init engines
        threatEngine   = ThreatEngine.getInstance(this);
        behaviorService = new BehaviorService(this);
        communityDB    = CommunityThreatDB.getInstance(this);
        responseEngine = new ResponseEngine(this);

        bindViews();
        setupTurboSwitch();
        setupButtons();
        refreshStats();
        registerThreatReceiver();
    }

    private void bindViews() {
        rootLayout        = findViewById(R.id.v2RootLayout);
        turboSwitch       = findViewById(R.id.turboSwitch);
        tvModeLabel       = findViewById(R.id.tvModeLabel);
        tvThreatScore     = findViewById(R.id.tvThreatScore);
        tvRiskLevel       = findViewById(R.id.tvRiskLevel);
        tvRiskHindi       = findViewById(R.id.tvRiskHindi);
        threatMeter       = findViewById(R.id.threatMeter);
        tvBehaviorSummary = findViewById(R.id.tvBehaviorSummary);
        tvCommunityStats  = findViewById(R.id.tvCommunityStats);
        tvEvidenceCount   = findViewById(R.id.tvEvidenceCount);
        tvConsoleOutput   = findViewById(R.id.tvConsoleOutput);
        btnReportFraud    = findViewById(R.id.btnReportFraud);
        btnClearEvidence  = findViewById(R.id.btnClearEvidence);
        turboVideoContainer = findViewById(R.id.turboVideoContainer);
        cardBehavior      = findViewById(R.id.cardBehavior);
        cardCommunity     = findViewById(R.id.cardCommunity);
        cardGuardian      = findViewById(R.id.cardGuardian);
        cardResponse      = findViewById(R.id.cardResponse);
    }

    // ── TURBO MODE ────────────────────────────────────────────────────────────

    private void setupTurboSwitch() {
        turboSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) activateTurboMode();
            else deactivateNormalMode();
        });
    }

    private void activateTurboMode() {
        isTurboMode = true;
        tvModeLabel.setText("🕷 TURBO MODE");
        tvModeLabel.setTextColor(Color.parseColor("#B400FF"));

        // Fade background to dark
        ObjectAnimator bgAnim = ObjectAnimator.ofArgb(
                rootLayout, "backgroundColor",
                Color.parseColor("#F5F7F6"),
                Color.parseColor("#0A0A0A"));
        bgAnim.setDuration(400);
        bgAnim.start();

        // Show video container (tu manually VideoView add karega)
        turboVideoContainer.setVisibility(View.VISIBLE);
        turboVideoContainer.setAlpha(0f);
        turboVideoContainer.animate().alpha(1f).setDuration(600).start();

        // Cards to dark cyber style
        int darkCard = Color.parseColor("#0D1117");
        animateCardColor(cardBehavior, darkCard);
        animateCardColor(cardCommunity, darkCard);
        animateCardColor(cardGuardian, darkCard);
        animateCardColor(cardResponse, darkCard);

        // Typewriter console effect
        runTypewriterSequence();

        // Threat meter glow pulse
        startThreatMeterPulse();
    }

    private void deactivateNormalMode() {
        isTurboMode = false;
        tvModeLabel.setText("🟢 NORMAL MODE");
        tvModeLabel.setTextColor(Color.parseColor("#1FAF8B"));

        ObjectAnimator bgAnim = ObjectAnimator.ofArgb(
                rootLayout, "backgroundColor",
                Color.parseColor("#0A0A0A"),
                Color.parseColor("#F5F7F6"));
        bgAnim.setDuration(400);
        bgAnim.start();

        turboVideoContainer.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> turboVideoContainer.setVisibility(View.GONE)).start();

        int lightCard = Color.WHITE;
        animateCardColor(cardBehavior, lightCard);
        animateCardColor(cardCommunity, lightCard);
        animateCardColor(cardGuardian, lightCard);
        animateCardColor(cardResponse, lightCard);

        tvConsoleOutput.setText("");
    }

    // Typewriter console — Jarvis activation feel
    private void runTypewriterSequence() {
        String[] lines = {
            "CYBERRAKSHA TURBO MODE ACTIVATED",
            "Threat Engine Online...",
            "Android Guardian Active...",
            "Community Shield Connected...",
            "Behavioral DNA Loaded...",
            "[ ALL SYSTEMS OPERATIONAL ]"
        };

        tvConsoleOutput.setText("");
        tvConsoleOutput.setVisibility(View.VISIBLE);

        final int[] lineIdx = {0};
        final int[] charIdx = {0};
        final StringBuilder current = new StringBuilder();

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (lineIdx[0] >= lines.length) return;

                String line = lines[lineIdx[0]];
                if (charIdx[0] < line.length()) {
                    current.append(line.charAt(charIdx[0]));
                    tvConsoleOutput.setText(current.toString() + "_");
                    charIdx[0]++;
                    handler.postDelayed(this, 40);
                } else {
                    current.append("\n");
                    lineIdx[0]++;
                    charIdx[0] = 0;
                    handler.postDelayed(this, 200);
                }
            }
        });
    }

    private void startThreatMeterPulse() {
        if (!isTurboMode) return;
        ValueAnimator pulse = ValueAnimator.ofFloat(0.7f, 1.0f);
        pulse.setDuration(800);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> threatMeter.setAlpha((Float) a.getAnimatedValue()));
        pulse.start();
    }

    private void animateCardColor(CardView card, int toColor) {
        ObjectAnimator anim = ObjectAnimator.ofArgb(card, "cardBackgroundColor",
                card.getCardBackgroundColor().getDefaultColor(), toColor);
        anim.setDuration(400);
        anim.start();
    }

    // ── THREAT UI UPDATE ──────────────────────────────────────────────────────

    private void updateThreatUI(int score, String reason) {
        threatMeter.setProgress(score);
        tvThreatScore.setText(score + "%");

        String level = threatEngine.getRiskLabel();
        tvRiskLevel.setText(level);
        tvRiskHindi.setText(threatEngine.getRiskHindi());

        int color;
        switch (threatEngine.getRiskLevel()) {
            case ThreatEngine.RISK_HIGH:
                color = Color.parseColor("#FF005C");
                break;
            case ThreatEngine.RISK_SUSPICIOUS:
                color = Color.parseColor("#FFD600");
                break;
            default:
                color = Color.parseColor("#00FF94");
        }
        tvRiskLevel.setTextColor(color);
        tvThreatScore.setTextColor(color);

        if (reason != null) {
            Toast.makeText(this, "⚠️ " + reason, Toast.LENGTH_LONG).show();
        }

        refreshStats();
    }

    private void refreshStats() {
        tvBehaviorSummary.setText(behaviorService.getBehaviorSummary());
        tvCommunityStats.setText(communityDB.getStats());
        tvEvidenceCount.setText("Evidence logs: " + responseEngine.getEvidenceCount());

        int score = threatEngine.getCurrentScore();
        threatMeter.setProgress(score);
        tvThreatScore.setText(score + "%");
        tvRiskLevel.setText(threatEngine.getRiskLabel());
        tvRiskHindi.setText(threatEngine.getRiskHindi());
    }

    // ── BUTTONS ───────────────────────────────────────────────────────────────

    private void setupButtons() {
        btnReportFraud.setOnClickListener(v -> {
            // In production — open report dialog for UPI/Number/Domain
            communityDB.reportNumber("0000000000"); // placeholder
            Toast.makeText(this, "✅ Fraud reported to community database", Toast.LENGTH_SHORT).show();
        });

        btnClearEvidence.setOnClickListener(v -> {
            responseEngine.clearEvidence();
            threatEngine.resetScore();
            refreshStats();
            Toast.makeText(this, "Evidence cleared", Toast.LENGTH_SHORT).show();
        });
    }

    // ── BROADCAST ─────────────────────────────────────────────────────────────

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerThreatReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ResponseEngine.ACTION_THREAT_DETECTED);
        filter.addAction(ResponseEngine.ACTION_UPI_SCAM);
        filter.addAction(ResponseEngine.ACTION_SCAM_CALL);
        filter.addAction(ResponseEngine.ACTION_OVERLAY_ATTACK);
        ContextCompat.registerReceiver(this, threatReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(threatReceiver); } catch (Exception ignored) {}
        handler.removeCallbacksAndMessages(null);
    }
}
