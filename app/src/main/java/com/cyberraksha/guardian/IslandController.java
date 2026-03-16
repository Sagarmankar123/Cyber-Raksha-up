package com.cyberraksha.guardian;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║           CyberRaksha Island Controller — Central Nerve System      ║
 * ║                                                                      ║
 * ║  Single source of truth for the Dynamic Island.                     ║
 * ║                                                                      ║
 * ║  ALL services post IslandEvent objects here.                        ║
 * ║  IslandController decides what to show based on priority.           ║
 * ║  DynamicBarService is the ONLY thing that ever touches the UI.      ║
 * ║                                                                      ║
 * ║  Priority (lower number = higher priority):                         ║
 * ║    1  CALL          — always wins, auto-expand                      ║
 * ║    2  SCAM_CALL     — security alert, auto-expand, pulse            ║
 * ║    3  SECURITY      — UPI fraud / overlay attack                    ║
 * ║    4  SCAN          — app scan progress                             ║
 * ║    5  MUSIC         — now playing controls                          ║
 * ║    6  MESSAGE       — WhatsApp/Telegram preview                     ║
 * ║    7  TIMER         — countdown                                     ║
 * ║    8  GENERIC       — any other notification                        ║
 * ║    9  GUARDIAN      — idle guardian status (lowest)                 ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
public class IslandController {

    private static final String TAG = "IslandController";

    // ── Event types ───────────────────────────────────────────────────────────
    public enum EventType {
        CALL(1), SCAM_CALL(2), SECURITY(3), SCAN(4),
        MUSIC(5), MESSAGE(6), TIMER(7), GENERIC(8), GUARDIAN(9);

        public final int priority;
        EventType(int p) { this.priority = p; }
    }

    // ── Island Event — what every service sends here ──────────────────────────
    public static class IslandEvent {
        public final EventType type;
        public final String    title;
        public final String    subtitle;
        public final String    body;
        public final String    key;        // unique key — same key = update, not stack
        public final int       riskScore;
        public final String    pkg;
        public final boolean   autoExpand; // true = island expands immediately
        public final long      autoDismissMs; // 0 = stay until dismissed

        private IslandEvent(Builder b) {
            this.type          = b.type;
            this.title         = b.title;
            this.subtitle      = b.subtitle;
            this.body          = b.body;
            this.key           = b.key != null ? b.key : b.type.name();
            this.riskScore     = b.riskScore;
            this.pkg           = b.pkg;
            this.autoExpand    = b.autoExpand;
            this.autoDismissMs = b.autoDismissMs;
        }

        public static class Builder {
            private final EventType type;
            private String title = "", subtitle = "", body = "", key = null, pkg = "";
            private int    riskScore    = 0;
            private boolean autoExpand  = false;
            private long   autoDismissMs = 5000;

            public Builder(EventType type)       { this.type = type; }
            public Builder title(String v)       { this.title = v != null ? v : ""; return this; }
            public Builder subtitle(String v)    { this.subtitle = v != null ? v : ""; return this; }
            public Builder body(String v)        { this.body = v != null ? v : ""; return this; }
            public Builder key(String v)         { this.key = v; return this; }
            public Builder pkg(String v)         { this.pkg = v != null ? v : ""; return this; }
            public Builder riskScore(int v)      { this.riskScore = v; return this; }
            public Builder autoExpand(boolean v) { this.autoExpand = v; return this; }
            public Builder autoDismissMs(long v) { this.autoDismissMs = v; return this; }
            public IslandEvent build()           { return new IslandEvent(this); }
        }
    }

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile IslandController instance;

