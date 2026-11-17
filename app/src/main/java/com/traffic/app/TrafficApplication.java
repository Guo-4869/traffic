package com.traffic.app;

import android.app.Application;
import android.content.*;
import android.os.Build;
import androidx.work.*;
import java.util.concurrent.*;

public final class TrafficApplication extends Application {
    static final String WORK = "traffic-daily-summary";
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private boolean registered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // Opportunity only: this receiver does not keep the process alive.
            if (!enabled(context) || !UsageAccess.granted(context) || !DeviceGate.canCollect(context)) return;
            WorkManager.getInstance(context).enqueueUniqueWork("traffic-unlock-catchup",
                    ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(SummaryWorker.class).build());
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        I18n.init(this);
        if (enabled(this)) { registerOpportunities(); schedule(this); }
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);I18n.init(this);
    }
    static boolean enabled(Context c) {
        return c.getSharedPreferences("settings",MODE_PRIVATE).getBoolean("enabled",false);
    }
    void setEnabled(boolean value) {
        getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("enabled",value).apply();
        if (value) { registerOpportunities(); schedule(this); }
        else {
            if (registered) { unregisterReceiver(receiver); registered=false; }
            WorkManager.getInstance(this).cancelUniqueWork(WORK);
            WorkManager.getInstance(this).cancelUniqueWork("traffic-unlock-catchup");
        }
    }
    private void registerOpportunities() {
        if (registered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT); filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver,filter);
        registered=true;
    }
    static void schedule(Context c) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SummaryWorker.class,
                24,TimeUnit.HOURS,2,TimeUnit.HOURS)
                .setInitialDelay(1,TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.KEEP,request);
    }
}
