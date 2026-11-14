package com.traffic.app;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;

final class UsageAccess {
    static boolean granted(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    private UsageAccess() {}
}
