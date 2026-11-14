package com.traffic.core;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.traffic.core.SessionEngine.*;

public class InsightsTest {
    @Test public void mergedSessionKeepsInactiveGapOutOfGanttAndHeatmap(){
        Session s=new Session("A",0);s.activeSince=null;s.end=20000L;s.endReason="pause";
        s.spans.add(new Span(0,5000));s.spans.add(new Span(10000,20000));
        var intervals=Insights.intervals(List.of(s),0,20000,20000);
        assertEquals(2,intervals.size());assertEquals(15000,Insights.total(intervals));
        assertEquals(15000,Insights.hourly(intervals,ZoneOffset.UTC).get("A")[0]);
    }
    @Test public void totalUsesUnionAcrossSplitScreenApps(){
        var intervals=List.of(new Insights.Interval("A",0,10000),new Insights.Interval("B",5000,15000));
        assertEquals(15000,Insights.total(intervals));
    }
    @Test public void hourlySplitsCrossHourAndRepeatedDstHour(){
        long start=Instant.parse("2026-11-01T05:30:00Z").toEpochMilli();
        long end=Instant.parse("2026-11-01T07:30:00Z").toEpochMilli();
        var values=Insights.hourly(List.of(new Insights.Interval("A",start,end)),ZoneId.of("America/New_York")).get("A");
        assertEquals(90*60000,values[1]);assertEquals(30*60000,values[2]);
        assertEquals(end-start,Arrays.stream(values).sum());
    }
}
