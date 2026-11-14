package com.traffic.app;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;

final class DeviceGate {
    static boolean canCollect(Context c) {
        UserManager user = c.getSystemService(UserManager.class);
        PowerManager power = c.getSystemService(PowerManager.class);
        KeyguardManager keyguard = c.getSystemService(KeyguardManager.class);
        if (user == null || power == null || keyguard == null
                || !user.isUserUnlocked() || !power.isInteractive() || keyguard.isKeyguardLocked()) return false;
        DisplayManager displays = c.getSystemService(DisplayManager.class);
        if (displays == null) return false;
        for (Display display : displays.getDisplays()) if (display.getState() == Display.STATE_ON) return true;
        return false;
    }
    private DeviceGate() {}
}
