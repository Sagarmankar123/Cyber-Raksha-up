package com.cyberraksha.guardian;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║                    TURBO MANAGER — CyberRaksha              ║
 * ║                                                              ║
 * ║  Central controller for all protection services.            ║
 * ║  When TURBO ON  → starts all services automatically         ║
 * ║  When TURBO OFF → stops non-essential services               ║
 * ║                                                              ║
 * ║  Services Managed:                                          ║
 * ║  1. SafeSearchService (Accessibility - Web Protection)    ║
 * ║  2. CallDetectionService (Call Monitoring)                  ║
 * ║  3. DynamicBarService (Floating Island UI)                  ║
 * ║  4. IslandNotificationService (Notification Listener)        ║
 * ║  5. TurboModeEngine (Intelligence Features)                ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public class TurboManager {
    private static final String TAG = "TurboManager";
    private static final String PREFS = "cyberraksha_prefs";
    private static final String KEY_TURBO_STATE = "turbo_mode_active";
    private static final String KEY_THREAT_COUNT = "dynamic_threat_count";

    // Service status tracking
    public enum ServiceStatus {
        RUNNING, STOPPED, PENDING, ERROR, NOT_AVAILABLE
    }

    public static class ServiceState {
        public final String name;
        public ServiceStatus status;
        public String message;
        public long lastUpdated;

        public ServiceState(String name) {
            this.name = name;
            this.status = ServiceStatus.STOPPED;
            this.message = "Not started";
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    private static TurboManager instance;
    private final Context context;
    private final Handler mainHandler;
    private final SharedPreferences prefs;
    private boolean isTurboActive = false;

    // Service states
    private final ServiceState safeSearchState;
    private final ServiceState callDetectionState;
    private final ServiceState dynamicBarState;
    private final ServiceState islandNotifState;
    private final ServiceState turboEngineState;

    // Dynamic threat counter
    private final AtomicInteger threatCounter = new AtomicInteger(0);
    private final ThreatTracker threatTracker;

    private TurboManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.threatTracker = new ThreatTracker(context);

        // Initialize service states
        safeSearchState = new ServiceState("SafeSearch");
        callDetectionState = new ServiceState("Call Detection");
        dynamicBarState = new ServiceState("Dynamic Bar");
        islandNotifState = new ServiceState("Island Notifications");
        turboEngineState = new ServiceState("Turbo Engine");

        // Restore previous threat count
        threatCounter.set(prefs.getInt(KEY_THREAT_COUNT, 0));

        // Restore turbo state if needed
        if (prefs.getBoolean(KEY_TURBO_STATE, false)) {
            // Don't auto-start immediately, let the system settle
            mainHandler.postDelayed(this::startTurboMode, 1000);
        }
    }

    public static synchronized TurboManager getInstance(Context context) {
        if (instance == null) {
            instance = new TurboManager(context);
        }
        return instance;
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API: Turbo Mode Control
    // ══════════════════════════════════════════════════════════════

    /**
     * Activate Turbo Mode - starts all protection services
     */
    public void startTurboMode() {
        if (isTurboActive) {
            Log.d(TAG, "Turbo Mode already active");
            return;
        }

        Log.d(TAG, "🚀 STARTING TURBO MODE");
        isTurboActive = true;
        prefs.edit().putBoolean(KEY_TURBO_STATE, true).apply();

        // Update system preference for other components
        prefs.edit().putBoolean("turbo_mode", true).apply();

        // Start all services in sequence
        startDynamicBarService();
        mainHandler.postDelayed(this::startCallDetectionService, 200);
        mainHandler.postDelayed(this::startIslandNotificationService, 400);
        mainHandler.postDelayed(this::startTurboEngine, 600);
        mainHandler.postDelayed(this::checkSafeSearchService, 800);

        // Broadcast status update
        broadcastStatusUpdate("TURBO_STARTING", "Initializing all protection services...");
    }

    /**
     * Deactivate Turbo Mode - stops non-essential services
     */
    public void stopTurboMode() {
        if (!isTurboActive) {
            Log.d(TAG, "Turbo Mode already inactive");
            return;
        }

        Log.d(TAG, "🛑 STOPPING TURBO MODE");
        isTurboActive = false;
        prefs.edit().putBoolean(KEY_TURBO_STATE, false).apply();
        prefs.edit().putBoolean("turbo_mode", false).apply();

        // Stop services (keeping essential ones)
        stopCallDetectionService();
        stopTurboEngine();

        // Note: We keep DynamicBarService and IslandNotificationService running
        // as they provide core UI functionality

        broadcastStatusUpdate("TURBO_STOPPED", "Turbo mode deactivated. Essential services running.");
    }

    public boolean isTurboActive() {
        return isTurboActive;
    }

    // ══════════════════════════════════════════════════════════════
    // SERVICE START/STOP METHODS
    // ══════════════════════════════════════════════════════════════

    private void startDynamicBarService() {
        try {
            Intent intent = new Intent(context, DynamicBarService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            updateServiceState(dynamicBarState, ServiceStatus.RUNNING, "Dynamic Bar Active");
            Log.d(TAG, "✅ DynamicBarService started");
        } catch (Exception e) {
            updateServiceState(dynamicBarState, ServiceStatus.ERROR, "Failed: " + e.getMessage());
            Log.e(TAG, "Failed to start DynamicBarService", e);
        }
    }

    private void startCallDetectionService() {
        try {
            Intent intent = new Intent(context, CallDetectionService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            updateServiceState(callDetectionState, ServiceStatus.RUNNING, "Call Protection Enabled");
            broadcastStatusUpdate("CALL_SERVICE", "Call monitoring active");
            Log.d(TAG, "✅ CallDetectionService started");
        } catch (Exception e) {
            updateServiceState(callDetectionState, ServiceStatus.ERROR, "Failed: " + e.getMessage());
            Log.e(TAG, "Failed to start CallDetectionService", e);
        }
    }

    private void startIslandNotificationService() {
        try {
            // Check if notification listener is enabled
            if (!isNotificationListenerEnabled()) {
                updateServiceState(islandNotifState, ServiceStatus.PENDING, "Enable in Settings");
                broadcastStatusUpdate("NOTIFICATION_SERVICE", "Please enable notification access");
                return;
            }

            Intent intent = new Intent(context, IslandNotificationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            updateServiceState(islandNotifState, ServiceStatus.RUNNING, "Island Notifications Active");
            broadcastStatusUpdate("NOTIFICATION_SERVICE", "Notification monitoring active");
            Log.d(TAG, "✅ IslandNotificationService started");
        } catch (Exception e) {
            updateServiceState(islandNotifState, ServiceStatus.ERROR, "Failed: " + e.getMessage());
            Log.e(TAG, "Failed to start IslandNotificationService", e);
        }
    }

    private void startTurboEngine() {
        try {
            TurboModeEngine engine = TurboModeEngine.getInstance(context);
            engine.start();
            updateServiceState(turboEngineState, ServiceStatus.RUNNING, "Intelligence Engine Online");
            broadcastStatusUpdate("TURBO_ENGINE", "Threat scanning engine online");
            Log.d(TAG, "✅ TurboModeEngine started");
        } catch (Exception e) {
            updateServiceState(turboEngineState, ServiceStatus.ERROR, "Failed: " + e.getMessage());
            Log.e(TAG, "Failed to start TurboModeEngine", e);
        }
    }

    private void checkSafeSearchService() {
        // SafeSearchService is an AccessibilityService - can't be started programmatically
        // We can only check if it's enabled
        if (isAccessibilityServiceEnabled()) {
            updateServiceState(safeSearchState, ServiceStatus.RUNNING, "Safe Browsing Active");
            broadcastStatusUpdate("SAFESEARCH_SERVICE", "Web protection active");
        } else {
            updateServiceState(safeSearchState, ServiceStatus.PENDING, "Enable in Accessibility Settings");
            broadcastStatusUpdate("SAFESEARCH_SERVICE", "Please enable accessibility service for web protection");
        }
    }

    private void stopCallDetectionService() {
        try {
            Intent intent = new Intent(context, CallDetectionService.class);
            context.stopService(intent);
            updateServiceState(callDetectionState, ServiceStatus.STOPPED, "Stopped");
            Log.d(TAG, "⏹️ CallDetectionService stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping CallDetectionService", e);
        }
    }

    private void stopTurboEngine() {
        try {
            TurboModeEngine engine = TurboModeEngine.getInstance(context);
            engine.stop();
            updateServiceState(turboEngineState, ServiceStatus.STOPPED, "Stopped");
            Log.d(TAG, "⏹️ TurboModeEngine stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping TurboModeEngine", e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ══════════════════════════════════════════════════════════════

    private boolean isAccessibilityServiceEnabled() {
        try {
            String serviceName = context.getPackageName() + "/" + SafeSearchService.class.getCanonicalName();
            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(serviceName);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    "enabled_notification_listeners");
            return enabledServices != null && enabledServices.contains(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void updateServiceState(ServiceState state, ServiceStatus status, String message) {
        state.status = status;
        state.message = message;
        state.lastUpdated = System.currentTimeMillis();

        // Broadcast state change
        Intent intent = new Intent("com.cyberraksha.guardian.SERVICE_STATUS");
        intent.putExtra("service", state.name);
        intent.putExtra("status", status.name());
        intent.putExtra("message", message);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void broadcastStatusUpdate(String key, String message) {
        Intent intent = new Intent("com.cyberraksha.guardian.TURBO_STATUS");
        intent.putExtra("key", key);
        intent.putExtra("message", message);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);

        // Also log to TurboModeEngine console
        Intent logIntent = new Intent(TurboModeEngine.ACTION_TURBO_LOG);
        logIntent.putExtra(TurboModeEngine.EXTRA_LOG_LINE, message);
        logIntent.setPackage(context.getPackageName());
        context.sendBroadcast(logIntent);
    }

    // ══════════════════════════════════════════════════════════════
    // THREAT TRACKING
    // ══════════════════════════════════════════════════════════════

    /**
     * Record a detected threat and update the counter
     */
    public void recordThreat(String type, String source, String details) {
        int count = threatCounter.incrementAndGet();
        prefs.edit().putInt(KEY_THREAT_COUNT, count).apply();

        // Track in threat tracker
        threatTracker.addThreat(type, source, details);

        // Broadcast threat update
        Intent intent = new Intent("com.cyberraksha.guardian.THREAT_DETECTED");
        intent.putExtra("count", count);
        intent.putExtra("type", type);
        intent.putExtra("source", source);
        intent.putExtra("details", details);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);

        Log.d(TAG, "🚨 Threat recorded: " + type + " from " + source + " | Total: " + count);
    }

    /**
     * Get current threat count
     */
    public int getThreatCount() {
        return threatCounter.get();
    }

    /**
     * Get dynamic threat count based on actual detected threats
     */
    public int getDynamicThreatCount() {
        return threatTracker.getActiveThreatCount();
    }

    /**
     * Reset threat counter (e.g., after user clears threats)
     */
    public void resetThreatCount() {
        threatCounter.set(0);
        prefs.edit().putInt(KEY_THREAT_COUNT, 0).apply();
        threatTracker.clearThreats();
    }

    /**
     * Get all service states for UI display
     */
    public ServiceState[] getAllServiceStates() {
        return new ServiceState[] {
            safeSearchState,
            callDetectionState,
            dynamicBarState,
            islandNotifState,
            turboEngineState
        };
    }

    public ServiceState getSafeSearchState() { return safeSearchState; }
    public ServiceState getCallDetectionState() { return callDetectionState; }
    public ServiceState getDynamicBarState() { return dynamicBarState; }
    public ServiceState getIslandNotifState() { return islandNotifState; }
    public ServiceState getTurboEngineState() { return turboEngineState; }

    /**
     * Get formatted status message for display
     */
    public String getFormattedStatusMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("🛡️ Protection Status\n\n");

        ServiceState[] states = getAllServiceStates();
        int running = 0;
        for (ServiceState state : states) {
            String icon = state.status == ServiceStatus.RUNNING ? "✅" :
                         state.status == ServiceStatus.PENDING ? "⏳" :
                         state.status == ServiceStatus.ERROR ? "❌" : "⚪";
            sb.append(icon).append(" ").append(state.name).append("\n");
            sb.append("   ").append(state.message).append("\n\n");
            if (state.status == ServiceStatus.RUNNING) running++;
        }

        sb.append("📊 Active Modules: ").append(running).append("/").append(states.length).append("\n");
        sb.append("🚨 Threat Count: ").append(getDynamicThreatCount()).append("\n");
        sb.append("💚 System Health: ").append(running == states.length ? "Excellent" :
                                            running >= states.length / 2 ? "Good" : "Needs Attention");

        return sb.toString();
    }
}
