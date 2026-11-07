package com.example.tvsites;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Remote-first, paged native catalogue. Only one page of poster views exists at a time. */
final class MovieBrowser extends LinearLayout {
    static final class State { String query="",genre=""; int language,year,sort,page; }
    interface Actions {
        void fetch(boolean more);
        void open(MovieCatalog.Movie movie);
    }
    private final State state;
    private final Actions actions;
    private final float unit;
    private final LinearLayout grid, filters, paging;
    private final TextView count,status;
    private final Button search,language,genre,year,sort,refresh;
    private List<MovieCatalog.Movie> movies=new ArrayList<>();
    private boolean busy;
    private ExecutorService images;
    private final LruCache<String,Bitmap> artwork=new LruCache<String,Bitmap>(4*1024*1024){
        @Override protected int sizeOf(String key,Bitmap image){return image.getByteCount();}
    };
    MovieBrowser(Context context,float unit,State state,Actions actions) {
        super(context);this.unit=unit;this.state=state;this.actions=actions;
        setOrientation(VERTICAL);setClipChildren(false);
        TextView heading=label("Филми",38,true);addView(heading);
        TextView hint=label("Един филм, всички налични източници. OK — подробности и гледане.",18,false);addView(hint);
        filters=new LinearLayout(context);filters.setPadding(0,px(20),0,px(16));
        search=button("Търсене",this::search);language=button("Език",this::language);
        genre=button("Жанр",this::genre);year=button("Година",this::year);sort=button("Подредба",this::sort);
        for(Button b:new Button[]{search,language,genre,year,sort}){LinearLayout.LayoutParams lp=new LayoutParams(0,px(55),1);lp.rightMargin=px(10);filters.addView(b,lp);}
        addView(filters,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        count=label("",19,false);addView(count);
        grid=new LinearLayout(context);grid.setOrientation(VERTICAL);grid.setClipChildren(false);addView(grid);
        paging=new LinearLayout(context);paging.setPadding(0,px(18),0,px(12));addView(paging);
        refresh=button("Обнови каталога",()->actions.fetch(false));addView(refresh,new LayoutParams(px(280),px(54)));
        status=label("",15,false);status.setPadding(0,px(12),0,0);addView(status);captions();
    }
    View first(){return search;}
    void update(List<MovieCatalog.Entry> entries,String message,boolean busy) {
        this.movies=MovieCatalog.merge(entries);this.busy=busy;status.setText(message);
        refresh.setEnabled(!busy);refresh.setText(busy?"Обновяване…":"Обнови каталога");render();
    }
    private void captions(){
        search.setText(state.query.isEmpty()?"⌕ Търсене":"⌕ "+state.query);
        language.setText(new String[]{"Всички езици","БГ аудио","БГ субтитри"}[state.language]);
        genre.setText(state.genre.isEmpty()?"Всички жанрове":state.genre);year.setText(state.year==0?"Всички години":""+state.year);
        sort.setText(new String[]{"Последно добавени","По азбучен ред","По година"}[state.sort]);
    }
    private void changed(boolean fetch){state.page=0;captions();render();if(fetch)actions.fetch(false);}
    void search(){
        EditText input=new EditText(getContext());input.setSingleLine(true);input.setText(state.query);input.setHint("Заглавие или думи от описанието");
        AlertDialog d=new AlertDialog.Builder(getContext()).setTitle("Търсене на филм").setView(input)
                .setPositiveButton("Търси",(v,w)->{state.query=input.getText().toString().trim();changed(true);})
                .setNeutralButton("Изчисти",(v,w)->{state.query="";changed(true);}).setNegativeButton("Отказ",null).create();d.show();input.requestFocus();
    }
    private void language(){new AlertDialog.Builder(getContext()).setTitle("Език на версията")
            .setSingleChoiceItems(new String[]{"Всички","БГ аудио","БГ субтитри"},state.language,(d,i)->{state.language=i;d.dismiss();changed(true);}).show();}
    private void genre(){TreeSet<String> values=new TreeSet<>();for(MovieCatalog.Movie m:movies)for(MovieCatalog.Entry e:m.sources)values.addAll(e.genres);
        ArrayList<String> labels=new ArrayList<>();labels.add("Всички жанрове");labels.addAll(values);
        new AlertDialog.Builder(getContext()).setTitle("Жанр в заредения каталог").setItems(labels.toArray(new String[0]),(d,i)->{state.genre=i==0?"":labels.get(i);changed(false);}).show();}
    private void year(){TreeSet<Integer> values=new TreeSet<>(Collections.reverseOrder());for(MovieCatalog.Movie m:movies)if(m.year()>0)values.add(m.year());
        ArrayList<Integer> years=new ArrayList<>(values);ArrayList<String> labels=new ArrayList<>();labels.add("Всички години");for(int y:years)labels.add(""+y);
        new AlertDialog.Builder(getContext()).setTitle("Година").setItems(labels.toArray(new String[0]),(d,i)->{state.year=i==0?0:years.get(i-1);changed(false);}).show();}
    private void sort(){new AlertDialog.Builder(getContext()).setTitle("Подреждане")
            .setSingleChoiceItems(new String[]{"Последно добавени в каталога","По азбучен ред","По година — новите първо"},state.sort,(d,i)->{state.sort=i;d.dismiss();changed(false);}).show();}
    private void render(){
        View focused=findFocus();Object tag=focused==null?null:focused.getTag();boolean inGrid=focused!=null && focused!=search && focused!=language && focused!=genre && focused!=year && focused!=sort && focused!=refresh;
        grid.removeAllViews();paging.removeAllViews();
        List<MovieCatalog.Movie> result=MovieCatalog.filter(movies,state.query,state.language,state.genre,state.year,state.sort);
        int pages=Math.max(1,(result.size()+23)/24);state.page=Math.min(state.page,pages-1);
        count.setText(result.size()+" филма · страница "+(state.page+1)+" / "+pages+(busy?" · обновяване…":"")
                +"\nКаталогът се зарежда постепенно. Търсенето проверява и сайтовете.");
        if(result.isEmpty()) {
            TextView empty=label(busy?"Зареждане на филмите…":"Няма намерени филми. Промени филтрите или зареди още заглавия. Езикът се показва само когато е указан от източника.",22,false);
            empty.setPadding(0,px(45),0,px(45));grid.addView(empty);
        }
        LinearLayout row=null;int end=Math.min(result.size(),(state.page+1)*24);
        for(int i=state.page*24;i<end;i++){
            if(i%6==0){row=new LinearLayout(getContext());row.setClipChildren(false);LayoutParams rp=new LayoutParams(LayoutParams.MATCH_PARENT,px(395));rp.topMargin=px(20);grid.addView(row,rp);}
            MovieCatalog.Movie movie=result.get(i);LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setPadding(px(9),px(9),px(9),px(9));
            card.setFocusable(true);card.setClickable(true);card.setTag("movie:"+movie.main().page);card.setBackground(TvTheme.control(unit,false));card.setOnFocusChangeListener(TvTheme::focus);
            card.setOnClickListener(v->actions.open(movie));card.setContentDescription(movie.title()+", "+movie.year()+", "+movie.languages()+", "+movie.sources.size()+" източника");
            ImageView poster=new ImageView(getContext());poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackgroundColor(TvTheme.BACKGROUND);card.addView(poster,new LayoutParams(LayoutParams.MATCH_PARENT,px(270)));poster(poster,movie.poster());
            TextView title=label(movie.title(),20,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title,new LayoutParams(LayoutParams.MATCH_PARENT,px(53)));
            TextView meta=label((movie.year()==0?"":movie.year()+" · ")+movie.sources.size()+" източника",14,false);card.addView(meta);
            TextView lang=label(movie.languages(),13,false);lang.setSingleLine(true);lang.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(lang);
            LayoutParams cp=new LayoutParams(0,LayoutParams.MATCH_PARENT,1);if(i%6>0)cp.leftMargin=px(13);row.addView(card,cp);
        }
        if(row!=null)while(row.getChildCount()<6)row.addView(new View(getContext()),new LayoutParams(0,1,1));
        if(state.page>0)addPaging("← Предишна",()->{state.page--;render();focusGrid();});
        if(state.page+1<pages)addPaging("Следваща →",()->{state.page++;render();focusGrid();});
        Button more=addPaging(busy?"Зареждане…":"Зареди още от сайтовете",()->actions.fetch(true));more.setEnabled(!busy);
        if(inGrid){View restore=tag==null?null:findViewWithTag(tag);if(restore!=null)restore.requestFocus();else more.requestFocus();}
    }
    private void focusGrid(){if(grid.getChildCount()>0 && grid.getChildAt(0) instanceof ViewGroup){ViewGroup row=(ViewGroup)grid.getChildAt(0);if(row.getChildCount()>0)row.getChildAt(0).requestFocus();}}
    private Button addPaging(String title,Runnable action){Button b=button(title,action);LayoutParams lp=new LayoutParams(LayoutParams.WRAP_CONTENT,px(54));lp.rightMargin=px(14);paging.addView(b,lp);return b;}
    private Button button(String title,Runnable action){Button b=new Button(getContext());b.setAllCaps(false);b.setText(title);b.setTextColor(TvTheme.TEXT);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(17));b.setBackground(TvTheme.control(unit,false));b.setOnFocusChangeListener(TvTheme::focus);b.setOnClickListener(v->action.run());return b;}
    private TextView label(String value,int size,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextColor(bold?TvTheme.TEXT:TvTheme.MUTED);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private int px(float n){return Math.round(n*unit);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();images=Executors.newFixedThreadPool(2);}
    @Override protected void onDetachedFromWindow(){if(images!=null)images.shutdownNow();images=null;artwork.evictAll();super.onDetachedFromWindow();}
    private void poster(ImageView view,String url){
        view.setTag(url);view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewDetachedFromWindow(View v){}
            @Override public void onViewAttachedToWindow(View v){
                Bitmap cached=artwork.get(url);if(cached!=null){view.setImageBitmap(cached);return;}
                if(url.isEmpty() || images==null || images.isShutdown())return;
                images.execute(()->{
                    try {
                        if(!view.isAttachedToWindow())return;
                        URL target=new URL(url);if(!target.getProtocol().equals("https") && !target.getProtocol().equals("http"))return;
                        URLConnection c=target.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>2*1024*1024)return;bytes.write(b,0,n);}}
                        byte[] data=bytes.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
                        o.inSampleSize=1;while(o.outWidth/o.inSampleSize>300 || o.outHeight/o.inSampleSize>450)o.inSampleSize*=2;
                        o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,o);
                        if(bitmap!=null)view.post(()->{if(view.isAttachedToWindow() && url.equals(view.getTag())){artwork.put(url,bitmap);view.setImageBitmap(bitmap);}});
                    }catch(Exception ignored){}
                });
            }
        });
    }
}
