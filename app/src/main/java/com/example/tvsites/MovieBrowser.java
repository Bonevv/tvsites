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
    static final class State { String query="",genre=""; int language,year,sort,page,kind; }
    interface Actions {
        void fetch(boolean more);
        void open(MovieCatalog.Movie movie);
    }
    private final State state;
    private final Actions actions;
    private final float unit;
    private final LinearLayout grid, filters, paging;
    private final TextView count,status;
    static final int PAGE_SIZE=20;
    private final Button search,language,genre,year,sort,refresh,kind;
    private List<MovieCatalog.Movie> movies=new ArrayList<>();
    private boolean busy,hasMore=true,artworkPaused;
    private int pendingPage=-1;
    private List<String> availableGenres=new ArrayList<>();
    private final ArrayList<ImageView> posters=new ArrayList<>();
    private final MovieArtwork artwork;
    private String rendered="";
    private final Map<String,MovieCatalog.Movie> displayedMovies=new HashMap<>();
    private final Map<String,TextView> metadata=new HashMap<>(),languages=new HashMap<>();
    private final Runnable visiblePosters=this::loadVisiblePosters;
    private void loadVisiblePosters(){
        if(artworkPaused || !isShown())return;
        android.graphics.Rect rect=new android.graphics.Rect();
        for(ImageView poster:posters){
            if(poster.getDrawable()!=null)continue;
            if(poster.getGlobalVisibleRect(rect) && rect.width()>0 && rect.height()>0)artwork.show(poster,(String)poster.getTag());
            else artwork.cancel(poster);
        }
    }
    private final android.view.ViewTreeObserver.OnScrollChangedListener scrollListener=()->schedulePosters();
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener layoutListener=()->schedulePosters();
    MovieBrowser(Context context,float unit,State state,Actions actions) {
        super(context);this.unit=unit;this.state=state;this.actions=actions;artwork=MovieArtwork.get(context);
        setOrientation(VERTICAL);setClipChildren(false);
        TextView heading=label("Филми и сериали",38,true);addView(heading);
        TextView hint=label("Един филм, всички налични източници. OK — подробности и гледане.",18,false);addView(hint);
        filters=new LinearLayout(context);filters.setPadding(0,px(20),0,px(16));
        kind=button("Вид",this::kind);search=button("Търсене",this::search);language=button("Език",this::language);
        genre=button("Жанр",this::genre);year=button("Година",this::year);sort=button("Подредба",this::sort);
        for(Button b:new Button[]{search,kind,language,genre,year,sort}){LinearLayout.LayoutParams lp=new LayoutParams(0,px(55),1);lp.rightMargin=px(10);filters.addView(b,lp);}
        addView(filters,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        count=label("",19,false);addView(count);
        grid=new LinearLayout(context);grid.setOrientation(VERTICAL);grid.setClipChildren(false);addView(grid);
        paging=new LinearLayout(context);paging.setPadding(0,px(18),0,px(12));addView(paging);
        refresh=button("Обнови каталога",()->actions.fetch(false));addView(refresh,new LayoutParams(px(280),px(54)));
        status=label("",15,false);status.setPadding(0,px(12),0,0);addView(status);captions();
    }
    View first(){return search;}
    void update(List<MovieCatalog.Entry> entries,String message,boolean busy){update(entries,message,busy,true,Collections.emptyList());}
    void update(List<MovieCatalog.Entry> entries,String message,boolean busy,boolean more,List<String> genres){
        List<MovieCatalog.Entry> selected=new ArrayList<>();for(MovieCatalog.Entry entry:entries)if(state.kind==0 || (state.kind==2)==entry.series)selected.add(entry);
        movies=MovieCatalog.merge(selected);this.busy=busy;hasMore=more;availableGenres=genres;status.setText(message);
        refresh.setEnabled(!busy);refresh.setText(busy?"Обновяване…":"Обнови каталога");render();
    }
    private void captions(){
        kind.setText(new String[]{"Филми и сериали","Филми","Сериали"}[state.kind]);
        search.setText(state.query.isEmpty()?"⌕ Търсене":"⌕ "+state.query);
        language.setText(new String[]{"Всички езици","БГ аудио","БГ субтитри"}[state.language]);
        genre.setText(state.genre.isEmpty()?"Всички жанрове":state.genre);year.setText(state.year==0?"Всички години":""+state.year);
        sort.setText(new String[]{"Ред от сайтовете","По азбучен ред","По година"}[state.sort]);
    }
    private void changed(boolean fetch){state.page=0;pendingPage=-1;captions();render();if(fetch)actions.fetch(false);}
    void search(){
        EditText input=new EditText(getContext());input.setSingleLine(true);input.setText(state.query);input.setHint("Заглавие или думи от описанието");
        AlertDialog d=new AlertDialog.Builder(getContext()).setTitle("Търсене на филм").setView(input)
                .setPositiveButton("Търси",(v,w)->{state.query=input.getText().toString().trim();changed(true);})
                .setNeutralButton("Изчисти",(v,w)->{state.query="";changed(true);}).setNegativeButton("Отказ",null).create();d.show();input.requestFocus();
    }
    private void language(){new AlertDialog.Builder(getContext()).setTitle("Език на версията")
            .setSingleChoiceItems(new String[]{"Всички","БГ аудио","БГ субтитри"},state.language,(d,i)->{state.language=i;d.dismiss();changed(true);}).show();}
    private void kind(){new AlertDialog.Builder(getContext()).setTitle("Какво ще гледаме?").setSingleChoiceItems(new String[]{"Филми и сериали","Филми","Сериали"},state.kind,(d,i)->{state.kind=i;d.dismiss();changed(true);}).show();}
    private void genre(){TreeSet<String> values=new TreeSet<>(availableGenres);if(values.isEmpty())values.addAll(MovieSources.COMMON_GENRES);
        ArrayList<String> labels=new ArrayList<>();labels.add("Всички жанрове");labels.addAll(values);
        new AlertDialog.Builder(getContext()).setTitle("Жанрове от сайтовете").setItems(labels.toArray(new String[0]),(d,i)->{state.genre=i==0?"":labels.get(i);changed(true);}).show();}
    private void year(){TreeSet<Integer> values=new TreeSet<>(Collections.reverseOrder());for(MovieCatalog.Movie m:movies)if(m.year()>0)values.add(m.year());
        ArrayList<Integer> years=new ArrayList<>(values);ArrayList<String> labels=new ArrayList<>();labels.add("Всички години");for(int y:years)labels.add(""+y);
        new AlertDialog.Builder(getContext()).setTitle("Година").setItems(labels.toArray(new String[0]),(d,i)->{state.year=i==0?0:years.get(i-1);changed(true);}).show();}
    private void sort(){new AlertDialog.Builder(getContext()).setTitle("Подреждане")
            .setSingleChoiceItems(new String[]{"Ред от сайтовете","По азбучен ред","По година — новите първо"},state.sort,(d,i)->{state.sort=i;d.dismiss();changed(false);}).show();}
    private void render(){
        View focused=findFocus();Object tag=focused==null?null:focused.getTag();boolean inGrid=focused!=null && focused!=search && focused!=language && focused!=genre && focused!=year && focused!=sort && focused!=refresh && focused!=kind;
        paging.removeAllViews();
        List<MovieCatalog.Movie> result=MovieCatalog.filter(movies,state.query,state.language,state.genre,state.year,state.sort==0?3:state.sort);
        int pages=Math.max(1,(result.size()+PAGE_SIZE-1)/PAGE_SIZE);
        if(pendingPage>=0 && result.size()>pendingPage*PAGE_SIZE){state.page=pendingPage;pendingPage=-1;}
        else if(pendingPage>=0 && !busy)pendingPage=-1;
        state.page=Math.min(state.page,pages-1);
        count.setText(result.size()+" заглавия · страница "+(state.page+1)+""+(busy?" · обновяване…":"")
                +"\n20 заглавия на страница · "+(hasMore?"Следваща зарежда още от сайтовете":"Край на наличните резултати"));
        int begin=state.page*PAGE_SIZE,end=Math.min(result.size(),begin+PAGE_SIZE);
        StringBuilder signature=new StringBuilder("page:"+state.page+(result.isEmpty()?":"+busy:""));
        for(int i=begin;i<end;i++){MovieCatalog.Movie m=result.get(i);signature.append('|').append(m.main().page).append(m.title()).append(m.poster());}
        displayedMovies.clear();for(int i=begin;i<end;i++)displayedMovies.put(result.get(i).main().page,result.get(i));
        if(!rendered.equals(signature.toString())){
        rendered=signature.toString();for(ImageView poster:posters)artwork.cancel(poster);posters.clear();metadata.clear();languages.clear();grid.removeAllViews();
        if(result.isEmpty()) {
            TextView empty=label(busy?"Зареждане на филмите…":"Няма намерени филми. Промени филтрите или зареди още заглавия. Езикът се показва само когато е указан от източника.",22,false);
            empty.setPadding(0,px(45),0,px(45));grid.addView(empty);
        }
        LinearLayout row=null;
        for(int i=begin;i<end;i++){
            if(i%5==0){row=new LinearLayout(getContext());row.setClipChildren(false);LayoutParams rp=new LayoutParams(LayoutParams.MATCH_PARENT,px(395));rp.topMargin=px(20);grid.addView(row,rp);}
            MovieCatalog.Movie movie=result.get(i);LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setPadding(px(9),px(9),px(9),px(9));
            card.setFocusable(true);card.setClickable(true);card.setTag("movie:"+movie.main().page);card.setBackground(TvTheme.control(unit,false));card.setOnFocusChangeListener(TvTheme::focus);
            card.setOnClickListener(v->{MovieCatalog.Movie current=displayedMovies.get(movie.main().page);if(current!=null)actions.open(current);});card.setContentDescription(movie.title()+", "+movie.year()+", "+movie.languages()+", "+movie.sources.size()+" източника");
            ImageView poster=new ImageView(getContext());poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackgroundColor(TvTheme.BACKGROUND);card.addView(poster,new LayoutParams(LayoutParams.MATCH_PARENT,px(270)));poster.setTag(movie.poster());posters.add(poster);
            TextView title=label(movie.title(),20,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title,new LayoutParams(LayoutParams.MATCH_PARENT,px(53)));
            TextView meta=label((movie.main().series?"Сериал · ":"")+(movie.year()==0?"":movie.year()+" · ")+movie.sources.size()+" източника",14,false);card.addView(meta);metadata.put(movie.main().page,meta);
            TextView lang=label(movie.languages(),13,false);lang.setSingleLine(true);lang.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(lang);languages.put(movie.main().page,lang);
            LayoutParams cp=new LayoutParams(0,LayoutParams.MATCH_PARENT,1);if(i%5>0)cp.leftMargin=px(13);row.addView(card,cp);
        }
        if(row!=null)while(row.getChildCount()<5)row.addView(new View(getContext()),new LayoutParams(0,1,1));
        schedulePosters();
        }
        for(int i=begin;i<end;i++){
            MovieCatalog.Movie movie=result.get(i);String key=movie.main().page;
            TextView meta=metadata.get(key),lang=languages.get(key);
            String caption=(movie.main().series?"Сериал · ":"")+(movie.year()==0?"":movie.year()+" · ")+movie.sources.size()+" източника";
            if(meta!=null && !meta.getText().toString().equals(caption))meta.setText(caption);
            if(lang!=null && !lang.getText().toString().equals(movie.languages()))lang.setText(movie.languages());
        }
        if(state.page>0)addPaging("← Предишна",()->{state.page--;render();focusGrid();});
        Button more=addPaging(busy && state.page+1>=pages?"Зареждане…":"Следваща →",()->{
            if(state.page+1<pages){state.page++;render();focusGrid();}
            else if(hasMore && !busy){pendingPage=state.page+1;actions.fetch(true);}
        });more.setEnabled(state.page+1<pages || (hasMore && !busy));
        if(inGrid){View restore=tag==null?null:findViewWithTag(tag);if(restore!=null)restore.requestFocus();else more.requestFocus();}
    }
    private void focusGrid(){if(grid.getChildCount()>0 && grid.getChildAt(0) instanceof ViewGroup){ViewGroup row=(ViewGroup)grid.getChildAt(0);if(row.getChildCount()>0)row.getChildAt(0).requestFocus();}}
    private Button addPaging(String title,Runnable action){Button b=button(title,action);LayoutParams lp=new LayoutParams(LayoutParams.WRAP_CONTENT,px(54));lp.rightMargin=px(14);paging.addView(b,lp);return b;}
    private Button button(String title,Runnable action){Button b=new Button(getContext());b.setAllCaps(false);b.setText(title);b.setTextColor(TvTheme.TEXT);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(17));b.setBackground(TvTheme.control(unit,false));b.setOnFocusChangeListener(TvTheme::focus);b.setOnClickListener(v->action.run());return b;}
    private TextView label(String value,int size,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextColor(bold?TvTheme.TEXT:TvTheme.MUTED);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private int px(float n){return Math.round(n*unit);}
    void pauseArtwork(){artworkPaused=true;removeCallbacks(visiblePosters);for(ImageView poster:posters){artwork.cancel(poster);poster.setImageDrawable(null);}}
    private void schedulePosters(){if(artworkPaused)return;removeCallbacks(visiblePosters);postDelayed(visiblePosters,100);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();artworkPaused=false;getViewTreeObserver().addOnScrollChangedListener(scrollListener);getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);schedulePosters();}
    @Override protected void onDetachedFromWindow(){pauseArtwork();if(getViewTreeObserver().isAlive()){getViewTreeObserver().removeOnScrollChangedListener(scrollListener);getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);}super.onDetachedFromWindow();}
}
