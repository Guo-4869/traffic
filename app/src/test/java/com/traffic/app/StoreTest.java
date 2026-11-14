package com.traffic.app;

import android.app.Application;
import org.robolectric.RuntimeEnvironment;
import com.traffic.core.*;
import com.traffic.core.SessionEngine.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, application=Application.class)
public class StoreTest {
    Application app;
    @Before public void setup() { app=RuntimeEnvironment.getApplication(); app.deleteDatabase("traffic.db"); }
    @Test public void checkpointRoundTripPreservesMergedDurationAndFlow() throws Exception {
        long now=2_000_000_000_000L;
        try(Store store=new Store(app)) {
            Store.Snapshot s=new Store.Snapshot();
            s.cursor=now; s.captureStart=now-10000;
            s.state.interactive=true;s.state.unlocked=true;
            var engine=new SessionEngine(s.state,s.sessions,s.transitions);
            engine.accept(new Event(now-9000,Kind.RESUME,"A",1));
            engine.accept(new Event(now-8000,Kind.PAUSE,"A",1));
            engine.accept(new Event(now-7500,Kind.RESUME,"B",2));
            engine.accept(new Event(now-7000,Kind.PAUSE,"B",2));
            engine.accept(new Event(now-6000,Kind.RESUME,"A",3));
            s.labels.put("A","应用 A");s.labels.put("B","应用 B");
            store.save(s,Collector.buildReports(s,now),now);
            Store.Snapshot loaded=store.load(now);
            assertEquals(2,loaded.sessions.size());
            assertEquals(2,loaded.transitions.size());
            assertEquals(Set.of(3),loaded.state.active.get("A"));
            assertEquals("A",loaded.state.focus);
            var summary=Reports.summarize(loaded.sessions,loaded.transitions,now-10000,now,now);
            assertEquals(7000,summary.apps().stream().filter(a->a.pkg().equals("A")).findFirst().get().duration());
            assertEquals("应用 A",loaded.labels.get("A"));
            assertEquals(now,loaded.cursor);
        }
    }
    @Test public void retentionRemovesOldUsageLabelsFlowsAndReportContribution() throws Exception {
        long now=2_000_000_000_000L;
        try(Store store=new Store(app)) {
            Store.Snapshot s=new Store.Snapshot();s.cursor=now;s.captureStart=now-2*Store.RETENTION;
            Session expired=new Session("old",now-Store.RETENTION-9000);
            expired.end=now-Store.RETENTION;expired.endReason="pause";expired.activeSince=null;
            expired.spans.add(new Span(expired.start,expired.end));s.sessions.add(expired);
            s.labels.put("old","已过期应用");
            s.transitions.add(new Transition("old","older",now-Store.RETENTION-1));
            SessionEngine.prune(s.sessions,now-Store.RETENTION);
            s.transitions.removeIf(t->t.time()<now-Store.RETENTION);
            store.save(s,Collector.buildReports(s,now),now);
            Store.Snapshot loaded=store.load(now);
            assertTrue(loaded.sessions.isEmpty());assertTrue(loaded.labels.isEmpty());assertTrue(loaded.transitions.isEmpty());
            for(var r:Collector.buildReports(loaded,now)) assertFalse(r.payload().contains("old"));
        }
    }
    @Test public void clearDeletesCheckpointAndAllUsageData() throws Exception {
        try(Store store=new Store(app)) {
            Store.Snapshot s=new Store.Snapshot();s.cursor=100;s.captureStart=100;
            s.sessions.add(new Session("A",100));
            store.save(s,List.of(),100);
            store.clear();
            var loaded=store.load(100);
            assertEquals(0,loaded.cursor);assertEquals(0,loaded.captureStart);assertTrue(loaded.sessions.isEmpty());
            assertTrue(loaded.state.active.isEmpty());assertTrue(loaded.labels.isEmpty());
        }
    }
    @Test public void lockedAndDisabledRefreshNeverStartsHistoryCollection() throws Exception {
        var result=Collector.refresh(app);
        assertEquals(0,result.snapshot().captureStart);
        assertEquals(0,result.snapshot().cursor);
        assertTrue(result.snapshot().sessions.isEmpty());
        assertTrue(result.reports().isEmpty());
    }
}
