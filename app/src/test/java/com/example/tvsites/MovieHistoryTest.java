package com.example.tvsites;
import android.content.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MovieHistoryTest {
    private MovieCatalog.Movie series(){
        MovieCatalog.Movie m=new MovieCatalog.Movie();for(String provider:new String[]{"NINE","ZENO"}){
            MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider=provider;e.page="https://"+provider+"/show";e.title="Пример Сезон 2";e.original="Example Season 2";e.series=true;
            for(int n=1;n<=2;n++)e.episodes.add(new MovieCatalog.Episode("Епизод "+n,e.page+"#episode="+n,"",2,n));m.sources.add(e);
        }return m;
    }
    @Test public void progressSurvivesRestartAndIsSharedAcrossEpisodeSourcesButNotOtherEpisodes(){
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("history-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();
        MovieHistory history=new MovieHistory(prefs);MovieCatalog.Movie series=series();List<MovieEpisodes.Choice> choices=MovieEpisodes.choices(series,0);
        history.save(choices.get(0).movie,125000,false);MovieHistory reloaded=new MovieHistory(prefs);
        assertEquals(125000,reloaded.position(choices.get(0).movie));assertEquals(0,reloaded.position(choices.get(1).movie));assertEquals(2,reloaded.last(series).optInt("season"));assertFalse(reloaded.watched(choices.get(0).movie));
        reloaded.save(choices.get(0).movie,5400000,true);assertTrue(reloaded.watched(choices.get(0).movie));assertEquals(0,reloaded.position(choices.get(0).movie));assertTrue(reloaded.last(series).optBoolean("ended"));
    }
    @Test public void workingSourceIsTriedFirstForThatEpisodeAndThenForTheSeries(){
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("source-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();MovieHistory history=new MovieHistory(prefs);
        List<MovieEpisodes.Choice> episodes=MovieEpisodes.choices(series(),0);MovieCatalog.Movie first=episodes.get(0).movie,next=episodes.get(1).movie;
        history.succeeded(first,first.sources.get(1));assertEquals("ZENO",history.preferred(first,first.sources).get(0).provider);assertEquals("ZENO",history.preferred(next,next.sources).get(0).provider);assertEquals("NINE",first.sources.get(0).provider);
        assertEquals(1,history.preferred(first,Collections.singletonList(first.sources.get(0))).size());
    }
}
