package com.cyberraksha.guardian;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeepSecurityScanner {
    private final PackageManager pm;

    public DeepSecurityScanner(Context context) {
        this.pm = context.getPackageManager();
    }

    public static class ScanResult {
        public int riskScore;
        public List<String> threatsFound = new ArrayList<>();
        public boolean isMalicious = false;
        public String installerSource = "Unknown";
        public String installerPackage = null;
        public String packageName;
        public List<String> permissions = new ArrayList<>();
        public boolean hasHiddenService = false;
        public boolean canAutoStart = false;
    }

    public ScanResult scanApp(PackageInfo pkg) {
        ScanResult result = new ScanResult();
        result.packageName = pkg.packageName;
        int score = 0;

        // Extract permissions
        if (pkg.requestedPermissions != null) {
            result.permissions.addAll(Arrays.asList(pkg.requestedPermissions));
        }

        // Check for Auto-Start permission
        for (String perm : result.permissions) {
            if ("android.permission.RECEIVE_BOOT_COMPLETED".equals(perm)) {
                result.canAutoStart = true;
                break;
            }
        }

        // Basic check for hidden behavior (no launcher activity could be one sign, 
        // but for now we'll just keep the field for compatibility)
        result.hasHiddenService = (pm.getLaunchIntentForPackage(pkg.packageName) == null);

        // 1. Installer Source Analysis - ONLY PRIMARY CHECK
        String installer = getInstaller(pkg.packageName);
        result.installerPackage = installer;
        
        if ("com.android.vending".equals(installer)) {
            result.installerSource = "Play Store";
            result.isMalicious = false;
        } else {
            if (installer == null) {
                result.installerSource = "Unknown Source (Direct APK/Sideload)";
                score = 100;
            } else if (installer.contains("browser") || installer.contains("download")) {
                result.installerSource = "Unknown Source (Browser/Downloader)";
                score = 100;
            } else {
                result.installerSource = "Unknown Source (" + installer + ")";
                score = 80;
            }
            result.isMalicious = true;
            result.threatsFound.add("Risky: Installed from " + result.installerSource);
        }

        result.riskScore = score;
        return result;
    }

    private String getInstaller(String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return pm.getInstallSourceInfo(packageName).getInstallingPackageName();
            } else {
                return pm.getInstallerPackageName(packageName);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
