package com.cyberraksha.guardian;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * CyberRaksha Dynamic Island Service — v4
 * ══════════════════════════════════════════════════════════════════════════════
 * Handles ALL island states:
 *   IDLE       → pill: dot + mode label + score
 *   STATUS     → Guardian info + Normal/Turbo toggle (manual tap)
 *   CALL       → incoming/ongoing call with fraud check
 *   MUSIC      → now playing + play/pause/next controls
 *   MESSAGE    → WhatsApp/Telegram/Instagram message preview
 *   TIMER      → countdown timer from Clock app
 *   SECURITY   → scam call / UPI fraud / overlay attack (CyberRaksha)
 *   SCAN       → live app scan progress
 *   GENERIC    → any other app notification
 *
 * Receives events from:
 *   - IslandNotificationService (all system notifications)
 *   - ResponseEngine broadcasts (security threats)
 *   - Direct broadcasts from ScanListActivity etc.
 */
public class DynamicBarService extends Service {

    // ── CyberRaksha security broadcasts ──────────────────────────────────────
    public static final String ACTION_SHOW_ALERT       = "com.cyberraksha.guardian.SHOW_ALERT";
    public static final String ACTION_SHOW_SCAM_CALL   = "com.cyberraksha.guardian.SHOW_SCAM_CALL";
    public static final String ACTION_SHOW_UPI_ALERT   = "com.cyberraksha.guardian.SHOW_UPI_ALERT";
    public static final String ACTION_SHOW_OVERLAY_ATK = "com.cyberraksha.guardian.SHOW_OVERLAY_ATK";
    public static final String ACTION_SHOW_SCAN        = "com.cyberraksha.guardian.SHOW_SCAN";
    public static final String ACTION_TURBO_ON         = "com.cyberraksha.guardian.TURBO_ON";
    public static final String ACTION_TURBO_OFF        = "com.cyberraksha.guardian.TURBO_OFF";
    public static final String ACTION_UPDATE_SCORE     = "com.cyberraksha.guardian.UPDATE_SCORE";
    // Island visibility — sent from Settings / Dashboard
    public static final String ACTION_HIDE_ISLAND      = "com.cyberraksha.guardian.HIDE_ISLAND";
    public static final String ACTION_SHOW_ISLAND      = "com.cyberraksha.guardian.SHOW_ISLAND";

    // ── Extras ────────────────────────────────────────────────────────────────
    public static final String EXTRA_ALERT_TEXT    = "alert_text";
    public static final String EXTRA_CALLER_NUMBER = "caller_number";
    public static final String EXTRA_RISK_SCORE    = "risk_score";
    public static final String EXTRA_UPI_ID        = "upi_id";
    public static final String EXTRA_PACKAGE_NAME  = "package_name";
    public static final String EXTRA_SCAN_CURRENT  = "scan_current";
    public static final String EXTRA_SCAN_TOTAL    = "scan_total";
    public static final String EXTRA_SCORE         = "score";

    private enum IslandState {
        IDLE, STATUS, CALL, MUSIC, MESSAGE, TIMER, SECURITY_SCAM,
        SECURITY_UPI, SECURITY_OVERLAY, SCAN, GENERIC
    }

    private static final String CHANNEL_ID        = "cyberraksha_island_v4";
    private static final int    NOTIF_ID          = 1001;
    private static final long   CALL_DISMISS_MS   = 30_000; // calls stay longer
    private static final long   MUSIC_DISMISS_MS  = 15_000;  // music stays longer
    private static final long   MSG_DISMISS_MS    = 6_000;
    private static final long   TIMER_DISMISS_MS  = 5_000;
    private static final long   STATUS_DISMISS_MS = 6_000;
    private static final long   ALERT_DISMISS_MS  = 10_000;
    private static final long   GENERIC_DISMISS_MS= 4_000;
    private static final long   EXPAND_MS         = 300;
    private static final long   COLLAPSE_MS       = 220;

    // ── WindowManager ─────────────────────────────────────────────────────────
    private WindowManager              wm;
    private View                       rootView;
    private WindowManager.LayoutParams wlpIdle;
    private WindowManager.LayoutParams wlpFocusable;

    // ── Pill views ────────────────────────────────────────────────────────────
    private View     pillContainer;
    private View     statusDot;
    private TextView tvPillMode;
    private TextView tvPillScore;

    // ── Panel shared views ────────────────────────────────────────────────────
    private View         panelContainer;
    private TextView     tvPanelIcon;
    private TextView     tvPanelTitle;
    private TextView     tvPanelSubtitle;

    // ── Section: CALL ─────────────────────────────────────────────────────────
    private LinearLayout sectionCall;
    private TextView     tvCallName, tvCallStatus, tvCallRisk;
    private TextView     btnCallAnswer, btnCallDecline;

    // ── Section: MUSIC ────────────────────────────────────────────────────────
    private LinearLayout sectionMusic;
    private TextView     tvMusicTitle, tvMusicArtist, tvMusicApp;
    private TextView     btnMusicPlayPause, btnMusicPrev, btnMusicNext;
    private String       currentMusicPackage = "";

    // ── Section: MESSAGE ──────────────────────────────────────────────────────
    private LinearLayout sectionMessage;
    private TextView     tvMsgSender, tvMsgPreview, tvMsgApp;

    // ── Section: TIMER ────────────────────────────────────────────────────────
    private LinearLayout sectionTimer;
    private TextView     tvTimerLabel, tvTimerValue;

