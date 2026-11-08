package com.example.tvsites;
import org.jsoup.Jsoup;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MovieEpisodesTest {
    private MovieCatalog.Entry source(String provider,String page){MovieCatalog.Entry e=new MovieCatalog.Entry();e.provider=provider;e.page=page;e.series=true;e.title="Example Season 2";return e;}
    @Test public void episodeIdentityMergesVersionsWithoutCollapsingDifferentSeasons(){
        MovieCatalog.Movie series=new MovieCatalog.Movie();MovieCatalog.Entry a=source("NINE","https://filmi9.com/serial/example");MovieCatalog.Entry b=source("ZENO","https://zenoplay.to/tv-show/example/");
        a.episodes.add(new MovieCatalog.Episode("Епизод 1",a.page+"#tvsites-episode=1","https://embed.test/1",2,1));
        b.episodes.add(new MovieCatalog.Episode("Епизод 1",b.page+"1x1","",1,1));b.episodes.add(new MovieCatalog.Episode("Епизод 1",b.page+"2x1","",2,1));
        series.sources.add(a);series.sources.add(b);List<MovieEpisodes.Choice> choices=MovieEpisodes.choices(series,0);
        assertEquals(2,choices.size());assertEquals(1,choices.get(0).season);assertEquals(2,choices.get(1).movie.sources.size());assertSame(series,choices.get(1).movie.parent);
        assertEquals("https://embed.test/1",choices.get(1).movie.main().embed);assertEquals(a.page,series.main().page);assertFalse(series.main().episodes.isEmpty());
    }
    @Test public void zenoplayUsesExplicitSeasonsAndNotDatabaseIds(){
        MovieCatalog.Entry e=source("ZENO","https://zenoplay.to/tv-show/example/");
        MovieSources.readEpisodes(e,Jsoup.parse("<section data-season-id='season-42755'><a class='episode-card' data-season-number='3' data-episode-number='2' href='/episode/example-3x2/'>Епизод 2</a></section><a class='episode-card' data-season-number='0' data-episode-number='1' href='/episode/special/'>Специален</a>",e.page));
        assertEquals(3,e.episodes.get(0).season);assertEquals(2,e.episodes.get(0).number);assertEquals(0,e.episodes.get(1).season);
        MovieCatalog.Movie movie=new MovieCatalog.Movie();movie.sources.add(e);assertFalse(MovieEpisodes.seasons(movie,0).contains(-1));
    }
    @Test public void nextPayloadKeepsEachSeasonsOwnPageAndEpisodes(){
        MovieCatalog.Entry e=source("NINE","https://filmi9.com/serial/example-season-2");
        String html="<script>{\"movie\":{\"title\":\"Example Season 2\",\"episodes\":[{\"episodeNumber\":1,\"videoUrl\":\"https://embed.test/s2e1\"}]},\"seasons\":[{\"title\":\"Example Season 1\",\"slug\":\"example-season-1\",\"episodes\":[{\"episodeNumber\":2,\"videoUrl\":\"https://embed.test/s1e2\"}]}]}</script>";
        MovieSources.readEpisodes(e,Jsoup.parse(html,e.page));assertEquals(2,e.episodes.size());
        assertEquals(2,e.episodes.get(0).season);assertEquals(1,e.episodes.get(1).season);assertTrue(e.episodes.get(1).page.contains("example-season-1#"));assertEquals("https://embed.test/s1e2",e.episodes.get(1).embed);
        MovieCatalog.Entry cached=MovieStore.decodeEntry(MovieStore.encodeEntry(e));assertEquals(1,cached.episodes.get(1).season);assertTrue(cached.seasons.get(0).loaded);assertEquals(2,cached.episodeSchema);
    }
    @Test public void filmizipDisabledFutureEpisodesAreExcludedAndRelatedShowsAreExcluded(){
        MovieCatalog.Entry e=source("ZIP","https://filmizip.com/seriali/2-example.html");e.title="Пример";e.original="Example";
        String html="<div class='poster'><a href='/seriali/2-example.html'><div class='poster__title'>Пример</div><div>Сезон 2</div></a></div><span data-tv_link='1'>01</span><span data-tv_link='2'>02</span><span class='disabled'>03</span><div class='poster'><a href='/seriali/1-example.html'><img alt='Example'><div class='poster__title'>Пример</div>Сезон 1</a></div><div class='poster'><a href='/seriali/3-other.html'><img alt='Other'><div class='poster__title'>Друг</div>Сезон 3</a></div>";
        MovieSources.readEpisodes(e,Jsoup.parse(html,e.page));assertEquals(2,e.episodes.size());assertEquals(2,e.episodes.get(0).season);assertTrue(e.episodes.get(1).page.endsWith("#tvsites-episode=2"));
        assertTrue(e.seasons.stream().anyMatch(link->link.number==1 && !link.loaded));assertFalse(e.seasons.stream().anyMatch(link->link.number==3));
    }
    @Test public void filmifenCurrentEpisodeIsIncludedAndSeasonNumberComesFromUrl(){
        MovieCatalog.Entry e=source("FEN","https://filmifen.com/seriali/15-example-season-4-episode-8.html");
        MovieSources.readEpisodes(e,Jsoup.parse("<a class='series-episode' href='/14-example-season-4-episode-7.html'>7</a><span class='series-episode' aria-current='page'>8</span>",e.page));
        assertEquals(2,e.episodes.size());assertEquals(4,e.episodes.get(1).season);assertEquals(8,e.episodes.get(1).number);assertEquals(e.page,e.episodes.get(1).page);
    }
    @Test public void metadataWithoutSeasonIsKeptUnknown(){
        assertEquals(-1,MovieEpisodes.season("A Movie 2025"));assertEquals(4,MovieEpisodes.season("show-4x12/"));assertEquals(12,MovieEpisodes.number("show-4x12/"));assertEquals(2,MovieEpisodes.season("Пример Сезон 2"));
    }
    @Test public void filmiztPublicPlayerArraysAreEpisodeVersionsOfTheSameSeason(){
        MovieCatalog.Entry e=source("ZT","https://filmizt.com/filmi/seriali/example_season_2/27-1-0-1");
        MovieSources.readEpisodes(e,Jsoup.parse("<script>vk.init();vk.show(2,[['https://player.test/#ep1','https://player.test/#ep2'],['https://other.test/#ep1']]);</script>",e.page));
        assertEquals(3,e.episodes.size());MovieCatalog.Movie series=new MovieCatalog.Movie();series.sources.add(e);List<MovieEpisodes.Choice> choices=MovieEpisodes.choices(series,0);
        assertEquals(2,choices.size());assertEquals(2,choices.get(0).movie.sources.size());assertEquals("https://player.test/#ep2",choices.get(1).movie.main().embed);assertEquals(2,choices.get(1).season);
    }
}
