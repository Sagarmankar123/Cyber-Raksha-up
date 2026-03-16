package com.cyberraksha.guardian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AppInstallReceiver
 * ──────────────────
 * Listens to Android's real PACKAGE_ADDED broadcast.
 * When any new app is installed, feeds it to BehaviorService
 * which checks: was it installed at an unusual hour? Is it unknown?
 *
 * This is what makes the "Behavioral DNA Engine" actually learn
 * instead of always showing 0 for Known Apps.
 *
 * Registered in AndroidManifest with:
 *   <action android:name="android.intent.action.PACKAGE_ADDED"/>
 */
public class AppInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) return;

        // Get the package name of the newly installed app
        android.net.Uri data = intent.getData();
        if (data == null) return;

        String packageName = data.getSchemeSpecificPart();
        if (packageName == null || packageName.isEmpty()) return;

        // Skip our own app updates
        if (packageName.equals(context.getPackageName())) return;

        // Feed to BehaviorService — this is the real learning
        BehaviorService behavior = new BehaviorService(context);
        int riskScore = behavior.checkAppInstallAnomaly(packageName);

        // If risky install detected (at unusual hour), alert through IslandController
        if (riskScore > 0) {
            String appName = getAppName(context, packageName);
            IslandController.getInstance(context).post(
                new IslandController.IslandEvent.Builder(IslandController.EventType.SECURITY)
                    .title("Suspicious App Install")
                    .subtitle("⚠ " + appName + " installed at unusual hour")
                    .pkg(packageName)
                    .riskScore(riskScore)
                    .key("install_" + packageName)
                    .autoExpand(true)
                    .autoDismissMs(8000)
                    .build()
            );
        }

        // Also broadcast for MainActivity to show in turbo console
        Intent installed = new Intent("com.cyberraksha.guardian.APK_INSTALLED");
        installed.putExtra("package_name", packageName);
        installed.putExtra("risk_score", riskScore);
        installed.setPackage(context.getPackageName());
        context.sendBroadcast(installed);
    }

    private String getAppName(Context ctx, String pkg) {
        try {
            return ctx.getPackageManager()
                    .getApplicationLabel(ctx.getPackageManager()
                    .getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
