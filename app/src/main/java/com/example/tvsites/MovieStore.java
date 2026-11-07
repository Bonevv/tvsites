package com.example.tvsites;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded disk cache, serialized I/O, and cancellable incremental provider scans. */
final class MovieStore {
    interface Listener { void changed(); }
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicFile cache;
    private final LinkedHashMap<String,MovieCatalog.Entry> entries=new LinkedHashMap<>();
    private final Map<String,String> next=new LinkedHashMap<>(), errors=new LinkedHashMap<>();
    private Listener listener;
    private volatile int generation;
    private volatile boolean closed, busy;
    private volatile long refreshed;
    MovieStore(Context context,Listener listener) {
        this.listener=listener;cache=new AtomicFile(new File(context.getFilesDir(),"movies-v1.json"));
        worker.execute(()->{load();notifyChanged();});
    }
    synchronized List<MovieCatalog.Entry> entries(){return new ArrayList<>(entries.values());}
    synchronized String status(){return String.join(" · ",errors.values());}
    boolean busy(){return busy;}
    boolean stale(){return System.currentTimeMillis()-refreshed>6*60*60*1000L;}
    void request(String query,int language,boolean more){
        if(closed)return;
        String key=query.trim()+"|"+language;
        boolean append=more;
        int gen=++generation;busy=true;notifyChanged();
        worker.execute(()->{
            boolean anySuccess=false;
            for(MovieSources.Provider p:MovieSources.Provider.values()) {
                if(closed || gen!=generation)return;
                String slot=key+"|"+p.name(),url;
                synchronized(this){url=append?next.get(slot):null;}
                if(append && "".equals(url))continue;
                if(url==null)url=p.start(query,language);
                try {
                    org.jsoup.nodes.Document doc=MovieSources.fetch(url);
                    if(!p.owns(doc.location()))throw new IOException("Източникът е пренасочен към друг сайт");
                    MovieSources.Page page=MovieSources.parse(p,doc);
                    if(closed || gen!=generation)return;
                    anySuccess|=!page.entries.isEmpty();
                    synchronized(this){
                        long now=System.currentTimeMillis();
                        // Preserve the site's order for records first discovered in the same batch.
                        for(int i=0;i<page.entries.size();i++)put(page.entries.get(i),now-i);
                        if(append || !next.containsKey(slot) || next.get(slot).isEmpty())next.put(slot,page.next);
                        while(next.size()>60)next.remove(next.keySet().iterator().next());
                        errors.put(p.name(),p.label+": "+page.entries.size()+" филма");
                    }
                } catch(Exception ex) {
                    if(closed || gen!=generation)return;
                    synchronized(this){errors.put(p.name(),p.label+": недостъпен"+(ex instanceof org.jsoup.HttpStatusException?" (HTTP "+((org.jsoup.HttpStatusException)ex).getStatusCode()+")":""));}
                }
                save();notifyChanged();
            }
            if(gen==generation && !closed){busy=false;if(anySuccess)refreshed=System.currentTimeMillis();save();notifyChanged();}
        });
    }
    void details(MovieCatalog.Movie movie,Runnable done){
        if(closed)return;
        List<MovieCatalog.Entry> sources=new ArrayList<>(movie.sources);
        worker.execute(()->{
            for(MovieCatalog.Entry e:sources){
                if(closed)return;if(e.detailed)continue;
                try {
                    org.jsoup.nodes.Document doc=MovieSources.fetch(e.page);
                    if(!MovieSources.Provider.valueOf(e.provider).owns(doc.location()))continue;
                    MovieCatalog.Entry detail=MovieSources.detail(e,doc);
                    synchronized(this){entries.put(e.page,detail);}
                }catch(Exception ignored){}
            }
            save();if(!closed)ui.post(done);
        });
    }
    synchronized MovieCatalog.Movie find(MovieCatalog.Movie old){
        for(MovieCatalog.Movie m:MovieCatalog.merge(entries.values()))
            for(MovieCatalog.Entry e:m.sources)for(MovieCatalog.Entry before:old.sources)if(e.page.equals(before.page))return m;
        return old;
    }
    private void put(MovieCatalog.Entry e,long added){
        MovieCatalog.Entry old=entries.get(e.page);
        if(old!=null){
            if(old.detailed){MovieCatalog.Entry copy=decodeEntry(encodeEntry(old));copy.bgAudio|=e.bgAudio;copy.bgSubs|=e.bgSubs;entries.put(e.page,copy);return;}
            e.added=old.added;
        }else e.added=added;
        entries.put(e.page,e);
        while(entries.size()>3000)entries.remove(entries.keySet().iterator().next());
    }
    private void notifyChanged(){ui.post(()->{if(!closed && listener!=null)listener.changed();});}
    private void load(){
        try(InputStream in=cache.openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){if(bytes.size()+n>12*1024*1024)throw new IOException("cache too large");bytes.write(buf,0,n);}
            JSONObject root=new JSONObject(bytes.toString("UTF-8"));JSONArray list=root.getJSONArray("entries");
            synchronized(this){for(int i=0;i<Math.min(3000,list.length());i++){MovieCatalog.Entry e=decodeEntry(list.getJSONObject(i));
                try{if(MovieSources.isMovie(MovieSources.Provider.valueOf(e.provider),e.page))entries.put(e.page,e);}catch(Exception ignored){}}
                refreshed=root.optLong("refreshed");
                JSONObject cursors=root.optJSONObject("cursors");
                if(cursors!=null){Iterator<String> keys=cursors.keys();while(keys.hasNext() && next.size()<60){String key=keys.next(),url=cursors.optString(key);
                    try{MovieSources.Provider p=MovieSources.Provider.valueOf(key.substring(key.lastIndexOf('|')+1));if(url.isEmpty() || p.owns(url))next.put(key,url);}catch(Exception ignored){}}
                }}
        }catch(Exception ignored){} // Missing/corrupt cache is recovered by a fresh scan.
    }
    private void save(){
        FileOutputStream out=null;
        try{
            JSONObject root=new JSONObject();JSONArray list=new JSONArray();
            synchronized(this){for(MovieCatalog.Entry e:entries.values())list.put(encodeEntry(e));root.put("refreshed",refreshed);root.put("cursors",new JSONObject(next));}
            root.put("entries",list);out=cache.startWrite();out.write(root.toString().getBytes("UTF-8"));cache.finishWrite(out);
        }catch(Exception ex){if(out!=null)cache.failWrite(out);}
    }
    static JSONObject encodeEntry(MovieCatalog.Entry e){
        JSONObject o=new JSONObject();try{
            o.put("provider",e.provider).put("page",e.page).put("title",e.title).put("original",e.original).put("poster",e.poster)
                    .put("description",e.description).put("imdb",e.imdb).put("tmdb",e.tmdb).put("year",e.year)
                    .put("audio",e.bgAudio).put("subs",e.bgSubs).put("detailed",e.detailed).put("added",e.added).put("genres",new JSONArray(e.genres));
        }catch(JSONException impossible){throw new IllegalStateException(impossible);}return o;
    }
    static MovieCatalog.Entry decodeEntry(JSONObject o){
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider=o.optString("provider");e.page=o.optString("page");e.title=o.optString("title");
        e.original=o.optString("original");e.poster=o.optString("poster");e.description=o.optString("description");e.imdb=o.optString("imdb");e.tmdb=o.optString("tmdb");
        e.year=o.optInt("year");e.bgAudio=o.optBoolean("audio");e.bgSubs=o.optBoolean("subs");e.detailed=o.optBoolean("detailed");e.added=o.optLong("added");
        JSONArray g=o.optJSONArray("genres");if(g!=null)for(int i=0;i<g.length();i++)e.genres.add(g.optString(i));return e;
    }
    void close(){closed=true;++generation;listener=null;worker.shutdownNow();ui.removeCallbacksAndMessages(null);}
}
