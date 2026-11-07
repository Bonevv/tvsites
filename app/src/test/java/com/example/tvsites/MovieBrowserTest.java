package com.example.tvsites;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={21,28})
public class MovieBrowserTest {
    private int buttons(View view,String caption){int n=view instanceof Button && ((Button)view).getText().toString().equals(caption)?1:0;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)n+=buttons(g.getChildAt(i),caption);}return n;}
    @Test public void languageFilterAndPagingStayInNativeUi(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();MovieBrowser.State state=new MovieBrowser.State();state.language=MovieCatalog.BG_AUDIO;
        MovieBrowser browser=new MovieBrowser(activity,1,state,new MovieBrowser.Actions(){public void fetch(boolean more){}public void open(MovieCatalog.Movie movie){}});
        activity.setContentView(browser);List<MovieCatalog.Entry> entries=new ArrayList<>();
        for(int i=0;i<26;i++){MovieCatalog.Entry e=new MovieCatalog.Entry();e.title="Филм "+i;e.page="https://example.test/"+i;e.year=2020;e.bgAudio=true;entries.add(e);}
        MovieCatalog.Entry unknown=new MovieCatalog.Entry();unknown.title="Unknown";unknown.page="unknown";entries.add(unknown);
        browser.update(entries,"",false);assertEquals(1,buttons(browser,"Следваща →"));assertNotNull(browser.findViewWithTag("movie:https://example.test/0"));assertNull(browser.findViewWithTag("movie:unknown"));
        state.page=1;browser.update(entries,"",false);assertEquals(1,buttons(browser,"← Предишна"));
        state.query="не съществува";browser.update(entries,"",false);assertNull(browser.findViewWithTag("movie:https://example.test/0"));
        activity.finish();
    }
    @Test public void resolverAcceptsSignedMediaAndRejectsSegmentsAndTrailers(){
        assertTrue(MovieResolver.media("https://cdn.example/master.m3u8?token=abc"));assertTrue(MovieResolver.media("https://cdn.example/movie.mp4"));
        assertFalse(MovieResolver.media("https://cdn.example/trailer.mp4"));assertFalse(MovieResolver.media("https://cdn.example/part.ts"));assertFalse(MovieResolver.media("blob:https://example/123"));
    }
    @Test public void closingHiddenResolverRemovesItsPageAndDoesNotCallFallback(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();FrameLayout root=new FrameLayout(activity);activity.setContentView(root);
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider="ZIP";e.page="https://filmizip.com/filmi/1-film.html";
        int[] callbacks={0};MovieResolver resolver=new MovieResolver(root,e,"",host->false,new MovieResolver.Callback(){public void found(MovieResolver.Stream stream){callbacks[0]++;}public void failed(){callbacks[0]++;}});
        resolver.start();assertEquals(1,root.getChildCount());resolver.close();assertEquals(0,root.getChildCount());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(35));assertEquals(0,callbacks[0]);activity.finish();
    }
    @Test public void detectedPlaylistClosesHiddenPageAndPreservesRequestHeaders(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();FrameLayout root=new FrameLayout(activity);activity.setContentView(root);
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider="ZIP";e.page="https://filmizip.com/filmi/1-film.html";
        MovieResolver.Stream[] result={null};MovieResolver resolver=new MovieResolver(root,e,"",host->false,new MovieResolver.Callback(){
            public void found(MovieResolver.Stream stream){result[0]=stream;}public void failed(){fail("Unexpected failure");}
        });
        resolver.start();android.webkit.WebView web=(android.webkit.WebView)root.getChildAt(0);
        android.webkit.WebResourceRequest request=new android.webkit.WebResourceRequest(){
            public android.net.Uri getUrl(){return android.net.Uri.parse("https://cdn.test/master.m3u8?token=signed");}
            public boolean isForMainFrame(){return false;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "GET";}
            public Map<String,String> getRequestHeaders(){Map<String,String> h=new HashMap<>();h.put("Referer",e.page);h.put("Origin","https://filmizip.com");return h;}
        };
        assertNotNull(org.robolectric.Shadows.shadowOf(web).getWebViewClient().shouldInterceptRequest(web,request));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1100));
        assertNotNull(result[0]);assertEquals("https://filmizip.com",result[0].headers.get("Origin"));assertEquals(e.page,result[0].headers.get("Referer"));assertEquals(0,root.getChildCount());activity.finish();
    }

    @Test public void pageHasTwentyCardsAndUnchangedUpdatesReuseThem(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();MovieBrowser.State state=new MovieBrowser.State();
        MovieBrowser browser=new MovieBrowser(activity,1,state,new MovieBrowser.Actions(){public void fetch(boolean more){}public void open(MovieCatalog.Movie movie){}});activity.setContentView(browser);
        List<MovieCatalog.Entry> entries=new ArrayList<>();for(int i=0;i<40;i++){MovieCatalog.Entry e=new MovieCatalog.Entry();e.title="Title "+i;e.page="page-"+i;entries.add(e);}
        browser.update(entries,"",false);View first=browser.findViewWithTag("movie:page-0");assertNotNull(first);assertNotNull(browser.findViewWithTag("movie:page-19"));assertNull(browser.findViewWithTag("movie:page-20"));
        MovieCatalog.Entry alternate=new MovieCatalog.Entry();alternate.title="Title 0";alternate.page="another-source";entries.get(0).year=2020;alternate.year=2020;entries.add(alternate);
        browser.update(entries,"updated status",true);assertSame(first,browser.findViewWithTag("movie:page-0"));
        state.page=1;browser.update(entries,"",false);assertNull(browser.findViewWithTag("movie:page-0"));assertNotNull(browser.findViewWithTag("movie:page-20"));activity.finish();
    }
    @Test public void consoleDiscoveryAcceptsExtensionlessManifestAndPreservesMime(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();FrameLayout root=new FrameLayout(activity);activity.setContentView(root);
        MovieCatalog.Entry entry=new MovieCatalog.Entry();entry.provider="ZIP";entry.page="https://filmizip.com/filmi/test.html";
        MovieResolver.Stream[] result={null};MovieResolver resolver=new MovieResolver(root,entry,"",host->false,new MovieResolver.Callback(){public void found(MovieResolver.Stream stream){result[0]=stream;}public void failed(){fail("unexpected timeout");}});
        resolver.start();android.webkit.WebView web=(android.webkit.WebView)root.getChildAt(0);
        String json="{\"url\":\"https://cdn.test/watch?id=1\",\"mime\":\"application/vnd.apple.mpegurl\",\"page\":\"https://embed.test/player\"}";
        org.robolectric.Shadows.shadowOf(web).getWebChromeClient().onConsoleMessage(new android.webkit.ConsoleMessage(MovieResolver.MEDIA_PREFIX+json,"",1,android.webkit.ConsoleMessage.MessageLevel.LOG));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));assertNotNull(result[0]);assertEquals("application/x-mpegURL",result[0].mime);assertEquals("https://embed.test/player",result[0].headers.get("Referer"));activity.finish();
    }

}