    public static IslandController getInstance(Context ctx) {
        if (instance == null) {
            synchronized (IslandController.class) {
                if (instance == null) instance = new IslandController(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Context appCtx;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Active events keyed by their unique key — prevents stacking
    private final Map<String, IslandEvent> activeEvents = new HashMap<>();
    // The event currently displayed
    private IslandEvent currentEvent = null;

    private IslandController(Context ctx) {
        this.appCtx = ctx;
        Log.d(TAG, "✅ IslandController initialized");
    }

    // ── Post an event ─────────────────────────────────────────────────────────

    /**
     * Any service calls this to send an event to the island.
     * Thread-safe — can be called from background threads.
     */
    public void post(IslandEvent event) {
        mainHandler.post(() -> handleEvent(event));
    }

    /**
     * Remove an active event by key (e.g. call ended, music stopped).
     */
    public void dismiss(String key) {
        mainHandler.post(() -> {
            activeEvents.remove(key);
            Log.d(TAG, "🗑 Event dismissed: " + key);
            // If this was the current event, show next highest priority
            if (currentEvent != null && key.equals(currentEvent.key)) {
                currentEvent = null;
                showTopEvent();
            }
        });
    }

    /**
     * Clear ALL events and collapse to idle.
     */
    public void reset() {
        mainHandler.post(() -> {
            activeEvents.clear();
            currentEvent = null;
            collapseIsland();
            Log.d(TAG, "🔄 IslandController reset");
        });
    }

    // ── Internal logic ────────────────────────────────────────────────────────

    private void handleEvent(IslandEvent event) {
        // Store / update event
        activeEvents.put(event.key, event);
        Log.d(TAG, "📨 Event received: " + event.type + " [" + event.key + "] priority=" + event.type.priority);

        // Should we preempt the current event?
        if (currentEvent == null || event.type.priority <= currentEvent.type.priority) {
            currentEvent = event;
            dispatchToIsland(event);
        }
        // else: lower priority — it's queued, will show when higher priority clears
    }

    private void showTopEvent() {
        if (activeEvents.isEmpty()) {
            collapseIsland();
            return;
        }
        // Find highest priority active event
        IslandEvent top = null;
        for (IslandEvent e : activeEvents.values()) {
            if (top == null || e.type.priority < top.type.priority) top = e;
        }
        if (top != null) {
            currentEvent = top;
            dispatchToIsland(top);
            Log.d(TAG, "📺 Showing next: " + top.type + " [" + top.key + "]");
        }
    }

    /**
     * Translates an IslandEvent into a broadcast that DynamicBarService understands.
     * DynamicBarService is the ONLY thing that modifies the overlay UI.
     */
    private void dispatchToIsland(IslandEvent event) {
        Intent intent;
        switch (event.type) {

            case CALL:
                intent = new Intent(IslandNotificationService.ACTION_ISLAND_CALL);
                intent.putExtra(IslandNotificationService.EXTRA_CALL_NAME,   event.title);
                intent.putExtra(IslandNotificationService.EXTRA_CALL_NUMBER, event.subtitle);
                intent.putExtra(IslandNotificationService.EXTRA_CALL_TYPE,   "incoming");
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_APP,  event.pkg);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_KEY,  event.key);
                break;

            case SCAM_CALL:
                intent = new Intent(DynamicBarService.ACTION_SHOW_SCAM_CALL);
                intent.putExtra(DynamicBarService.EXTRA_CALLER_NUMBER, event.title);
                intent.putExtra(DynamicBarService.EXTRA_RISK_SCORE,    event.riskScore);
                break;

            case SECURITY:
                // Distinguish UPI vs overlay by key prefix
                if (event.key.startsWith("upi_")) {
                    intent = new Intent(DynamicBarService.ACTION_SHOW_UPI_ALERT);
                    intent.putExtra(DynamicBarService.EXTRA_UPI_ID,     event.title);
                    intent.putExtra(DynamicBarService.EXTRA_RISK_SCORE, event.riskScore);
                } else {
                    intent = new Intent(DynamicBarService.ACTION_SHOW_OVERLAY_ATK);
                    intent.putExtra(DynamicBarService.EXTRA_PACKAGE_NAME, event.pkg);
                }
                break;

            case SCAN:
                intent = new Intent(DynamicBarService.ACTION_SHOW_SCAN);
                // Parse "current/total" from subtitle e.g. "12 / 42"
                int cur = 0, tot = 42;
                try {
                    String[] parts = event.subtitle.split("/");
                    cur = Integer.parseInt(parts[0].trim());
                    tot = Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {}
                intent.putExtra(DynamicBarService.EXTRA_SCAN_CURRENT, cur);
                intent.putExtra(DynamicBarService.EXTRA_SCAN_TOTAL,   tot);
                break;

            case MUSIC:
                intent = new Intent(IslandNotificationService.ACTION_ISLAND_MUSIC);
                intent.putExtra(IslandNotificationService.EXTRA_MUSIC_TITLE,   event.title);
                intent.putExtra(IslandNotificationService.EXTRA_MUSIC_ARTIST,  event.subtitle);
                intent.putExtra(IslandNotificationService.EXTRA_MUSIC_PACKAGE, event.pkg);
                intent.putExtra(IslandNotificationService.EXTRA_MUSIC_PLAYING, true);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_KEY,    event.key);
                break;

            case MESSAGE:
                intent = new Intent(IslandNotificationService.ACTION_ISLAND_MESSAGE);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_TITLE, event.title);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_TEXT,  event.subtitle);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_ICON,  event.pkg);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_KEY,   event.key);
                break;

