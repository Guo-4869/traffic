package com.traffic.app;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import com.traffic.core.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** The five visual panels share one date, one source snapshot and a stable App palette. */
@SuppressLint("ViewConstructor") // Programmatic data-bound view; not inflated from XML.
final class Dashboard extends LinearLayout {
    final Store.Snapshot data;
    final long now;
    final ZoneId zone=ZoneId.systemDefault();
    final VisualCharts.Palette palette;
    LocalDate day;
    boolean week,fullFlow;
    Dashboard(Context c,Store.Snapshot data,long now){super(c);this.data=data;this.now=now;
        setOrientation(VERTICAL);day=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        Set<String> packages=new TreeSet<>();for(var s:data.sessions)packages.add(s.pkg);for(var t:data.transitions){packages.add(t.from());packages.add(t.to());}
        palette=new VisualCharts.Palette(packages);render();
    }
    void render(){removeAllViews();
        long dayStart=day.atStartOfDay(zone).toInstant().toEpochMilli(),dayEnd=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long horizon=Math.min(now,data.cursor),end=Math.min(dayEnd,horizon);
        var daily=Reports.summarize(data.sessions,data.transitions,dayStart,Math.max(dayStart,end),horizon);
        var intervals=Insights.intervals(data.sessions,dayStart,Math.max(dayStart,end),horizon);
        LinearLayout datebar=row();
        Button previous=button("‹",false,()->{day=day.minusDays(1);render();});
        LocalDate first=Instant.ofEpochMilli(now-Store.RETENTION).atZone(zone).toLocalDate();previous.setEnabled(day.isAfter(first));
        datebar.addView(previous,new LayoutParams(dp(48),dp(48)));
        TextView date=text(null,day.format(DateTimeFormatter.ofPattern("MM月dd日  EEEE",Locale.CHINESE)),15,Charts.INK,true);
        date.setGravity(Gravity.CENTER);datebar.addView(date,new LayoutParams(0,dp(48),1));
        Button next=button("›",false,()->{day=day.plusDays(1);render();});next.setEnabled(day.isBefore(Instant.ofEpochMilli(now).atZone(zone).toLocalDate()));datebar.addView(next,new LayoutParams(dp(48),dp(48)));addView(datebar);

        boolean today=day.equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate());
        LinearLayout hero=card("01",today?"今日总览":"当日总览",today?"今天的时间，落在了哪里":"所选日期的使用分布");
        long total=Insights.total(intervals);
        text(hero,Charts.duration(total),34,Charts.GREEN,true);
        long unlocks=data.unlocks.stream().filter(t->t>=dayStart&&t<Math.max(dayStart,end)).count();
        text(hero,unlocks+" 次解锁   ·   "+daily.apps().stream().mapToInt(Reports.AppUsage::opens).sum()+" 次打开   ·   "+daily.apps().size()+" 个 App",13,Charts.MUTED,false);
        hero.addView(new VisualCharts.Donut(getContext(),daily.apps(),data.labels,palette));
        long appTotal=daily.apps().stream().mapToLong(Reports.AppUsage::duration).sum();
        for(int i=0;i<Math.min(5,daily.apps().size());i++){var a=daily.apps().get(i);legend(hero,name(a.pkg()),a.duration(),appTotal,palette.color(a.pkg()));}
        long others=daily.apps().stream().skip(5).mapToLong(Reports.AppUsage::duration).sum();if(others>0)legend(hero,"其他 App",others,appTotal,VisualCharts.OTHER);
        text(hero,"总使用时长去除重叠区间；环形图按各 App 累计时长计算占比。",11,Charts.MUTED,false);
        completeness(hero,dayStart,dayEnd);

        LinearLayout selector=row();selector.setPadding(0,dp(12),0,0);
        selector.addView(button("按日查看",!week,()->{week=false;render();}),new LayoutParams(0,dp(48),1));
        selector.addView(button("按周查看",week,()->{week=true;render();}),new LayoutParams(0,dp(48),1));addView(selector);
        long rangeStart=week?Reports.weekStart(dayStart,zone):dayStart;
        long rangeEnd=week?Instant.ofEpochMilli(rangeStart).atZone(zone).plusWeeks(1).toInstant().toEpochMilli():dayEnd;
        long effectiveStart=Math.max(rangeStart,Math.max(now-Store.RETENTION,data.captureStart));
        long effectiveEnd=Math.max(effectiveStart,Math.min(rangeEnd,horizon));
        var summary=Reports.summarize(data.sessions,data.transitions,effectiveStart,effectiveEnd,horizon);
        String period=week?"本地自然周 · 周一至周日":"所选自然日 · 00:00 至次日 00:00";
        LinearLayout rank=card("02","App 排行",period+" · 已排除桌面");
        rank.addView(new VisualCharts.Bars(getContext(),summary.apps(),data.labels,palette));completeness(rank,rangeStart,rangeEnd);
        LinearLayout flow=card("03","流转桑基图",period+" · 带宽代表切换次数");
        flow.addView(button(fullFlow?"收起为主要 App":"展开全部 App 节点",false,()->{fullFlow=!fullFlow;render();}),new LayoutParams(-1,dp(48)));
        flow.addView(new VisualCharts.Sankey(getContext(),summary.flows(),data.labels,palette,fullFlow));
        text(flow,"左侧切出、右侧切入，同一 App 可出现在两侧。默认各侧前 8 个 App 单独显示，其余合并；可展开全部节点。",11,Charts.MUTED,false);
        text(flow,"忽略桌面后，30 秒内的 A → 桌面 → B 计为 A → B；跨熄屏、锁屏不连接。",11,Charts.MUTED,false);
        for(var f:summary.flows().subList(0,Math.min(5,summary.flows().size())))text(flow,name(f.from())+" → "+name(f.to())+"  ·  "+f.count()+" 次",12,Charts.INK,false);

