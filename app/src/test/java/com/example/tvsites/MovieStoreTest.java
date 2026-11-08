package com.example.tvsites;

import android.app.Application;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MovieStoreTest {
    private void finished(MovieStore store)throws Exception{long end=System.currentTimeMillis()+3000;while(store.busy() && System.currentTimeMillis()<end)Thread.sleep(10);assertFalse("request did not finish",store.busy());}
    private String page(String prefix,String next){StringBuilder html=new StringBuilder("<a href='/genre/comedy/'>Комедия</a>");
        for(int i=0;i<20;i++)html.append("<a class='media-card' href='/movie/").append(prefix).append(i).append("/'><div class='media-title'>").append(prefix).append(i).append("</div><span class='media-year'>2020</span></a>");
        if(!next.isEmpty())html.append("<a class='next' href='").append(next).append("'>Следваща</a>");return html.toString();}
    @Test public void genreFetchesRemoteCategoryAndNextPageAppendsTwentyInOrder()throws Exception{
        Application context=RuntimeEnvironment.getApplication();new java.io.File(context.getFilesDir(),"movies-v1.json").delete();List<String> requests=Collections.synchronizedList(new ArrayList<>());
        MovieStore store=new MovieStore(context,()->{},url->{requests.add(url);if(!url.startsWith("https://zenoplay.to"))throw new IOException("offline provider");
            String body=url.endsWith("/movies/")?"<a href='/genre/comedy/'>Комедия</a>":url.contains("/page/2/")?page("second",""):page("first","/genre/comedy/page/2/");return Jsoup.parse(body,url);});
        try {
            store.request("",0,"Комедия",0,0,false);finished(store);assertEquals(20,store.entries().size());assertTrue(requests.contains("https://zenoplay.to/genre/comedy/"));assertTrue(store.hasMore());
            assertEquals("first0",store.entries().get(0).title);assertTrue(store.entries().get(0).genres.contains("Комедия"));
            store.request("",0,"Комедия",0,0,true);finished(store);assertEquals(40,store.entries().size());assertEquals("first0",store.entries().get(0).title);assertEquals("second0",store.entries().get(20).title);assertFalse(store.hasMore());
        } finally {store.close();}
    }
    @Test public void seasonSelectionFetchesOnlyTheSelectedSeasonAndKeepsEpisodeDestinations()throws Exception{
        Application context=RuntimeEnvironment.getApplication();new java.io.File(context.getFilesDir(),"movies-v1.json").delete();List<String> requests=Collections.synchronizedList(new ArrayList<>());
        MovieCatalog.Movie series=new MovieCatalog.Movie();MovieCatalog.Entry source=new MovieCatalog.Entry();source.provider="ZIP";source.series=true;source.title="Example";source.page="https://filmizip.com/seriali/2-example.html";
        source.seasons.add(new MovieCatalog.SeasonLink(1,"https://filmizip.com/seriali/1-example.html",false));source.seasons.add(new MovieCatalog.SeasonLink(3,"https://filmizip.com/seriali/3-example.html",false));series.sources.add(source);
        MovieStore store=new MovieStore(context,()->{},url->{requests.add(url);return Jsoup.parse("<h1>Example</h1><span data-tv_link='1'>01</span><span data-tv_link='2'>02</span>",url);});
        java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();
        try{
            store.seasonDetails(series,1,()->done.set(true));long deadline=System.currentTimeMillis()+3000;
            while(!done.get() && System.currentTimeMillis()<deadline){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
            assertTrue(done.get());assertEquals(Collections.singletonList("https://filmizip.com/seriali/1-example.html"),requests);
            MovieCatalog.Movie updated=store.find(series);assertEquals(2,updated.main().episodes.size());assertEquals(1,updated.main().episodes.get(0).season);assertTrue(updated.main().episodes.get(0).page.startsWith("https://filmizip.com/seriali/1-example.html#"));
        }finally{store.close();}
    }
}
