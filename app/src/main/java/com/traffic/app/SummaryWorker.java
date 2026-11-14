package com.traffic.app;

import android.content.Context;
import androidx.work.*;

public final class SummaryWorker extends Worker {
    public SummaryWorker(Context context, WorkerParameters parameters) { super(context,parameters); }
    @Override public Result doWork() {
        Context c=getApplicationContext();
        if (!TrafficApplication.enabled(c) || !DeviceGate.canCollect(c)) return Result.success();
        try {
            Collector.refresh(c);
            return Result.success();
        } catch (Exception e) {
            // Never log packages or timestamps; no screen-off retry loop.
            return getRunAttemptCount()<2 ? Result.retry() : Result.failure();
        }
    }
}
