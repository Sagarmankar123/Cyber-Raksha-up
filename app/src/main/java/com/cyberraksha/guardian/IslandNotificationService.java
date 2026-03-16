package com.cyberraksha.guardian;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║           CyberRaksha Island Notification Service                   ║
 * ║                                                                      ║
 * ║  This is the engine that makes CyberRaksha's Dynamic Island         ║
 * ║  work like the iPhone Dynamic Island — intercepting ALL system       ║
 * ║  events: calls, music, WhatsApp, timer, charging, every app.        ║
 * ║                                                                      ║
 * ║  HOW IT WORKS:                                                       ║
 * ║  Android's NotificationListenerService gives this service a          ║
 * ║  real-time stream of every notification posted on the phone.         ║
 * ║  We classify each one and broadcast it to DynamicBarService          ║
 * ║  which shows it in the floating island overlay.                      ║
 * ║                                                                      ║
 * ║  PERMISSION REQUIRED:                                                ║
 * ║  Settings → Notification Access → CyberRaksha → Enable              ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
public class IslandNotificationService extends NotificationListenerService {

    private static final String TAG = "IslandNotifService";

    // ── Broadcast actions sent TO DynamicBarService ───────────────────────────
    public static final String ACTION_ISLAND_CALL    = "com.cyberraksha.island.CALL";
    public static final String ACTION_ISLAND_MUSIC   = "com.cyberraksha.island.MUSIC";
    public static final String ACTION_ISLAND_MESSAGE = "com.cyberraksha.island.MESSAGE";
    public static final String ACTION_ISLAND_TIMER   = "com.cyberraksha.island.TIMER";
    public static final String ACTION_ISLAND_BATTERY = "com.cyberraksha.island.BATTERY";
    public static final String ACTION_ISLAND_GENERIC = "com.cyberraksha.island.GENERIC";
    public static final String ACTION_ISLAND_DISMISS = "com.cyberraksha.island.DISMISS";

    // ── Extras ────────────────────────────────────────────────────────────────
    public static final String EXTRA_ISLAND_TITLE   = "island_title";
    public static final String EXTRA_ISLAND_TEXT    = "island_text";
    public static final String EXTRA_ISLAND_APP     = "island_app";
    public static final String EXTRA_ISLAND_ICON    = "island_icon";   // package name for icon
    public static final String EXTRA_ISLAND_KEY     = "island_key";    // notif key for dismiss

    // Music extras
    public static final String EXTRA_MUSIC_TITLE    = "music_title";
    public static final String EXTRA_MUSIC_ARTIST   = "music_artist";
    public static final String EXTRA_MUSIC_PACKAGE  = "music_package";
    public static final String EXTRA_MUSIC_PLAYING  = "music_playing";

    // Call extras
    public static final String EXTRA_CALL_NUMBER    = "call_number";
    public static final String EXTRA_CALL_NAME      = "call_name";
    public static final String EXTRA_CALL_TYPE      = "call_type"; // incoming/ongoing

    // ── Known package categories ───────────────────────────────────────────────
    private static final String[] MUSIC_PACKAGES = {
        "com.spotify.music", "com.google.android.youtube",
        "com.google.android.apps.youtube.music", "com.amazon.music",
        "com.apple.android.music", "com.soundcloud.android",
        "com.gaana", "com.jio.media.jiobeats", "com.wynk.music",
        "in.jiomusic", "com.hungama.myPlay", "com.saavn.android"
    };

    private static final String[] CALL_PACKAGES = {
        "com.android.dialer", "com.android.server.telecom",
        "com.google.android.dialer", "com.samsung.android.dialer",
        "com.miui.incallui", "com.coloros.incallui",
        "com.oneplus.dialer", "com.realme.dialer"
    };

    private static final String[] MESSAGE_PACKAGES = {
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.telegram.messenger.web",
        "com.facebook.orca",  // Messenger
        "com.instagram.android",
        "com.google.android.apps.messaging", // Google Messages
        "com.android.mms", "com.samsung.android.messaging",
        "com.discord", "com.snapchat.android",
        "com.twitter.android", "com.linkedin.android"
    };

    private static final String[] TIMER_PACKAGES = {
        "com.google.android.deskclock", "com.android.deskclock",
        "com.samsung.android.clock", "com.miui.clock",
        "com.oneplus.clock"
    };

