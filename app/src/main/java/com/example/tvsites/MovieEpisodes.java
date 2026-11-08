package com.example.tvsites;

import java.util.*;
import java.util.regex.*;

/** Provider-independent episode identity; unknown season/number are never guessed. */
final class MovieEpisodes {
    static int season(String text){
        Matcher m=Pattern.compile("(?iu)(?:season|сезон|sezon)[\\s_:/–-]*(\\d{1,3})|(?:^|[^a-z0-9])s(\\d{1,3})e\\d|(?:^|[^0-9])(\\d{1,3})x\\d+").matcher(text);
        if(!m.find())return -1;for(int i=1;i<=m.groupCount();i++)if(m.group(i)!=null)return Integer.parseInt(m.group(i));return -1;
    }
    static int number(String text){
        Matcher m=Pattern.compile("(?iu)(?:episode|епизод|tvsites-episode)[\\s_:=/–-]*(\\d{1,4})|s\\d+e(\\d{1,4})|\\d+x(\\d{1,4})").matcher(text);
        if(m.find()){for(int i=1;i<=m.groupCount();i++)if(m.group(i)!=null)return Integer.parseInt(m.group(i));}
        return text.trim().matches("[0-9]{1,4}")?Integer.parseInt(text.trim()):0;
    }
    static String baseTitle(String title){return MovieCatalog.normalize(title.replaceAll("(?iu)(?:season|сезон|sezon)[\\s_–-]*\\d+.*$","")
            .replaceAll("(?iu)\\bs\\d+e\\d+.*$","").replaceAll("\\s*\\((19|20)\\d{2}\\)\\s*$", ""));}
    static String seasonLabel(int season){return season<0?"Епизоди (сезонът не е указан)":season==0?"Специални епизоди":"Сезон "+season;}
    static final class Choice {
        final int season,number;final String name,key;final MovieCatalog.Movie movie=new MovieCatalog.Movie();
        Choice(MovieCatalog.Episode ep){season=ep.season;number=ep.number;name=number>0?"Епизод "+number:ep.name;key=id(ep.season,ep.number,ep.page);}
        String label(){return seasonLabel(season)+" · "+name;}
    }
    static String id(int season,int number,String page){return season+":"+(number>0?number:page);}
    static List<Choice> choices(MovieCatalog.Movie series,int language){
        Map<String,Choice> found=new LinkedHashMap<>();
        for(MovieCatalog.Entry source:series.versions(language))for(MovieCatalog.Episode ep:source.episodes){
            String key=id(ep.season,ep.number,ep.page);Choice choice=found.computeIfAbsent(key,k->new Choice(ep));
            choice.movie.parent=series;choice.movie.season=ep.season;choice.movie.episodeNumber=ep.number;choice.movie.episodeKey=key;
            boolean duplicate=false;for(MovieCatalog.Entry old:choice.movie.sources)duplicate|=old.page.equals(ep.page);if(duplicate)continue;
            MovieCatalog.Entry selected=MovieStore.decodeEntry(MovieStore.encodeEntry(source));selected.page=ep.page;selected.embed=ep.embed;
            selected.title=series.title()+" · "+choice.label();selected.episodes.clear();selected.seasons.clear();choice.movie.sources.add(selected);
        }
        List<Choice> out=new ArrayList<>(found.values());out.sort(Comparator.comparingInt((Choice c)->c.season).thenComparingInt(c->c.number));return out;
    }
    static SortedSet<Integer> seasons(MovieCatalog.Movie series,int language){
        SortedSet<Integer> out=new TreeSet<>();for(MovieCatalog.Entry e:series.versions(language)){
            for(MovieCatalog.Episode ep:e.episodes)out.add(ep.season);for(MovieCatalog.SeasonLink s:e.seasons)out.add(s.number);
        }return out;
    }
    static Choice next(MovieCatalog.Movie current,List<Choice> choices){
        boolean found=false;for(Choice choice:choices){
            if(found && choice.season==current.season)return choice;
            if(choice.key.equals(current.episodeKey))found=true;
        }return null;
    }
}
