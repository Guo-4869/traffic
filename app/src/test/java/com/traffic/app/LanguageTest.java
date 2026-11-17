package com.traffic.app;

import android.app.*;
import android.content.*;
import android.os.LocaleList;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=Application.class)
public class LanguageTest {
    @Test public void choicePersistsAndDoesNotChangeTrackingOrData() throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("settings",0).edit().putBoolean("enabled",true).commit();
        try(Store store=new Store(app)){var s=new Store.Snapshot();s.cursor=123;s.captureStart=123;store.save(s,java.util.List.of(),123);}
        I18n.set(app,"en");assertEquals("Settings",I18n.t(R.string.ui_063));
        I18n.init(app);assertEquals("en",I18n.choice(app));
        I18n.set(app,"zh");assertEquals("记录设置",I18n.t(R.string.ui_063));
        assertTrue(TrafficApplication.enabled(app));try(Store store=new Store(app)){assertEquals(123,store.load(123).cursor);}
        I18n.set(app,"system");assertEquals("system",I18n.choice(app));
    }
    @Test public void newActivityUsesSelectedEnglishAndChinese(){
        Application app=RuntimeEnvironment.getApplication();
        for(String tag:new String[]{"en","zh"}){
            I18n.set(app,tag);
            try(var controller=Robolectric.buildActivity(MainActivity.class).create()){
                View root=controller.get().getWindow().getDecorView();
                assertTrue(contains(root,tag.equals("en")?"Settings":"记录设置"));
                assertEquals(tag,controller.get().getResources().getConfiguration().getLocales().get(0).getLanguage());
            }
        }
    }
    @Test public void settingsPickerSelectsEnglishWithoutClearingRecords(){
        Application app=RuntimeEnvironment.getApplication();I18n.set(app,"zh");
        try(var controller=Robolectric.buildActivity(MainActivity.class).create()){
            find(controller.get().getWindow().getDecorView(),"记录设置").performClick();
            AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            menu.getListView().performItemClick(null,4,4);
            AlertDialog picker=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(1,picker.getListView().getCheckedItemPosition());
            picker.getListView().performItemClick(null,2,2);assertEquals("en",I18n.choice(app));
        }
    }
    private static View find(View view,String text){
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    @Test @Config(sdk=33) public void nativeAppLanguageAndSystemChoiceStayInSync(){
        Application app=RuntimeEnvironment.getApplication();LocaleManager manager=app.getSystemService(LocaleManager.class);
        I18n.set(app,"zh");assertEquals("zh",manager.getApplicationLocales().get(0).getLanguage());
        manager.setApplicationLocales(LocaleList.forLanguageTags("en"));I18n.init(app);
        assertEquals("en",I18n.choice(app));assertEquals("Settings",I18n.t(R.string.ui_063));
        I18n.set(app,"system");assertTrue(manager.getApplicationLocales().isEmpty());
    }
    private static boolean contains(View view,String text){
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),text))return true;}
        return false;
    }
}