    private static final String[] NAVIGATION_PACKAGES = {
        "com.google.android.apps.maps",
        "com.waze", "com.here.app.maps"
    };

    private static IslandNotificationService instance;

    public static IslandNotificationService getInstance() { return instance; }

    private MediaSessionManager mediaSessionManager;
    private final java.util.List<android.media.session.MediaController> activeControllers
            = new java.util.ArrayList<>();

    // ── Music polling — checks every 3s so island updates when app goes BG ───
    private final Handler musicPollHandler = new Handler(Looper.getMainLooper());
    private static final long MUSIC_POLL_INTERVAL_MS = 3000;
    private String lastMusicKey = ""; // deduplicate identical broadcasts

    private final Runnable musicPollRunnable = new Runnable() {
        @Override
        public void run() {
            pollActiveMusicSession();
            musicPollHandler.postDelayed(this, MUSIC_POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "✅ IslandNotificationService connected");
        // Start music polling — keeps island in sync when apps go to background
        musicPollHandler.postDelayed(musicPollRunnable, MUSIC_POLL_INTERVAL_MS);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        // Register for MediaSession changes — this fires when music starts/stops/changes
        try {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, IslandNotificationService.class);
            mediaSessionManager.addOnActiveSessionsChangedListener(
                controllers -> onMediaSessionsChanged(controllers), cn);
        } catch (Exception e) {
            Log.w(TAG, "MediaSession listener failed: " + e.getMessage());
        }
    }

