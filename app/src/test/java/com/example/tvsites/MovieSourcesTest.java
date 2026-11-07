package com.example.tvsites;

import org.jsoup.Jsoup;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class MovieSourcesTest {
    private String fixture(String name)throws Exception{try(InputStream in=getClass().getResourceAsStream("/movies/"+name+".html")){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    @Test public void allSixRealListingFormatsHaveUsableMovies()throws Exception{
        for(MovieSources.Provider p:MovieSources.Provider.values()){
            MovieSources.Page page=MovieSources.parse(p,p.base+p.catalog,fixture(p.name().toLowerCase()+"-list"));
            assertEquals(p.label,2,page.entries.size());
            for(MovieCatalog.Entry e:page.entries){assertFalse(e.title.isEmpty());assertEquals(p.label+" "+e.title,2026,e.year);assertTrue(e.poster.startsWith("https://"));assertTrue(MovieSources.isMovie(p,e.page));}
            assertEquals(p.base+(p==MovieSources.Provider.ZT?"/filmi/filmizt/29-2":p==MovieSources.Provider.FEN?"/filmi/page/2/":"/page/2/"),page.next);
        }
    }
    @Test public void originalAndTranslatedTitlesSurvive()throws Exception{
        MovieSources.Page page=MovieSources.parse(MovieSources.Provider.ALL,"https://vsichkifilmi.net/",fixture("all-list"));
        assertEquals("Tell Me Some Creepier Stories",page.entries.get(0).original);
        assertEquals("Разкажи ми някои по-зловещи истории",page.entries.get(0).title);
    }
    @Test public void detailsAllowSameFilmWithDifferentBulgarianTitlesToMerge()throws Exception{
        MovieCatalog.Entry zip=new MovieCatalog.Entry();zip.provider="ZIP";zip.page="https://filmizip.com/filmi/21811-model-citizen.html";
        zip=MovieSources.detail(zip,Jsoup.parse(fixture("zip-detail"),zip.page));
        MovieCatalog.Entry nine=new MovieCatalog.Entry();nine.provider="NINE";nine.page="https://filmi9.com/film/model-citizen-obrazcov-grazhdanin";
        nine=MovieSources.detail(nine,Jsoup.parse(fixture("nine-detail"),nine.page));
        assertEquals("Model Citizen",zip.original);assertEquals("Model Citizen",nine.original);assertEquals(2026,zip.year);assertEquals(2026,nine.year);
        assertTrue(MovieCatalog.same(zip,nine));assertFalse(zip.description.isEmpty());assertTrue(nine.genres.contains("Криминален"));
        // A page written in Bulgarian is not proof of Bulgarian audio.
        assertFalse(nine.bgAudio);
    }
    @Test public void zenoDetailsReadTmdbWithoutLeakingNavigationAudioLabel()throws Exception{
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider="ZENO";e.page="https://zenoplay.to/movie/obrazcov-grazhdanin-2026/";
        e=MovieSources.detail(e,Jsoup.parse(fixture("zeno-detail"),e.page));assertEquals("1792170",e.tmdb);assertFalse(e.bgAudio);assertTrue(e.detailed);
    }
    @Test public void fenSubtitleBadgeIsVersionSpecific()throws Exception{
        MovieSources.Page page=MovieSources.parse(MovieSources.Provider.FEN,"https://filmifen.com/filmi/",fixture("fen-list"));
        assertTrue(page.entries.get(0).bgSubs);assertFalse(page.entries.get(0).bgAudio);
    }
    @Test public void audioCategoryIsEvidenceAndDoesNotAffectOtherProviders()throws Exception{
        MovieSources.Page page=MovieSources.parse(MovieSources.Provider.ZENO,"https://zenoplay.to/genre/bg-audio/",fixture("zeno-list"));assertTrue(page.entries.get(0).bgAudio);
        page=MovieSources.parse(MovieSources.Provider.ZENO,"https://zenoplay.to/movies/",fixture("zeno-list"));assertFalse(page.entries.get(0).bgAudio);
    }
    @Test public void serialsAndForeignLinksAreExcluded(){
        String html="<div class='poster'><a href='/seriali/1-show.html'><img alt='Show'></a></div><div class='poster'><a href='https://ad.example/filmi/1-ad.html'>ad</a></div>";
        assertTrue(MovieSources.parse(MovieSources.Provider.ZIP,"https://filmizip.com/",html).entries.isEmpty());
    }
    @Test public void cacheRoundTripRetainsVersionMetadata(){
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.title="Филм";e.provider="FEN";e.bgSubs=true;e.genres.add("Драма");e.added=123;
        MovieCatalog.Entry restored=MovieStore.decodeEntry(MovieStore.encodeEntry(e));assertEquals(e.title,restored.title);assertEquals(e.genres,restored.genres);assertTrue(restored.bgSubs);assertEquals(123,restored.added);
    }
    @Test public void fenDetailPreservesTranslatedTitleAndOriginal()throws Exception{
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider="FEN";e.page="https://filmifen.com/filmi/kriminalni/14222-model-citizen.html";
        e=MovieSources.detail(e,Jsoup.parse(fixture("fen-detail"),e.page));
        assertEquals("Примерен гражданин",e.title);assertEquals("Model Citizen",e.original);assertFalse(e.bgAudio);
    }
    @Test public void ztNavigationCannotMarkUndubbedFilmAsBulgarian()throws Exception{
        MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider="ZT";e.page="https://filmizt.com/filmi/filmizt/example/29-1-0-10943";
        e=MovieSources.detail(e,Jsoup.parse(fixture("zt-detail"),e.page));
        assertEquals("Съперниците на Амзая Кинг",e.title);assertEquals("The Rivals of Amziah King",e.original);assertEquals("tt27706593",e.imdb);
        assertFalse(e.bgAudio);assertEquals(2026,e.year);assertTrue(e.genres.contains("Трилър"));
    }

}
