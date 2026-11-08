package com.example.tvsites;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Progress is shared by equivalent episode versions, while player URLs remain in the catalogue. */
final class MovieHistory {
    private final SharedPreferences prefs;
    MovieHistory(SharedPreferences prefs){this.prefs=prefs;}
    private Set<String> aliases(MovieCatalog.Movie movie){
        Set<String> out=new LinkedHashSet<>();
        for(MovieCatalog.Entry e:movie.sources){out.add("page:"+e.page);
            if(e.series)for(String title:new String[]{e.title,e.original}){String base=MovieEpisodes.baseTitle(title);if(!base.isEmpty())out.add("series:"+base);}
        }return out;
    }
    private Set<String> keys(MovieCatalog.Movie movie){
        Set<String> out=new LinkedHashSet<>();for(String alias:aliases(movie.parent==null?movie:movie.parent))out.add(alias+(movie.parent==null?"":":"+movie.episodeKey));return out;
    }
    long position(MovieCatalog.Movie movie){
        long position=0;for(String key:keys(movie))position=Math.max(position,prefs.getLong("movie:progress:"+key,0));
        for(MovieCatalog.Entry e:movie.sources)position=Math.max(position,prefs.getLong("movie:pos:"+e.page,0));return position;
    }
    boolean watched(MovieCatalog.Movie movie){for(String key:keys(movie))if(prefs.getBoolean("movie:watched:"+key,false))return true;return false;}
    void save(MovieCatalog.Movie movie,long position,boolean ended){
        SharedPreferences.Editor edit=prefs.edit();
        for(String key:keys(movie)){edit.putLong("movie:progress:"+key,ended?0:position);if(ended)edit.putBoolean("movie:watched:"+key,true);else if(position>=30000)edit.putBoolean("movie:watched:"+key,false);}
        for(MovieCatalog.Entry e:movie.sources)edit.putLong("movie:pos:"+e.page,ended?0:position);
        if(movie.parent!=null && (position>=1000 || ended))try{
            JSONObject last=new JSONObject().put("season",movie.season).put("number",movie.episodeNumber).put("key",movie.episodeKey).put("ended",ended).put("at",System.currentTimeMillis());
            for(String alias:aliases(movie.parent))edit.putString("movie:last:"+alias,last.toString());
        }catch(JSONException ignored){}edit.apply();
    }
    JSONObject last(MovieCatalog.Movie series){
        JSONObject latest=null;for(String alias:aliases(series))try{
            JSONObject value=new JSONObject(prefs.getString("movie:last:"+alias,"{}"));if(value.has("key") && (latest==null || value.optLong("at")>latest.optLong("at")))latest=value;
        }catch(JSONException ignored){}return latest;
    }
    List<MovieCatalog.Entry> preferred(MovieCatalog.Movie movie,List<MovieCatalog.Entry> plan){
        String provider="";for(String key:keys(movie)){provider=prefs.getString("movie:source:"+key,"");if(!provider.isEmpty())break;}
        if(provider.isEmpty() && movie.parent!=null)for(String key:aliases(movie.parent)){provider=prefs.getString("movie:source:"+key,"");if(!provider.isEmpty())break;}
        List<MovieCatalog.Entry> out=new ArrayList<>(plan);final String first=provider;out.sort(Comparator.comparingInt(e->e.provider.equals(first)?0:1));return out;
    }
    void succeeded(MovieCatalog.Movie movie,MovieCatalog.Entry source){
        SharedPreferences.Editor edit=prefs.edit();for(String key:keys(movie))edit.putString("movie:source:"+key,source.provider);
        // A newly opened episode also benefits from the working source of its series.
        if(movie.parent!=null)for(String key:aliases(movie.parent))edit.putString("movie:source:"+key,source.provider);edit.apply();
    }
}