        List<String> apps=new ArrayList<>();for(var a:daily.apps())apps.add(a.pkg());
        LinearLayout gantt=card("04","时间轴甘特图",day.format(DateTimeFormatter.ofPattern("MM月dd日"))+" · 同一 App 保持同一颜色");
        gantt.addView(new VisualCharts.Gantt(getContext(),intervals,apps,dayStart,dayEnd,data.labels,palette));
        text(gantt,"仅绘制已知的实际前台区间，短暂离开的间隔留空；结束未知的部分不推测。",11,Charts.MUTED,false);
        LinearLayout heat=card("05","每小时使用强度",day.format(DateTimeFormatter.ofPattern("MM月dd日"))+" · App × 小时");
        // Keep 24 columns readable on narrow phones; left and right labels scroll together.
        HorizontalScrollView horizontal=new HorizontalScrollView(getContext());horizontal.setHorizontalScrollBarEnabled(true);
        VisualCharts.Heatmap map=new VisualCharts.Heatmap(getContext(),Insights.hourly(intervals,zone),apps,data.labels,palette);
        horizontal.addView(map,new android.widget.FrameLayout.LayoutParams(dp(610),-2));heat.addView(horizontal);
        text(heat,"横向滑动查看全天，轻点单元格查看时长。颜色越深表示使用越久，空白表示无已记录时长。",11,Charts.MUTED,false);
        LinearLayout note=card("说明","数据边界","仅本机保存 · 最近 7 天 · 不记录内容");
        text(note,"解锁次数只统计已记录的系统解锁事件；系统事件缺失或未设锁屏时不推断。",12,Charts.MUTED,false);
        if(data.unlockStart>dayStart)text(note,"解锁计数从 "+Instant.ofEpochMilli(data.unlockStart).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))+" 开始；更早的解锁次数未记录。",12,0xFF986420,false);
        text(note,"系统事件与后台执行按尽力模式提供。当前日 / 周、首次记录区间及超出 7 天保留窗口的区间均可能不完整。",12,Charts.MUTED,false);
        text(note,"上次拉取至 "+(data.cursor==0?"尚未开始":Instant.ofEpochMilli(data.cursor).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))),11,Charts.MUTED,false);
    }
    void completeness(LinearLayout parent,long start,long end){boolean partial=data.captureStart==0||data.captureStart>start||start<now-Store.RETENTION||data.cursor<end;
        for(var gap:data.gaps)if(gap.start()<end&&gap.end()>start)partial=true;
        text(parent,partial?"◌  进行中或数据不完整；不把缺失视为零使用":"✓  已汇总 · 系统事件可能有遗漏",11,partial?0xFF986420:Charts.GREEN,false);
    }
    void legend(LinearLayout parent,String name,long value,long total,int color){LinearLayout row=row();View dot=new View(getContext());GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(4));dot.setBackground(bg);LayoutParams d=new LayoutParams(dp(9),dp(9));d.gravity=Gravity.CENTER_VERTICAL;d.setMargins(0,0,dp(10),0);row.addView(dot,d);
        TextView label=text(null,name,13,Charts.INK,false);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(label,new LayoutParams(0,-2,1));text(row,Charts.duration(value)+"  ·  "+String.format(Locale.CHINA,"%.0f%%",total==0?0:100.0*value/total),12,Charts.MUTED,false);parent.addView(row);}
    String name(String pkg){return data.labels.getOrDefault(pkg,pkg);}
    LinearLayout card(String index,String title,String caption){LinearLayout box=new LinearLayout(getContext());box.setOrientation(VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(16));GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setCornerRadius(dp(22));background.setStroke(dp(1),0xFFE6ECEF);box.setBackground(background);LayoutParams p=new LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);addView(box,p);text(box,index+"  /  "+title,18,Charts.INK,true);text(box,caption,12,Charts.MUTED,false);return box;}
    TextView text(LinearLayout parent,String s,int size,int color,boolean bold){TextView t=new TextView(getContext());t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(4),0,dp(6));t.setLineSpacing(dp(2),1);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);if(parent!=null)parent.addView(t);return t;}
    Button button(String s,boolean selected,Runnable click){Button b=new Button(getContext());b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(selected?Color.WHITE:Charts.GREEN);GradientDrawable bg=new GradientDrawable();bg.setColor(selected?Charts.GREEN:0xFFEAF1EF);bg.setCornerRadius(dp(12));b.setBackground(bg);b.setOnClickListener(v->click.run());b.setContentDescription(s);return b;}
    LinearLayout row(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(HORIZONTAL);return l;}
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
