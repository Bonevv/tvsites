package com.example.tvsites;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Shared header and compact rail; absent during playback and website browsing. */
@android.annotation.SuppressLint("ViewConstructor")
final class AppNavigation extends FrameLayout {
    interface Listener { void select(int section); }
    private final float unit;
    private final View[] tabs = new View[6];
    private int selected;
    private Runnable contentAction;
    private final TextView memory;
    AppNavigation(Context context, float unit, Listener listener) {
        super(context); this.unit = unit;
        setClipChildren(false); setClipToPadding(false); setElevation(px(24));
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("ТВ Сайтове", 25, true); header.addView(brand);
        header.addView(new View(context), new LinearLayout.LayoutParams(0,1,1));
        memory = label("RAM процес: …\nСвободна RAM: …", 16, false); memory.setSingleLine(false); memory.setTextColor(TvTheme.MUTED);
        LinearLayout.LayoutParams ram=new LinearLayout.LayoutParams(px(260),LayoutParams.WRAP_CONTENT);ram.rightMargin=px(28);header.addView(memory,ram);
        TextClock clock = new TextClock(context);clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("HH:mm");
        clock.setTextColor(TvTheme.TEXT);clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(28));
        clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));header.addView(clock);
        LayoutParams hp=new LayoutParams(LayoutParams.MATCH_PARENT,px(80),Gravity.TOP);hp.setMargins(px(54),px(14),px(54),0);addView(header,hp);
        LinearLayout rail = new LinearLayout(context); rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER_HORIZONTAL);
        int[] sections={5,0,1,2,3,4};String[] names={"Начало","ТВ","Видеотека","Радио","Сайтове","Настройки"};
        for(int j=0;j<sections.length;j++){
            final int index=sections[j];LinearLayout tab=new LinearLayout(context);tab.setOrientation(LinearLayout.VERTICAL);tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);tab.setFocusableInTouchMode(true);tab.setClickable(true);tab.setTag("nav:"+index);tab.setContentDescription(names[j]);
            tab.addView(new NavigationIcon(context,index),new LinearLayout.LayoutParams(px(24),px(24)));
            TextView caption=label(names[j],12,false);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);cp.topMargin=px(7);tab.addView(caption,cp);
            tab.setOnClickListener(v->listener.select(index));
            tab.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT && contentAction!=null){if(event.getAction()==KeyEvent.ACTION_DOWN)contentAction.run();return true;}return false;
            });
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(px(90),px(92));tp.bottomMargin=px(10);rail.addView(tab,tp);tabs[index]=tab;
        }
        LayoutParams rp=new LayoutParams(px(100),LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.START);rp.leftMargin=px(30);rp.topMargin=px(136);addView(rail,rp);
        setSection(5);
    }
    void setSection(int section){selected=Math.max(0,Math.min(5,section));for(int i=0;i<tabs.length;i++){tabs[i].setBackground(TvTheme.control(unit,i==selected));tabs[i].setSelected(i==selected);}}
    void setContentAction(Runnable action){contentAction=action;}
    void setMemory(long processMb,long freeMb,long totalMb){memory.setText("RAM процес: "+(processMb>0?"≈ "+processMb+" МБ":"…")+"\nСвободна: "+freeMb+" / "+totalMb+" МБ");}
    void focusSelected(){tabs[selected].requestFocusFromTouch();}
    private TextView label(String value,int size,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextColor(TvTheme.TEXT);t.setSingleLine(true);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private int px(float value){return Math.round(value*unit);}
    static final class NavigationIcon extends View {
        final int kind;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        NavigationIcon(Context c,int kind){super(c);this.kind=kind;}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(TvTheme.TEXT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setStrokeCap(Paint.Cap.ROUND);
            if(kind==5){android.graphics.Path path=new android.graphics.Path();path.moveTo(2,11);path.lineTo(12,2);path.lineTo(22,11);path.moveTo(5,9);path.lineTo(5,22);path.lineTo(19,22);path.lineTo(19,9);c.drawPath(path,p);}
            else if(kind==0){c.drawRoundRect(2,4,22,18,3,3,p);c.drawLine(8,22,16,22,p);c.drawLine(9,1,12,4,p);c.drawLine(15,1,12,4,p);}
            else if(kind==1){c.drawRoundRect(3,3,21,21,3,3,p);c.drawLine(3,9,21,9,p);c.drawLine(9,3,9,9,p);c.drawLine(16,3,16,9,p);android.graphics.Path path=new android.graphics.Path();path.moveTo(10,12);path.lineTo(15,15);path.lineTo(10,18);path.close();c.drawPath(path,p);}
            else if(kind==2){c.drawRoundRect(3,8,21,21,3,3,p);c.drawLine(5,8,18,2,p);c.drawCircle(9,14,3,p);c.drawLine(15,13,18,13,p);c.drawLine(15,17,18,17,p);}
            else if(kind==3){c.drawCircle(12,12,10,p);c.drawOval(7,2,17,22,p);c.drawLine(2,12,22,12,p);}
            else{for(int i=0;i<3;i++){float y=5+i*7;c.drawLine(3,y,21,y,p);c.drawCircle(i==1?9:15,y,2,p);}}
            c.restore();}
    }
}
