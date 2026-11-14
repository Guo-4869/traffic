package com.traffic.app;

import android.app.Application;
import android.database.sqlite.SQLiteException;
import com.traffic.core.SessionEngine.Session;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.file.*;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, application=Application.class)
public class SafetyTest {
    Application app;
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication(); app.deleteDatabase("traffic.db");
    }
    @Test public void clearLeavesUnrelatedFilesAndDatabasesUntouched() throws Exception {
        Path sentinel=app.getFilesDir().toPath().resolve("unrelated.txt");
        Files.write(sentinel,"keep me".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(var other=app.openOrCreateDatabase("unrelated.db",0,null)) {
            other.execSQL("CREATE TABLE sentinel (value TEXT)");
            other.execSQL("INSERT INTO sentinel VALUES ('keep me')");
            try(Store store=new Store(app)) {
                var s=new Store.Snapshot(); s.cursor=100; s.captureStart=100;
                s.sessions.add(new Session("A",100)); store.save(s,List.of(),100);
                store.clear();
                assertTrue(store.load(100).sessions.isEmpty());
            }
            assertEquals("keep me",new String(Files.readAllBytes(sentinel),java.nio.charset.StandardCharsets.UTF_8));
            try(var c=other.rawQuery("SELECT value FROM sentinel",null)) {
                assertTrue(c.moveToFirst()); assertEquals("keep me",c.getString(0));
            }
        }
    }
    @Test public void failedWriteRollsBackCursorAndExistingSessions() throws Exception {
        try(Store store=new Store(app)) {
            var s=new Store.Snapshot(); s.cursor=100; s.captureStart=100;
            s.sessions.add(new Session("A",100)); store.save(s,List.of(),100);
            store.getWritableDatabase().execSQL("CREATE TRIGGER fail_write BEFORE INSERT ON sessions BEGIN SELECT RAISE(ABORT, 'injected write failure'); END");
            var next=store.load(100); next.cursor=200; next.sessions.clear(); next.sessions.add(new Session("B",200));
            assertThrows(SQLiteException.class,()->store.save(next,List.of(),200));
            var recovered=store.load(200);
            assertEquals(100,recovered.cursor);
            assertEquals(1,recovered.sessions.size());
            assertEquals("A",recovered.sessions.get(0).pkg);
        }
    }
}
