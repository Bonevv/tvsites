package com.example.tvsites;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MovieCatalogTest {
    private MovieCatalog.Entry entry(String title,int year,String page){MovieCatalog.Entry e=new MovieCatalog.Entry();e.title=title;e.year=year;e.page=page;return e;}
    @Test public void threeSourcesProduceOneCardAndKeepLanguagePerVersion(){
        MovieCatalog.Entry a=entry("Аватар",2009,"a"),b=entry("Avatar",2009,"b"),c=entry("Аватар",2009,"c");
        a.original="Avatar";a.bgAudio=true;b.bgSubs=true;c.bgAudio=true;
        List<MovieCatalog.Movie> result=MovieCatalog.merge(Arrays.asList(a,b,c));assertEquals(1,result.size());
        assertEquals(3,result.get(0).sources.size());assertEquals(2,result.get(0).versions(MovieCatalog.BG_AUDIO).size());
        assertSame(b,result.get(0).versions(MovieCatalog.BG_SUBS).get(0));
    }
    @Test public void remakesAndUnknownYearsStaySeparate(){
        assertEquals(3,MovieCatalog.merge(Arrays.asList(entry("Thing",1982,"a"),entry("Thing",2011,"b"),entry("Thing",0,"c"))).size());
    }
    @Test public void authoritativeIdsMatchAcrossTranslations(){
        MovieCatalog.Entry a=entry("Примерен гражданин",2026,"a"),b=entry("Образцов гражданин",2026,"b");a.tmdb="1792170";b.tmdb="1792170";
        assertEquals(1,MovieCatalog.merge(Arrays.asList(a,b)).size());
    }
    @Test public void conflictingIdsDoNotMergeThroughAliasBridge(){
        MovieCatalog.Entry a=entry("Same",2020,"a"),b=entry("Same",2020,"b"),c=entry("Same",2020,"c");a.imdb="tt0000001";c.imdb="tt0000002";
        assertEquals(2,MovieCatalog.merge(Arrays.asList(a,b,c)).size());
    }
    @Test public void searchAcceptsWordsInEitherTitleAndDescriptionWithFilters(){
        MovieCatalog.Entry a=entry("Аватар",2009,"a");a.original="Avatar";a.description="Пътешествие на далечна планета";a.bgAudio=true;a.genres.add("Фантастика");
        List<MovieCatalog.Movie> movies=MovieCatalog.merge(Collections.singletonList(a));
        assertEquals(1,MovieCatalog.filter(movies,"AVATAR планета",MovieCatalog.BG_AUDIO,"Фантастика",2009,0).size());
        assertTrue(MovieCatalog.filter(movies,"avatar",MovieCatalog.BG_SUBS,"",0,0).isEmpty());
        assertTrue(MovieCatalog.filter(movies,"avatar",0,"",2010,0).isEmpty());
    }
    @Test public void unknownAudioIsNeverTreatedAsBulgarian(){
        MovieCatalog.Entry a=entry("Film",2024,"a");assertFalse(a.accepts(MovieCatalog.BG_AUDIO));assertTrue(a.accepts(MovieCatalog.ALL));
    }
    @Test public void sortingIsDeterministic(){
        MovieCatalog.Entry a=entry("B",2000,"a"),b=entry("A",2020,"b");a.added=50;b.added=20;
        List<MovieCatalog.Movie> movies=MovieCatalog.merge(Arrays.asList(a,b));
        assertEquals("B",MovieCatalog.filter(movies,"",0,"",0,0).get(0).title());
        assertEquals("A",MovieCatalog.filter(movies,"",0,"",0,1).get(0).title());
        assertEquals("A",MovieCatalog.filter(movies,"",0,"",0,2).get(0).title());
    }
    @Test public void laterAliasBridgesAlreadyDiscoveredSources(){
        MovieCatalog.Entry a=entry("Превод А",2024,"a"),b=entry("Original",2024,"b"),c=entry("Превод А",2024,"c");c.original="Original";
        assertEquals(1,MovieCatalog.merge(Arrays.asList(a,b,c)).size());
    }
    @Test public void thousandsOfUnrelatedRecordsRemainSeparate(){
        List<MovieCatalog.Entry> records=new ArrayList<>();for(int i=0;i<3000;i++)records.add(entry("Film "+i,2024,"page-"+i));
        assertEquals(3000,MovieCatalog.merge(records).size());
    }

}
