package com.example.tvsites;

import android.app.Application;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
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
}
