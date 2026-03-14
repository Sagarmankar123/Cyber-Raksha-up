package com.avish.sheidhero;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallDetectionService extends AccessibilityService {

    private static final String TAG = "CallDetectionService";
    private WindowManager windowManager;
    private View overlayView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoDismissRunnable = this::dismissOverlay;
    private String lastNumber = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) return;

            String number = findPhoneNumber(rootNode);
            if (number != null && !number.equals(lastNumber)) {
                lastNumber = number;
                handleCallDetected(number);
            }
            rootNode.recycle();
        }
    }

    private String findPhoneNumber(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.getText() != null) {
            String text = node.getText().toString();
            Pattern pattern = Pattern.compile("(\\+?\\d[\\d -]{8,12}\\d)");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).replaceAll("[\\s-]", "");
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            String found = findPhoneNumber(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private void handleCallDetected(String number) {
        if (!Settings.canDrawOverlays(this)) return;

        // NEW: Check local spam CSV first
        SpamDatabase db = SpamDatabase.getInstance(getApplicationContext());
        if (db.isSpam(number)) {
            showOverlayWithSpam(number, db.getSpamLabel(number), 
                "⚠️ Found in CyberRaksha spam database", db.getRiskLevel(number));
            return; 
        }

        ContactInfo contact = lookupContact(number);
        showOverlay(number, contact);

        // Fallback to AI/Network check if not in local DB
        new Thread(() -> {
            String label = db.getSpamLabel(number);
            boolean isSpam = db.isSpam(number);
            handler.post(() -> updateOverlaySpamInfo(label, isSpam));
        }).start();
    }

    private void showOverlayWithSpam(String number, String label, String sub, String risk) {
        handler.post(() -> {
            if (overlayView != null) dismissOverlay();

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.caller_id_overlay, null);

            TextView tvNumber = overlayView.findViewById(R.id.tvPhoneNumber);
            TextView tvSpamBadge = overlayView.findViewById(R.id.tvSpamBadge);
            ImageButton btnClose = overlayView.findViewById(R.id.btnClose);
            View leftBorder = overlayView.findViewById(R.id.leftBorder);

            tvNumber.setText(number);
            tvSpamBadge.setText(label);
            
            int color = risk.equals("HIGH") ? Color.parseColor("#FF3B30") : Color.parseColor("#FF9500");
            tvSpamBadge.setBackgroundColor(color);
            leftBorder.setBackgroundColor(color);

            btnClose.setOnClickListener(v -> dismissOverlay());

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                        : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 80;

            try {
                windowManager.addView(overlayView, params);
                handler.postDelayed(autoDismissRunnable, 10000);
            } catch (Exception e) {
                Log.e(TAG, "Error showing overlay", e);
            }
        });
    }

    private void showOverlay(String number, ContactInfo contact) {
        handler.post(() -> {
            if (overlayView != null) dismissOverlay();

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.caller_id_overlay, null);

            TextView tvNumber = overlayView.findViewById(R.id.tvPhoneNumber);
            TextView tvContactName = overlayView.findViewById(R.id.tvContactName);
            TextView tvSavedContact = overlayView.findViewById(R.id.tvSavedContact);
            TextView tvSpamBadge = overlayView.findViewById(R.id.tvSpamBadge);
            ImageButton btnClose = overlayView.findViewById(R.id.btnClose);

            tvNumber.setText(number);
            if (contact != null) {
                tvContactName.setText(contact.name);
                tvContactName.setVisibility(View.VISIBLE);
                tvSavedContact.setVisibility(View.VISIBLE);
            } else {
                tvContactName.setVisibility(View.GONE);
                tvSavedContact.setVisibility(View.GONE);
            }

            tvSpamBadge.setText("Checking...");
            tvSpamBadge.setBackgroundColor(Color.parseColor("#444444"));

            btnClose.setOnClickListener(v -> dismissOverlay());

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                        : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 80;

            try {
                windowManager.addView(overlayView, params);
                handler.postDelayed(autoDismissRunnable, 10000);
            } catch (Exception e) {
                Log.e(TAG, "Error showing overlay", e);
            }
        });
    }

    private void updateOverlaySpamInfo(String label, boolean isSpam) {
        if (overlayView == null) return;

        TextView tvSpamBadge = overlayView.findViewById(R.id.tvSpamBadge);
        View leftBorder = overlayView.findViewById(R.id.leftBorder);

        tvSpamBadge.setText(isSpam ? label : "✅ SAFE");
        int color = Color.parseColor(isSpam ? "#FF3B30" : "#34C759");
        tvSpamBadge.setBackgroundColor(color);
        leftBorder.setBackgroundColor(color);
    }

    private void dismissOverlay() {
        handler.removeCallbacks(autoDismissRunnable);
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
            lastNumber = "";
        }
    }

    private ContactInfo lookupContact(String number) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                ContactInfo info = new ContactInfo();
                info.name = cursor.getString(0);
                cursor.close();
                return info;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void onInterrupt() {
        dismissOverlay();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissOverlay();
    }

    static class ContactInfo { String name; }
}
