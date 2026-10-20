package com.example.tvsites;

import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The same virtualised channel list and programme panel, in the TV section and over video. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in Java with explicit actions, never inflated from XML.
final class TvGuidePanel extends LinearLayout {
    static final class Channel {
        final String id, name; final boolean favourite;
        Channel(String id, String name, boolean favourite) { this.id=id; this.name=name; this.favourite=favourite; }
    }
    interface Actions {
        void watch(String id);
        void sources(String id);
        void favourite(String id);
        void retry();
        void menu();
        View logo(String name);
    }
    private final float unit;
    private final Actions actions;
    private final ListView list;
    private final ArrayList<Channel> channels = new ArrayList<>();
    private final LinearLayout details;
    private final BaseAdapter adapter;
    private EpgGuide guide;
    private String guideError;
    private String selectedId, returnId;
    private int returnTop;
    private boolean overVideo;
    TvGuidePanel(Context context, float unit, Actions actions, boolean overVideo) {
        super(context); this.unit=unit; this.actions=actions; this.overVideo=overVideo;
        setOrientation(HORIZONTAL); setPadding(px(12),px(12),px(12),px(12));
        setBackgroundColor(overVideo?0xEE1C1A29:android.graphics.Color.TRANSPARENT);
        list=new ListView(context); list.setId(View.generateViewId());list.setFocusable(true);list.setFocusableInTouchMode(true);
        list.setDivider(null); list.setDividerHeight(px(5)); list.setVerticalScrollBarEnabled(false);
        list.setItemsCanFocus(false); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setSelector(shape(0x203E394D, TvTheme.BLUE_LIGHT));
        list.setDrawSelectorOnTop(false);
        adapter=new BaseAdapter() {
            @Override public int getCount(){return channels.size();}
            @Override public Object getItem(int position){return channels.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent){
                Row row=convertView instanceof Row?(Row)convertView:new Row();
                row.bind(channels.get(position),position+1);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnKeyListener((v,key,event)->{
            if(!overVideo && key==KeyEvent.KEYCODE_DPAD_LEFT){if(event.getAction()==KeyEvent.ACTION_DOWN)actions.menu();return true;}
            return false;
        });
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view,int position,long id){select(channels.get(position).id);}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        list.setOnItemClickListener((parent,view,position,id)->{select(channels.get(position).id);actions.watch(selectedId);});
        list.setOnItemLongClickListener((parent,view,position,id)->{actions.sources(channels.get(position).id);return true;});
        LayoutParams left=new LayoutParams(0,LayoutParams.MATCH_PARENT,1f); left.rightMargin=px(24);
        addView(list,left);
        ScrollView scroll=new ScrollView(context);scroll.setVerticalScrollBarEnabled(false);
        details=new LinearLayout(context);details.setOrientation(VERTICAL);details.setPadding(px(16),px(12),px(16),px(20));
        details.setBackground(shape(0x153E394D,0));
        scroll.addView(details,new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        addView(scroll,new LayoutParams(0,LayoutParams.MATCH_PARENT,1.10f));
    }
    void setChannels(List<Channel> values, String preferredId) {
        channels.clear();channels.addAll(values);adapter.notifyDataSetChanged();
        int chosen=0;for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(preferredId))chosen=i;
        if(!channels.isEmpty()){list.setSelection(chosen);select(channels.get(chosen).id);}
        else {selectedId=null;details.removeAllViews();details.addView(text("Няма канали в този списък",28,TvTheme.TEXT,true));
            details.addView(text("Добави любими чрез ☆ Любим в програмата на канал.",18,TvTheme.MUTED,false),top(20));}
    }
    void setGuide(EpgGuide value,String error) {guide=value;guideError=error;refresh();}
    void refresh(){adapter.notifyDataSetChanged();if(selectedId!=null && !details.hasFocus())select(selectedId);}
    void rememberPosition(){
        returnId=selectedId;
        int index=list.getSelectedItemPosition();
        if(index>=0 && index<channels.size())returnId=channels.get(index).id;
        View row=list.getSelectedView();returnTop=row==null?0:row.getTop();
    }
    void focusChannels(){
        String id=returnId!=null?returnId:selectedId;int position=0;
        for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(id)){position=i;break;}
        final int target=position,top=returnTop;returnId=null;
        list.requestFocusFromTouch();list.setSelectionFromTop(target,top);
        list.post(()->{if(list.hasFocus() && target<channels.size())list.setSelectionFromTop(target,top);});
    }
    String selection(){return selectedId;}
    private void select(String id){
        if(id.equals(selectedId) && details.hasFocus())return;
        boolean changed=!id.equals(selectedId);
        selectedId=id;
        if(changed)adapter.notifyDataSetChanged();
        Channel channel=null;for(Channel c:channels)if(c.id.equals(id)){channel=c;break;}
        if(channel==null)return;
        details.removeAllViews();
        long now=System.currentTimeMillis();
        EpgGuide.Event current=guide==null?null:guide.current(channel.name,now);
        if(!overVideo){
            FrameLayout hero=new FrameLayout(getContext());
            GradientDrawable backdrop=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{TvTheme.BACKGROUND_TOP,TvTheme.BACKGROUND});
            backdrop.setCornerRadius(px(12));hero.setBackground(backdrop);
            hero.addView(actions.logo(channel.name),new FrameLayout.LayoutParams(px(180),px(105),Gravity.CENTER));
            if(current!=null && !current.image.isEmpty()){
                TvArtwork artwork=new TvArtwork(getContext());hero.addView(artwork,new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
                artwork.show(current.image);
            }
            View shade=new View(getContext());shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x001C1A29,0xF01C1A29}));
            hero.addView(shade,new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,px(130),Gravity.BOTTOM));
            TextView live=text("НА ЖИВО",13,TvTheme.TEXT,true);live.setPadding(px(10),px(5),px(10),px(5));live.setBackground(shape(0xFFE32646,0));
            FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.END);badge.setMargins(px(12),px(12),px(12),0);hero.addView(live,badge);
            TextView title=text(current==null?channel.name:current.title,28,TvTheme.TEXT,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT,Gravity.BOTTOM);caption.setMargins(px(16),0,px(16),px(14));hero.addView(title,caption);
            details.addView(hero,new LayoutParams(LayoutParams.MATCH_PARENT,px(220)));
        }else{
            details.addView(text(channel.name,22,TvTheme.BLUE_LIGHT,true));
            TextView title=text(current==null?"Телевизия на живо":current.title,28,TvTheme.TEXT,true);title.setMaxLines(2);details.addView(title,top(12));
        }
        TextView time=text(current==null?"Програмата се зарежда отделно от видеото":times(current),18,TvTheme.MUTED,false);
        details.addView(time,top(12));
        ProgressBar progress=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(TvTheme.BLUE));
        if(current!=null)progress.setProgress((int)Math.max(0,Math.min(1000,(now-current.start)*1000/Math.max(1,current.end-current.start))));
        LayoutParams pp=new LayoutParams(LayoutParams.MATCH_PARENT,px(4));pp.topMargin=px(14);details.addView(progress,pp);
        TextView description=text(current==null?"Избери канал с ↑ ↓ и натисни OK за гледане.":current.description,18,TvTheme.MUTED,false);
        description.setMaxLines(3);description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(description,top(16));
        LinearLayout buttons=new LinearLayout(getContext());buttons.setOrientation(HORIZONTAL);
        final Channel selected=channel;
        buttons.addView(button(overVideo?"Гледай канала":"Гледай",()->actions.watch(id),true));
        LayoutParams sourceLp=new LayoutParams(LayoutParams.WRAP_CONTENT,px(48));sourceLp.leftMargin=px(10);
        buttons.addView(button("Източници",()->actions.sources(id),false),sourceLp);
        LayoutParams favouriteLp=new LayoutParams(LayoutParams.WRAP_CONTENT,px(48));favouriteLp.leftMargin=px(10);
        View favourite=button(selected.favourite?"★ Любим":"☆ Любим",()->actions.favourite(id),false);
        if(selected.favourite)((TextView)favourite).setTextColor(TvTheme.ACCENT);
        buttons.addView(favourite,favouriteLp);
        details.addView(buttons,top(20));
        details.addView(text("ПРОГРАМА",15,TvTheme.MUTED,true),top(28));
        int count=0;
        if(guide!=null)for(EpgGuide.Event event:guide.forChannel(channel.name,now)){
            if(event==current)continue;
            TextView upcoming=text(times(event)+"\n"+event.title,18,TvTheme.TEXT,false);
            upcoming.setPadding(px(14),px(12),px(14),px(12));upcoming.setBackground(shape(TvTheme.SURFACE_ALT,TvTheme.OUTLINE));
            upcoming.setMaxLines(3);details.addView(upcoming,top(8));if(++count==(overVideo?5:2))break;
        }
        if(count==0)details.addView(text(guide==null?(guideError==null?"Зареждане на EPG…":"Програмата временно не е достъпна."):"Няма следващи предавания в източника.",17,TvTheme.MUTED,false),top(12));
        if(guideError!=null)details.addView(button("Обнови програмата",()->actions.retry(),false),top(12));
    }
    private View button(String label,Runnable action,boolean primary){
        TextView b=text(label,16,TvTheme.TEXT,true);b.setGravity(Gravity.CENTER);b.setPadding(px(16),px(12),px(16),px(12));
        b.setFocusable(true);b.setClickable(true);b.setBackground(selector(primary));
        b.setNextFocusLeftId(list.getId());b.setOnClickListener(v->action.run());return b;
    }
    private String times(EpgGuide.Event event){SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.getDefault());return f.format(new Date(event.start))+" – "+f.format(new Date(event.end));}
    private int px(float n){return Math.round(n*unit);}
    private LayoutParams top(int margin){LayoutParams p=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);p.topMargin=px(margin);return p;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private GradientDrawable shape(int color,int stroke){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(px(10));if(stroke!=0)bg.setStroke(Math.max(1,px(1)),stroke);return bg;}
    private StateListDrawable selector(boolean primary){return TvTheme.control(unit,primary);}

    private final class Row extends LinearLayout {
        final TextView name,programme,time,number;final FrameLayout logo;final ProgressBar rowProgress;
        Row(){super(TvGuidePanel.this.getContext());setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(px(14),px(12),px(14),px(12));
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            number=text("",16,TvTheme.MUTED,false);addView(number,new LayoutParams(px(26),LayoutParams.WRAP_CONTENT));
            logo=new FrameLayout(getContext());LayoutParams lo=new LayoutParams(px(68),px(52));lo.rightMargin=px(16);addView(logo,lo);
            LinearLayout labels=new LinearLayout(getContext());labels.setOrientation(VERTICAL);
            name=text("",21,TvTheme.TEXT,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);
            programme=text("",16,TvTheme.MUTED,false);programme.setSingleLine(true);programme.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(programme,top(4));
            time=text("",13,TvTheme.MUTED,false);labels.addView(time,top(2));
            rowProgress=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);rowProgress.setMax(1000);rowProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(TvTheme.BLUE));
            LayoutParams bar=new LayoutParams(LayoutParams.MATCH_PARENT,px(3));bar.topMargin=px(5);labels.addView(rowProgress,bar);addView(labels,new LayoutParams(0,LayoutParams.WRAP_CONTENT,1f));
        }
        void bind(Channel c,int index){setBackground(shape(c.id.equals(selectedId)?TvTheme.SURFACE_ALT:TvTheme.SURFACE,c.id.equals(selectedId)?TvTheme.FOCUS:TvTheme.OUTLINE));number.setText(String.valueOf(index));name.setText(c.name+(c.favourite?"  ★":""));logo.removeAllViews();logo.addView(actions.logo(c.name),new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
            EpgGuide.Event event=guide==null?null:guide.current(c.name,System.currentTimeMillis());
            programme.setText(event==null?"На живо":event.title);time.setText(event==null?"":times(event));
            rowProgress.setProgress(event==null?0:(int)Math.max(0,Math.min(1000,(System.currentTimeMillis()-event.start)*1000/Math.max(1,event.end-event.start))));
        }
    }
}
