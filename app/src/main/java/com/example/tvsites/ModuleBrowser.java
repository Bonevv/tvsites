package com.example.tvsites;

import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Simple native station/site cards. Websites and streams only start on click. */
@android.annotation.SuppressLint("ViewConstructor")
final class ModuleBrowser extends LinearLayout {
    static final class Entry {
        final String name,subtitle,description,primary,secondary;
        final Runnable open,alternate;
        final int icon;
        Entry(String name,String subtitle,String description,int icon,String primary,Runnable open,String secondary,Runnable alternate){
            this.name=name==null || name.trim().isEmpty()?"Сайт":name;this.subtitle=subtitle;this.description=description;this.icon=icon;this.primary=primary;this.open=open;this.secondary=secondary;this.alternate=alternate;
        }
    }
    private final float unit;
    private View first;
    ModuleBrowser(Context c,float unit,List<Entry> entries){
        super(c);this.unit=unit;setOrientation(VERTICAL);setPadding(px(3),px(3),px(3),px(3));
        boolean radio=!entries.isEmpty() && entries.get(0).icon==2;int columns=radio?2:3;LinearLayout row=null;
        int[] colors={0xFF136BFF,0xFF218561,0xFF7934CC,0xFFC3384B,0xFFBA6C18};
        for(int i=0;i<entries.size();i++){
            Entry e=entries.get(i);
            if(i%columns==0){row=new LinearLayout(c);LayoutParams rp=new LayoutParams(LayoutParams.MATCH_PARENT,px(116));rp.bottomMargin=px(18);addView(row,rp);}
            LinearLayout tile=new LinearLayout(c);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(px(20),px(16),px(20),px(16));tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setClickable(true);tile.setBackground(TvTheme.control(unit,false));tile.setTag((radio?"radio:":"site:")+e.name);
            TextView mark=label(mark(e.name,radio),radio?21:30,true);mark.setGravity(Gravity.CENTER);mark.setBackground(TvTheme.panel(px(36),colors[(e.name.hashCode()&0x7fffffff)%colors.length],0,0));
            LayoutParams ml=new LayoutParams(px(60),px(60));ml.rightMargin=px(18);tile.addView(mark,ml);
            LinearLayout labels=new LinearLayout(c);labels.setOrientation(VERTICAL);
            TextView name=label(e.name,22,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);
            TextView sub=label(e.subtitle,17,false);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams sl=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);sl.topMargin=px(6);labels.addView(sub,sl);
            tile.addView(labels,new LayoutParams(0,LayoutParams.WRAP_CONTENT,1));
            if(radio){TextView play=label("▶  Слушай",18,true);play.setGravity(Gravity.CENTER);play.setPadding(px(14),px(10),px(14),px(10));play.setBackground(TvTheme.panel(px(8),TvTheme.BLUE,0,0));tile.addView(play);}
            tile.setOnClickListener(v->e.open.run());
            if(e.alternate!=null){tile.setLongClickable(true);tile.setOnLongClickListener(v->{e.alternate.run();return true;});tile.setContentDescription(e.name+". OK: "+e.primary+". Задръж OK: "+e.secondary);}
            LayoutParams tp=new LayoutParams(0,LayoutParams.MATCH_PARENT,1);if(i%columns>0)tp.leftMargin=px(18);row.addView(tile,tp);if(first==null)first=tile;
        }
        if(row!=null)while(row.getChildCount()<columns)row.addView(new View(c),new LayoutParams(0,1,1));
    }
    View first(){return first;}
    private String mark(String name,boolean radio){
        if(!radio)return name.substring(0,1).toUpperCase(java.util.Locale.ROOT);
        if(name.contains("NOVA"))return "NOVA";if(name.contains("City"))return "City";if(name.contains("БНР"))return "БНР";if(name.contains("Радио 1"))return "1";if(name.contains("Вероника"))return "V";return "БГ";
    }
    private TextView label(String s,int size,boolean bold){TextView t=new TextView(getContext());t.setText(s);t.setTextColor(bold?TvTheme.TEXT:TvTheme.MUTED);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private int px(float value){return Math.round(value*unit);}
}
