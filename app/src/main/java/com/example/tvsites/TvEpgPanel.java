package com.example.tvsites;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Virtualised three-hour guide. Uses the already loaded, bounded XMLTV data. */
@android.annotation.SuppressLint("ViewConstructor")
final class TvEpgPanel extends LinearLayout {
    interface Actions { void watch(String id); void programme(String id,EpgGuide.Event event); }
    private final float unit;
    private final Actions actions;
    private final ArrayList<TvGuidePanel.Channel> channels=new ArrayList<>();
    private EpgGuide guide;
    private final ListView list;
    private final BaseAdapter adapter;
    private final TextView[] times=new TextView[3];
    private long start;
    private int revision;
    private static final long WINDOW=3*60*60*1000L;
    TvEpgPanel(Context context,float unit,List<TvGuidePanel.Channel> values,EpgGuide guide,Actions actions){
        super(context);this.unit=unit;this.actions=actions;this.guide=guide;channels.addAll(values);setOrientation(VERTICAL);
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("КАНАЛ",16,TvTheme.MUTED),new LayoutParams(px(160),px(42)));
        for(int i=0;i<3;i++){times[i]=label("",17,TvTheme.MUTED);header.addView(times[i],new LayoutParams(0,px(42),1));}addView(header);
        list=new ListView(context);list.setDivider(null);list.setDividerHeight(px(5));list.setVerticalScrollBarEnabled(false);list.setItemsCanFocus(true);list.setFocusable(true);
        adapter=new BaseAdapter(){
            @Override public int getCount(){return channels.size();}
            @Override public Object getItem(int position){return channels.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View convert,ViewGroup parent){Row row=convert instanceof Row?(Row)convert:new Row();row.bind(channels.get(position));return row;}
        };list.setAdapter(adapter);addView(list,new LayoutParams(LayoutParams.MATCH_PARENT,0,1));refresh();
    }
    void refresh(){
        Calendar now=Calendar.getInstance();now.set(Calendar.MINUTE,0);now.set(Calendar.SECOND,0);now.set(Calendar.MILLISECOND,0);start=now.getTimeInMillis();
        revision++;SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.getDefault());for(int i=0;i<3;i++)times[i].setText(f.format(new Date(start+i*3600000L)));adapter.notifyDataSetChanged();
    }
    void focusChannels(){list.requestFocusFromTouch();list.setSelection(0);}
    void setGuide(EpgGuide value){guide=value;refresh();}
    void release(){list.setAdapter(null);guide=null;channels.clear();}
    private int px(float v){return Math.round(v*unit);}
    private TextView label(String title,int size,int color){TextView t=new TextView(getContext());t.setText(title);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(px(14),0,px(12),0);return t;}
    private TextView button(String title,boolean primary){TextView t=label(title,18,TvTheme.TEXT);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setFocusable(true);t.setClickable(true);t.setBackground(TvTheme.control(unit,primary));return t;}
    private final class Row extends FrameLayout {
        TvGuidePanel.Channel channel;
        boolean pending;
        int renderedWidth,renderedRevision=-1;
        String renderedChannel;
        Row(){super(TvEpgPanel.this.getContext());setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT,px(78)));}
        void bind(TvGuidePanel.Channel c){channel=c;if(needsRender())schedule();}
        private boolean needsRender(){return channel!=null && (!channel.id.equals(renderedChannel) || renderedWidth!=getWidth() || renderedRevision!=revision);}
        private void schedule(){if(pending)return;pending=true;post(()->{pending=false;if(getWidth()>0 && needsRender())render();});}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);schedule();}
        private void render(){
            renderedWidth=getWidth();renderedRevision=revision;renderedChannel=channel.id;
            removeAllViews();final TvGuidePanel.Channel c=channel;TextView name=button(c.name,false);name.setSingleLine(true);name.setOnClickListener(v->actions.watch(c.id));addView(name,new FrameLayout.LayoutParams(px(153),LayoutParams.MATCH_PARENT));
            int left=px(160),width=getWidth()-left;if(width<=0)return;int count=0;
            if(guide!=null)for(EpgGuide.Event event:guide.forChannel(c.name,System.currentTimeMillis())){
                long from=Math.max(start,event.start),to=Math.min(start+WINDOW,event.end);if(from>=to)continue;
                TextView cell=button(event.title+"\n"+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(event.start)),false);
                if(event.start<=System.currentTimeMillis() && event.end>System.currentTimeMillis())cell.setBackground(TvTheme.panel(px(9),TvTheme.SURFACE_ALT,TvTheme.BLUE,unit));
                cell.setOnClickListener(v->actions.programme(c.id,event));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.max(px(30),(int)((to-from)*width/WINDOW)-px(5)),LayoutParams.MATCH_PARENT);lp.leftMargin=left+(int)((from-start)*width/WINDOW);addView(cell,lp);count++;
            }
            if(count==0){TextView empty=button("Гледай канала · няма налична програма",false);empty.setOnClickListener(v->actions.watch(c.id));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,LayoutParams.MATCH_PARENT);lp.leftMargin=left;addView(empty,lp);}
        }
    }
}
