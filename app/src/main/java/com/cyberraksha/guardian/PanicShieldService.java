package com.cyberraksha.guardian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class PanicShieldService extends VpnService {
    private static final String TAG = "PanicShieldService";
    private static final String CHANNEL_ID = "PanicModeChannel";
    private ParcelFileDescriptor vpnInterface = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Panic Mode Active")
                .setContentText("Your device is currently in Lockdown Mode.")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this exists or use a generic one
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build();

        startForeground(1001, notification);

        setupVpn();

        return START_STICKY;
    }

    private void setupVpn() {
        if (vpnInterface != null) return;

        Builder builder = new Builder();
        builder.setSession("CyberRakshaPanicVPN")
                .addAddress("10.0.0.1", 24)
                .addDnsServer("8.8.8.8")
                .setBlocking(true); // This creates the "Kill Switch" effect by not routing traffic

        try {
            vpnInterface = builder.establish();
            Log.d(TAG, "VPN Interface established. Internet blocked.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish VPN", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Panic Mode Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
            vpnInterface = null;
        }
        stopForeground(true);
    }
}
