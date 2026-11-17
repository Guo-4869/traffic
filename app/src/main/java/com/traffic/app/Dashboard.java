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
    Dashboard(Context c,Store.Snapshot data,long now){super(c);I18n.init(c);this.data=data;this.now=now;
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
        TextView date=text(null,day.format(DateTimeFormatter.ofPattern(I18n.t(R.string.ui_018),I18n.locale())),15,Charts.INK,true);
        date.setGravity(Gravity.CENTER);datebar.addView(date,new LayoutParams(0,dp(48),1));
        Button next=button("›",false,()->{day=day.plusDays(1);render();});next.setEnabled(day.isBefore(Instant.ofEpochMilli(now).atZone(zone).toLocalDate()));datebar.addView(next,new LayoutParams(dp(48),dp(48)));addView(datebar);

        boolean today=day.equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate());
        LinearLayout hero=card("01",today?I18n.t(R.string.ui_019):I18n.t(R.string.ui_020),today?I18n.t(R.string.ui_021):I18n.t(R.string.ui_022));
        long total=Insights.total(intervals);
        text(hero,Charts.duration(total),34,Charts.GREEN,true);
        long unlocks=data.unlocks.stream().filter(t->t>=dayStart&&t<Math.max(dayStart,end)).count();
        text(hero,unlocks+I18n.t(R.string.ui_023)+daily.apps().stream().mapToInt(Reports.AppUsage::opens).sum()+I18n.t(R.string.ui_024)+daily.apps().size()+I18n.t(R.string.ui_025),13,Charts.MUTED,false);
        hero.addView(new VisualCharts.Donut(getContext(),daily.apps(),data.labels,palette));
        long appTotal=daily.apps().stream().mapToLong(Reports.AppUsage::duration).sum();
        for(int i=0;i<Math.min(5,daily.apps().size());i++){var a=daily.apps().get(i);legend(hero,name(a.pkg()),a.duration(),appTotal,palette.color(a.pkg()));}
        long others=daily.apps().stream().skip(5).mapToLong(Reports.AppUsage::duration).sum();if(others>0)legend(hero,I18n.t(R.string.ui_026),others,appTotal,VisualCharts.OTHER);
        text(hero,I18n.t(R.string.ui_027),11,Charts.MUTED,false);
        completeness(hero,dayStart,dayEnd);

        LinearLayout selector=row();selector.setPadding(0,dp(12),0,0);
        selector.addView(button(I18n.t(R.string.ui_028),!week,()->{week=false;render();}),new LayoutParams(0,dp(48),1));
        selector.addView(button(I18n.t(R.string.ui_029),week,()->{week=true;render();}),new LayoutParams(0,dp(48),1));addView(selector);
        long rangeStart=week?Reports.weekStart(dayStart,zone):dayStart;
        long rangeEnd=week?Instant.ofEpochMilli(rangeStart).atZone(zone).plusWeeks(1).toInstant().toEpochMilli():dayEnd;
        long effectiveStart=Math.max(rangeStart,Math.max(now-Store.RETENTION,data.captureStart));
        long effectiveEnd=Math.max(effectiveStart,Math.min(rangeEnd,horizon));
        var summary=Reports.summarize(data.sessions,data.transitions,effectiveStart,effectiveEnd,horizon);
        String period=week?I18n.t(R.string.ui_030):I18n.t(R.string.ui_031);
        LinearLayout rank=card("02",I18n.t(R.string.ui_032),period+I18n.t(R.string.ui_033));
        rank.addView(new VisualCharts.Bars(getContext(),summary.apps(),data.labels,palette));completeness(rank,rangeStart,rangeEnd);
        LinearLayout flow=card("03",I18n.t(R.string.ui_034),period+I18n.t(R.string.ui_035));
        flow.addView(button(fullFlow?I18n.t(R.string.ui_036):I18n.t(R.string.ui_037),false,()->{fullFlow=!fullFlow;render();}),new LayoutParams(-1,dp(48)));
        flow.addView(new VisualCharts.Sankey(getContext(),summary.flows(),data.labels,palette,fullFlow));
        text(flow,I18n.t(R.string.ui_038),11,Charts.MUTED,false);
        text(flow,I18n.t(R.string.ui_039),11,Charts.MUTED,false);
        for(var f:summary.flows().subList(0,Math.min(5,summary.flows().size())))text(flow,name(f.from())+" → "+name(f.to())+"  ·  "+f.count()+I18n.t(R.string.ui_040),12,Charts.INK,false);

        List<String> apps=new ArrayList<>();for(var a:daily.apps())apps.add(a.pkg());
        LinearLayout gantt=card("04",I18n.t(R.string.ui_041),day.format(DateTimeFormatter.ofPattern(I18n.t(R.string.ui_042),I18n.locale()))+I18n.t(R.string.ui_043));
        gantt.addView(new VisualCharts.Gantt(getContext(),intervals,apps,dayStart,dayEnd,data.labels,palette));
        text(gantt,I18n.t(R.string.ui_044),11,Charts.MUTED,false);
        LinearLayout heat=card("05",I18n.t(R.string.ui_045),day.format(DateTimeFormatter.ofPattern(I18n.t(R.string.ui_042),I18n.locale()))+I18n.t(R.string.ui_046));
        // Keep 24 columns readable on narrow phones; left and right labels scroll together.
        HorizontalScrollView horizontal=new HorizontalScrollView(getContext());horizontal.setHorizontalScrollBarEnabled(true);
        VisualCharts.Heatmap map=new VisualCharts.Heatmap(getContext(),Insights.hourly(intervals,zone),apps,data.labels,palette);
        horizontal.addView(map,new android.widget.FrameLayout.LayoutParams(dp(610),-2));heat.addView(horizontal);
        text(heat,I18n.t(R.string.ui_047),11,Charts.MUTED,false);
        LinearLayout note=card(I18n.t(R.string.ui_048),I18n.t(R.string.ui_049),I18n.t(R.string.ui_050));
        text(note,I18n.t(R.string.ui_051),12,Charts.MUTED,false);
        if(data.unlockStart>dayStart)text(note,I18n.t(R.string.ui_052)+Instant.ofEpochMilli(data.unlockStart).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))+I18n.t(R.string.ui_053),12,0xFF986420,false);
        text(note,I18n.t(R.string.ui_054),12,Charts.MUTED,false);
        text(note,I18n.t(R.string.ui_055)+(data.cursor==0?I18n.t(R.string.ui_056):Instant.ofEpochMilli(data.cursor).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))),11,Charts.MUTED,false);
    }
    void completeness(LinearLayout parent,long start,long end){boolean partial=data.captureStart==0||data.captureStart>start||start<now-Store.RETENTION||data.cursor<end;
        for(var gap:data.gaps)if(gap.start()<end&&gap.end()>start)partial=true;
        text(parent,partial?I18n.t(R.string.ui_057):I18n.t(R.string.ui_058),11,partial?0xFF986420:Charts.GREEN,false);
    }
    void legend(LinearLayout parent,String name,long value,long total,int color){LinearLayout row=row();View dot=new View(getContext());GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(4));dot.setBackground(bg);LayoutParams d=new LayoutParams(dp(9),dp(9));d.gravity=Gravity.CENTER_VERTICAL;d.setMargins(0,0,dp(10),0);row.addView(dot,d);
        TextView label=text(null,name,13,Charts.INK,false);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(label,new LayoutParams(0,-2,1));text(row,Charts.duration(value)+"  ·  "+String.format(I18n.locale(),"%.0f%%",total==0?0:100.0*value/total),12,Charts.MUTED,false);parent.addView(row);}
    String name(String pkg){return data.labels.getOrDefault(pkg,pkg);}
    LinearLayout card(String index,String title,String caption){LinearLayout box=new LinearLayout(getContext());box.setOrientation(VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(16));GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setCornerRadius(dp(22));background.setStroke(dp(1),0xFFE6ECEF);box.setBackground(background);LayoutParams p=new LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);addView(box,p);text(box,index+"  /  "+title,18,Charts.INK,true);text(box,caption,12,Charts.MUTED,false);return box;}
    TextView text(LinearLayout parent,String s,int size,int color,boolean bold){TextView t=new TextView(getContext());t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(4),0,dp(6));t.setLineSpacing(dp(2),1);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);if(parent!=null)parent.addView(t);return t;}
    Button button(String s,boolean selected,Runnable click){Button b=new Button(getContext());b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(selected?Color.WHITE:Charts.GREEN);GradientDrawable bg=new GradientDrawable();bg.setColor(selected?Charts.GREEN:0xFFEAF1EF);bg.setCornerRadius(dp(12));b.setBackground(bg);b.setOnClickListener(v->click.run());b.setContentDescription(s);return b;}
    LinearLayout row(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(HORIZONTAL);return l;}
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
