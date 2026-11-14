package com.traffic.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.traffic.core.Reports;
import java.util.*;

final class Charts {
    static final int GREEN=Color.rgb(20,108,91), INK=Color.rgb(25,42,43), MUTED=Color.rgb(91,109,111);
    static String duration(long ms) {
        long seconds=ms/1000;
        if (seconds<60) return seconds+" 秒";
        long minutes=seconds/60;
        return minutes<60 ? minutes+" 分" : minutes/60+" 小时 "+minutes%60+" 分";
    }
    static final class Bars extends View {
        private final List<Reports.AppUsage> apps;
        private final Map<String,String> labels;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d, textScale;
        Bars(Context c,List<Reports.AppUsage> apps,Map<String,String> labels) {
            super(c); this.apps=apps.subList(0,Math.min(8,apps.size())); this.labels=labels;
            d=getResources().getDisplayMetrics().density;
            textScale=getResources().getDisplayMetrics().scaledDensity;
            StringBuilder description=new StringBuilder("App 使用时长排行。");
            for (var a:this.apps) description.append(labels.getOrDefault(a.pkg(),a.pkg())).append(' ').append(duration(a.duration())).append('，');
            setContentDescription(description.toString());
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onMeasure(int w,int h) { setMeasuredDimension(MeasureSpec.getSize(w),(int)(Math.max(1,apps.size())*64*d)); }
        @Override protected void onDraw(Canvas canvas) {
            float width=getWidth(), y=0;
            long maximum=apps.stream().mapToLong(Reports.AppUsage::duration).max().orElse(1);
            for (var a:apps) {
                paint.setColor(INK); paint.setTextSize(13*textScale); paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(shorten(paint,labels.getOrDefault(a.pkg(),a.pkg()),width*.56f),0,y+18*d,paint);
                paint.setTextAlign(Paint.Align.RIGHT); paint.setColor(MUTED);
                canvas.drawText(duration(a.duration()),width,y+18*d,paint);
                paint.setTextAlign(Paint.Align.LEFT); paint.setColor(Color.rgb(230,239,236));
                canvas.drawRoundRect(0,y+29*d,width,y+39*d,5*d,5*d,paint);
                paint.setColor(GREEN);
                canvas.drawRoundRect(0,y+29*d,Math.max(2*d,width*a.duration()/Math.max(1,maximum)),y+39*d,5*d,5*d,paint);
                y+=64*d;
            }
        }
    }
    static final class Flows extends View {
        private final List<Reports.Flow> flows;
        private final Map<String,String> labels;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d, textScale;
        Flows(Context c,List<Reports.Flow> flows,Map<String,String> labels) {
            super(c); this.flows=flows.subList(0,Math.min(6,flows.size())); this.labels=labels;
            d=getResources().getDisplayMetrics().density; textScale=getResources().getDisplayMetrics().scaledDensity;
            StringBuilder description=new StringBuilder("App 前台流向。");
            for(var f:this.flows) description.append(labels.getOrDefault(f.from(),f.from())).append(" 到 ").append(labels.getOrDefault(f.to(),f.to())).append(' ').append(f.count()).append(" 次，");
            setContentDescription(description.toString()); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onMeasure(int w,int h) { setMeasuredDimension(MeasureSpec.getSize(w),(int)(Math.max(1,flows.size())*58*d)); }
        @Override protected void onDraw(Canvas canvas) {
            int maximum=flows.stream().mapToInt(Reports.Flow::count).max().orElse(1);
            float width=getWidth(), left=width*.29f,right=width*.71f,y=26*d;
            for(var f:flows) {
                paint.setColor(INK); paint.setTextSize(12*textScale); paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(shorten(paint,labels.getOrDefault(f.from(),f.from()),left-10*d),0,y+4*d,paint);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(shorten(paint,labels.getOrDefault(f.to(),f.to()),width-right-10*d),width,y+4*d,paint);
                paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.rgb(117,181,163));
                paint.setStrokeWidth((2+6f*f.count()/maximum)*d);
                Path path=new Path(); path.moveTo(left,y); path.cubicTo(width*.43f,y+12*d,width*.57f,y+12*d,right,y); canvas.drawPath(path,paint);
                paint.setStyle(Paint.Style.FILL); Path arrow=new Path(); arrow.moveTo(right,y); arrow.lineTo(right-7*d,y-4*d); arrow.lineTo(right-7*d,y+4*d); arrow.close(); canvas.drawPath(arrow,paint);
                paint.setColor(GREEN); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(12*textScale);
                canvas.drawText(f.count()+" 次",width*.5f,y-6*d,paint);
                y+=58*d;
            }
        }
    }
    static String shorten(Paint p,String text,float width) {
        if(p.measureText(text)<=width) return text;
        int length=p.breakText(text,true,Math.max(0,width-p.measureText("…")),null);
        return text.substring(0,length)+"…";
    }
    private Charts() {}
}