    // ── Section: SECURITY ─────────────────────────────────────────────────────
    private LinearLayout sectionSecurity;
    private TextView     tvPanelBody;
    private LinearLayout actionRow;
    private TextView     btnAction1, btnAction2, btnAction3;

    // ── Section: SCAN ─────────────────────────────────────────────────────────
    private LinearLayout sectionScan;
    private ProgressBar  scanProgress;
    private TextView     tvScanLabel;

    // ── Mode toggle (always shown in panel) ───────────────────────────────────
    private TextView     btnModeNormal, btnModeTurbo;

    // ── State ─────────────────────────────────────────────────────────────────
    private IslandState   currentState  = IslandState.IDLE;
    private boolean       isTurboMode   = false;
    private int           currentScore  = 0;
    private boolean       isAnimating   = false;
    private boolean       islandVisible = true;   // user can hide the island
    private ValueAnimator pulseAnim     = null;
    private String        currentNotifKey = "";

    private final Handler  handler       = new Handler(Looper.getMainLooper());
    private final Runnable dismissRunner = this::collapseToIdle;

    // ── Mega BroadcastReceiver — handles everything ───────────────────────────
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            switch (action) {

                // ── Island Notification events (from IslandNotificationService)
                case IslandNotificationService.ACTION_ISLAND_CALL:
                    showCall(
                        intent.getStringExtra(IslandNotificationService.EXTRA_CALL_NAME),
                        intent.getStringExtra(IslandNotificationService.EXTRA_CALL_NUMBER),
                        intent.getStringExtra(IslandNotificationService.EXTRA_CALL_TYPE),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_APP),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_KEY));
                    break;

                case IslandNotificationService.ACTION_ISLAND_MUSIC:
                    showMusic(
                        intent.getStringExtra(IslandNotificationService.EXTRA_MUSIC_TITLE),
                        intent.getStringExtra(IslandNotificationService.EXTRA_MUSIC_ARTIST),
                        intent.getStringExtra(IslandNotificationService.EXTRA_MUSIC_PACKAGE),
                        intent.getBooleanExtra(IslandNotificationService.EXTRA_MUSIC_PLAYING, true),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_KEY));
                    break;

                case IslandNotificationService.ACTION_ISLAND_MESSAGE:
                    showMessage(
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_TITLE),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_TEXT),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_ICON),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_KEY));
                    break;

                case IslandNotificationService.ACTION_ISLAND_TIMER:
                    showTimer(
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_TITLE),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_TEXT),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_KEY));
                    break;

                case IslandNotificationService.ACTION_ISLAND_GENERIC:
                    showGenericNotif(
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_TITLE),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_TEXT),
                        intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_KEY));
                    break;

                case IslandNotificationService.ACTION_ISLAND_DISMISS:
                    String key = intent.getStringExtra(IslandNotificationService.EXTRA_ISLAND_KEY);
                    // "__controller_reset__" = IslandController clearing all events
                    if ("__controller_reset__".equals(key)) {
                        collapseToIdle();
                    } else if (key != null && key.equals(currentNotifKey)) {
                        collapseToIdle();
                    }
                    break;

                // ── CyberRaksha Security events
                case ACTION_SHOW_SCAM_CALL:
                case ResponseEngine.ACTION_SCAM_CALL:
                    showSecurityScam(
                        intent.getStringExtra(EXTRA_CALLER_NUMBER) != null
                            ? intent.getStringExtra(EXTRA_CALLER_NUMBER)
                            : intent.getStringExtra(ResponseEngine.EXTRA_REASON),
                        intent.getIntExtra(EXTRA_RISK_SCORE,
                            intent.getIntExtra(ResponseEngine.EXTRA_RISK_SCORE, 80)));
                    break;

                case ACTION_SHOW_UPI_ALERT:
                case ResponseEngine.ACTION_UPI_SCAM:
                    showSecurityUpi(
                        intent.getStringExtra(EXTRA_UPI_ID) != null
                            ? intent.getStringExtra(EXTRA_UPI_ID)
                            : intent.getStringExtra(ResponseEngine.EXTRA_REASON),
                        intent.getIntExtra(EXTRA_RISK_SCORE,
                            intent.getIntExtra(ResponseEngine.EXTRA_RISK_SCORE, 70)));
                    break;

                case ACTION_SHOW_OVERLAY_ATK:
                case ResponseEngine.ACTION_OVERLAY_ATTACK:
                    showSecurityOverlay(
                        intent.getStringExtra(EXTRA_PACKAGE_NAME) != null
                            ? intent.getStringExtra(EXTRA_PACKAGE_NAME)
                            : intent.getStringExtra(ResponseEngine.EXTRA_PACKAGE));
                    break;

                case ACTION_SHOW_SCAN:
                    showScan(intent.getIntExtra(EXTRA_SCAN_CURRENT, 0),
                             intent.getIntExtra(EXTRA_SCAN_TOTAL, 42));
                    break;

                case ACTION_SHOW_ALERT:
                    showGenericAlert(intent.getStringExtra(EXTRA_ALERT_TEXT));
                    break;

                case ACTION_TURBO_ON:  applyTurbo(true);  break;
                case ACTION_TURBO_OFF: applyTurbo(false); break;

                case ACTION_UPDATE_SCORE:
                    applyScore(intent.getIntExtra(EXTRA_SCORE, 0)); break;

                case ACTION_HIDE_ISLAND:
                    hideIsland(); break;

                case ACTION_SHOW_ISLAND:
                    showIsland(); break;

                case "com.cyberraksha.guardian.ISLAND_PREF":
                    boolean showToggle = intent.getBooleanExtra("show_toggle", true);
                    applyToggleVisibility(showToggle); break;
            }
        }
    };

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        inflate();
        registerBroadcastReceiver();
        // Restore persisted state
        android.content.SharedPreferences prefs =
                getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE);
        islandVisible = prefs.getBoolean("island_visible", true);
        isTurboMode   = prefs.getBoolean("turbo_mode", false);
        if (!islandVisible && rootView != null) rootView.setVisibility(android.view.View.GONE);
        refreshPill();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPulse();
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        if (rootView != null && wm != null)
            try { wm.removeView(rootView); } catch (Exception ignored) {}
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    // ═══════════════════════════════════════════════════════════════════════════
    // INFLATE
    // ═══════════════════════════════════════════════════════════════════════════

    private void inflate() {
        rootView = LayoutInflater.from(this).inflate(R.layout.overlay_dynamic_bar, null);

        // Pill
        pillContainer = rootView.findViewById(R.id.pillContainer);
        statusDot     = rootView.findViewById(R.id.statusDot);
        tvPillMode    = rootView.findViewById(R.id.tvPillMode);
        tvPillScore   = rootView.findViewById(R.id.tvPillScore);

        // Panel shared
        panelContainer  = rootView.findViewById(R.id.panelContainer);
        tvPanelIcon     = rootView.findViewById(R.id.tvPanelIcon);
        tvPanelTitle    = rootView.findViewById(R.id.tvPanelTitle);
        tvPanelSubtitle = rootView.findViewById(R.id.tvPanelSubtitle);

        // Call
        sectionCall    = rootView.findViewById(R.id.sectionCall);
        tvCallName     = rootView.findViewById(R.id.tvCallName);
        tvCallStatus   = rootView.findViewById(R.id.tvCallStatus);
        tvCallRisk     = rootView.findViewById(R.id.tvCallRisk);
        btnCallAnswer  = rootView.findViewById(R.id.btnCallAnswer);
        btnCallDecline = rootView.findViewById(R.id.btnCallDecline);

        // Music
        sectionMusic      = rootView.findViewById(R.id.sectionMusic);
        tvMusicTitle      = rootView.findViewById(R.id.tvMusicTitle);
        tvMusicArtist     = rootView.findViewById(R.id.tvMusicArtist);
        tvMusicApp        = rootView.findViewById(R.id.tvMusicApp);
        btnMusicPlayPause = rootView.findViewById(R.id.btnMusicPlayPause);
        btnMusicPrev      = rootView.findViewById(R.id.btnMusicPrev);
        btnMusicNext      = rootView.findViewById(R.id.btnMusicNext);
        // tvMusicArt is optional (emoji art circle) - no field needed, set via showMusic

        // Message
        sectionMessage = rootView.findViewById(R.id.sectionMessage);
        tvMsgSender    = rootView.findViewById(R.id.tvMsgSender);
        tvMsgPreview   = rootView.findViewById(R.id.tvMsgPreview);
        tvMsgApp       = rootView.findViewById(R.id.tvMsgApp);

        // Timer
        sectionTimer  = rootView.findViewById(R.id.sectionTimer);
        tvTimerLabel  = rootView.findViewById(R.id.tvTimerLabel);
        tvTimerValue  = rootView.findViewById(R.id.tvTimerValue);

        // Security
        sectionSecurity = rootView.findViewById(R.id.sectionSecurity);
        tvPanelBody     = rootView.findViewById(R.id.tvPanelBody);
        actionRow       = rootView.findViewById(R.id.actionRow);
        btnAction1      = rootView.findViewById(R.id.btnAction1);
        btnAction2      = rootView.findViewById(R.id.btnAction2);
        btnAction3      = rootView.findViewById(R.id.btnAction3);

        // Scan
        sectionScan  = rootView.findViewById(R.id.sectionScan);
        scanProgress = rootView.findViewById(R.id.scanProgress);
        tvScanLabel  = rootView.findViewById(R.id.tvScanLabel);

        // Mode toggle
        btnModeNormal = rootView.findViewById(R.id.btnModeNormal);
        btnModeTurbo  = rootView.findViewById(R.id.btnModeTurbo);

        // Window params
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        wlpIdle = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        wlpIdle.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wlpIdle.y = getStatusBarHeight() / 2 - 18; // vertically centered inside status bar

        wlpFocusable = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        wlpFocusable.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wlpFocusable.y = getStatusBarHeight() / 2 - 18; // same — expands downward from status bar

        wm.addView(rootView, wlpIdle);
        wireTouch();
    }

    private void wireTouch() {
        pillContainer.setOnClickListener(v -> {
            if (currentState == IslandState.IDLE) expandStatus();
        });
        panelContainer.setOnClickListener(v -> collapseToIdle());
        rootView.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_OUTSIDE
                    && currentState != IslandState.IDLE) collapseToIdle();
            return false;
        });

        btnModeNormal.setOnClickListener(v -> {
            if (isTurboMode) {
                Intent intent = new Intent(ACTION_TURBO_OFF);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
                applyTurbo(false);
            }
            collapseToIdle();
        });
        btnModeTurbo.setOnClickListener(v -> {
            if (!isTurboMode) {
                Intent intent = new Intent(ACTION_TURBO_ON);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
                applyTurbo(true);
            }
            collapseToIdle();
        });

        // Music controls
        btnMusicPlayPause.setOnClickListener(v -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        btnMusicPrev.setOnClickListener(v -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        btnMusicNext.setOnClickListener(v -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATE METHODS — one per island state
    // ═══════════════════════════════════════════════════════════════════════════

    private void refreshPill() {
        if (isTurboMode) {
            pillContainer.setBackgroundResource(R.drawable.bg_bar_turbo);
            tvPillMode.setText("🕷 Turbo");
            tvPillScore.setTextColor(Color.parseColor("#FF005C"));
            statusDot.setBackgroundResource(R.drawable.dot_danger);
        } else {
            pillContainer.setBackgroundResource(R.drawable.bg_dynamic_bar);
            tvPillMode.setText("🛡 Guardian");
            tvPillScore.setTextColor(Color.parseColor("#00FF94"));
            statusDot.setBackgroundResource(
                    currentScore >= 70 ? R.drawable.dot_danger : R.drawable.dot_safe);
        }
        tvPillScore.setText(currentScore + "%");
    }

    /** Tap pill → show status + mode toggle */
    private void expandStatus() {
        cancelDismiss();
        currentState = IslandState.STATUS;
        currentNotifKey = "";

        tvPanelIcon.setVisibility(View.GONE);
        tvPanelTitle.setText("CyberRaksha " + (isTurboMode ? "TURBO" : "Guardian"));
        tvPanelTitle.setTextColor(isTurboMode ? 0xFFFF005C : 0xFF00FF94);
        String label  = currentScore >= 70 ? "HIGH RISK" : currentScore >= 40 ? "SUSPICIOUS" : "PROTECTED";
        int    lcolor = currentScore >= 70 ? 0xFFFF005C  : currentScore >= 40 ? 0xFFFFB800  : 0xFF00FF94;
        tvPanelSubtitle.setText("Threat Level: " + label + "  |  Score: " + currentScore);
        tvPanelSubtitle.setTextColor(lcolor);

        hideAllSections();
        refreshModeToggle();
        expandPanel(STATUS_DISMISS_MS);
    }

    /** Incoming / ongoing call */
    private void showCall(String name, String number, String type, String pkg, String key) {
        if (!shouldShowForEvent(true)) return;  // SECURITY — always breaks through
        if (name == null) name = number != null ? number : "Unknown";
        cancelDismiss();
        currentState    = IslandState.CALL;
        currentNotifKey = key != null ? key : "";

        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("📞");
        tvPanelTitle.setText("incoming".equals(type) ? "Incoming Call" : "Active Call");
        tvPanelTitle.setTextColor(Color.parseColor("#00FF94"));
        tvPanelSubtitle.setText(pkg != null ? getFriendlyName(pkg) : "Phone");
        tvPanelSubtitle.setTextColor(0x88FFFFFF);

        tvCallName.setText(name);
        tvCallStatus.setText("incoming".equals(type) ? "📲 Incoming" : "📞 Ongoing");
        tvCallRisk.setText("🛡 Checking reputation...");
        tvCallRisk.setTextColor(Color.parseColor("#FFB800"));

        // Async fraud check
        final String finalName = name;
        handler.postDelayed(() -> {
            String numToCheck = extractDigits(finalName);
            if (numToCheck.isEmpty() && number != null) numToCheck = extractDigits(number);
            if (!numToCheck.isEmpty()) {
                CommunityThreatDB db = CommunityThreatDB.getInstance(DynamicBarService.this);
                if (db.isNumberReported(numToCheck)) {
                    tvCallRisk.setText("🚨 FRAUD NUMBER — Do not answer!");
                    tvCallRisk.setTextColor(Color.parseColor("#FF005C"));
                    startPulse();
                } else {
                    tvCallRisk.setText("🛡 Not in fraud database");
                    tvCallRisk.setTextColor(Color.parseColor("#00FF94"));
                }
            } else {
                tvCallRisk.setText("🛡 Not in fraud database");
                tvCallRisk.setTextColor(Color.parseColor("#00FF94"));
            }
        }, 600);

        btnCallAnswer.setOnClickListener(v -> {
            // Open dialer to answer
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            collapseToIdle();
        });
        btnCallDecline.setOnClickListener(v -> collapseToIdle());

        hideAllSections();
        sectionCall.setVisibility(View.VISIBLE);
        refreshModeToggle();
        expandPanel(CALL_DISMISS_MS);
    }

    /** Music now playing */
    private void showMusic(String title, String artist, String pkg, boolean playing, String key) {
        if (!shouldShowForEvent(false)) return;  // cosmetic — respect hidden state
        if (title == null || title.isEmpty()) return;
        cancelDismiss();
        currentState        = IslandState.MUSIC;
        currentNotifKey     = key != null ? key : "";
        currentMusicPackage = pkg != null ? pkg : "";

        String appName = getFriendlyName(pkg != null ? pkg : "");

        // Update PILL to show music (so user sees it even without expanding)
        tvPillMode.setText("🎵 " + truncate(title, 18));
        tvPillScore.setText(playing ? "▶" : "⏸");
        tvPillScore.setTextColor(Color.parseColor("#00FF94"));
        statusDot.setBackgroundResource(R.drawable.dot_safe);

        // Panel content
        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("🎵");
        tvPanelTitle.setText(playing ? "Now Playing" : "Paused");
        tvPanelTitle.setTextColor(Color.parseColor("#00FF94"));
        tvPanelSubtitle.setText(appName);
        tvPanelSubtitle.setTextColor(0x88FFFFFF);

        tvMusicTitle.setText(title);
        tvMusicTitle.setSelected(true); // enables marquee scrolling
        tvMusicArtist.setText(artist != null ? artist : "");
        tvMusicApp.setText(appName);
        btnMusicPlayPause.setText(playing ? "⏸" : "▶");
        // Update album art emoji based on app
        TextView tvMusicArt = rootView.findViewById(R.id.tvMusicArt);
        if (tvMusicArt != null) {
            tvMusicArt.setText(getMusicEmoji(pkg != null ? pkg : ""));
        }

        hideAllSections();
        sectionMusic.setVisibility(View.VISIBLE);
        refreshModeToggle();
        expandPanel(MUSIC_DISMISS_MS);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /** Message from WhatsApp / Telegram etc */
    private void showMessage(String sender, String preview, String appName, String key) {
        if (!shouldShowForEvent(false)) return;  // cosmetic — respect hidden state
        if (sender == null || sender.isEmpty()) return;
        cancelDismiss();
        currentState    = IslandState.MESSAGE;
        currentNotifKey = key != null ? key : "";

        String app = appName != null ? appName : "Message";
        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText(getAppEmoji(appName));
        tvPanelTitle.setText(app);
        tvPanelTitle.setTextColor(Color.WHITE);
        tvPanelSubtitle.setText("New message");
        tvPanelSubtitle.setTextColor(0x88FFFFFF);

        tvMsgSender.setText(sender);
        tvMsgPreview.setText(preview != null ? preview : "");
        tvMsgApp.setText(app);

        hideAllSections();
        sectionMessage.setVisibility(View.VISIBLE);
        refreshModeToggle();
        expandPanel(MSG_DISMISS_MS);
    }

    /** Timer / alarm */
    private void showTimer(String title, String text, String key) {
        if (!shouldShowForEvent(false)) return;  // cosmetic — respect hidden state
        cancelDismiss();
        currentState    = IslandState.TIMER;
        currentNotifKey = key != null ? key : "";

        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("⏱");
        tvPanelTitle.setText("Timer");
        tvPanelTitle.setTextColor(Color.WHITE);
        tvPanelSubtitle.setText(title != null ? title : "");
        tvPanelSubtitle.setTextColor(0x88FFFFFF);

        tvTimerLabel.setText(title != null ? title : "Timer");
        tvTimerValue.setText(text != null ? text : "");

        hideAllSections();
        sectionTimer.setVisibility(View.VISIBLE);
        refreshModeToggle();
        expandPanel(TIMER_DISMISS_MS);
    }

    /** Generic app notification */
    private void showGenericNotif(String title, String text, String key) {
        if (!shouldShowForEvent(false)) return;  // cosmetic — respect hidden state
        if (title == null || title.isEmpty()) return;
        cancelDismiss();
        currentState    = IslandState.GENERIC;
        currentNotifKey = key != null ? key : "";

        tvPanelIcon.setVisibility(View.GONE);
        tvPanelTitle.setText(title);
        tvPanelTitle.setTextColor(Color.WHITE);
        tvPanelSubtitle.setText(text != null ? text : "");
        tvPanelSubtitle.setTextColor(0xAAFFFFFF);

        hideAllSections();
        refreshModeToggle();
        expandPanel(GENERIC_DISMISS_MS);
    }

    // ── Security states ───────────────────────────────────────────────────────

    public void showSecurityScam(String number, int score) {
        if (!shouldShowForEvent(true)) return;  // SECURITY — always breaks through
        cancelDismiss();
        currentState    = IslandState.SECURITY_SCAM;
        currentNotifKey = "";

        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("⚠️");
        tvPanelTitle.setText("Possible Scam Call");
        tvPanelTitle.setTextColor(0xFFFF005C);
        tvPanelSubtitle.setText("Caller: " + (number != null ? number : "Unknown"));
        tvPanelSubtitle.setTextColor(0xFFFFB800);

        tvPanelBody.setText("Caller Reputation: LOW\nRisk Score: " + score + " / 100");
        showActions("Block", "Accept", "Report");
        btnAction1.setOnClickListener(v -> { broadcastSec(ResponseEngine.ACTION_SCAM_CALL, number, score); collapseToIdle(); });
        btnAction2.setOnClickListener(v -> collapseToIdle());
        btnAction3.setOnClickListener(v -> { broadcastSec(ResponseEngine.ACTION_SCAM_CALL, number, score); collapseToIdle(); });

        hideAllSections();
        sectionSecurity.setVisibility(View.VISIBLE);
        refreshModeToggle();
        startPulse();
        expandPanel(ALERT_DISMISS_MS);
    }

    public void showSecurityUpi(String upiId, int score) {
        if (!shouldShowForEvent(true)) return;  // SECURITY — always breaks through
        cancelDismiss();
        currentState    = IslandState.SECURITY_UPI;
        currentNotifKey = "";

        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("🔴");
        tvPanelTitle.setText("UPI Fraud Detected");
        tvPanelTitle.setTextColor(0xFFFF005C);
        tvPanelSubtitle.setText("ID: " + (upiId != null ? upiId : "Unknown"));
        tvPanelSubtitle.setTextColor(0xFFFF6B6B);

        tvPanelBody.setText("Flagged in Community Threat DB.\nDo NOT send money!");
        showActions("Block", "Dismiss", null);
        final String id = upiId;
        btnAction1.setOnClickListener(v -> { broadcastSec(ResponseEngine.ACTION_UPI_SCAM, id, score); collapseToIdle(); });
        btnAction2.setOnClickListener(v -> collapseToIdle());

        hideAllSections();
        sectionSecurity.setVisibility(View.VISIBLE);
        refreshModeToggle();
        startPulse();
        expandPanel(ALERT_DISMISS_MS);
    }

    public void showSecurityOverlay(String pkg) {
        if (!shouldShowForEvent(true)) return;  // SECURITY — always breaks through
        cancelDismiss();
        currentState    = IslandState.SECURITY_OVERLAY;
        currentNotifKey = "";

        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("🚨");
        tvPanelTitle.setText("Overlay Attack!");
        tvPanelTitle.setTextColor(0xFFFF005C);
        tvPanelSubtitle.setText("App: " + (pkg != null ? pkg : "Unknown"));
        tvPanelSubtitle.setTextColor(0xFFFF6B6B);

        tvPanelBody.setText("App drawing over screen.\nScreen-share scam technique.");
        showActions("Remove", "Dismiss", null);
        final String p = pkg;
        btnAction1.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + p));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            collapseToIdle();
        });
        btnAction2.setOnClickListener(v -> collapseToIdle());

        hideAllSections();
        sectionSecurity.setVisibility(View.VISIBLE);
        refreshModeToggle();
        startPulse();
        expandPanel(ALERT_DISMISS_MS);
    }

    public void showScan(int current, int total) {
        cancelDismiss();
        currentState    = IslandState.SCAN;
        currentNotifKey = "";

        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("🔍");
        tvPanelTitle.setText("Scanning Apps...");
        tvPanelTitle.setTextColor(0xFF00FF94);
        tvPanelSubtitle.setText(current + " / " + total + " checked");
        tvPanelSubtitle.setTextColor(0xAAFFFFFF);

        scanProgress.setMax(total > 0 ? total : 1);
        scanProgress.setProgress(current);
        tvScanLabel.setText(current + " / " + total);

        hideAllSections();
        sectionScan.setVisibility(View.VISIBLE);
        refreshModeToggle();

        if (panelContainer.getVisibility() != View.VISIBLE) expandPanel(-1);
        else updateLayout();

        if (current >= total && total > 0) {
            tvPanelTitle.setText("✅ Scan Complete");
            tvPanelSubtitle.setText("All " + total + " apps checked");
            scheduleAutoDismiss(3000);
        }
    }

    public void showGenericAlert(String text) {
        if (text == null) return;
        cancelDismiss();
        currentState    = IslandState.GENERIC;
        currentNotifKey = "";

        tvPanelIcon.setVisibility(View.VISIBLE);
        tvPanelIcon.setText("⚠️");
        tvPanelTitle.setText("Security Alert");
        tvPanelTitle.setTextColor(0xFFFFB800);
        tvPanelSubtitle.setText(text);
        tvPanelSubtitle.setTextColor(0xCCFFFFFF);

        hideAllSections();
        refreshModeToggle();
        expandPanel(ALERT_DISMISS_MS);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void hideAllSections() {
        sectionCall.setVisibility(View.GONE);
        sectionMusic.setVisibility(View.GONE);
        sectionMessage.setVisibility(View.GONE);
        sectionTimer.setVisibility(View.GONE);
        sectionSecurity.setVisibility(View.GONE);
        sectionScan.setVisibility(View.GONE);
        actionRow.setVisibility(View.GONE);
    }

    private void showActions(String l1, String l2, String l3) {
        actionRow.setVisibility(View.VISIBLE);
        setBtn(btnAction1, l1); setBtn(btnAction2, l2); setBtn(btnAction3, l3);
    }

    private void setBtn(TextView btn, String label) {
        if (label != null) { btn.setText(label); btn.setVisibility(View.VISIBLE); }
        else                btn.setVisibility(View.GONE);
    }

    private void refreshModeToggle() {
        // Check user preference — can hide the toggle
        boolean showToggle = getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .getBoolean("show_island_toggle", true);
        View toggleRow   = rootView.findViewById(R.id.modeToggleRow);
        View dividerMode = rootView.findViewById(R.id.dividerMode);
        if (toggleRow   != null) toggleRow.setVisibility(showToggle ? View.VISIBLE : View.GONE);
        if (dividerMode != null) dividerMode.setVisibility(showToggle ? View.VISIBLE : View.GONE);

        if (isTurboMode) {
            btnModeNormal.setBackgroundResource(R.drawable.bg_mode_normal_inactive);
            btnModeNormal.setTextColor(Color.parseColor("#55FFFFFF"));
            btnModeTurbo.setBackgroundResource(R.drawable.bg_mode_turbo_active);
            btnModeTurbo.setTextColor(Color.WHITE);
        } else {
            btnModeNormal.setBackgroundResource(R.drawable.bg_mode_normal_active);
            btnModeNormal.setTextColor(Color.WHITE);
            btnModeTurbo.setBackgroundResource(R.drawable.bg_mode_turbo_inactive);
            btnModeTurbo.setTextColor(Color.parseColor("#55FFFFFF"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATION
    // ═══════════════════════════════════════════════════════════════════════════

    private void expandPanel(long autoDismissMs) {
        try { wm.updateViewLayout(rootView, wlpFocusable); } catch (Exception ignored) {}

        if (isAnimating) {
            panelContainer.setVisibility(View.VISIBLE);
            pillContainer.setVisibility(View.GONE);
            if (autoDismissMs > 0) scheduleAutoDismiss(autoDismissMs);
            return;
        }
        isAnimating = true;

        panelContainer.setAlpha(0f);
        panelContainer.setScaleX(0.80f);
        panelContainer.setScaleY(0.80f);
        panelContainer.setVisibility(View.VISIBLE);
        pillContainer.setVisibility(View.GONE);

        panelContainer.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(EXPAND_MS)
                .setInterpolator(new OvershootInterpolator(1.4f))  // more spring = more Dynamic Island feel
                .withEndAction(() -> {
                    isAnimating = false;
                    updateLayout();
                    if (autoDismissMs > 0) scheduleAutoDismiss(autoDismissMs);
                })
                .start();
    }

    private void collapseToIdle() {
        stopPulse();
        cancelDismiss();
        if (panelContainer.getVisibility() != View.VISIBLE) {
            currentState = IslandState.IDLE;
            try { wm.updateViewLayout(rootView, wlpIdle); } catch (Exception ignored) {}
            // If island was hidden by user, re-hide after security alert clears
            if (!islandVisible && rootView != null) {
                rootView.setVisibility(android.view.View.GONE);
            }
            return;
        }
        if (isAnimating) return;
        isAnimating = true;

        panelContainer.animate()
                .alpha(0f).scaleX(0.80f).scaleY(0.80f)
                .setDuration(COLLAPSE_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    isAnimating = false;
                    panelContainer.setVisibility(View.GONE);
                    currentState    = IslandState.IDLE;
                    currentNotifKey = "";
                    currentMusicPackage = "";

                    // If user had hidden the island, re-hide it after the alert is done
                    // instead of leaving the pill floating
                    if (!islandVisible) {
                        rootView.setVisibility(android.view.View.GONE);
                    } else {
                        pillContainer.setAlpha(0f);
                        pillContainer.setVisibility(View.VISIBLE);
                        pillContainer.animate().alpha(1f).setDuration(160).start();
                        refreshPill();
                    }
                    try { wm.updateViewLayout(rootView, wlpIdle); } catch (Exception ignored) {}
                })
                .start();
    }

    private void startPulse() {
        stopPulse();
        pulseAnim = ValueAnimator.ofFloat(0.97f, 1.0f);
        pulseAnim.setDuration(700);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            if (panelContainer != null) { panelContainer.setScaleX(v); panelContainer.setScaleY(v); }
        });
        pulseAnim.start();
    }

    private void stopPulse() {
        if (pulseAnim != null) { pulseAnim.cancel(); pulseAnim = null; }
        if (panelContainer != null) { panelContainer.setScaleX(1f); panelContainer.setScaleY(1f); }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEDIA CONTROL
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendMediaKey(int keyCode) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   keyCode));
            // Toggle play/pause icon
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                String cur = btnMusicPlayPause.getText().toString();
                btnMusicPlayPause.setText("⏸".equals(cur) ? "▶" : "⏸");
            }
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILS
    // ═══════════════════════════════════════════════════════════════════════════

    private void applyTurbo(boolean turbo) {
        isTurboMode = turbo;
        if (currentState == IslandState.IDLE) refreshPill();
        else refreshModeToggle();
    }

    private void applyToggleVisibility(boolean show) {
        android.content.SharedPreferences prefs =
            getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("show_island_toggle", show).apply();
        // If panel is open, update immediately
        if (panelContainer.getVisibility() == View.VISIBLE) {
            View dividerMode = rootView.findViewById(R.id.dividerMode);
            if (dividerMode != null)
                dividerMode.setVisibility(show ? View.VISIBLE : View.GONE);
            if (btnModeNormal != null) {
                LinearLayout toggleRow = rootView.findViewById(R.id.modeToggleRow);
                if (toggleRow != null)
                    toggleRow.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void applyScore(int score) {
        currentScore = Math.max(0, Math.min(100, score));
        if (currentState == IslandState.IDLE) refreshPill();
    }

    /** Hide the floating island — user choice. Persisted across restarts.
     *  NOTE: Security alerts (calls, scam, UPI fraud) ALWAYS break through this.
     *  Only cosmetic events (music, messages, generic) respect the hidden state. */
    private void hideIsland() {
        islandVisible = false;
        getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .edit().putBoolean("island_visible", false).apply();
        if (rootView != null) {
            rootView.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                    .setDuration(200)
                    .withEndAction(() -> rootView.setVisibility(android.view.View.GONE))
                    .start();
        }
        Log.d("DynamicBarService", "🫥 Island hidden by user (security alerts will still show)");
    }

    /**
     * Called before every show method.
     * SECURITY events (call, scam, UPI, overlay) force the rootView visible —
     * the user hiding the island means "hide the cosmetic pill", not "disable fraud alerts".
     * Everything else respects the hidden state.
     */
    private boolean shouldShowForEvent(boolean isSecurityEvent) {
        if (isSecurityEvent) {
            // Force-show for security — make rootView visible again temporarily
            if (rootView != null && rootView.getVisibility() != View.VISIBLE) {
                rootView.setVisibility(View.VISIBLE);
                rootView.setAlpha(0f);
                rootView.setScaleX(0.9f);
                rootView.setScaleY(0.9f);
                rootView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
            }
            return true;
        }
        // Cosmetic events — skip silently if island is hidden
        return islandVisible;
    }

    /** Restore the floating island. */
    private void showIsland() {
        islandVisible = true;
        getSharedPreferences("cyberraksha_prefs", MODE_PRIVATE)
                .edit().putBoolean("island_visible", true).apply();
        if (rootView != null) {
            rootView.setVisibility(android.view.View.VISIBLE);
            rootView.setAlpha(0f); rootView.setScaleX(0.8f); rootView.setScaleY(0.8f);
            rootView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start();
        }
        collapseToIdle();
        Log.d("DynamicBarService", "✅ Island restored");
    }

    private void scheduleAutoDismiss(long ms) {
        handler.removeCallbacks(dismissRunner);
        handler.postDelayed(dismissRunner, ms);
    }

    private void cancelDismiss() { handler.removeCallbacks(dismissRunner); }

    private void updateLayout() {
        try {
            if (wm != null && rootView != null) {
                wm.updateViewLayout(rootView,
                    panelContainer.getVisibility() == View.VISIBLE ? wlpFocusable : wlpIdle);
            }
        } catch (Exception ignored) {}
    }

    private void broadcastSec(String action, String reason, int score) {
        Intent i = new Intent(action);
        i.putExtra(ResponseEngine.EXTRA_REASON, reason);
        i.putExtra(ResponseEngine.EXTRA_RISK_SCORE, score);
        sendBroadcast(i);
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private String extractDigits(String s) {
        if (s == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[6-9]\\d{9}").matcher(s.replaceAll("[\\s\\-\\+]", ""));
        return m.find() ? m.group() : "";
    }

    private String getFriendlyName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        switch (pkg) {
            case "com.whatsapp":               return "WhatsApp";
            case "com.whatsapp.w4b":           return "WhatsApp Business";
            case "org.telegram.messenger":     return "Telegram";
            case "com.instagram.android":      return "Instagram";
            case "com.facebook.orca":          return "Messenger";
            case "com.discord":                return "Discord";
            case "com.snapchat.android":       return "Snapchat";
            case "com.spotify.music":          return "Spotify";
            case "com.google.android.youtube": return "YouTube";
            case "com.google.android.apps.youtube.music": return "YT Music";
            case "com.amazon.music":           return "Amazon Music";
            case "com.gaana":                  return "Gaana";
            case "com.jio.media.jiobeats":     return "JioSaavn";
            case "com.saavn.android":          return "Saavn";
            case "com.google.android.apps.maps": return "Maps";
            case "com.android.dialer":
            case "com.google.android.dialer":  return "Phone";
            case "com.google.android.deskclock":
            case "com.android.deskclock":      return "Clock";
            default:
                String[] p = pkg.split("\\.");
                return p.length > 0
                    ? Character.toUpperCase(p[p.length-1].charAt(0)) + p[p.length-1].substring(1)
                    : pkg;
        }
    }

    private String getMusicEmoji(String pkg) {
        if (pkg.contains("spotify"))  return "💚";
        if (pkg.contains("youtube"))  return "▶️";
        if (pkg.contains("gaana"))    return "🎶";
        if (pkg.contains("saavn") || pkg.contains("jio")) return "🎵";
        if (pkg.contains("amazon"))   return "🎧";
        if (pkg.contains("wynk"))     return "🎼";
        return "🎵";
    }

    private String getAppEmoji(String appName) {
        if (appName == null) return "💬";
        switch (appName) {
            case "WhatsApp":  return "💚";
            case "Telegram":  return "✈️";
            case "Instagram": return "📷";
            case "Messenger": return "💬";
            case "Discord":   return "🎮";
            case "Snapchat":  return "👻";
            default:          return "💬";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION + BROADCAST RECEIVER
    // ═══════════════════════════════════════════════════════════════════════════

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBroadcastReceiver() {
        IntentFilter f = new IntentFilter();
        // Island notification events
        f.addAction(IslandNotificationService.ACTION_ISLAND_CALL);
        f.addAction(IslandNotificationService.ACTION_ISLAND_MUSIC);
        f.addAction(IslandNotificationService.ACTION_ISLAND_MESSAGE);
        f.addAction(IslandNotificationService.ACTION_ISLAND_TIMER);
        f.addAction(IslandNotificationService.ACTION_ISLAND_GENERIC);
        f.addAction(IslandNotificationService.ACTION_ISLAND_DISMISS);
        // CyberRaksha security events
        f.addAction(ACTION_SHOW_ALERT);
        f.addAction(ACTION_SHOW_SCAM_CALL);
        f.addAction(ACTION_SHOW_UPI_ALERT);
        f.addAction(ACTION_SHOW_OVERLAY_ATK);
        f.addAction(ACTION_SHOW_SCAN);
        f.addAction(ACTION_TURBO_ON);
        f.addAction(ACTION_TURBO_OFF);
        f.addAction(ACTION_UPDATE_SCORE);
        f.addAction(ACTION_HIDE_ISLAND);
        f.addAction(ACTION_SHOW_ISLAND);
        f.addAction(ResponseEngine.ACTION_SCAM_CALL);
        f.addAction(ResponseEngine.ACTION_UPI_SCAM);
        f.addAction(ResponseEngine.ACTION_OVERLAY_ATTACK);
        f.addAction("com.cyberraksha.guardian.ISLAND_PREF");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(receiver, f);
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🛡 CyberRaksha Active")
                .setContentText("Guardian monitoring • Tap to open")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "CyberRaksha Dynamic Island",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("System guardian overlay");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
}
