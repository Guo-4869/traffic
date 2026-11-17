package com.traffic.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class LaunchTest {
    @Test public void opensWithoutUsagePermissionAndShowsControls() {
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            View root=controller.get().getWindow().getDecorView();
            assertTrue(contains(root,I18n.t(R.string.ui_060)));
            assertTrue(contains(root,I18n.t(R.string.ui_063)));
            assertTrue(contains(root,I18n.t(R.string.ui_064)));
            assertFalse(TrafficApplication.enabled(controller.get()));
        }
    }
    private static boolean contains(View v,String text) {
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText())) return true;
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++) if(contains(group.getChildAt(i),text)) return true;
        }
        return false;
    }
}
