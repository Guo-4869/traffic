package com.traffic.core;

import java.util.*;

/** Pure Java, event-time reducer. Never consumes class names, content or input events. */
public final class SessionEngine {
    public static final long MERGE_MS = 5_000;
    public enum Kind { RESUME, PAUSE, SCREEN_ON, SCREEN_OFF, UNLOCK, LOCK, SHUTDOWN, STARTUP }
    public record Event(long time, Kind kind, String pkg, int activityId) {}
    public record Transition(String from, String to, long time) {}
    public record Span(long start, long end) {}
    public static final class Session {
        public String pkg;
        public long start;
        public Long end;
        public String endReason;
        public boolean clipped;
        public Long activeSince;
        public final List<Span> spans = new ArrayList<>();
        public Session(String pkg, long start) { this.pkg = pkg; this.start = start; this.activeSince=start; }
        public Session copy() {
            Session s = new Session(pkg, start);
            s.end = end; s.endReason = endReason; s.clipped = clipped;
            s.activeSince=activeSince; s.spans.addAll(spans);
            return s;
        }
    }
    /** Opaque activity instance numbers are processing state only; no activity/page names. */
    public static final class State {
        public boolean interactive, unlocked;
        public long boundary;
        public String focus;
        public long focusEnded;
        public final Map<String, Set<Integer>> active = new HashMap<>();
    }
    private final State state;
    private final List<Session> sessions;
    private final List<Transition> transitions;
    public SessionEngine(State state, List<Session> sessions) {
        this(state,sessions,new ArrayList<>());
    }
    public SessionEngine(State state, List<Session> sessions, List<Transition> transitions) {
        this.state = state; this.sessions = sessions; this.transitions = transitions;
    }
    public void accept(Event e) {
        switch (e.kind) {
            case RESUME -> {
                if (eligible()) {
                    if (state.focus != null && !state.focus.equals(e.pkg)
                            && (state.active.containsKey(state.focus) || e.time-state.focusEnded <= 30_000))
                        transitions.add(new Transition(state.focus,e.pkg,e.time));
                    state.focus = e.pkg;
                }
                state.active.computeIfAbsent(e.pkg, k -> new HashSet<>()).add(e.activityId);
                if (eligible()) open(e.pkg, e.time);
            }
            case PAUSE -> {
                Set<Integer> ids = state.active.get(e.pkg);
                if (ids != null) {
                    ids.remove(e.activityId);
                    if (ids.isEmpty()) state.active.remove(e.pkg);
                }
                if (!state.active.containsKey(e.pkg)) close(e.pkg, e.time, "pause");
                if (e.pkg.equals(state.focus) && !state.active.containsKey(e.pkg)) state.focusEnded = e.time;
            }
            case SCREEN_ON -> { state.interactive = true; reopen(e.time); }
            case UNLOCK -> { state.unlocked = true; reopen(e.time); }
            case SCREEN_OFF -> { state.interactive = false; boundary(e.time, "screen_off", false); }
            case LOCK -> { state.unlocked = false; boundary(e.time, "lock", false); }
            case SHUTDOWN, STARTUP -> {
                boundary(e.time, "unknown", true);
                state.interactive = false; state.unlocked = false;
            }
        }
    }
    public void discontinuity(long time) {
        boundary(time, "unknown", true);
        state.interactive = false; state.unlocked = false;
    }
    public boolean eligible() { return state.interactive && state.unlocked; }
    private void reopen(long t) {
        if (eligible()) for (String pkg : state.active.keySet()) open(pkg, t);
    }
    private void open(String pkg, long t) {
        Session last = null;
        for (int i = sessions.size() - 1; i >= 0; i--) {
            if (sessions.get(i).pkg.equals(pkg)) { last = sessions.get(i); break; }
        }
        if (last != null && last.end == null && last.endReason == null) return;
        if (last != null && last.end != null && "pause".equals(last.endReason)
                && last.end > state.boundary && t >= last.end && t - last.end <= MERGE_MS) {
            last.end = null; last.endReason = null; last.activeSince=t;
        } else sessions.add(new Session(pkg, t));
    }
    private void close(String pkg, long t, String reason) {
        for (int i = sessions.size() - 1; i >= 0; i--) {
            Session s = sessions.get(i);
            if (s.pkg.equals(pkg) && s.end == null && s.endReason == null) {
                if (!"unknown".equals(reason)) s.end = Math.max(s.start, t);
                finishSpan(s,t,reason);
                s.endReason = reason;
                return;
            }
        }
    }
    private void boundary(long t, String reason, boolean clear) {
        for (Session s : sessions) {
            if (s.end == null && s.endReason == null) {
                if (!"unknown".equals(reason)) s.end = Math.max(s.start, t);
                finishSpan(s,t,reason);
                s.endReason = reason;
            }
        }
        state.boundary = t;
        state.focus = null;
        if (clear) state.active.clear();
    }
    public static void prune(List<Session> sessions, long cutoff) {
        sessions.removeIf(s -> (s.end != null && s.end <= cutoff)
                || (s.end == null && s.endReason != null && s.start < cutoff && s.spans.stream().noneMatch(p->p.end()>cutoff)));
        for (Session s : sessions) {
            if (s.start < cutoff) { s.start = cutoff; s.clipped = true; }
            if(s.activeSince!=null && s.activeSince<cutoff) s.activeSince=cutoff;
            List<Span> retained=new ArrayList<>();
            for(Span p:s.spans) if(p.end()>cutoff) retained.add(new Span(Math.max(cutoff,p.start()),p.end()));
            s.spans.clear(); s.spans.addAll(retained);
        }
    }
    private static void finishSpan(Session s,long t,String reason) {
        if(s.activeSince!=null && !"unknown".equals(reason)) s.spans.add(new Span(s.activeSince,Math.max(s.activeSince,t)));
        s.activeSince=null;
    }
}
