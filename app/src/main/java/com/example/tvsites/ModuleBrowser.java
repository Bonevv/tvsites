package com.example.tvsites;

import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Lightweight grid and selected-item panel, shared by radio and saved websites. */
@android.annotation.SuppressLint("ViewConstructor")
final class ModuleBrowser extends LinearLayout {
    static final class Entry {
        final String name, subtitle, description, primary, secondary;
        final Runnable open, alternate;
        final int icon;
        Entry(String name,String subtitle,String description,int icon,String primary,Runnable open,String secondary,Runnable alternate){
            this.name=name;this.subtitle=subtitle;this.description=description;this.icon=icon;this.primary=primary;this.open=open;this.secondary=secondary;this.alternate=alternate;
        }
    }
    private final float unit;
    private final LinearLayout detail;
    private View first;
    ModuleBrowser(Context c,float unit,List<Entry> entries){
        super(c);this.unit=unit;setOrientation(HORIZONTAL);
        ScrollView scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(false);
        LinearLayout grid=new LinearLayout(c);grid.setOrientation(VERTICAL);grid.setPadding(px(3),px(3),px(12),px(12));scroll.addView(grid);
        LayoutParams gl=new LayoutParams(0,LayoutParams.MATCH_PARENT,1.7f);gl.rightMargin=px(24);addView(scroll,gl);
        detail=new LinearLayout(c);detail.setOrientation(VERTICAL);detail.setPadding(px(24),px(24),px(24),px(20));detail.setBackground(TvTheme.panel(px(12),TvTheme.SURFACE,TvTheme.OUTLINE,unit));
        ScrollView ds=new ScrollView(c);ds.setVerticalScrollBarEnabled(false);ds.addView(detail);addView(ds,new LayoutParams(0,LayoutParams.MATCH_PARENT,1f));
        LinearLayout row=null;
        for(int i=0;i<entries.size();i++){
            Entry e=entries.get(i);
            if(i%3==0){row=new LinearLayout(c);LayoutParams rl=new LayoutParams(LayoutParams.MATCH_PARENT,px(148));rl.bottomMargin=px(16);grid.addView(row,rl);}
            LinearLayout tile=new LinearLayout(c);tile.setOrientation(VERTICAL);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(px(18),px(18),px(18),px(18));tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setClickable(true);tile.setBackground(TvTheme.control(unit,false));
            tile.addView(new AppNavigation.NavigationIcon(c,e.icon),new LayoutParams(px(30),px(30)));
            TextView name=label(e.name,21,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LayoutParams nl=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);nl.topMargin=px(14);tile.addView(name,nl);
            TextView sub=label(e.subtitle,14,false);sub.setSingleLine(true);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(sub);
            tile.setOnFocusChangeListener((v,focus)->{if(focus)show(e);});tile.setOnClickListener(v->e.open.run());
            LayoutParams tl=new LayoutParams(0,LayoutParams.MATCH_PARENT,1);if(i%3>0)tl.leftMargin=px(14);row.addView(tile,tl);if(first==null)first=tile;
        }
        if(row!=null)while(row.getChildCount()<3)row.addView(new View(c),new LayoutParams(0,1,1));
        if(!entries.isEmpty())show(entries.get(0));
    }
    View first(){return first;}
    private void show(Entry e){
        detail.removeAllViews();FrameLayout symbol=new FrameLayout(getContext());symbol.setBackground(TvTheme.panel(px(10),TvTheme.BACKGROUND_TOP,0,0));symbol.addView(new AppNavigation.NavigationIcon(getContext(),e.icon),new FrameLayout.LayoutParams(px(76),px(76),Gravity.CENTER));detail.addView(symbol,new LayoutParams(LayoutParams.MATCH_PARENT,px(120)));
        detail.addView(label(e.name,28,true),top(20));detail.addView(label(e.subtitle,16,false),top(8));TextView description=label(e.description,17,false);description.setMaxLines(3);description.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.addView(description,top(16));
        detail.addView(button(e.primary,e.open,true),top(18));if(e.alternate!=null)detail.addView(button(e.secondary,e.alternate,false),top(10));
    }
    private View button(String title,Runnable action,boolean primary){TextView t=label(title,19,true);t.setGravity(Gravity.CENTER);t.setPadding(px(16),px(14),px(16),px(14));t.setFocusable(true);t.setClickable(true);t.setBackground(TvTheme.control(unit,primary));t.setOnClickListener(v->action.run());return t;}
    private TextView label(String s,int size,boolean bold){TextView t=new TextView(getContext());t.setText(s);t.setTextColor(bold?TvTheme.TEXT:TvTheme.MUTED);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private LayoutParams top(int space){LayoutParams p=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);p.topMargin=px(space);return p;}
    private int px(float value){return Math.round(value*unit);}
}
