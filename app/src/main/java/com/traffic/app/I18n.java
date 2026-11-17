package com.traffic.app;

import android.app.LocaleManager;
import android.content.*;
import android.content.res.*;
import android.os.*;
import java.util.Locale;

/** Locale-scoped resources; never changes the process or device default locale. */
final class I18n {
    private static volatile Resources strings;
    private static volatile Locale selected=Locale.ENGLISH;
    static String choice(Context context) {
        if(Build.VERSION.SDK_INT>=33){
            LocaleManager manager=context.getSystemService(LocaleManager.class);
            if(manager!=null){LocaleList locales=manager.getApplicationLocales();return locales.isEmpty()?"system":locales.get(0).getLanguage();}
        }
        return context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("language","system");
    }
    static Context wrap(Context context) {
        String choice=choice(context);
        Locale source="system".equals(choice)?Resources.getSystem().getConfiguration().getLocales().get(0):Locale.forLanguageTag(choice);
        Locale locale="zh".equals(source.getLanguage())?Locale.SIMPLIFIED_CHINESE:Locale.ENGLISH;
        Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
    static void init(Context context) {
        Context localized=wrap(context.getApplicationContext()==null?context:context.getApplicationContext());
        selected=localized.getResources().getConfiguration().getLocales().get(0);strings=localized.getResources();
    }
    static String t(int resource) { if(strings==null)throw new IllegalStateException("Language resources not initialized");return strings.getString(resource); }
    static Locale locale(){return selected;}
    static void set(Context context,String choice){
        if(!choice.equals("system")&&!choice.equals("en")&&!choice.equals("zh"))throw new IllegalArgumentException("Unsupported language");
        context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("language",choice).apply();
        if(Build.VERSION.SDK_INT>=33){LocaleManager manager=context.getSystemService(LocaleManager.class);if(manager!=null)manager.setApplicationLocales(LocaleList.forLanguageTags(choice.equals("system")?"":choice));}
        init(context);
    }
    private I18n(){}
}
