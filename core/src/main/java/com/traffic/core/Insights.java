package com.traffic.core;

import java.time.*;
import java.util.*;

public final class Insights {
    public record Interval(String pkg,long start,long end) {}
    public static List<Interval> intervals(List<SessionEngine.Session> sessions,long start,long end,long now) {
        List<Interval> result=new ArrayList<>();
        for(var s:sessions) {
            for(var span:s.spans) add(result,s.pkg,span.start(),span.end(),start,end);
            if(s.activeSince!=null) add(result,s.pkg,s.activeSince,now,start,end);
        }
        result.sort(Comparator.comparingLong(Interval::start));return result;
    }
    private static void add(List<Interval> out,String pkg,long a,long b,long start,long end) {
        a=Math.max(a,start);b=Math.min(b,end);if(b>a)out.add(new Interval(pkg,a,b));
    }
    public static long total(List<Interval> intervals) {
        long sum=0,a=0,b=0; boolean first=true;
        for(var p:intervals) {
            if(first) {a=p.start;b=p.end;first=false;}
            else if(p.start<=b)b=Math.max(b,p.end);
            else {sum+=b-a;a=p.start;b=p.end;}
        }
        return sum+(first?0:b-a);
    }
    public static Map<String,long[]> hourly(List<Interval> intervals,ZoneId zone) {
        Map<String,long[]> result=new LinkedHashMap<>();
        for(var p:intervals) {
            long cursor=p.start;
            long[] values=result.computeIfAbsent(p.pkg,k->new long[24]);
            while(cursor<p.end) {
                ZonedDateTime z=Instant.ofEpochMilli(cursor).atZone(zone);
                long next=z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli();
                long stop=Math.min(p.end,Math.max(cursor+1,next));
                values[z.getHour()]+=stop-cursor;cursor=stop;
            }
        }
        return result;
    }
    private Insights() {}
}
