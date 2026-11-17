package com.traffic.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.Toast;
import com.traffic.core.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Offline native Canvas charts. Shared palette makes App identity consistent across panels. */
final class VisualCharts {
    static final int TRACK=Color.rgb(235,240,243),OTHER=Color.rgb(148,163,174);
    static final class Palette {
        final Map<String,Integer> colors=new HashMap<>();
        Palette(Collection<String> packages) {
            List<String> sorted=new ArrayList<>(new TreeSet<>(packages));int i=0;
            int[] base={0xFF208579,0xFF6889D1,0xFFE9AC58,0xFFB28DC7,0xFFDC8577,0xFF6EA7BC,0xFF9CAC65,0xFFB27C98};
            for(String pkg:sorted) {int n=i++;colors.put(pkg,n<base.length?base[n]:Color.HSVToColor(new float[]{(n*137.508f)%360,.48f,.76f}));}
        }
        int color(String pkg) {return colors.getOrDefault(pkg,OTHER);}
    }
    @SuppressLint("ViewConstructor")
    static abstract class Plot extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path=new Path();
        final float density,font;
        final Map<String,String> labels;
        final Palette palette;
        Plot(Context c,Map<String,String> labels,Palette palette) {
            super(c);I18n.init(c);this.labels=labels;this.palette=palette;
            density=getResources().getDisplayMetrics().density;
            font=Math.min(1.4f,getResources().getConfiguration().fontScale);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        abstract float height();
        abstract void drawPlot(Canvas c,float w);
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),(int)Math.ceil(height()*density));}
        @Override protected void onDraw(Canvas c){c.save();c.scale(density,density);drawPlot(c,getWidth()/density);c.restore();}
        void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size*font);p.setTypeface(Typeface.DEFAULT);p.setTextAlign(align);c.drawText(s,x,y,p);}
        void label(Canvas c,String pkg,float x,float y,float width,Paint.Align align){p.setTextSize(11*font);text(c,Charts.shorten(p,labels.getOrDefault(pkg,pkg),width),x,y,11,Charts.INK,align);}
        void rect(Canvas c,float a,float b,float x,float y,float r,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(a,b,x,y,r,r,p);}
        void empty(Canvas c,float w){text(c,I18n.t(R.string.ui_121),w/2,height()/2,14,Charts.MUTED,Paint.Align.CENTER);}
    }
    @SuppressLint("ViewConstructor") // Constructed from explicit data, never XML.
    static final class Donut extends Plot {
        final List<Reports.AppUsage> apps;final long total;
        Donut(Context c,List<Reports.AppUsage> apps,Map<String,String> names,Palette colors){super(c,names,colors);this.apps=apps;total=apps.stream().mapToLong(Reports.AppUsage::duration).sum();setContentDescription(I18n.t(R.string.ui_122));}
        float height(){return 218;}
        void drawPlot(Canvas c,float w){
            float size=Math.min(190,w-24),cx=w/2,cy=107;
            RectF oval=new RectF(cx-size/2,cy-size/2,cx+size/2,cy+size/2);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(24);p.setColor(TRACK);c.drawOval(oval,p);
            float angle=-90;
            if(total>0) for(int i=0;i<Math.min(6,apps.size()+1);i++) {
                long value=i<5&&i<apps.size()?apps.get(i).duration():apps.stream().skip(5).mapToLong(Reports.AppUsage::duration).sum();
                if(value<=0)continue;
                float sweep=360f*value/total;p.setColor(i<5?palette.color(apps.get(i).pkg()):OTHER);
                c.drawArc(oval,angle+Math.min(1,sweep/4),Math.max(.05f,sweep-Math.min(2,sweep/2)),false,p);angle+=sweep;
                if(i>=apps.size()-1 && apps.size()<=5)break;
            }
            text(c,I18n.t(R.string.ui_123),cx,cy-7,11,Charts.MUTED,Paint.Align.CENTER);
            text(c,apps.isEmpty()?"—":Math.min(5,apps.size())+I18n.t(R.string.ui_124),cx,cy+21,18,Charts.INK,Paint.Align.CENTER);
        }
    }
    @SuppressLint("ViewConstructor")
    static final class Bars extends Plot {
        final List<Reports.AppUsage> apps;
        Bars(Context c,List<Reports.AppUsage> apps,Map<String,String> names,Palette colors){super(c,names,colors);this.apps=apps;setContentDescription(I18n.t(R.string.ui_125));}
        float height(){return Math.max(1,apps.size())*71;}
        void drawPlot(Canvas c,float w){if(apps.isEmpty()){empty(c,w);return;}long max=Math.max(1,apps.get(0).duration());float y=0;
            for(var a:apps){rect(c,0,y+7,5,y+30,2,palette.color(a.pkg()));label(c,a.pkg(),14,y+22,w*.56f,Paint.Align.LEFT);
                text(c,Charts.duration(a.duration()),w,y+20,12,Charts.INK,Paint.Align.RIGHT);
                text(c,a.opens()+I18n.t(R.string.ui_079),w,y+39,10,Charts.MUTED,Paint.Align.RIGHT);
                rect(c,14,y+43,w,y+51,4,TRACK);if(a.duration()>0)rect(c,14,y+43,14+(w-14)*a.duration()/max,y+51,4,palette.color(a.pkg()));y+=71;}
        }
    }
    @SuppressLint("ViewConstructor")
    static final class Gantt extends Plot {
        final List<Insights.Interval> intervals;final List<String> apps;final long start,end;
        Gantt(Context c,List<Insights.Interval> intervals,List<String> apps,long start,long end,Map<String,String> names,Palette colors){super(c,names,colors);this.intervals=intervals;this.apps=apps;this.start=start;this.end=end;setContentDescription(I18n.t(R.string.ui_126));setOnTouchListener((v,e)->{
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX()/density,y=e.getY()/density,w=getWidth()/density,left=85;
            int index=(int)((y-35)/38);if(index>=0&&index<apps.size()&&x>=left){long time=start+(long)((x-left)/(w-left-8)*(end-start));for(var a:intervals)if(a.pkg().equals(apps.get(index))&&time>=a.start()&&time<=a.end()){Toast.makeText(c,names.getOrDefault(a.pkg(),a.pkg())+"  "+clock(a.start())+" – "+clock(a.end()),Toast.LENGTH_LONG).show();break;}}
            performClick();return true;});}
        @Override public boolean performClick(){super.performClick();return true;}
        float height(){return 65+Math.max(1,apps.size())*38;}
        void drawPlot(Canvas c,float w){if(apps.isEmpty()){empty(c,w);return;}float left=85,right=w-8,top=35,bottom=top+apps.size()*38;
            for(int h=0;h<=24;h+=6){long tick=h==24?end:Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).withHour(h).toInstant().toEpochMilli();float x=left+(right-left)*(tick-start)/(end-start);rect(c,x,top-3,x+.6f,bottom,0,TRACK);text(c,String.format(Locale.ROOT,"%02d",h),x,20,10,Charts.MUTED,Paint.Align.CENTER);}
            for(int i=0;i<apps.size();i++){String pkg=apps.get(i);float y=top+i*38;label(c,pkg,0,y+21,77,Paint.Align.LEFT);rect(c,left,y+8,right,y+29,4,0xFFF5F7F8);
                for(var a:intervals)if(a.pkg().equals(pkg)){float x0=left+(right-left)*(a.start()-start)/(end-start),x1=left+(right-left)*(a.end()-start)/(end-start);rect(c,x0,y+8,Math.max(x0+1,x1),y+29,3,palette.color(pkg));}}
            text(c,I18n.t(R.string.ui_127),left,bottom+22,9,Charts.MUTED,Paint.Align.LEFT);
        }
    }
    @SuppressLint("ViewConstructor")
    static final class Heatmap extends Plot {
        final Map<String,long[]> values;final List<String> apps;long maximum=1;
        Heatmap(Context c,Map<String,long[]> values,List<String> apps,Map<String,String> names,Palette colors){super(c,names,colors);this.values=values;this.apps=apps;for(long[] row:values.values())for(long value:row)maximum=Math.max(maximum,value);setContentDescription(I18n.t(R.string.ui_128));setOnTouchListener((v,e)->{if(e.getAction()!=MotionEvent.ACTION_UP)return true;int row=(int)((e.getY()/density-34)/32),hour=(int)((e.getX()/density-85)/((getWidth()/density-90)/24));if(row>=0&&row<apps.size()&&e.getX()/density>=85&&hour>=0&&hour<24){String pkg=apps.get(row);Toast.makeText(c,names.getOrDefault(pkg,pkg)+" · "+hour+":00 · "+Charts.duration(values.getOrDefault(pkg,new long[24])[hour]),Toast.LENGTH_LONG).show();}performClick();return true;});}
        @Override public boolean performClick(){super.performClick();return true;}
        float height(){return 72+Math.max(1,apps.size())*32;}
        void drawPlot(Canvas c,float w){if(apps.isEmpty()){empty(c,w);return;}float left=85,cell=(w-left-5)/24;
            for(int h=0;h<24;h+=3)text(c,String.format(Locale.ROOT,"%02d",h),left+cell*(h+.5f),19,9,Charts.MUTED,Paint.Align.CENTER);
            for(int row=0;row<apps.size();row++){String pkg=apps.get(row);float y=34+row*32;label(c,pkg,0,y+17,77,Paint.Align.LEFT);long[] a=values.getOrDefault(pkg,new long[24]);
                for(int h=0;h<24;h++){float v=a[h]/(float)maximum;rect(c,left+h*cell,y,left+(h+1)*cell-2,y+23,2,mix(0xFFEDF4F2,0xFF167C68,v));}}
            float y=43+apps.size()*32;text(c,I18n.t(R.string.ui_129),left,y+10,10,Charts.MUTED,Paint.Align.LEFT);for(int i=0;i<5;i++)rect(c,left+22+i*16,y,left+35+i*16,y+12,2,mix(0xFFEDF4F2,0xFF167C68,i/4f));text(c,I18n.t(R.string.ui_130),left+110,y+10,10,Charts.MUTED,Paint.Align.LEFT);
        }
    }
    @SuppressLint("ViewConstructor")
    static final class Sankey extends Plot {
        record Edge(String from,String to,int value) {}
        final List<Edge> edges=new ArrayList<>();final LinkedHashMap<String,Integer> left=new LinkedHashMap<>(),right=new LinkedHashMap<>();
        static final String OTHERS="\u0000other";
        Sankey(Context c,List<Reports.Flow> flows,Map<String,String> names,Palette colors){this(c,flows,names,colors,false);}
        Sankey(Context c,List<Reports.Flow> flows,Map<String,String> names,Palette colors,boolean allApps){super(c,new HashMap<>(names),colors);labels.put(OTHERS,I18n.t(R.string.ui_026));
            Map<String,Integer> out=new HashMap<>(),in=new HashMap<>();for(var f:flows){out.merge(f.from(),f.count(),Integer::sum);in.merge(f.to(),f.count(),Integer::sum);}
            Set<String> a=allApps?out.keySet():top(out),b=allApps?in.keySet():top(in);Map<List<String>,Integer> merged=new LinkedHashMap<>();
            for(var f:flows)merged.merge(List.of(a.contains(f.from())?f.from():OTHERS,b.contains(f.to())?f.to():OTHERS),f.count(),Integer::sum);
            merged.forEach((k,n)->edges.add(new Edge(k.get(0),k.get(1),n)));
            for(var e:edges){left.merge(e.from,e.value,Integer::sum);right.merge(e.to,e.value,Integer::sum);}
            setContentDescription(I18n.t(R.string.ui_131)+(allApps?I18n.t(R.string.ui_132):I18n.t(R.string.ui_133)));
        }
        static Set<String> top(Map<String,Integer> values){List<String> sorted=new ArrayList<>(values.keySet());sorted.sort(Comparator.<String>comparingInt(values::get).reversed().thenComparing(s->s));return new LinkedHashSet<>(sorted.subList(0,Math.min(8,sorted.size())));}
        float height(){return Math.max(240,Math.max(left.size(),right.size())*55+64);}
        void drawPlot(Canvas c,float w){if(edges.isEmpty()){empty(c,w);return;}int n=Math.max(left.size(),right.size());float total=left.values().stream().mapToInt(v->v).sum(),scale=(height()-65-n*32)/Math.max(1,total),x0=93,x1=w-93;
            text(c,I18n.t(R.string.ui_134),0,17,10,Charts.MUTED,Paint.Align.LEFT);text(c,I18n.t(R.string.ui_135),w,17,10,Charts.MUTED,Paint.Align.RIGHT);
            Map<String,Float> ly=positions(left,scale),ry=positions(right,scale),lo=new HashMap<>(),ro=new HashMap<>();
            for(var e:edges){float h=e.value*scale,y0=ly.get(e.from)+9+lo.getOrDefault(e.from,0f),y1=ry.get(e.to)+9+ro.getOrDefault(e.to,0f);float mid=(x0+x1)/2;
                path.reset();path.moveTo(x0+7,y0);path.cubicTo(mid,y0,mid,y1,x1-7,y1);path.lineTo(x1-7,y1+h);path.cubicTo(mid,y1+h,mid,y0+h,x0+7,y0+h);path.close();p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(x0,0,x1,0,new int[]{alpha(palette.color(e.from),105),alpha(palette.color(e.to),105)},null,Shader.TileMode.CLAMP));c.drawPath(path,p);p.setShader(null);lo.merge(e.from,h,Float::sum);ro.merge(e.to,h,Float::sum);}
            nodes(c,left,ly,scale,x0,w,true);nodes(c,right,ry,scale,x1,w,false);
        }
        Map<String,Float> positions(Map<String,Integer> nodes,float scale){Map<String,Float> y=new HashMap<>();float cursor=37;for(var e:nodes.entrySet()){y.put(e.getKey(),cursor);cursor+=e.getValue()*scale+32;}return y;}
        void nodes(Canvas c,Map<String,Integer> nodes,Map<String,Float> y,float scale,float x,float w,boolean source){for(var e:nodes.entrySet()){float top=y.get(e.getKey()),h=e.getValue()*scale+18;rect(c,source?x:x-7,top,source?x+7:x,top+h,3,palette.color(e.getKey()));label(c,e.getKey(),source?0:w,top+h/2-2,83,source?Paint.Align.LEFT:Paint.Align.RIGHT);text(c,e.getValue()+I18n.t(R.string.ui_040),source?0:w,top+h/2+13,9,Charts.MUTED,source?Paint.Align.LEFT:Paint.Align.RIGHT);}}
    }
    static int mix(int a,int b,float f){f=Math.max(0,Math.min(1,f));return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*f),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*f),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*f));}
    static int alpha(int color,int a){return (color&0xFFFFFF)|(a<<24);}
    static String clock(long time){return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"));}
    private VisualCharts() {}
}
