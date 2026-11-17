package com.traffic.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.traffic.core.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(I18n.wrap(base)); }
    private LinearLayout root,content,tabs;
    private TextView status;
    private Button refresh;
    private Collector.Result data;
    private int tab, timelineLimit=100;
    private boolean sevenDays,busy;
    private final int ink=Charts.INK,muted=Charts.MUTED,green=Charts.GREEN;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        I18n.init(this);
        if(state!=null) { tab=state.getInt("tab"); sevenDays=state.getBoolean("sevenDays"); }
        root=column(); root.setBackgroundColor(Color.rgb(245,247,248));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                var bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header=column(); header.setPadding(dp(20),dp(18),dp(20),dp(8));
        text(header,I18n.t(R.string.ui_059),12,green,true);
        text(header,I18n.t(R.string.ui_060),27,ink,true);
        text(header,I18n.t(R.string.ui_061),13,muted,false);
        status=text(header,I18n.t(R.string.ui_062),12,muted,false);
        root.addView(header);
        LinearLayout controls=row(); controls.setPadding(dp(16),0,dp(16),0);
        Button access=button(I18n.t(R.string.ui_063),()->settings()); controls.addView(access,weight());
        refresh=button(I18n.t(R.string.ui_064),()->refreshData()); controls.addView(refresh,weight());
        root.addView(controls);
        tabs=row(); tabs.setPadding(dp(16),dp(4),dp(16),dp(4)); root.addView(tabs);
        for(int i=0;i<3;i++) {
            final int index=i;
            tabs.addView(button(new String[]{I18n.t(R.string.ui_065),I18n.t(R.string.ui_066),I18n.t(R.string.ui_067)}[i],()->{tab=index;render();}),weight());
        }
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        content=column(); content.setPadding(dp(20),dp(8),dp(20),dp(28));
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); root.requestApplyInsets();
    }
    @Override protected void onResume() { super.onResume(); refreshData(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putInt("tab",tab); out.putBoolean("sevenDays",sevenDays);
    }
    private void refreshData() {
        if(busy) return;
        busy=true; refresh.setEnabled(false); status.setText(I18n.t(R.string.ui_068));
        Context app=getApplicationContext();
        TrafficApplication.IO.execute(()->{
            try {
                boolean granted=UsageAccess.granted(app) && TrafficApplication.enabled(app);
                var prefs=app.getSharedPreferences("settings",MODE_PRIVATE);
                if(granted && !prefs.getBoolean("had_access",false) && DeviceGate.canCollect(app)) Collector.begin(app);
                prefs.edit().putBoolean("had_access",granted).apply();
                Collector.Result result=Collector.refresh(app);
                runOnUiThread(()->{
                    if(isDestroyed()) return;
                    data=result; busy=false; refresh.setEnabled(true); status.setText(result.message()); render();
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(isDestroyed()) return;
                    busy=false; refresh.setEnabled(true);
                    status.setText(I18n.t(R.string.ui_069));
                    if(data==null) { content.removeAllViews(); text(content,I18n.t(R.string.ui_070),16,ink,false); }
                });
            }
        });
    }
    private void render() {
        content.removeAllViews();
        for(int i=0;i<tabs.getChildCount();i++) tabs.getChildAt(i).setSelected(i==tab);
        if(data==null) return;
        if(!TrafficApplication.enabled(this) || !UsageAccess.granted(this)) {
            LinearLayout card=card(content);
            text(card,I18n.t(R.string.ui_071),19,ink,true);
            text(card,I18n.t(R.string.ui_072),14,muted,false);
            card.addView(button(I18n.t(R.string.ui_073),()->consent()));
        }
        if(tab==0) overview(content);
        else if(tab==1) timeline();
        else reports();
    }
    private void overview(LinearLayout parent) {
        parent.addView(new Dashboard(this,data.snapshot(),System.currentTimeMillis()));
    }
    private void summaryView(LinearLayout parent,Reports.Summary summary) {
        LinearLayout metrics=card(parent);
        long duration=summary.apps().stream().mapToLong(Reports.AppUsage::duration).sum();
        int opens=summary.apps().stream().mapToInt(Reports.AppUsage::opens).sum();
        int switches=summary.flows().stream().mapToInt(Reports.Flow::count).sum();
        text(metrics,Charts.duration(duration),30,green,true);
        text(metrics,I18n.t(R.string.ui_074)+opens+I18n.t(R.string.ui_075)+switches+I18n.t(R.string.ui_076),13,muted,false);
        LinearLayout ranking=card(parent);
        text(ranking,I18n.t(R.string.ui_077),19,ink,true);
        if(summary.apps().isEmpty()) text(ranking,I18n.t(R.string.ui_078),14,muted,false);
        else {
            ranking.addView(new VisualCharts.Bars(this,summary.apps(),data.snapshot().labels,new VisualCharts.Palette(data.snapshot().labels.keySet())));
            for(var a:summary.apps()) text(ranking,name(a.pkg())+"  ·  "+a.opens()+I18n.t(R.string.ui_079)+(a.unknownEnds()>0?"  ·  "+a.unknownEnds()+I18n.t(R.string.ui_080):""),12,muted,false);
        }
        LinearLayout flows=card(parent);
        text(flows,I18n.t(R.string.ui_081),19,ink,true);
        text(flows,I18n.t(R.string.ui_082),12,muted,false);
        if(summary.flows().isEmpty()) text(flows,I18n.t(R.string.ui_083),14,muted,false);
        else {
            flows.addView(new VisualCharts.Sankey(this,summary.flows(),data.snapshot().labels,new VisualCharts.Palette(data.snapshot().labels.keySet())));
            for(var f:summary.flows()) text(flows,name(f.from())+" → "+name(f.to())+"   "+f.count()+I18n.t(R.string.ui_040),13,ink,false);
        }
    }
    private void timeline() {
        text(content,I18n.t(R.string.ui_084),21,ink,true);
        text(content,I18n.t(R.string.ui_085),12,muted,false);
        List<SessionEngine.Session> sessions=new ArrayList<>(data.snapshot().sessions);
        sessions.sort(Comparator.comparingLong((SessionEngine.Session s)->s.start).reversed());
        if(sessions.isEmpty()) text(content,I18n.t(R.string.ui_086),15,muted,false);
        int shown=0;
        for(var s:sessions) {
            if(shown++>=timelineLimit) break;
            LinearLayout card=card(content);
            text(card,name(s.pkg),17,ink,true); text(card,s.pkg,11,muted,false);
            text(card,I18n.t(R.string.ui_087)+format(s.start)+(s.clipped?I18n.t(R.string.ui_088):""),13,ink,false);
            String close=s.end!=null?format(s.end):s.endReason==null?I18n.t(R.string.ui_089):I18n.t(R.string.ui_090);
            text(card,I18n.t(R.string.ui_091)+close,13,ink,false);
            if(s.endReason!=null && !"pause".equals(s.endReason)) text(card,switch(s.endReason){case "screen_off"->I18n.t(R.string.ui_092);case "lock"->I18n.t(R.string.ui_093);default->I18n.t(R.string.ui_094);},12,muted,false);
        }
        if(sessions.size()>timelineLimit) content.addView(button(I18n.t(R.string.ui_095),()->{timelineLimit+=100;render();}));
    }
    private void reports() {
        text(content,I18n.t(R.string.ui_096),21,ink,true);
        text(content,I18n.t(R.string.ui_097),12,muted,false);
        List<Store.ReportRow> rows=new ArrayList<>(data.reports());
        rows.sort(Comparator.comparingLong(Store.ReportRow::start).reversed().thenComparing(Store.ReportRow::kind));
        for(var report:rows) {
            LinearLayout card=card(content);
            String title=(report.kind().equals("day")?I18n.t(R.string.ui_098):I18n.t(R.string.ui_099))+date(report.start());
            text(card,title,18,ink,true);
            long now=System.currentTimeMillis();
            long start=Math.max(report.start(),Math.max(now-Store.RETENTION,data.snapshot().captureStart));
            long end=Math.max(start,Math.min(report.end(),data.snapshot().cursor));
            var summary=Reports.summarize(data.snapshot().sessions,data.snapshot().transitions,start,end,data.snapshot().cursor);
            text(card,Charts.duration(summary.apps().stream().mapToLong(Reports.AppUsage::duration).sum())+" · "+summary.apps().size()+I18n.t(R.string.ui_025),14,green,true);
            boolean partial=start>report.start() || end<report.end();
            for(var gap:data.snapshot().gaps) if(gap.start()<report.end() && gap.end()>report.start()) partial=true;
            text(card,partial?I18n.t(R.string.ui_100):I18n.t(R.string.ui_101),12,muted,false);
            card.addView(button(I18n.t(R.string.ui_102),()->{
                LinearLayout detail=column(); detail.setPadding(dp(16),dp(8),dp(16),dp(16)); summaryView(detail,summary);
                ScrollView scroll=new ScrollView(this);scroll.addView(detail);
                new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton(I18n.t(R.string.ui_103),null).show();
            }));
        }
        if(rows.isEmpty()) text(content,I18n.t(R.string.ui_104),15,muted,false);
    }
    private void settings() {
        String[] items={TrafficApplication.enabled(this)?I18n.t(R.string.ui_105):I18n.t(R.string.ui_106),I18n.t(R.string.ui_107),I18n.t(R.string.ui_108),I18n.t(R.string.ui_109),getString(R.string.language)};
        new AlertDialog.Builder(this).setTitle(I18n.t(R.string.ui_063)).setItems(items,(dialog,index)->{
            if(index==0) {
                if(TrafficApplication.enabled(this)) {
                    ((TrafficApplication)getApplication()).setEnabled(false);
                    getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("had_access",false).apply();
                    refreshData();
                } else consent();
            } else if(index==1) openUsageSettings();
            else if(index==2) new AlertDialog.Builder(this).setTitle(I18n.t(R.string.ui_108))
                .setMessage(I18n.t(R.string.ui_110))
                .setPositiveButton(I18n.t(R.string.ui_111),null).show();
            else if(index==4) language();
            else new AlertDialog.Builder(this).setTitle(I18n.t(R.string.ui_112)).setMessage(I18n.t(R.string.ui_113))
                .setNegativeButton(I18n.t(R.string.ui_114),null).setPositiveButton(I18n.t(R.string.ui_115),(d,w)->{
                    ((TrafficApplication)getApplication()).setEnabled(false);
                    getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("had_access",false).apply();
                    TrafficApplication.IO.execute(()->{Collector.clear(getApplicationContext());runOnUiThread(()->refreshData());});
                }).show();
        }).show();
    }
    private void consent() {
        new AlertDialog.Builder(this).setTitle(I18n.t(R.string.ui_116))
            .setMessage(I18n.t(R.string.ui_117))
            .setNegativeButton(I18n.t(R.string.ui_118),null).setPositiveButton(I18n.t(R.string.ui_119),(dialog,which)->{
                ((TrafficApplication)getApplication()).setEnabled(true);
                if(UsageAccess.granted(this)) refreshData(); else openUsageSettings();
            }).show();
    }
    private void language() {
        String[] choices={"system","zh","en"};String selected=I18n.choice(this);int checked=0;
        for(int i=0;i<choices.length;i++)if(choices[i].equals(selected))checked=i;
        new AlertDialog.Builder(this).setTitle(R.string.language)
            .setSingleChoiceItems(new String[]{getString(R.string.system_language),"简体中文","English"},checked,(dialog,index)->{
                dialog.dismiss();I18n.set(this,choices[index]);if(Build.VERSION.SDK_INT<33)recreate();
            }).setNegativeButton(I18n.t(R.string.ui_114),null).show();
    }
    private void openUsageSettings() {
        try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
        catch(ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
            catch(ActivityNotFoundException ignored) { Toast.makeText(this,I18n.t(R.string.ui_120),Toast.LENGTH_LONG).show(); }
        }
    }
    private String name(String pkg) { return data.snapshot().labels.getOrDefault(pkg,pkg); }
    private String format(long t) { return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")); }
    private String date(long t) { return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd")); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0,-2,1); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout l=column();l.setPadding(dp(16),dp(14),dp(16),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));l.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,dp(2));parent.addView(l,p);return l;
    }
    private TextView text(LinearLayout parent,String value,int sp,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(6));
        v.setLineSpacing(dp(3),1);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(v);return v;
    }
    private Button button(String label,Runnable action) {
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(green);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
}