    private void onMediaSessionsChanged(
            java.util.List<android.media.session.MediaController> controllers) {
        if (controllers == null) return;

        // ── Immediate check on session list change ────────────────────────────
        pollActiveMusicSession();

        for (android.media.session.MediaController controller : controllers) {
            // Register playback callback for each active controller
            controller.registerCallback(new android.media.session.MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    if (metadata == null) return;
                    String title  = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    if (artist == null) artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                    if (title == null || title.isEmpty()) return;

                    String pkg = controller.getPackageName();
                    Intent i = new Intent(ACTION_ISLAND_MUSIC);
                    i.putExtra(EXTRA_MUSIC_TITLE,   title);
                    i.putExtra(EXTRA_MUSIC_ARTIST,  artist != null ? artist : "");
                    i.putExtra(EXTRA_MUSIC_PACKAGE, pkg);
                    i.putExtra(EXTRA_MUSIC_PLAYING, true);
                    i.putExtra(EXTRA_ISLAND_KEY,    "media_" + pkg);
                    i.setPackage(getPackageName());
                    sendBroadcast(i);
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    if (state == null) return;
                    String pkg = controller.getPackageName();
                    MediaMetadata meta = controller.getMetadata();
                    if (meta == null) return;

                    String title  = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    boolean isPlaying = state.getState() == PlaybackState.STATE_PLAYING;

                    if (title == null || title.isEmpty()) return;

                    if (isPlaying) {
                        Intent i = new Intent(ACTION_ISLAND_MUSIC);
                        i.putExtra(EXTRA_MUSIC_TITLE,   title);
                        i.putExtra(EXTRA_MUSIC_ARTIST,  artist != null ? artist : "");
                        i.putExtra(EXTRA_MUSIC_PACKAGE, pkg);
                        i.putExtra(EXTRA_MUSIC_PLAYING, true);
                        i.putExtra(EXTRA_ISLAND_KEY,    "media_" + pkg);
                        i.setPackage(getPackageName());
                        sendBroadcast(i);
                    } else {
                        // Music paused/stopped — dismiss island if showing this
                        Intent d = new Intent(ACTION_ISLAND_DISMISS);
                        d.putExtra(EXTRA_ISLAND_KEY, "media_" + pkg);
                        d.setPackage(getPackageName());
                        sendBroadcast(d);
                    }
                }
            });
        }
    }

    /**
     * Polls the active MediaSession every 3 seconds.
     * This is the KEY FIX — without this, the island only updates when
     * a new notification is posted. When YouTube/Spotify goes to background,
     * Android updates the EXISTING notification, so onNotificationPosted
     * never fires again. Polling catches that.
     */
    private void pollActiveMusicSession() {
        try {
            if (mediaSessionManager == null) return;
            ComponentName cn = new ComponentName(this, IslandNotificationService.class);
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);

            boolean foundPlaying = false;
            for (MediaController controller : controllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null) continue;

                boolean isPlaying = state.getState() == PlaybackState.STATE_PLAYING;
                if (!isPlaying) continue;

                MediaMetadata meta = controller.getMetadata();
                if (meta == null) continue;

                String title  = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                if (title == null || title.isEmpty()) continue;

                String pkg = controller.getPackageName();

                // Deduplicate — only post if something changed
                String key = pkg + "|" + title + "|" + artist;
                if (!key.equals(lastMusicKey)) {
                    lastMusicKey = key;
                    // Route through IslandController — respects priority (won't interrupt a call)
                    IslandController.getInstance(this)
                            .post(IslandController.musicEvent(title, artist != null ? artist : "", pkg));
                    Log.d(TAG, "🎵 Poll → IslandController: " + title + " from " + pkg);
                }
                foundPlaying = true;
                break; // Only care about the top active session
            }

            // Nothing playing — dismiss music from controller
            if (!foundPlaying && lastMusicKey != null && !lastMusicKey.isEmpty()) {
                lastMusicKey = "";
                IslandController.getInstance(this).dismiss("music_poll");
            }
        } catch (Exception e) {
            Log.w(TAG, "pollActiveMusicSession error: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        musicPollHandler.removeCallbacks(musicPollRunnable);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION POSTED — main entry point
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        Notification notif = sbn.getNotification();
        if (notif == null) return;

        // Skip our own notifications
        if (pkg.equals(getPackageName())) return;

        // Skip group summary / silent notifications
        if ((notif.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle extras = notif.extras;
        if (extras == null) return;

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text  = extras.getCharSequence(Notification.EXTRA_TEXT);

        String titleStr = title != null ? title.toString().trim() : "";
        String textStr  = text  != null ? text.toString().trim()  : "";

        if (titleStr.isEmpty() && textStr.isEmpty()) return;

        Log.d(TAG, "📬 Notification from: " + pkg + " | " + titleStr + " | " + textStr);

        // ── CLASSIFY AND ROUTE ────────────────────────────────────────────────

        // Fix: CATEGORY_CALL catches ALL OEM dialers (MIUI, ColorOS, OnePlus, stock)
        // regardless of package name — this is the most reliable detection method
        boolean isCategoryCall = Notification.CATEGORY_CALL.equals(notif.category);

        if (isCategoryCall || isCallPackage(pkg) || isCallNotification(titleStr, textStr)) {
            routeCall(sbn, titleStr, textStr, pkg);
        } else if (isMusicPackage(pkg)) {
            routeMusic(sbn, titleStr, textStr, pkg);
        } else if (isTimerPackage(pkg) || isTimerNotification(titleStr, textStr)) {
            routeTimer(sbn, titleStr, textStr);
        } else if (isNavigationPackage(pkg)) {
            routeNavigation(sbn, titleStr, textStr, pkg);
        } else if (isMessagePackage(pkg)) {
            routeMessage(sbn, titleStr, textStr, pkg);
        } else {
            routeGeneric(sbn, titleStr, textStr, pkg);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        // Tell the island to dismiss if it's showing this notification
        Intent i = new Intent(ACTION_ISLAND_DISMISS);
        i.putExtra(EXTRA_ISLAND_KEY, sbn.getKey());
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROUTERS — one per notification category
    // ═══════════════════════════════════════════════════════════════════════════

    private void routeCall(StatusBarNotification sbn, String title, String text, String pkg) {
        // Determine if incoming or ongoing
        boolean isIncoming = title.toLowerCase().contains("incoming")
                || text.toLowerCase().contains("incoming")
                || title.toLowerCase().contains("calling")
                || text.toLowerCase().contains("is calling");

        Intent i = new Intent(ACTION_ISLAND_CALL);
        i.putExtra(EXTRA_CALL_NAME,   title);
        i.putExtra(EXTRA_CALL_NUMBER, text);
        i.putExtra(EXTRA_CALL_TYPE,   isIncoming ? "incoming" : "ongoing");
        i.putExtra(EXTRA_ISLAND_APP,  pkg);
        i.putExtra(EXTRA_ISLAND_KEY,  sbn.getKey());
        i.setPackage(getPackageName());
        sendBroadcast(i);

        // Also run through CyberRaksha fraud check
        String number = extractNumber(title + " " + text);
        if (!number.isEmpty()) {
            CommunityThreatDB db = CommunityThreatDB.getInstance(this);
            if (db.isNumberReported(number)) {
                Intent scam = new Intent(DynamicBarService.ACTION_SHOW_SCAM_CALL);
                scam.putExtra(DynamicBarService.EXTRA_CALLER_NUMBER, number);
                scam.putExtra(DynamicBarService.EXTRA_RISK_SCORE, 90);
                sendBroadcast(scam);
            }
        }
    }

    private void routeMusic(StatusBarNotification sbn, String title, String text, String pkg) {
        // title = song name, text = artist name typically
        boolean isPlaying = isActiveMediaSession(pkg);

        Intent i = new Intent(ACTION_ISLAND_MUSIC);
        i.putExtra(EXTRA_MUSIC_TITLE,   title);
        i.putExtra(EXTRA_MUSIC_ARTIST,  text);
        i.putExtra(EXTRA_MUSIC_PACKAGE, pkg);
        i.putExtra(EXTRA_MUSIC_PLAYING, isPlaying);
        i.putExtra(EXTRA_ISLAND_KEY,    sbn.getKey());
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void routeTimer(StatusBarNotification sbn, String title, String text) {
        Intent i = new Intent(ACTION_ISLAND_TIMER);
        i.putExtra(EXTRA_ISLAND_TITLE, title);
        i.putExtra(EXTRA_ISLAND_TEXT,  text);
        i.putExtra(EXTRA_ISLAND_KEY,   sbn.getKey());
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void routeNavigation(StatusBarNotification sbn, String title, String text, String pkg) {
        Intent i = new Intent(ACTION_ISLAND_GENERIC);
        i.putExtra(EXTRA_ISLAND_TITLE, "🗺  " + title);
        i.putExtra(EXTRA_ISLAND_TEXT,  text);
        i.putExtra(EXTRA_ISLAND_APP,   pkg);
        i.putExtra(EXTRA_ISLAND_KEY,   sbn.getKey());
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void routeMessage(StatusBarNotification sbn, String title, String text, String pkg) {
        String appLabel = getFriendlyAppName(pkg);
        Intent i = new Intent(ACTION_ISLAND_MESSAGE);
        i.putExtra(EXTRA_ISLAND_TITLE, title);
        i.putExtra(EXTRA_ISLAND_TEXT,  text);
        i.putExtra(EXTRA_ISLAND_APP,   pkg);
        i.putExtra(EXTRA_ISLAND_ICON,  appLabel);
        i.putExtra(EXTRA_ISLAND_KEY,   sbn.getKey());
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void routeGeneric(StatusBarNotification sbn, String title, String text, String pkg) {
        String appLabel = getFriendlyAppName(pkg);
        Intent i = new Intent(ACTION_ISLAND_GENERIC);
        i.putExtra(EXTRA_ISLAND_TITLE, appLabel + ":  " + title);
        i.putExtra(EXTRA_ISLAND_TEXT,  text);
        i.putExtra(EXTRA_ISLAND_APP,   pkg);
        i.putExtra(EXTRA_ISLAND_KEY,   sbn.getKey());
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEDIA SESSION — check if music is actually playing
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isActiveMediaSession(String pkg) {
        try {
            MediaSessionManager msm = (MediaSessionManager)
                    getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, IslandNotificationService.class);
            List<MediaController> controllers = msm.getActiveSessions(cn);
            for (MediaController controller : controllers) {
                if (pkg.equals(controller.getPackageName())) {
                    PlaybackState state = controller.getPlaybackState();
                    if (state != null &&
                        state.getState() == PlaybackState.STATE_PLAYING) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaSession check failed: " + e.getMessage());
        }
        return true; // assume playing if we can't check
    }

    /**
     * Pull live music metadata from MediaSession.
     * Call this from DynamicBarService when handling ACTION_ISLAND_MUSIC
     * for the most up-to-date song info.
     */
    public static MediaMetadata getLiveMediaMetadata(Context ctx, String pkg) {
        try {
            MediaSessionManager msm = (MediaSessionManager)
                    ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(ctx, IslandNotificationService.class);
            List<MediaController> controllers = msm.getActiveSessions(cn);
            for (MediaController controller : controllers) {
                if (pkg.equals(controller.getPackageName())) {
                    return controller.getMetadata();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLASSIFIERS
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isCallPackage(String pkg) {
        for (String p : CALL_PACKAGES) if (pkg.equals(p)) return true;
        return false;
    }

    private boolean isMusicPackage(String pkg) {
        for (String p : MUSIC_PACKAGES) if (pkg.equals(p)) return true;
        return false;
    }

    private boolean isMessagePackage(String pkg) {
        for (String p : MESSAGE_PACKAGES) if (pkg.equals(p)) return true;
        return false;
    }

    private boolean isTimerPackage(String pkg) {
        for (String p : TIMER_PACKAGES) if (pkg.equals(p)) return true;
        return false;
    }

    private boolean isNavigationPackage(String pkg) {
        for (String p : NAVIGATION_PACKAGES) if (pkg.equals(p)) return true;
        return false;
    }

    private boolean isCallNotification(String title, String text) {
        String combined = (title + " " + text).toLowerCase();
        // English patterns
        if (combined.contains("incoming call") || combined.contains("is calling")
                || combined.contains("calling you") || combined.contains("ongoing call")
                || combined.contains("call from") || combined.contains("active call")
                || combined.contains("receiving call") || combined.contains("answering call")) return true;
        // OEM-specific patterns (MIUI, ColorOS, Samsung, etc.)
        if (combined.contains("incoming") && combined.contains("call")) return true;
        if (combined.contains("phone call") || combined.contains("voice call")) return true;
        // Hindi / Indian regional patterns
        if (combined.contains("कॉल") || combined.contains("incoming")) return true;
        return false;
    }

    private boolean isTimerNotification(String title, String text) {
        String combined = (title + " " + text).toLowerCase();
        return combined.contains("timer") || combined.contains("alarm")
                || combined.contains("stopwatch") || combined.contains("countdown");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILS
    // ═══════════════════════════════════════════════════════════════════════════

    private String extractNumber(String text) {
        // Extract 10-digit Indian mobile number
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\+91[\\s-]?)?[6-9]\\d{9}")
                .matcher(text);
        return m.find() ? m.group().replaceAll("[\\s-]", "") : "";
    }

    private String getFriendlyAppName(String pkg) {
        switch (pkg) {
            case "com.whatsapp":              return "WhatsApp";
            case "com.whatsapp.w4b":          return "WhatsApp Business";
            case "org.telegram.messenger":    return "Telegram";
            case "com.instagram.android":     return "Instagram";
            case "com.facebook.orca":         return "Messenger";
            case "com.discord":               return "Discord";
            case "com.snapchat.android":      return "Snapchat";
            case "com.twitter.android":       return "Twitter";
            case "com.linkedin.android":      return "LinkedIn";
            case "com.google.android.apps.messaging": return "Messages";
            case "com.spotify.music":         return "Spotify";
            case "com.google.android.youtube":return "YouTube";
            case "com.google.android.apps.youtube.music": return "YT Music";
            case "com.amazon.music":          return "Amazon Music";
            case "com.gaana":                 return "Gaana";
            case "com.jio.media.jiobeats":    return "JioSaavn";
            case "com.saavn.android":         return "Saavn";
            case "com.google.android.apps.maps": return "Maps";
            case "com.waze":                  return "Waze";
            case "com.android.dialer":
            case "com.google.android.dialer": return "Phone";
            case "com.google.android.deskclock":
            case "com.android.deskclock":     return "Clock";
            case "com.phonepe.app":           return "PhonePe";
            case "net.one97.paytm":           return "Paytm";
            case "com.google.android.apps.nbu.paisa.user": return "GPay";
            default:
                // Extract readable name from package
                String[] parts = pkg.split("\\.");
                return parts.length > 0
                        ? Character.toUpperCase(parts[parts.length - 1].charAt(0))
                          + parts[parts.length - 1].substring(1)
                        : pkg;
        }
    }

    // ── Static helper for permission check ───────────────────────────────────

    /**
     * Call this from MainActivity / OnboardingActivity to check if
     * Notification Access has been granted.
     */
    public static boolean isPermissionGranted(Context ctx) {
        String flat = android.provider.Settings.Secure.getString(
                ctx.getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(ctx.getPackageName());
    }
}
