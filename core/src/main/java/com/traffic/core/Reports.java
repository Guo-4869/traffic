package com.traffic.core;

import java.time.*;
import java.util.*;
import static com.traffic.core.SessionEngine.Session;

public final class Reports {
    public record AppUsage(String pkg, long duration, int opens, int unknownEnds) {}
    public record Flow(String from, String to, int count) {}
    public record Summary(long start, long end, List<AppUsage> apps, List<Flow> flows) {}
    public static Summary summarize(List<Session> sessions, long start, long end, long now) {
        return summarize(sessions,List.of(),start,end,now);
    }
    public static Summary summarize(List<Session> sessions, List<SessionEngine.Transition> transitions, long start, long end, long now) {
        Map<String, long[]> totals = new HashMap<>();
        List<Session> ordered = new ArrayList<>(sessions);
        ordered.sort(Comparator.comparingLong(s -> s.start));
        for (Session s : ordered) {
            long stop = s.end == null ? (s.endReason == null ? now : s.spans.stream().mapToLong(SessionEngine.Span::end).max().orElse(s.start)) : s.end;
            if (s.start >= end || stop < start || (stop == start && s.start < start)) continue;
            long[] v = totals.computeIfAbsent(s.pkg, k -> new long[3]);
            for(var span:s.spans) v[0] += Math.max(0,Math.min(span.end(),end)-Math.max(span.start(),start));
            if(s.activeSince!=null) v[0]+=Math.max(0,Math.min(now,end)-Math.max(s.activeSince,start));
            if (s.start >= start && !s.clipped) v[1]++;
            if (s.end == null && s.endReason != null) v[2]++;
        }
        Map<List<String>, Integer> edges = new HashMap<>();
        for (var t : transitions) {
            if (t.time() >= start && t.time() < end)
                edges.merge(List.of(t.from(), t.to()), 1, Integer::sum);
        }
        List<AppUsage> apps = new ArrayList<>();
        totals.forEach((pkg, v) -> apps.add(new AppUsage(pkg, v[0], (int)v[1], (int)v[2])));
        apps.sort(Comparator.comparingLong(AppUsage::duration).reversed().thenComparing(AppUsage::pkg));
        List<Flow> flows = new ArrayList<>();
        edges.forEach((k, count) -> flows.add(new Flow(k.get(0), k.get(1), count)));
        flows.sort(Comparator.comparingInt(Flow::count).reversed().thenComparing(Flow::from).thenComparing(Flow::to));
        return new Summary(start, end, apps, flows);
    }
    public static long dayStart(long timestamp, ZoneId zone) {
        return Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
    }
    public static long weekStart(long timestamp, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
        return date.minusDays(date.getDayOfWeek().getValue() - 1).atStartOfDay(zone).toInstant().toEpochMilli();
    }
    private Reports() {}
}
