package com.traffic.app;

import android.app.Application;
import android.content.*;
import android.content.pm.*;
import com.traffic.core.SessionEngine.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class DashboardTest {
    Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();app.deleteDatabase("traffic.db");}
    @Test public void desktopIsDetectedAndPurgedFromPersistedUsage() throws Exception {
        ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.packageName="test.home";
        info.activityInfo.name="Home";Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),info);
        Set<String> homes=HomeApps.packages(app);assertTrue(homes.contains("test.home"));
        var s=new Store.Snapshot();s.sessions.add(new Session("test.home",100));s.sessions.add(new Session("A",100));s.labels.put("test.home","桌面");s.labels.put("A","阅读");
        s.transitions.add(new Transition("A","test.home",100));s.state.focus="test.home";s.state.active.put("test.home",new HashSet<>(Set.of(0)));
        HomeApps.remove(s,homes);
        try(Store store=new Store(app)){store.save(s,List.of(),200);var loaded=store.load(200);
            assertEquals(1,loaded.sessions.size());assertEquals("A",loaded.sessions.get(0).pkg);
            assertFalse(loaded.labels.containsKey("test.home"));assertTrue(loaded.transitions.isEmpty());assertNull(loaded.state.focus);assertFalse(loaded.state.active.containsKey("test.home"));}
    }
    @Test public void upgradeKeepsSessionsAndAddsUnlockStorage() throws Exception {
        try(Store old=new Store(app)){var s=new Store.Snapshot();s.sessions.add(new Session("A",100));old.save(s,List.of(),200);old.getWritableDatabase().execSQL("DROP TABLE unlocks");old.getWritableDatabase().setVersion(1);}
        try(Store current=new Store(app)){assertEquals(1,current.load(200).sessions.size());assertEquals(2,current.getReadableDatabase().getVersion());assertTrue(current.load(200).unlocks.isEmpty());}
    }
    @Test public void unlocksExpireAndClearAlongWithSessions() throws Exception {
        long now=2_000_000_000_000L;try(Store store=new Store(app)){var s=new Store.Snapshot();s.unlocks.add(now-Store.RETENTION-1);s.unlocks.add(now-1000);s.state.lastUnlock=now-1000;s.unlockStart=now-5000;store.save(s,List.of(),now);
            var loaded=store.load(now);assertEquals(List.of(now-1000),loaded.unlocks);assertEquals(now-1000,loaded.state.lastUnlock);assertEquals(now-5000,loaded.unlockStart);
            store.clear();assertTrue(store.load(now).unlocks.isEmpty());}
    }
}
