package com.avish.sheidhero;

import java.util.List;

public class AppInfo {
    public String name;
    public String packageName;
    public String riskLevel;
    public String vtResult;
    public List<String> sensitivePermissions;

    public AppInfo(String name, String packageName, String riskLevel) {
        this.name = name;
        this.packageName = packageName;
        this.riskLevel = riskLevel;
    }
}   
