package com.example.tvsites;

import java.text.Normalizer;
import java.util.*;

/** Source records are kept separately: language belongs to a version, not the whole film. */
final class MovieCatalog {
    static final int ALL = 0, BG_AUDIO = 1, BG_SUBS = 2;
    static final class Entry {
        String provider = "", page = "", title = "", original = "", poster = "", description = "";
        String imdb = "", tmdb = "";
        int year;
        boolean bgAudio, bgSubs, detailed;
        long added;
        final Set<String> genres = new LinkedHashSet<>();
        String language() { return bgAudio ? "БГ аудио" : bgSubs ? "БГ субтитри" : "Езикът не е указан"; }
        boolean accepts(int language) { return language == ALL || (language == BG_AUDIO ? bgAudio : bgSubs); }
    }
    static final class Movie {
        final List<Entry> sources = new ArrayList<>();
        Entry main() { return sources.get(0); }
        String title() { return main().title; }
        int year() { for (Entry e : sources) if (e.year > 0) return e.year; return 0; }
        long added() { long v = 0; for (Entry e : sources) v = Math.max(v, e.added); return v; }
        String poster() { for (Entry e : sources) if (!e.poster.isEmpty()) return e.poster; return ""; }
        String description() { for (Entry e : sources) if (!e.description.isEmpty()) return e.description; return ""; }
        String languages() {
            boolean audio = false, subs = false;
            for (Entry e : sources) { audio |= e.bgAudio; subs |= e.bgSubs; }
            return audio ? "БГ аудио" + (subs ? " · БГ субтитри" : "") : subs ? "БГ субтитри" : "Езикът не е указан";
        }
        List<Entry> versions(int language) {
            List<Entry> out = new ArrayList<>();
            for (Entry e : sources) if (e.accepts(language)) out.add(e);
            return out;
        }
    }
    static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC)
                .replace('ѝ', 'и').replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    static Set<String> titles(Entry e) {
        Set<String> out = new HashSet<>();
        for (String s : new String[]{e.title, e.original}) {
            String n = normalize(s); if (!n.isEmpty()) out.add(n);
        }
        return out;
    }
    static boolean same(Entry a, Entry b) {
        if (a.page.equals(b.page)) return true;
        // Conflicting authoritative IDs never collapse through a translated title.
        if (!a.imdb.isEmpty() && !b.imdb.isEmpty()) return a.imdb.equals(b.imdb);
        if (!a.tmdb.isEmpty() && !b.tmdb.isEmpty()) return a.tmdb.equals(b.tmdb);
        if (a.year == 0 || a.year != b.year) return false;
        Set<String> names = titles(a); names.retainAll(titles(b));
        return !names.isEmpty();
    }
    static List<Movie> merge(Collection<Entry> records) {
        List<Movie> out = new ArrayList<>();
        Map<String,LinkedHashSet<Movie>> index=new HashMap<>();
        for (Entry e : records) {
            LinkedHashSet<Movie> candidates=new LinkedHashSet<>();
            List<String> keys=keys(e);
            for(String key:keys) { Set<Movie> found=index.get(key);if(found!=null)candidates.addAll(found); }
            Movie target=null;
            for(Movie candidate:candidates) {
                boolean match=false,conflict=false;
                for(Entry old:candidate.sources) {
                    match|=same(old,e);
                    conflict|=conflicts(old,e);
                    if(target!=null)for(Entry joined:target.sources)conflict|=conflicts(old,joined);
                }
                if(!match || conflict)continue;
                if(target==null)target=candidate;
                else if(target!=candidate){
                    target.sources.addAll(candidate.sources);out.remove(candidate);
                    for(Entry old:candidate.sources)for(String key:keys(old)){
                        LinkedHashSet<Movie> bucket=index.get(key);bucket.remove(candidate);bucket.add(target);
                    }
                }
            }
            if(target==null){target=new Movie();out.add(target);}
            target.sources.add(e);
            for(String key:keys)index.computeIfAbsent(key,k->new LinkedHashSet<>()).add(target);
        }
        return out;
    }
    private static boolean conflicts(Entry a,Entry b){
        return (!a.imdb.isEmpty() && !b.imdb.isEmpty() && !a.imdb.equals(b.imdb))
                || (!a.tmdb.isEmpty() && !b.tmdb.isEmpty() && !a.tmdb.equals(b.tmdb));
    }
    private static List<String> keys(Entry e){
        List<String> keys=new ArrayList<>();keys.add("page:"+e.page);
        if(!e.imdb.isEmpty())keys.add("imdb:"+e.imdb);
        if(!e.tmdb.isEmpty())keys.add("tmdb:"+e.tmdb);
        if(e.year>0)for(String title:titles(e))keys.add("title:"+e.year+":"+title);
        return keys;
    }
    static List<Movie> filter(List<Movie> movies, String query, int language, String genre, int year, int sort) {
        List<Movie> out = new ArrayList<>();
        String q = normalize(query);
        for (Movie movie : movies) {
            if (year != 0 && movie.year() != year) continue;
            boolean languageMatch = false, genreMatch = genre.isEmpty();
            StringBuilder hay = new StringBuilder();
            for (Entry e : movie.sources) {
                languageMatch |= e.accepts(language);
                genreMatch |= e.genres.contains(genre);
                hay.append(' ').append(e.title).append(' ').append(e.original).append(' ').append(e.description);
            }
            if (!languageMatch || !genreMatch) continue;
            String text = normalize(hay.toString()); boolean matches = true;
            for (String word : q.split(" ")) if (!text.contains(word)) { matches = false; break; }
            if (matches) out.add(movie);
        }
        Comparator<Movie> alpha = (a,b) -> a.title().compareToIgnoreCase(b.title());
        out.sort(sort == 1 ? alpha : sort == 2 ? Comparator.comparingInt(Movie::year).reversed().thenComparing(alpha)
                : Comparator.comparingLong(Movie::added).reversed().thenComparing(alpha));
        return out;
    }
}
