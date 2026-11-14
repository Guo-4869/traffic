package com.traffic.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import com.traffic.core.SessionEngine;
import com.traffic.core.SessionEngine.Session;
import org.json.*;
import java.util.*;

/** App-private SQLite; no class names, screen contents, text, network or backup. */
final class Store extends SQLiteOpenHelper {
    static final long RETENTION = 7L * 24 * 60 * 60 * 1000;
    record Gap(long start, long end, String reason) {}
    record ReportRow(String kind, long start, long end, String payload) {}
    static final class Snapshot {
        long cursor, captureStart;
        SessionEngine.State state = new SessionEngine.State();
        final List<Session> sessions = new ArrayList<>();
        final List<SessionEngine.Transition> transitions = new ArrayList<>();
        final List<Gap> gaps = new ArrayList<>();
        final Map<String, String> labels = new HashMap<>();
    }
    Store(Context context) { super(context, "traffic.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) {
        db.execSQL("PRAGMA secure_delete=ON");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sessions (pkg TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER, reason TEXT, clipped INTEGER NOT NULL, active_since INTEGER, spans TEXT NOT NULL, PRIMARY KEY(pkg,start))");
        db.execSQL("CREATE INDEX sessions_end ON sessions(end)");
        db.execSQL("CREATE TABLE transitions (source TEXT NOT NULL, destination TEXT NOT NULL, time INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE labels (pkg TEXT PRIMARY KEY, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE gaps (start INTEGER NOT NULL, end INTEGER NOT NULL, reason TEXT NOT NULL)");
        db.execSQL("CREATE TABLE reports (kind TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(kind,start))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Database migration required");
    }
    Snapshot load(long now) throws JSONException {
        Snapshot s = new Snapshot();
        SQLiteDatabase db = getReadableDatabase();
        long cutoff = now - RETENTION;
        try (Cursor c = db.rawQuery("SELECT key,value FROM meta", null)) {
            while (c.moveToNext()) switch (c.getString(0)) {
                case "cursor" -> s.cursor = Long.parseLong(c.getString(1));
                case "capture_start" -> s.captureStart = Long.parseLong(c.getString(1));
                case "state" -> {
                    JSONObject j = new JSONObject(c.getString(1));
                    s.state.interactive = j.optBoolean("interactive");
                    s.state.unlocked = j.optBoolean("unlocked");
                    s.state.boundary = j.optLong("boundary");
                    s.state.focus = j.isNull("focus") ? null : j.optString("focus",null);
                    s.state.focusEnded = j.optLong("focusEnded");
                    JSONObject active = j.optJSONObject("active");
                    if (active != null) {
                        Iterator<String> keys = active.keys();
                        while (keys.hasNext()) {
                            String pkg = keys.next();
                            JSONArray a = active.getJSONArray(pkg);
                            Set<Integer> ids = new HashSet<>();
                            for (int i = 0; i < a.length(); i++) ids.add(a.getInt(i));
                            s.state.active.put(pkg, ids);
                        }
                    }
                }
            }
        }
        try (Cursor c = db.rawQuery("SELECT pkg,start,end,reason,clipped,active_since,spans FROM sessions WHERE end>? OR end IS NULL ORDER BY start,pkg", new String[]{""+cutoff})) {
            while (c.moveToNext()) {
                Session item = new Session(c.getString(0), c.getLong(1));
                item.end = c.isNull(2) ? null : c.getLong(2);
                item.endReason = c.isNull(3) ? null : c.getString(3);
                item.clipped = c.getInt(4) != 0;
                item.activeSince=c.isNull(5)?null:c.getLong(5);
                JSONArray spans=new JSONArray(c.getString(6));
                for(int i=0;i<spans.length();i++) {
                    JSONArray p=spans.getJSONArray(i);
                    item.spans.add(new SessionEngine.Span(p.getLong(0),p.getLong(1)));
                }
                s.sessions.add(item);
            }
        }
        SessionEngine.prune(s.sessions, cutoff);
        try (Cursor c = db.rawQuery("SELECT source,destination,time FROM transitions WHERE time>=? ORDER BY time", new String[]{""+cutoff})) {
            while (c.moveToNext()) s.transitions.add(new SessionEngine.Transition(c.getString(0),c.getString(1),c.getLong(2)));
        }
        try (Cursor c = db.rawQuery("SELECT start,end,reason FROM gaps WHERE end>?", new String[]{""+cutoff})) {
            while (c.moveToNext()) s.gaps.add(new Gap(Math.max(cutoff,c.getLong(0)),c.getLong(1),c.getString(2)));
        }
        try (Cursor c = db.rawQuery("SELECT pkg,name FROM labels", null)) {
            while (c.moveToNext()) s.labels.put(c.getString(0), c.getString(1));
        }
        return s;
    }
    void save(Snapshot s, List<ReportRow> reports, long now) throws JSONException {
        SessionEngine.prune(s.sessions, now - RETENTION);
        s.gaps.removeIf(g -> g.end <= now - RETENTION);
        s.transitions.removeIf(t -> t.time() < now-RETENTION);
        Set<String> retained = new HashSet<>(s.state.active.keySet());
        for (Session session : s.sessions) retained.add(session.pkg);
        for (var t : s.transitions) { retained.add(t.from()); retained.add(t.to()); }
        s.labels.keySet().retainAll(retained);
        JSONObject j = new JSONObject(), active = new JSONObject();
        for (var e : s.state.active.entrySet()) active.put(e.getKey(), new JSONArray(e.getValue()));
        j.put("interactive", s.state.interactive).put("unlocked", s.state.unlocked)
                .put("boundary", s.state.boundary).put("active", active)
                .put("focus",s.state.focus).put("focusEnded",s.state.focusEnded);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putMeta(db,"cursor", ""+s.cursor); putMeta(db,"capture_start", ""+s.captureStart);
            putMeta(db,"state",j.toString());
            db.delete("sessions", null, null);
            for (Session item : s.sessions) {
                ContentValues v = new ContentValues();
                v.put("pkg",item.pkg); v.put("start",item.start); v.put("end",item.end);
                v.put("reason",item.endReason); v.put("clipped",item.clipped ? 1 : 0);
                v.put("active_since",item.activeSince);
                JSONArray spans=new JSONArray();
                for(var span:item.spans) spans.put(new JSONArray().put(span.start()).put(span.end()));
                v.put("spans",spans.toString());
                db.insertOrThrow("sessions",null,v);
            }
            db.delete("labels", null, null);
            db.delete("transitions",null,null);
            for (var t : s.transitions) {
                ContentValues v = new ContentValues(); v.put("source",t.from()); v.put("destination",t.to()); v.put("time",t.time());
                db.insertOrThrow("transitions",null,v);
            }
            for (var e : s.labels.entrySet()) {
                ContentValues v = new ContentValues(); v.put("pkg",e.getKey()); v.put("name",e.getValue());
                db.insertOrThrow("labels",null,v);
            }
            db.delete("gaps",null,null);
            for (Gap g : s.gaps) {
                ContentValues v = new ContentValues(); v.put("start",Math.max(now-RETENTION,g.start)); v.put("end",g.end); v.put("reason",g.reason);
                db.insertOrThrow("gaps",null,v);
            }
            // Rebuild aggregates from retained source data; old summaries never extend retention.
            db.delete("reports",null,null);
            for (ReportRow row : reports) {
                ContentValues v = new ContentValues(); v.put("kind",row.kind); v.put("start",row.start); v.put("end",row.end); v.put("payload",row.payload);
                db.insertOrThrow("reports",null,v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void putMeta(SQLiteDatabase db, String key, String value) {
        ContentValues v = new ContentValues(); v.put("key",key); v.put("value",value);
        db.insertWithOnConflict("meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String table : List.of("sessions","transitions","labels","gaps","reports","meta")) db.delete(table,null,null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        db.execSQL("VACUUM");
    }
}
