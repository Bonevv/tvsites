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
    interface Fetcher { org.jsoup.nodes.Document fetch(String url) throws IOException; }
    private final Fetcher fetcher;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicFile cache;
    private final LinkedHashMap<String,MovieCatalog.Entry> entries=new LinkedHashMap<>();
    private final Map<String,String> next=new LinkedHashMap<>(), errors=new LinkedHashMap<>();
    private Listener listener;
    private volatile int generation;
    private volatile boolean closed, busy;
    private volatile long refreshed;
    MovieStore(Context context,Listener listener){this(context,listener,MovieSources::fetch);}
    MovieStore(Context context,Listener listener,Fetcher fetcher) {
        this.fetcher=fetcher;this.listener=listener;cache=new AtomicFile(new File(context.getFilesDir(),"movies-v1.json"));
        worker.execute(()->{load();notifyChanged();});
    }
    private static final class Browse {
        final LinkedHashSet<String> urls=new LinkedHashSet<>();
        final LinkedHashMap<String,String> cursors=new LinkedHashMap<>();
    }
    private final LinkedHashMap<String,Browse> browsing=new LinkedHashMap<>();
    private final Map<String,Map<String,String>> categories=new LinkedHashMap<>();
    private String activeKey="";
    synchronized List<MovieCatalog.Entry> entries(){
        Browse browse=browsing.get(activeKey);List<MovieCatalog.Entry> out=new ArrayList<>();
        if(browse==null)return new ArrayList<>(entries.values());
        for(String url:browse.urls){MovieCatalog.Entry entry=entries.get(url);if(entry!=null)out.add(entry);}return out;
    }
    synchronized List<String> genres(){TreeSet<String> out=new TreeSet<>();for(Map<String,String> links:categories.values())out.addAll(links.keySet());return new ArrayList<>(out);}
    synchronized boolean hasMore(){Browse browse=browsing.get(activeKey);if(browse==null || browse.cursors.isEmpty())return true;for(String value:browse.cursors.values())if(!value.isEmpty())return true;return false;}
    synchronized String status(){return String.join(" · ",errors.values());}
    boolean busy(){return busy;}
    boolean stale(){return System.currentTimeMillis()-refreshed>6*60*60*1000L;}
    void pause(){++generation;busy=false;}
    void request(String query,int language,boolean more){request(query,language,"",0,0,more);}
    void request(String query,int language,String genre,int kind,int year,boolean more){
        if(closed)return;
        final String key=query.trim()+"|"+language+"|"+genre+"|"+kind+"|"+year;
        synchronized(this){activeKey=key;browsing.computeIfAbsent(key,k->new Browse());while(browsing.size()>8){String oldest=browsing.keySet().iterator().next();if(oldest.equals(key))break;browsing.remove(oldest);}errors.clear();}
        int gen=++generation;busy=true;notifyChanged();
        worker.execute(()->{
            boolean anySuccess=false;
            for(MovieSources.Provider p:MovieSources.Provider.values()) {
                if(closed || gen!=generation)return;
                try {
                    String start=p.start(query,language);
                    if(!genre.isEmpty() && query.trim().isEmpty()){
                        Map<String,String> links;
                        synchronized(this){links=categories.get(p.name());}
                        if(links==null || !links.containsKey(genre)){
                            org.jsoup.nodes.Document menu=fetcher.fetch(p.base+p.catalog);
                            links=MovieSources.genres(p,menu);synchronized(this){categories.put(p.name(),links);}
                        }
                        start=links.get(genre);
                        if(start==null){synchronized(this){errors.put(p.name(),p.label+": няма тази категория");browsing.get(key).cursors.put(p.name(),"");}continue;}
                    }
                    List<String> feeds=new ArrayList<>();
                    if(!genre.isEmpty() || !query.trim().isEmpty())feeds.add(start);
                    else {if(kind!=2)feeds.add(start);if(kind!=1)feeds.add(p.seriesStart());}
                    for(int feed=0;feed<feeds.size();feed++){
                        if(closed || gen!=generation)return;
                        String slot=p.name()+":"+feed,url;
                        synchronized(this){url=more?browsing.get(key).cursors.get(slot):null;}
                        if("".equals(url))continue;if(url==null)url=feeds.get(feed);
                        org.jsoup.nodes.Document doc=fetcher.fetch(url);
                        if(!p.owns(doc.location()))throw new IOException("Източникът е пренасочен");
                        MovieSources.Page page=MovieSources.parse(p,doc);
                        if(closed || gen!=generation)return;
                        anySuccess|=!page.entries.isEmpty();
                        synchronized(this){
                            categories.computeIfAbsent(p.name(),k->new LinkedHashMap<>()).putAll(page.genres);
                            long now=System.currentTimeMillis();Browse browse=browsing.get(key);
                            for(int i=0;i<page.entries.size();i++){
                                MovieCatalog.Entry entry=page.entries.get(i);
                                if(!genre.isEmpty() && query.trim().isEmpty())entry.genres.add(genre);
                                if(kind==1 && entry.series || kind==2 && !entry.series)continue;
                                put(entry,now-i);browse.urls.add(entry.page);
                            }
                            if(more || !browse.cursors.containsKey(slot) || browse.cursors.get(slot).isEmpty())browse.cursors.put(slot,page.next);
                            errors.put(p.name(),p.label+": зареден каталог");
                        }
                        notifyChanged();
                    }
                } catch(Exception ex) {
                    if(closed || gen!=generation)return;
                    synchronized(this){errors.put(p.name(),p.label+": недостъпен"+(ex instanceof org.jsoup.HttpStatusException?" (HTTP "+((org.jsoup.HttpStatusException)ex).getStatusCode()+")":""));}
                }
            }
            if(gen==generation && !closed){busy=false;if(anySuccess)refreshed=System.currentTimeMillis();save();notifyChanged();}
        });
    }
    void details(MovieCatalog.Movie movie,Runnable done){
        if(closed)return;
        List<MovieCatalog.Entry> sources=new ArrayList<>(movie.sources);
        worker.execute(()->{
            Set<String> seriesProviders=new HashSet<>();
            for(MovieCatalog.Entry e:sources){
                if(closed)return;if(e.detailed && !(e.embed.isEmpty() && (e.provider.equals("NINE") || e.provider.equals("ALL"))))continue;if(e.series && !seriesProviders.add(e.provider))continue;
                try {
                    org.jsoup.nodes.Document doc=fetcher.fetch(e.page);
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
            if(old.detailed){MovieCatalog.Entry copy=decodeEntry(encodeEntry(old));copy.bgAudio|=e.bgAudio;copy.bgSubs|=e.bgSubs;copy.genres.addAll(e.genres);entries.put(e.page,copy);return;}
            e.added=old.added;e.genres.addAll(old.genres);e.bgAudio|=old.bgAudio;e.bgSubs|=old.bgSubs;
        }else e.added=added;
        entries.put(e.page,e);
        while(entries.size()>5000)entries.remove(entries.keySet().iterator().next());
    }
    private final Runnable publish=()->{if(!closed && listener!=null)listener.changed();};
    private void notifyChanged(){ui.removeCallbacks(publish);ui.postDelayed(publish,busy?250:0);}
    private void load(){
        try(InputStream in=cache.openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){if(bytes.size()+n>12*1024*1024)throw new IOException("cache too large");bytes.write(buf,0,n);}
            JSONObject root=new JSONObject(bytes.toString("UTF-8"));JSONArray list=root.getJSONArray("entries");
            synchronized(this){for(int i=0;i<Math.min(5000,list.length());i++){MovieCatalog.Entry e=decodeEntry(list.getJSONObject(i));
                try{if(MovieSources.isTitle(MovieSources.Provider.valueOf(e.provider),e.page))entries.put(e.page,e);}catch(Exception ignored){}}
                refreshed=root.optLong("refreshed");
                JSONObject saved=root.optJSONObject("browsing");
                if(saved!=null){Iterator<String> keys=saved.keys();while(keys.hasNext() && browsing.size()<8){String key=keys.next();JSONObject item=saved.getJSONObject(key);Browse browse=browsing.computeIfAbsent(key,k->new Browse());
                    JSONArray urls=item.optJSONArray("urls");if(urls!=null)for(int i=0;i<urls.length();i++)if(entries.containsKey(urls.optString(i)))browse.urls.add(urls.optString(i));
                    JSONObject cursors=item.optJSONObject("cursors");if(cursors!=null){Iterator<String> slots=cursors.keys();while(slots.hasNext()){String slot=slots.next(),url=cursors.optString(slot);try{MovieSources.Provider provider=MovieSources.Provider.valueOf(slot.split(":")[0]);if(url.isEmpty() || provider.owns(url))browse.cursors.put(slot,url);}catch(Exception ignored){}}}
                }}
                JSONObject links=root.optJSONObject("categories");if(links!=null)for(MovieSources.Provider provider:MovieSources.Provider.values()){
                    JSONObject item=links.optJSONObject(provider.name());if(item!=null){Map<String,String> map=new LinkedHashMap<>();Iterator<String> keys=item.keys();while(keys.hasNext()){String key=keys.next(),url=item.optString(key);if(provider.owns(url))map.put(key,url);}categories.put(provider.name(),map);}
                }
            }

        }catch(Exception ignored){} // Missing/corrupt cache is recovered by a fresh scan.
    }
    private void save(){
        FileOutputStream out=null;
        try{
            JSONObject root=new JSONObject();JSONArray list=new JSONArray();
            synchronized(this){for(MovieCatalog.Entry e:entries.values())list.put(encodeEntry(e));root.put("refreshed",refreshed);JSONObject sessions=new JSONObject();for(Map.Entry<String,Browse> pair:browsing.entrySet())sessions.put(pair.getKey(),new JSONObject().put("urls",new JSONArray(pair.getValue().urls)).put("cursors",new JSONObject(pair.getValue().cursors)));
                root.put("browsing",sessions);JSONObject links=new JSONObject();for(Map.Entry<String,Map<String,String>> pair:categories.entrySet())links.put(pair.getKey(),new JSONObject(pair.getValue()));root.put("categories",links);}
            root.put("entries",list);out=cache.startWrite();out.write(root.toString().getBytes("UTF-8"));cache.finishWrite(out);
        }catch(Exception ex){if(out!=null)cache.failWrite(out);}
    }
    static JSONObject encodeEntry(MovieCatalog.Entry e){
        JSONObject o=new JSONObject();try{
            JSONArray episodes=new JSONArray();for(MovieCatalog.Episode ep:e.episodes)episodes.put(new JSONObject().put("name",ep.name).put("page",ep.page).put("embed",ep.embed));
            o.put("episodes",episodes).put("embed",e.embed);
            o.put("provider",e.provider).put("page",e.page).put("title",e.title).put("original",e.original).put("poster",e.poster)
                    .put("description",e.description).put("imdb",e.imdb).put("tmdb",e.tmdb).put("year",e.year)
                    .put("audio",e.bgAudio).put("subs",e.bgSubs).put("detailed",e.detailed).put("series",e.series).put("added",e.added).put("genres",new JSONArray(e.genres));
        }catch(JSONException impossible){throw new IllegalStateException(impossible);}return o;
    }
    static MovieCatalog.Entry decodeEntry(JSONObject o){
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider=o.optString("provider");e.page=o.optString("page");e.title=o.optString("title");
        e.original=o.optString("original");e.poster=o.optString("poster");e.description=o.optString("description");e.imdb=o.optString("imdb");e.tmdb=o.optString("tmdb");e.embed=o.optString("embed");
        JSONArray episodes=o.optJSONArray("episodes");if(episodes!=null)for(int i=0;i<episodes.length();i++){JSONObject ep=episodes.optJSONObject(i);if(ep!=null)e.episodes.add(new MovieCatalog.Episode(ep.optString("name"),ep.optString("page"),ep.optString("embed")));}
        e.year=o.optInt("year");e.bgAudio=o.optBoolean("audio");e.bgSubs=o.optBoolean("subs");e.detailed=o.optBoolean("detailed");e.series=o.optBoolean("series");e.added=o.optLong("added");
        JSONArray g=o.optJSONArray("genres");if(g!=null)for(int i=0;i<g.length();i++)e.genres.add(MovieSources.genreName(g.optString(i)));return e;
    }
    void close(){closed=true;++generation;listener=null;worker.shutdownNow();ui.removeCallbacksAndMessages(null);}
}
