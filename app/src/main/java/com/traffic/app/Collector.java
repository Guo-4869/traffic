package com.traffic.app;

import android.app.usage.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import com.traffic.core.*;
import org.json.*;
import java.time.*;
import java.util.*;

final class Collector {
    record Result(Store.Snapshot snapshot, List<Store.ReportRow> reports, String message) {}
    private final Context context;
    Collector(Context context) { this.context = context.getApplicationContext(); I18n.init(context); }
    /** Shared monitor serializes Activity, broadcasts and WorkManager transactions. */
    synchronized static Result refresh(Context c) throws JSONException {
        return new Collector(c).run();
    }
    synchronized static void clear(Context c) {
        try (Store store = new Store(c)) { store.clear(); }
    }
    synchronized static void begin(Context c) throws JSONException {
        I18n.init(c);
        long now=System.currentTimeMillis();
        try (Store store=new Store(c)) {
            Store.Snapshot s=store.load(now);
            HomeApps.remove(s,HomeApps.packages(c));
            if(s.cursor>0) {
                new SessionEngine(s.state,s.sessions).discontinuity(s.cursor);
                addGap(s,s.cursor,now,I18n.t(R.string.ui_003));
            }
            s.state=new SessionEngine.State();
            s.state.interactive=DeviceGate.canCollect(c); s.state.unlocked=s.state.interactive;
            s.state.boundary=now; s.cursor=now;
            if(s.captureStart==0) s.captureStart=now;
            if(s.unlockStart==0) s.unlockStart=now;
            store.save(s,buildReports(s,now),now);
        }
    }
    private Result run() throws JSONException {
        long now = System.currentTimeMillis();
        try (Store store = new Store(context)) {
            Store.Snapshot s = store.load(now);
            HomeApps.remove(s,HomeApps.packages(context));
            if(s.captureStart>0 && s.unlockStart==0)s.unlockStart=now;
            String message;
            boolean granted = UsageAccess.granted(context);
            if (!TrafficApplication.enabled(context)) {
                if (s.cursor>0) {
                    new SessionEngine(s.state,s.sessions).discontinuity(s.cursor);
                    addGap(s,s.cursor,now,I18n.t(R.string.ui_004));
                    s.cursor=now;
                }
                message=I18n.t(R.string.ui_004);
            } else if (!granted) {
                if (s.cursor > 0) {
                    new SessionEngine(s.state,s.sessions).discontinuity(s.cursor);
                    addGap(s,s.cursor,now,I18n.t(R.string.ui_005));
                    s.cursor = now;
                }
                message = I18n.t(R.string.ui_006);
            } else if (!DeviceGate.canCollect(context)) {
                // Do not query UsageStats, advance the cursor or start a retry loop while locked/asleep.
                message = I18n.t(R.string.ui_007);
            } else {
                // Avoid finalizing events right at the source's current write edge.
                long end = now - SessionEngine.MERGE_MS;
                if (s.captureStart == 0) {
                    s.captureStart = now; s.cursor = now;
                    s.unlockStart=now;
                    s.state.interactive = true; s.state.unlocked = true;
                    s.state.boundary = now;
                    message = I18n.t(R.string.ui_008);
                } else if (end <= s.cursor) {
                    if (now < s.cursor) {
                        new SessionEngine(s.state,s.sessions).discontinuity(now);
                        s.cursor = now; s.state.interactive = true; s.state.unlocked = true;
                        addGap(s,now,now+1,I18n.t(R.string.ui_009));
                    }
                    message = I18n.t(R.string.ui_010);
                } else {
                    try { message = collect(s, end, now); }
                    catch (Deferred ignored) {
                        s = store.load(now);
                        message = I18n.t(R.string.ui_011);
                    }
                }
            }
            // A deferred query may reload the pre-filter snapshot; scrub before every commit.
            HomeApps.remove(s,HomeApps.packages(context));
            resolveLabels(s);
            SessionEngine.prune(s.sessions,now-Store.RETENTION);
            List<Store.ReportRow> reports = buildReports(s, now);
            store.save(s,reports,now);
            return new Result(s,reports,message);
        }
    }
    private String collect(Store.Snapshot s, long end, long now) {
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        long begin = Math.max(s.cursor, now-Store.RETENTION);
        if (manager == null) return I18n.t(R.string.ui_012);
        UsageEvents source;
        try { source = manager.queryEvents(begin,end); }
        catch (SecurityException e) { return I18n.t(R.string.ui_013); }
        if (source == null) return I18n.t(R.string.ui_014);
        SessionEngine engine = new SessionEngine(s.state,s.sessions,s.transitions);
        if (end-s.cursor > 24*60*60*1000L) {
            // There is no public retention/coverage watermark. Conservatively mark long gaps.
            addGap(s,s.cursor,end,I18n.t(R.string.ui_015));
            engine.discontinuity(s.cursor);
        }
        UsageEvents.Event event = new UsageEvents.Event();
        Set<String> homes=HomeApps.packages(context);
        long previous = begin;
        int count = 0;
        while (source.hasNextEvent()) {
            if ((count++ & 127) == 0 && !DeviceGate.canCollect(context)) throw new Deferred();
            source.getNextEvent(event);
            if (event.getTimeStamp() < begin || event.getTimeStamp() >= end) continue;
            SessionEngine.Kind kind = switch (event.getEventType()) {
                case UsageEvents.Event.ACTIVITY_RESUMED -> SessionEngine.Kind.RESUME;
                case UsageEvents.Event.ACTIVITY_PAUSED -> SessionEngine.Kind.PAUSE;
                case UsageEvents.Event.SCREEN_INTERACTIVE -> SessionEngine.Kind.SCREEN_ON;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE -> SessionEngine.Kind.SCREEN_OFF;
                case UsageEvents.Event.KEYGUARD_HIDDEN -> SessionEngine.Kind.UNLOCK;
                case UsageEvents.Event.KEYGUARD_SHOWN -> SessionEngine.Kind.LOCK;
                case UsageEvents.Event.DEVICE_SHUTDOWN -> SessionEngine.Kind.SHUTDOWN;
                case UsageEvents.Event.DEVICE_STARTUP -> SessionEngine.Kind.STARTUP;
                default -> null;
            };
            if (kind == null) continue;
            if (event.getTimeStamp() < previous) {
                addGap(s,event.getTimeStamp(),end,I18n.t(R.string.ui_016));
                engine.discontinuity(previous);
                continue;
            }
            previous = event.getTimeStamp();
            String pkg = event.getPackageName();
            if(kind==SessionEngine.Kind.UNLOCK && event.getTimeStamp()>=s.unlockStart
                    && event.getTimeStamp()>s.state.lastUnlock) {
                s.unlocks.add(event.getTimeStamp()); s.state.lastUnlock=event.getTimeStamp();
            }
            if ((kind == SessionEngine.Kind.RESUME || kind == SessionEngine.Kind.PAUSE) && pkg == null) continue;
            if((kind==SessionEngine.Kind.RESUME || kind==SessionEngine.Kind.PAUSE) && homes.contains(pkg)) continue;
            // Public UsageEvents has no activity-instance ID. Keep package-level state;
            // do not inspect class/page names or use hidden APIs.
            int id = 0;
            engine.accept(new SessionEngine.Event(event.getTimeStamp(),kind,pkg,id));
        }
        if (!UsageAccess.granted(context) || !DeviceGate.canCollect(context)) throw new Deferred();
        s.cursor = end;
        return I18n.t(R.string.ui_017);
    }
    private static final class Deferred extends RuntimeException {}
    private void resolveLabels(Store.Snapshot s) {
        PackageManager pm = context.getPackageManager();
        for (SessionEngine.Session item : s.sessions) if (!s.labels.containsKey(item.pkg)) {
            String name = item.pkg;
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(item.pkg,0)).toString(); }
            catch (PackageManager.NameNotFoundException | SecurityException ignored) {}
            s.labels.put(item.pkg,name);
        }
        for (var t : s.transitions) {
            for (String pkg : List.of(t.from(),t.to())) if (!s.labels.containsKey(pkg)) {
                try { s.labels.put(pkg,pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString()); }
                catch (PackageManager.NameNotFoundException | SecurityException ignored) { s.labels.put(pkg,pkg); }
            }
        }
    }
    private static void addGap(Store.Snapshot s, long start, long end, String reason) {
        if (end <= start) return;
        if (!s.gaps.isEmpty()) {
            Store.Gap last = s.gaps.get(s.gaps.size()-1);
            if (last.reason().equals(reason) && last.end() >= start) {
                s.gaps.set(s.gaps.size()-1,new Store.Gap(last.start(),Math.max(last.end(),end),reason));
                return;
            }
        }
        s.gaps.add(new Store.Gap(start,end,reason));
    }
    static List<Store.ReportRow> buildReports(Store.Snapshot s, long now) throws JSONException {
        ZoneId zone = ZoneId.systemDefault();
        long cutoff = now-Store.RETENTION;
        List<Store.ReportRow> rows = new ArrayList<>();
        if (s.captureStart == 0) return rows;
        LocalDate first = Instant.ofEpochMilli(Math.max(cutoff,s.captureStart)).atZone(zone).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            long start = d.atStartOfDay(zone).toInstant().toEpochMilli();
            long end = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            rows.add(report(s,"day",start,end,now));
        }
        long week = Reports.weekStart(now,zone);
        long previousWeek = Instant.ofEpochMilli(week).atZone(zone).minusWeeks(1).toInstant().toEpochMilli();
        if (previousWeek < now && week > Math.max(cutoff,s.captureStart)) rows.add(report(s,"week",previousWeek,week,now));
        rows.add(report(s,"week",week,Instant.ofEpochMilli(week).atZone(zone).plusWeeks(1).toInstant().toEpochMilli(),now));
        return rows;
    }
    private static Store.ReportRow report(Store.Snapshot s, String kind, long start, long end, long now) throws JSONException {
        long availableStart = Math.max(start,Math.max(now-Store.RETENTION,s.captureStart));
        long availableEnd = Math.min(end,Math.min(now,s.cursor));
        var summary = Reports.summarize(s.sessions,s.transitions,availableStart,Math.max(availableStart,availableEnd),s.cursor);
        boolean partial = availableStart > start || availableEnd < end;
        for (Store.Gap gap : s.gaps) if (gap.start()<end && gap.end()>start) partial = true;
        JSONArray apps = new JSONArray(), flows = new JSONArray();
        for (var a : summary.apps()) apps.put(new JSONObject().put("package",a.pkg())
                .put("name",s.labels.getOrDefault(a.pkg(),a.pkg())).put("durationMs",a.duration())
                .put("opens",a.opens()).put("unknownEnds",a.unknownEnds()));
        for (var f : summary.flows()) flows.put(new JSONObject().put("from",f.from()).put("to",f.to()).put("count",f.count()));
        JSONObject payload = new JSONObject().put("availableStart",availableStart).put("availableEnd",availableEnd)
                .put("partial",partial).put("bestEffort",true).put("apps",apps).put("flows",flows)
                .put("unlocks",s.unlocks.stream().filter(t->t>=availableStart && t<availableEnd).count());
        return new Store.ReportRow(kind,start,end,payload.toString());
    }
}
