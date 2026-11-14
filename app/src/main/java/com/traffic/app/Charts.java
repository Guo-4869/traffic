package com.traffic.app;


import android.graphics.*;




final class Charts {
    static final int GREEN=Color.rgb(20,108,91), INK=Color.rgb(25,42,43), MUTED=Color.rgb(91,109,111);
    static String duration(long ms) {
        long seconds=ms/1000;
        if (seconds<60) return seconds+" 秒";
        long minutes=seconds/60;
        return minutes<60 ? minutes+" 分" : minutes/60+" 小时 "+minutes%60+" 分";
    }
    static String shorten(Paint p,String text,float width) {
        if(p.measureText(text)<=width) return text;
        int length=p.breakText(text,true,Math.max(0,width-p.measureText("…")),null);
        return text.substring(0,length)+"…";
    }
    private Charts() {}
}
