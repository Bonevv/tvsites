package com.example.tvsites;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Shared top navigation and real process memory label; absent during playback and website browsing. */
@android.annotation.SuppressLint("ViewConstructor")
final class AppNavigation extends FrameLayout {
    interface Listener { void select(int section); }
    private final float unit;
    private final View[] tabs = new View[6];
    private int selected;
    private Runnable contentAction;
    private final TextView memory;
    AppNavigation(Context context, float unit, Listener listener) {
        super(context);this.unit=unit;setClipChildren(false);setClipToPadding(false);setElevation(px(24));
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(px(54),0,px(48),0);header.setBackgroundColor(TvTheme.BACKGROUND);
        LayoutParams hp=new LayoutParams(LayoutParams.MATCH_PARENT,px(96),Gravity.TOP);addView(header,hp);
        LinearLayout brand=new LinearLayout(context);brand.setGravity(Gravity.CENTER_VERTICAL);
        NavigationIcon brandIcon=new NavigationIcon(context,0);brandIcon.tint=TvTheme.BLUE;brand.addView(brandIcon,new LinearLayout.LayoutParams(px(30),px(30)));
        TextView name=label("ТВ Сайтове",24,true);LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);nl.leftMargin=px(13);brand.addView(name,nl);header.addView(brand,new LinearLayout.LayoutParams(px(240),LayoutParams.MATCH_PARENT));
        int[] sections={5,0,2,3,4};String[] names={"Начало","Телевизия","Радио","Сайтове","Настройки"};
        for(int j=0;j<sections.length;j++){
            final int index=sections[j];LinearLayout tab=new LinearLayout(context);tab.setGravity(Gravity.CENTER_VERTICAL);tab.setPadding(px(15),0,px(15),0);
            tab.setId(View.generateViewId());tab.setFocusable(true);tab.setFocusableInTouchMode(true);tab.setClickable(true);tab.setTag("nav:"+index);tab.setContentDescription(names[j]);
            tab.addView(new NavigationIcon(context,index),new LinearLayout.LayoutParams(px(23),px(23)));
            TextView caption=label(names[j],20,false);LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);textLp.leftMargin=px(9);tab.addView(caption,textLp);
            tab.setOnClickListener(v->listener.select(index));
            tab.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN && contentAction!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)contentAction.run();return true;}return false;});
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,px(53));tp.rightMargin=px(7);header.addView(tab,tp);tabs[index]=tab;
        }
        for(int j=0;j<sections.length;j++){View tab=tabs[sections[j]];tab.setNextFocusLeftId(tabs[sections[Math.max(0,j-1)]].getId());tab.setNextFocusRightId(tabs[sections[Math.min(sections.length-1,j+1)]].getId());}
        header.addView(new View(context),new LinearLayout.LayoutParams(0,1,1));
        TextClock clock=new TextClock(context);clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("HH:mm");clock.setTextColor(TvTheme.TEXT);clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(24));header.addView(clock,new LinearLayout.LayoutParams(px(82),LayoutParams.WRAP_CONTENT));
        FrameLayout search=new FrameLayout(context);search.setId(View.generateViewId());search.setFocusable(true);search.setClickable(true);search.setContentDescription("Търсене");search.setBackground(TvTheme.control(unit,false));search.addView(new NavigationIcon(context,6),new LayoutParams(px(23),px(23),Gravity.CENTER));search.setOnClickListener(v->{if(searchAction!=null)searchAction.run();});search.setNextFocusLeftId(tabs[4].getId());tabs[4].setNextFocusRightId(search.getId());search.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_DOWN && contentAction!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)contentAction.run();return true;}return false;});header.addView(search,new LinearLayout.LayoutParams(px(49),px(49)));
        View divider=new View(context);divider.setBackgroundColor(TvTheme.OUTLINE);LayoutParams dp=new LayoutParams(LayoutParams.MATCH_PARENT,Math.max(1,px(1)));dp.topMargin=px(95);addView(divider,dp);
        View footer=new View(context);footer.setBackgroundColor(TvTheme.BACKGROUND);addView(footer,new LayoutParams(LayoutParams.MATCH_PARENT,px(56),Gravity.BOTTOM));
        memory=label("RAM процес: …",15,false);memory.setTextColor(TvTheme.MUTED);memory.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        LayoutParams mp=new LayoutParams(px(650),px(30),Gravity.END|Gravity.BOTTOM);mp.rightMargin=px(48);mp.bottomMargin=px(16);addView(memory,mp);setSection(5);
    }
    private Runnable searchAction;
    void setSearchAction(Runnable action){searchAction=action;}
    void setSection(int section){selected=Math.max(0,Math.min(5,section));for(int i=0;i<tabs.length;i++){if(tabs[i]==null)continue;tabs[i].setBackground(TvTheme.navigation(unit,i==selected));tabs[i].setSelected(i==selected);}}
    void setContentAction(Runnable action){contentAction=action;}
    void setMemory(long processMb,long freeMb,long totalMb){memory.setText("RAM процес: "+(processMb>0?"≈ "+processMb+" МБ":"…")+" · Свободна: "+freeMb+" / "+totalMb+" МБ");}
    void focusSelected(){tabs[selected].requestFocusFromTouch();}
    private TextView label(String value,int size,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextColor(TvTheme.TEXT);t.setSingleLine(true);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private int px(float value){return Math.round(value*unit);}
    static final class NavigationIcon extends View {
        final int kind;int tint=TvTheme.TEXT;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        NavigationIcon(Context c,int kind){super(c);this.kind=kind;}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(tint);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setStrokeCap(Paint.Cap.ROUND);
            if(kind==5){android.graphics.Path path=new android.graphics.Path();path.moveTo(2,11);path.lineTo(12,2);path.lineTo(22,11);path.moveTo(5,9);path.lineTo(5,22);path.lineTo(19,22);path.lineTo(19,9);c.drawPath(path,p);}
            else if(kind==0){c.drawRoundRect(2,4,22,18,3,3,p);c.drawLine(8,22,16,22,p);c.drawLine(9,1,12,4,p);c.drawLine(15,1,12,4,p);}
            else if(kind==1){c.drawRoundRect(3,3,21,21,3,3,p);c.drawLine(3,9,21,9,p);c.drawLine(9,3,9,9,p);c.drawLine(16,3,16,9,p);android.graphics.Path path=new android.graphics.Path();path.moveTo(10,12);path.lineTo(15,15);path.lineTo(10,18);path.close();c.drawPath(path,p);}
            else if(kind==2){c.drawRoundRect(3,8,21,21,3,3,p);c.drawLine(5,8,18,2,p);c.drawCircle(9,14,3,p);c.drawLine(15,13,18,13,p);c.drawLine(15,17,18,17,p);}
            else if(kind==3){c.drawCircle(12,12,10,p);c.drawOval(7,2,17,22,p);c.drawLine(2,12,22,12,p);}
            else if(kind==6){c.drawCircle(10,10,6,p);c.drawLine(15,15,21,21,p);}
            else{for(int i=0;i<3;i++){float y=5+i*7;c.drawLine(3,y,21,y,p);c.drawCircle(i==1?9:15,y,2,p);}}
            c.restore();}
    }
}
