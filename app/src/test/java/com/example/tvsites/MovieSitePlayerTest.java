package com.example.tvsites;

import android.app.Activity;
import android.webkit.WebView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={21,28})
public class MovieSitePlayerTest {
    private MovieCatalog.Entry source(){MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider="NINE";e.page="https://filmi9.com/film/example";e.embed="https://embed.test/player";return e;}
    private MovieSitePlayer.Events events(int[] count){return new MovieSitePlayer.Events(){public void ready(){count[0]++;}public void failed(){count[1]++;}public void captionsChanged(){count[2]++;}};}
    @Test public void opensOriginalPlayerDirectlyAndDoesNotExtractOrBlockItsMedia(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();WebView web=new WebView(activity);int[] count={0,0,0};MovieSitePlayer session=new MovieSitePlayer(web,source(),events(count));
        session.start();assertEquals("https://embed.test/player",Shadows.shadowOf(web).getLastLoadedUrl());
        assertNull(Shadows.shadowOf(web).getWebViewClient());
        session.console(MovieSitePlayer.PREFIX+"{\"type\":\"playing\"}");assertEquals(0,count[0]);
        session.console(MovieSitePlayer.PREFIX+"{\"type\":\"ready\"}");assertEquals(1,count[0]);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(25));assertEquals(0,count[1]);session.close();web.destroy();activity.finish();
    }
    @Test public void captionChoicesKeepAllLanguagesAndCommandsTargetTheirFrame(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();WebView web=new WebView(activity);int[] count={0,0,0};MovieSitePlayer session=new MovieSitePlayer(web,source(),events(count));session.start();
        session.console(MovieSitePlayer.PREFIX+"{\"type\":\"captions\",\"frame\":\"child\",\"backend\":\"jw\",\"tracks\":[{\"index\":1,\"label\":\"Bulgarian\",\"language\":\"bg\"},{\"index\":2,\"label\":\"English\",\"language\":\"en\",\"selected\":true}]}");
        assertEquals(2,session.captions().size());assertEquals("English",session.captions().get(1).label);assertTrue(session.captions().get(1).selected);
        session.choose(session.captions().get(1));String command=Shadows.shadowOf(web).getLastEvaluatedJavascript();assertTrue(command.contains("\"frame\":\"child\""));assertTrue(command.contains("\"index\":2"));
        session.off();assertTrue(Shadows.shadowOf(web).getLastEvaluatedJavascript().contains("\"action\":\"off\""));session.close();web.destroy();activity.finish();
    }
    @Test public void timeoutAndCancellationDoNotLeaveBackgroundSearch(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();WebView web=new WebView(activity);int[] count={0,0,0};MovieSitePlayer session=new MovieSitePlayer(web,source(),events(count));session.start();session.close();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));assertEquals(0,count[1]);
        MovieSitePlayer retry=new MovieSitePlayer(web,source(),events(count));retry.start();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(25));assertEquals(1,count[1]);web.destroy();activity.finish();
    }
    @Test public void readyPlayerBlocksUnrelatedTopLevelNavigation(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();WebView web=new WebView(activity);MovieSitePlayer session=new MovieSitePlayer(web,source(),events(new int[3]));session.start();
        assertTrue(session.allowsNavigation("https://redirected-player.test/watch"));session.console(MovieSitePlayer.PREFIX+"{\"type\":\"ready\"}");
        assertTrue(session.allowsNavigation("https://embed.test/another-player"));assertFalse(session.allowsNavigation("https://ad.test/buy"));assertTrue(session.allowsNavigation("https://player-cdn.test/watch",false));assertFalse(session.allowsNavigation("intent://other-app",false));assertFalse(session.allowsNavigation("intent://other-app"));session.close();web.destroy();activity.finish();
    }
    @Test public void originalPlayerReportsPositionWithoutASecondPlayer(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();WebView web=new WebView(activity);MovieSitePlayer session=new MovieSitePlayer(web,source(),events(new int[3]));session.start();
        session.console(MovieSitePlayer.PREFIX+"{\"type\":\"position\",\"position\":125,\"duration\":5400}");assertEquals(125000,session.position());assertEquals(5400000,session.duration());
        session.console(MovieSitePlayer.PREFIX+"{\"type\":\"position\",\"position\":5,\"duration\":30}");assertEquals(125000,session.position());
        session.seek(125000);assertTrue(Shadows.shadowOf(web).getLastEvaluatedJavascript().contains("\"position\":125"));session.close();web.destroy();activity.finish();
    }

}
