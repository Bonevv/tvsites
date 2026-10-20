package com.example.tvsites;

import java.util.*;
import java.util.regex.*;

/** Metadata heuristics, never a claim that subtitle cues match spoken audio. */
final class SubtitleMatch {
    private static final Pattern EPISODE = Pattern.compile("(?i)(?<![a-z0-9])(?:s(\\d{1,2})[ ._-]*e(\\d{1,3})|(\\d{1,2})x(\\d{1,3}))(?!\\d)");
    private static final Pattern CONTINUATION = Pattern.compile("(?i)^([ ._]*-?[ ._]*e|[ ._]*-[ ._]*)(\\d{1,3})(?!\\d)");
    private static final Pattern SEASON = Pattern.compile("(?i)(?<![a-z0-9])(?:s|season[ ._-]*)(\\d{1,2})(?![a-z0-9])");
    private static final Pattern EP_ONLY = Pattern.compile("(?i)(?:^|[ ._-])(?:episode|ep|e)[ ._-]*(\\d{1,3})(?!\\d)|^0*(\\d{1,3})(?:[ ._-](?:bg|en|bul|eng))?\\.(?:srt|vtt|ass|ssa|sub)$");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");

    /** 1: explicit requested episode/season, -1: explicit conflict, 0: unknown. */
    static int episode(String name, int season, int episode) {
        Matcher m=EPISODE.matcher(name);boolean known=false;
        while(m.find()) {
            known=true;int s=Integer.parseInt(m.group(1)!=null?m.group(1):m.group(3));
            int first=Integer.parseInt(m.group(2)!=null?m.group(2):m.group(4));
            if(s==season && first==episode)return 1;
            String tail=name.substring(m.end());int previous=first;
            Matcher more=CONTINUATION.matcher(tail);
            while(more.find()) {
                int next=Integer.parseInt(more.group(2));
                boolean range=more.group(1).contains("-");
                if(s==season && (episode==next || range && episode>previous && episode<next))return 1;
                previous=next;tail=tail.substring(more.end());more=CONTINUATION.matcher(tail);
            }
        }
        if(known)return -1;
        Matcher only=EP_ONLY.matcher(name);
        if(only.find())return Integer.parseInt(only.group(1)!=null?only.group(1):only.group(2))==episode?1:-1;
        Matcher pack=SEASON.matcher(name);
        if(pack.find())return Integer.parseInt(pack.group(1))==season?0:-1;
        return 0;
    }

    static int rank(String release,String title,String original,String year,boolean tv,int season,int episode,String videoName) {
        int ep=tv?episode(release,season,episode):0;
        if(ep<0)return -10000;
        int score=ep>0?300:0;
        Set<String> tokens=tokens(release);
        score+=Math.max(overlap(tokens,tokens(title)),overlap(tokens,tokens(original)));
        if(!tv && year.matches("(?:19|20)\\d{2}")) {
            Matcher y=YEAR.matcher(release);
            if(y.find())score+=y.group().equals(year)?120:-80;
        }
        String source=source(videoName), candidate=source(release);
        if(!source.isEmpty() && !candidate.isEmpty())score+=source.equals(candidate)?100:-60;
        return score;
    }
    private static Set<String> tokens(String value) {
        Set<String> out=new HashSet<>();
        for(String token:value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))if(token.length()>1)out.add(token);
        return out;
    }
    private static int overlap(Set<String> release,Set<String> title) {
        if(title.isEmpty())return 0;int matched=0;for(String word:title)if(release.contains(word))matched++;
        return matched*100/title.size();
    }
    static String source(String value) {
        String text=value.toLowerCase(Locale.ROOT).replaceAll("[ ._-]","");
        if(text.contains("webrip"))return "WEBRip";
        if(text.contains("webdl"))return "WEB-DL";
        if(text.contains("bluray") || text.contains("bdrip") || text.contains("brrip"))return "BluRay";
        if(text.contains("hdtv"))return "HDTV";
        if(text.contains("dvdrip"))return "DVD";
        return "";
    }
    static int pick(String[] names,int[] sizes,boolean tv,int season,int episode,boolean auto) {
        int best=-1,bestScore=Integer.MIN_VALUE;
        for(int i=0;i<names.length;i++) {
            int ep=tv?episode(names[i],season,episode):0;
            if(auto && ep<0)continue;
            int score=ep>0?2:ep<0?-1:0;
            if(best<0 || score>bestScore || score==bestScore && sizes[i]>sizes[best]){best=i;bestScore=score;}
        }
        return best;
    }
    static String durationWarning(long subtitleEnd,long videoDuration) {
        if(subtitleEnd<=0 || videoDuration<=0)return null;
        long difference=videoDuration-subtitleEnd;
        if(difference < -90000)return "Субтитрите свършват "+(-difference/60000)+" мин. СЛЕД филма — може да са за друга версия";
        if(difference>Math.max(5*60000L,videoDuration*15/100))return "Субтитрите свършват "+(difference/60000)+" мин. ПРЕДИ края — може да са за друга версия";
        return null;
    }
    private SubtitleMatch() {}
}
