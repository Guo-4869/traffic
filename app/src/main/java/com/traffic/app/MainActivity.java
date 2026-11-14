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
    private LinearLayout root,content,tabs;
    private TextView status;
    private Button refresh;
    private Collector.Result data;
    private int tab, timelineLimit=100;
    private boolean sevenDays,busy;
    private final int ink=Charts.INK,muted=Charts.MUTED,green=Charts.GREEN;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        text(header,"TRAFFIC  /  使用轨迹",12,green,true);
        text(header,"把时间，看清楚。",27,ink,true);
        text(header,"仅记录 App 与时间 · 数据保留 7 天",13,muted,false);
        status=text(header,"正在读取本地记录…",12,muted,false);
        root.addView(header);
        LinearLayout controls=row(); controls.setPadding(dp(16),0,dp(16),0);
        Button access=button("记录设置",()->settings()); controls.addView(access,weight());
        refresh=button("刷新统计",()->refreshData()); controls.addView(refresh,weight());
        root.addView(controls);
        tabs=row(); tabs.setPadding(dp(16),dp(4),dp(16),dp(4)); root.addView(tabs);
        for(int i=0;i<3;i++) {
            final int index=i;
            tabs.addView(button(new String[]{"概览","时间线","日报 / 周报"}[i],()->{tab=index;render();}),weight());
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
        busy=true; refresh.setEnabled(false); status.setText("正在同步系统使用事件…");
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
                    status.setText("读取失败；已有记录未覆盖，请稍后刷新");
                    if(data==null) { content.removeAllViews(); text(content,"暂时无法读取本地记录，请重新打开 App 后重试。",16,ink,false); }
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
            text(card,"你的使用数据，只留在这台设备",19,ink,true);
            text(card,"Traffic 只保存 App 名称、包名、打开与离开前台的时间，并据此统计 App 之间的流动。不会读取页面、输入、通知内容、按键或截图。",14,muted,false);
            card.addView(button("了解并开启记录",()->consent()));
        }
        if(tab==0) overview(content);
        else if(tab==1) timeline();
        else reports();
    }
    private void overview(LinearLayout parent) {
        LinearLayout choices=row();
        choices.addView(button(sevenDays?"今日":"● 今日",()->{sevenDays=false;render();}),weight());
        choices.addView(button(sevenDays?"● 近 7 天":"近 7 天",()->{sevenDays=true;render();}),weight());
        parent.addView(choices);
        long now=System.currentTimeMillis();
        long start=sevenDays?now-Store.RETENTION:Reports.dayStart(now,ZoneId.systemDefault());
        long end=Math.max(start,Math.min(now,data.snapshot().cursor));
        var summary=Reports.summarize(data.snapshot().sessions,data.snapshot().transitions,start,end,data.snapshot().cursor);
        summaryView(parent,summary);
        LinearLayout note=card(parent);
        text(note,"数据说明",16,ink,true);
        text(note,"“打开 / 关闭”指进入 / 离开前台，不代表进程启动或退出。5 秒内同 App 的重复会话会合并。流向记录独立保留，不因会话合并消失。",13,muted,false);
        text(note,"系统历史与后台调度均为尽力模式；日报允许延迟补算。分屏时多个 App 可同时计时，总时长可能大于屏幕使用时长。",13,muted,false);
        if(data.snapshot().captureStart>start) text(note,"本区间包含开启记录前的时间，统计不完整。",13,Color.rgb(151,92,12),false);
        for(Store.Gap gap:data.snapshot().gaps) if(gap.start()<end && gap.end()>start)
            text(note,"缺失 / 不确定："+format(gap.start())+" — "+format(gap.end())+"\n"+gap.reason(),12,Color.rgb(151,92,12),false);
        text(note,"上次拉取至："+(data.snapshot().cursor==0?"尚未开始":format(data.snapshot().cursor)),12,muted,false);
    }
    private void summaryView(LinearLayout parent,Reports.Summary summary) {
        LinearLayout metrics=card(parent);
        long duration=summary.apps().stream().mapToLong(Reports.AppUsage::duration).sum();
        int opens=summary.apps().stream().mapToInt(Reports.AppUsage::opens).sum();
        int switches=summary.flows().stream().mapToInt(Reports.Flow::count).sum();
        text(metrics,Charts.duration(duration),30,green,true);
        text(metrics,"前台累计时长    "+opens+" 次打开    "+switches+" 次流动",13,muted,false);
        LinearLayout ranking=card(parent);
        text(ranking,"时间花在哪里",19,ink,true);
        if(summary.apps().isEmpty()) text(ranking,"暂无使用记录。授权后正常使用其他 App，再返回刷新。",14,muted,false);
        else {
            ranking.addView(new Charts.Bars(this,summary.apps(),data.snapshot().labels));
            for(var a:summary.apps()) text(ranking,name(a.pkg())+"  ·  "+a.opens()+" 次打开"+(a.unknownEnds()>0?"  ·  "+a.unknownEnds()+" 次结束时间未知":""),12,muted,false);
        }
        LinearLayout flows=card(parent);
        text(flows,"App 之间的流动",19,ink,true);
        text(flows,"相邻前台进入关系 · 方向分别计数 · 锁屏切断流向",12,muted,false);
        if(summary.flows().isEmpty()) text(flows,"暂无流动记录。连续使用两个不同 App 后会在这里显示。",14,muted,false);
        else {
            flows.addView(new Charts.Flows(this,summary.flows(),data.snapshot().labels));
            for(var f:summary.flows()) text(flows,name(f.from())+" → "+name(f.to())+"   "+f.count()+" 次",13,ink,false);
        }
    }
    private void timeline() {
        text(content,"最近 7 天的使用时间线",21,ink,true);
        text(content,"原始时间精确到秒；最近 5 秒等待下次拉取确认。",12,muted,false);
        List<SessionEngine.Session> sessions=new ArrayList<>(data.snapshot().sessions);
        sessions.sort(Comparator.comparingLong((SessionEngine.Session s)->s.start).reversed());
        if(sessions.isEmpty()) text(content,"还没有会话记录。",15,muted,false);
        int shown=0;
        for(var s:sessions) {
            if(shown++>=timelineLimit) break;
            LinearLayout card=card(content);
            text(card,name(s.pkg),17,ink,true); text(card,s.pkg,11,muted,false);
            text(card,"打开  "+format(s.start)+(s.clipped?"（保留边界截断）":""),13,ink,false);
            String close=s.end!=null?format(s.end):s.endReason==null?"截至上次拉取仍在前台":"未知，未计入时长";
            text(card,"关闭  "+close,13,ink,false);
            if(s.endReason!=null && !"pause".equals(s.endReason)) text(card,switch(s.endReason){case "screen_off"->"熄屏边界结束";case "lock"->"锁屏边界结束";default->"系统关机、时间变化或历史不连续，结束时间未知";},12,muted,false);
        }
        if(sessions.size()>timelineLimit) content.addView(button("加载更多",()->{timelineLimit+=100;render();}));
    }
    private void reports() {
        text(content,"日报与周报",21,ink,true);
        text(content,"自然日 00:00 分界 · 周一开始 · 自动补算\n过期源数据从报表同步移除，历史周报可能不完整。",12,muted,false);
        List<Store.ReportRow> rows=new ArrayList<>(data.reports());
        rows.sort(Comparator.comparingLong(Store.ReportRow::start).reversed().thenComparing(Store.ReportRow::kind));
        for(var report:rows) {
            LinearLayout card=card(content);
            String title=(report.kind().equals("day")?"日报 · ":"周报 · ")+date(report.start());
            text(card,title,18,ink,true);
            long now=System.currentTimeMillis();
            long start=Math.max(report.start(),Math.max(now-Store.RETENTION,data.snapshot().captureStart));
            long end=Math.max(start,Math.min(report.end(),data.snapshot().cursor));
            var summary=Reports.summarize(data.snapshot().sessions,data.snapshot().transitions,start,end,data.snapshot().cursor);
            text(card,Charts.duration(summary.apps().stream().mapToLong(Reports.AppUsage::duration).sum())+" · "+summary.apps().size()+" 个 App",14,green,true);
            boolean partial=start>report.start() || end<report.end();
            for(var gap:data.snapshot().gaps) if(gap.start()<report.end() && gap.end()>report.start()) partial=true;
            text(card,partial?"进行中 / 数据不完整 · 系统尽力提供":"已汇总 · 系统事件可能有遗漏",12,muted,false);
            card.addView(button("查看可视化",()->{
                LinearLayout detail=column(); detail.setPadding(dp(16),dp(8),dp(16),dp(16)); summaryView(detail,summary);
                ScrollView scroll=new ScrollView(this);scroll.addView(detail);
                new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show();
            }));
        }
        if(rows.isEmpty()) text(content,"开启记录后生成第一份日报。",15,muted,false);
    }
    private void settings() {
        String[] items={TrafficApplication.enabled(this)?"暂停记录":"开启记录","系统使用权限","隐私与统计说明","清空全部本地记录"};
        new AlertDialog.Builder(this).setTitle("记录设置").setItems(items,(dialog,index)->{
            if(index==0) {
                if(TrafficApplication.enabled(this)) {
                    ((TrafficApplication)getApplication()).setEnabled(false);
                    getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("had_access",false).apply();
                    refreshData();
                } else consent();
            } else if(index==1) openUsageSettings();
            else if(index==2) new AlertDialog.Builder(this).setTitle("隐私与统计说明")
                .setMessage("仅本机保存包名、App 名称、使用时间和流向统计。无联网权限，不读取操作内容、页面、键盘、通知或截图。\n\n7 天滚动保留，包含日报和周报的源数据；关闭系统备份和设备迁移。停止运行期间无法准时物理删除，恢复后立即清理。\n\n不运行前台服务或无障碍服务。熄屏不主动轮询；系统启动后台任务后检查并退出仍可能有少量开销，不能承诺绝对零功耗。\n\n流向表示前台进入顺序，不证明用户点击；分屏不等同于唯一焦点。无匹配结束事件时标记未知，不伪造时间。")
                .setPositiveButton("知道了",null).show();
            else new AlertDialog.Builder(this).setTitle("清空本地记录？").setMessage("所有使用会话、流向与报表都会删除，并暂停记录。")
                .setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{
                    ((TrafficApplication)getApplication()).setEnabled(false);
                    getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("had_access",false).apply();
                    TrafficApplication.IO.execute(()->{Collector.clear(getApplicationContext());runOnUiThread(()->refreshData());});
                }).show();
        }).show();
    }
    private void consent() {
        new AlertDialog.Builder(this).setTitle("开启本地使用统计")
            .setMessage("将记录授权后的 App 名称、包名及进入 / 离开前台的时间，用于时长、次数和流动可视化。\n\n不采集页面和操作内容；数据仅保存最近 7 天，不上传。后台可能延迟，无法保证完整不漏。")
            .setNegativeButton("暂不开启",null).setPositiveButton("同意并继续",(dialog,which)->{
                ((TrafficApplication)getApplication()).setEnabled(true);
                if(UsageAccess.granted(this)) refreshData(); else openUsageSettings();
            }).show();
    }
    private void openUsageSettings() {
        try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
        catch(ActivityNotFoundException e) {
            try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
            catch(ActivityNotFoundException ignored) { Toast.makeText(this,"请到系统设置中搜索“使用情况访问权限”",Toast.LENGTH_LONG).show(); }
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
