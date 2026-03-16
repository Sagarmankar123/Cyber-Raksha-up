package com.cyberraksha.guardian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class CallDetectionService extends Service {
    private static final String TAG = "CallDetectionService";
    private static final String CHANNEL_ID = "cyberraksha_calls";
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastNumber = "";

    @Override
    public void onCreate() {
        super.onCreate();
        // MUST call startForeground immediately in onCreate to avoid crash
        createNotificationChannel();
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🛡️ CyberRaksha Active")
                .setContentText("Protecting you from scam calls...")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(999, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(999, n);

        SpamDatabase.getInstance(getApplicationContext());
        registerPhoneListener();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Call Detection", NotificationManager.IMPORTANCE_LOW);
            NotificationManager m = getSystemService(NotificationManager.class);
            if (m != null) m.createNotificationChannel(ch);
        }
    }

    private void registerPhoneListener() {
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override public void onCallStateChanged(int state, String number) {
                if (state == TelephonyManager.CALL_STATE_RINGING && number != null && !number.isEmpty() && !number.equals(lastNumber)) {
                    lastNumber = number; handleIncomingCall(number);
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    String prevNumber = lastNumber;
                    lastNumber = "";
                    dismissCallIsland(prevNumber);
                }
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void handleIncomingCall(String number) {
        // ── Step 1: Route through IslandController (priority-aware, no conflicts) ──
        IslandController.getInstance(getApplicationContext())
                .post(IslandController.callEvent(number, number, "com.android.dialer"));

        // ── Step 2: Community Threat DB — instant fraud check ─────────────────────
        CommunityThreatDB communityDB = CommunityThreatDB.getInstance(getApplicationContext());
        if (communityDB.isNumberReported(number)) {
            int score = ThreatEngine.getInstance(getApplicationContext())
                    .addScore(ThreatEngine.SCORE_SCAM_CALL, "Community reported: " + number);
            new ResponseEngine(getApplicationContext()).triggerScamCall(number, score);
            // Upgrade through IslandController — scam beats call in priority
            IslandController ctrl = IslandController.getInstance(getApplicationContext());
            ctrl.dismiss("call_" + number);
            ctrl.post(IslandController.scamCallEvent(number, score));
            return;
        }

        // ── Step 3: Check saved contacts ──────────────────────────────────────────
        ContactInfo contact = lookupContact(number);
        if (contact != null) {
            // Update event with real name
            IslandController.getInstance(getApplicationContext())
                    .post(IslandController.callEvent(contact.name, number, "com.android.dialer"));
            return;
        }

        // ── Step 4: Async CSV spam check ──────────────────────────────────────────
        SpamDatabase db = SpamDatabase.getInstance(getApplicationContext());
        new Thread(() -> {
            for (int i = 0; i < 6; i++) {
                if (db.isLoaded()) break;
                try { Thread.sleep(500); } catch (Exception ignored) {}
            }
            if (db.isSpam(number)) {
                String risk  = db.getRiskLevel(number);
                int score = "HIGH".equalsIgnoreCase(risk) ? 75 : 45;
                IslandController ctrl = IslandController.getInstance(getApplicationContext());
                if ("HIGH".equalsIgnoreCase(risk)) {
                    handler.post(() -> {
                        ctrl.dismiss("call_" + number);
                        ctrl.post(IslandController.scamCallEvent(number, score));
                    });
                } else {
                    // Medium — update call panel name to show warning
                    handler.post(() -> ctrl.post(
                            IslandController.callEvent("⚠️ Possible Spam", number, "com.android.dialer")));
                }
            }
        }).start();
    }

    /** Dismiss the call island when call ends */
    private void dismissCallIsland(String number) {
        if (number != null && !number.isEmpty()) {
            IslandController ctrl = IslandController.getInstance(getApplicationContext());
            ctrl.dismiss("call_" + number);
            ctrl.dismiss("scam_" + number);
        }
    }

    private ContactInfo lookupContact(String number) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.TIMES_CONTACTED}, null, null, null);
            if (c != null && c.moveToFirst()) { ContactInfo i = new ContactInfo(); i.name = c.getString(0); i.timesContacted = c.getInt(1); c.close(); return i; }
        } catch (Exception ignored) {}
        return null;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null) telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        dismissCallIsland(lastNumber);
    }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    static class ContactInfo { String name; int timesContacted; }
}