            case TIMER:
                intent = new Intent(IslandNotificationService.ACTION_ISLAND_TIMER);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_TITLE, event.title);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_TEXT,  event.subtitle);
                intent.putExtra(IslandNotificationService.EXTRA_ISLAND_KEY,   event.key);
                break;

            case GENERIC:
            case GUARDIAN:
            default:
                intent = new Intent(DynamicBarService.ACTION_SHOW_ALERT);
                intent.putExtra(DynamicBarService.EXTRA_ALERT_TEXT, event.title);
                break;
        }

        intent.setPackage(appCtx.getPackageName());
        appCtx.sendBroadcast(intent);
        Log.d(TAG, "📡 Dispatched to island: " + event.type);
    }

    private void collapseIsland() {
        // Send a dismiss broadcast with a special key — DynamicBarService collapses on any unmatched dismiss
        Intent dismiss = new Intent(IslandNotificationService.ACTION_ISLAND_DISMISS);
        dismiss.putExtra(IslandNotificationService.EXTRA_ISLAND_KEY, "__controller_reset__");
        dismiss.setPackage(appCtx.getPackageName());
        appCtx.sendBroadcast(dismiss);
    }

    // ── Convenience factory methods — services call these ─────────────────────

    public static IslandEvent callEvent(String callerName, String number, String pkg) {
        return new IslandEvent.Builder(EventType.CALL)
                .title(callerName).subtitle(number).pkg(pkg)
                .key("call_" + number).autoExpand(true).autoDismissMs(0).build();
    }

    public static IslandEvent scamCallEvent(String number, int score) {
        return new IslandEvent.Builder(EventType.SCAM_CALL)
                .title(number).riskScore(score)
                .key("scam_" + number).autoExpand(true).autoDismissMs(15000).build();
    }

    public static IslandEvent securityEvent(String title, String body, String pkg, int score, boolean isUpi) {
        return new IslandEvent.Builder(EventType.SECURITY)
                .title(title).body(body).pkg(pkg).riskScore(score)
                .key(isUpi ? "upi_" + title : "overlay_" + pkg)
                .autoExpand(true).autoDismissMs(12000).build();
    }

    public static IslandEvent scanEvent(int current, int total) {
        return new IslandEvent.Builder(EventType.SCAN)
                .title("Scanning apps").subtitle(current + " / " + total)
                .key("scan_active").autoExpand(false).autoDismissMs(0).build();
    }

    public static IslandEvent musicEvent(String title, String artist, String pkg) {
        return new IslandEvent.Builder(EventType.MUSIC)
                .title(title).subtitle(artist).pkg(pkg)
                .key("music_" + pkg).autoExpand(false).autoDismissMs(0).build();
    }

    public static IslandEvent messageEvent(String sender, String preview, String appPkg) {
        return new IslandEvent.Builder(EventType.MESSAGE)
                .title(sender).subtitle(preview).pkg(appPkg)
                .key("msg_" + appPkg + "_" + sender).autoExpand(false).autoDismissMs(6000).build();
    }
}
