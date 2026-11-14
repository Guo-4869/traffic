package com.traffic.app;

import android.app.*;
import android.graphics.*;
import android.view.View;
import com.traffic.core.*;
import com.traffic.core.SessionEngine.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

/** Only synthetic fixtures are rendered; no real phone usage is uploaded or captured. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w411dp-h891dp-mdpi",application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ChartRenderTest {
    @Test public void rendersAllFiveNativeChartsAndConservesSankeyWeights() throws Exception {
        try(var controller=Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity=controller.get();activity.setTheme(com.traffic.app.R.style.AppTheme);
            ZoneId zone=ZoneId.systemDefault();LocalDate date=LocalDate.of(2026,11,14);
            long day=date.atStartOfDay(zone).toInstant().toEpochMilli(),now=day+23*3600000L;
            var s=new Store.Snapshot();s.captureStart=day;s.unlockStart=day;s.cursor=now;
            String[] packages={"demo.reader","demo.messages","demo.music","demo.browser","demo.maps","demo.camera","demo.mail"};
            String[] names={"阅读","消息","音乐","浏览器","地图","相机","邮箱"};
            for(int i=0;i<packages.length;i++)s.labels.put(packages[i],names[i]);
            for(int i=0;i<24;i++){
                String pkg=packages[i%packages.length];long start=day+(7*60+i*35)*60000L;
                Session item=new Session(pkg,start);item.end=start+(i%4+1)*6*60000L;item.activeSince=null;item.endReason="pause";item.spans.add(new Span(start,item.end));s.sessions.add(item);
                if(i>0)s.transitions.add(new Transition(packages[(i-1)%packages.length],pkg,start));
                if(i%3==0)s.unlocks.add(start);
            }
            for(int i=0;i<18;i++)s.transitions.add(new Transition(packages[i%3],packages[(i*2+3)%packages.length],day+(9*60+i*20)*60000L));
            var summary=Reports.summarize(s.sessions,s.transitions,day,now,now);
            var colors=new VisualCharts.Palette(s.labels.keySet());
            VisualCharts.Sankey sankey=new VisualCharts.Sankey(activity,summary.flows(),s.labels,colors);
            int original=summary.flows().stream().mapToInt(Reports.Flow::count).sum();
            assertEquals(original,sankey.edges.stream().mapToInt(VisualCharts.Sankey.Edge::value).sum());
            assertEquals(original,sankey.left.values().stream().mapToInt(v->v).sum());
            assertEquals(original,sankey.right.values().stream().mapToInt(v->v).sum());
            File out=new File("build/reports/visuals");assertTrue(out.isDirectory()||out.mkdirs());
            Dashboard dashboard=new Dashboard(activity,s,now);
            render(dashboard,411,new File(out,"dashboard.png"));
            dashboard.week=true;dashboard.render();render(dashboard,411,new File(out,"weekly.png"));
            sankey.setPadding(0,0,0,0);render(sankey,375,new File(out,"sankey.png"));
            Dashboard empty=new Dashboard(activity,new Store.Snapshot(),now);render(empty,411,new File(out,"empty.png"));
        }
    }
    @Test public void sankeyOtherNodePreservesAllSwitchCounts() {
        Application app=RuntimeEnvironment.getApplication();List<Reports.Flow> flows=new ArrayList<>();Map<String,String> names=new HashMap<>();
        for(int i=0;i<20;i++){flows.add(new Reports.Flow("source"+i,"dest"+(19-i),i+1));names.put("source"+i,"源"+i);names.put("dest"+(19-i),"目标"+i);}
        var chart=new VisualCharts.Sankey(app,flows,names,new VisualCharts.Palette(names.keySet()));
        assertEquals(210,chart.edges.stream().mapToInt(VisualCharts.Sankey.Edge::value).sum());
        assertTrue(chart.left.containsKey(VisualCharts.Sankey.OTHERS));assertTrue(chart.right.containsKey(VisualCharts.Sankey.OTHERS));
        assertTrue(chart.left.size()<=9);assertTrue(chart.right.size()<=9);
        var full=new VisualCharts.Sankey(app,flows,names,new VisualCharts.Palette(names.keySet()),true);
        assertEquals(20,full.left.size());assertEquals(20,full.right.size());
        assertFalse(full.left.containsKey(VisualCharts.Sankey.OTHERS));
        assertEquals(210,full.edges.stream().mapToInt(VisualCharts.Sankey.Edge::value).sum());
    }
    private static void render(View view,int width,File target) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        assertTrue(view.getMeasuredHeight()>0);view.layout(0,0,width,view.getMeasuredHeight());
        Bitmap bitmap=Bitmap.createBitmap(width,view.getMeasuredHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xFFF5F7F8);view.draw(canvas);
        int colored=0;for(int y=0;y<bitmap.getHeight();y+=7)for(int x=0;x<width;x+=7){int p=bitmap.getPixel(x,y);if(Color.green(p)>Color.red(p)+15)colored++;}
        assertTrue("Expected visible chart or UI accent pixels",colored>5);
        try(FileOutputStream out=new FileOutputStream(target)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
}
