package com.example.tvsites;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.json.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Small provider adapters; no player URLs or tokens are stored in the catalogue. */
final class MovieSources {
    enum Provider {
        ZIP("FilmiZip", "https://filmizip.com", "/filmi/"),
        NINE("Filmi9", "https://filmi9.com", "/filmi/1"),
        ZENO("ZenoPlay", "https://zenoplay.to", "/movies/"),
        ALL("Всички филми", "https://vsichkifilmi.net", "/movies"),
        ZT("FilmiZT", "https://filmizt.com", "/filmi/filmizt/29"),
        FEN("FilmiFen", "https://filmifen.com", "/filmi/");
        final String label, base, catalog;
        Provider(String label, String base, String catalog) { this.label=label; this.base=base; this.catalog=catalog; }
        String start(String query, int language) {
            if (!query.trim().isEmpty()) {
                String q; try { q=URLEncoder.encode(query,"UTF-8"); } catch(Exception e) { q=""; }
                if (this==ZIP || this==FEN) return base+"/index.php?do=search&subaction=search&story="+q;
                if (this==ZENO) return base+"/?s="+q;
                if (this==ZT) return base+"/search/?q="+q;
                return base+"/search?term="+q;
            }
            if (language==MovieCatalog.BG_AUDIO) {
                if (this==NINE) return base+"/filmi/bg-audio";
                if (this==ALL) return base+"/movies/bg-audio";
                if (this==ZENO) return base+"/genre/bg-audio/";
                if (this==ZT) return base+"/filmi/bgaudio/18";
                if (this==FEN) return base+"/filmi-bg-audio/";
            }
            return base+catalog;
        }
        boolean owns(String url) {
            try { String host=new URI(url).getHost(); return host!=null && host.replaceFirst("^www\\.","").equals(new URI(base).getHost()); }
            catch(Exception e) { return false; }
        }
    }
    static final class Page {
        final List<MovieCatalog.Entry> entries = new ArrayList<>();
        String next = "";
    }
    static Document fetch(String url) throws IOException {
        return Jsoup.connect(url).userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000).maxBodySize(3*1024*1024).followRedirects(true).get();
    }
    static Page parse(Provider p, String url, String html) { return parse(p, Jsoup.parse(html,url)); }
    static Page parse(Provider p, Document doc) {
        Page result=new Page(); Set<String> seen=new HashSet<>();
        String selector=p==Provider.ZIP?".poster":p==Provider.FEN?"a.item-poster":p==Provider.ZENO?"a.media-card":
                p==Provider.NINE?"a[href*=/film/][title]":p==Provider.ALL?"a[name=movie_card]":".ml-item";
        Map<String,String> names=new HashMap<>();
        for (Element script:doc.select("script[type=application/ld+json]")) {
            try { collectNames(new JSONTokener(script.data()).nextValue(),names); } catch(Exception ignored) {}
        }
        for (Element node:doc.select(selector)) {
            Element link=node.tagName().equals("a")?node:node.selectFirst("a[href]");
            if(link==null) continue;
            String page=link.absUrl("href");
            if(!isMovie(p,page) || !seen.add(page)) continue;
            Element card=p==Provider.NINE && node.parent()!=null && node.parent().parent()!=null?node.parent().parent():node;
            MovieCatalog.Entry e=new MovieCatalog.Entry(); e.provider=p.name(); e.page=page;
            e.title=text(card,".poster__title,.item-poster__title,.media-title,h2");
            if(e.title.isEmpty()) e.title=link.attr("title");
            Element img=card.selectFirst("img");
            if(img!=null) {
                e.poster=img.absUrl(img.hasAttr("data-original")?"data-original":img.hasAttr("data-src")?"data-src":"src");
                e.original=img.attr("alt").replaceAll("(?i)\\s*\\(\\d{4}\\).*","").replaceAll("(?i)\\s+онлайн$","").trim();
                if(e.title.isEmpty()) e.title=e.original;
            }
            if(names.containsKey(page)) setNames(e,names.get(page));
            else if(p==Provider.ALL && img!=null) setNames(e,img.attr("alt"));
            else if(p==Provider.ZT) setNames(e,link.attr("oldtitle"));
            e.year=year(text(card,".poster__info,.item-poster__year,.media-year"));
            if(e.year==0) for(Element leaf:card.select("span,time"))if(leaf.ownText().trim().matches("(19|20)[0-9]{2}")){e.year=year(leaf.ownText());break;}
            if(e.year==0) e.year=year(card.text());
            e.title=e.title.replaceAll("\\s*\\((?:19|20)\\d{2}\\)\\s*$","").trim();
            if(e.original.equals(e.title) || e.original.startsWith("Гледай филми")) e.original="";
            markLanguage(e,card.text()+" "+card.select("[title]").eachAttr("title"));
            if(!card.select(".icon-subs").isEmpty()) e.bgSubs=true;
            if(doc.location().contains("bg-audio") || doc.location().contains("bgaudio")) e.bgAudio=true;
            String genre=text(card,".item__poster-category"); if(!genre.isEmpty()) e.genres.add(genre);
            if(p==Provider.NINE) { Element g=card.selectFirst("p:last-child"); if(g!=null) for(String t:g.text().split(" · ")) if(year(t)==0 && !t.matches(".*\\d.*")) e.genres.add(t); }
            if(!e.title.isEmpty()) result.entries.add(e);
        }
        for(Element a:doc.select("a[href]")) {
            String t=a.text().trim().toLowerCase(Locale.ROOT), rel=a.attr("rel");
            if(rel.contains("next") || a.hasClass("next") || a.hasClass("swchItem-next") || t.startsWith("напред") || t.contains("следваща") || t.contains("покажи още филми") || a.attr("aria-label").equalsIgnoreCase("Next page")) {
                String next=a.absUrl("href"); if(p.owns(next) && !next.equals(doc.location())) { result.next=next; break; }
            }
        }
        return result;
    }
    static boolean isMovie(Provider p,String url) {
        if(!p.owns(url)) return false;
        try {
            String path=new URI(url).getPath();
            if(p==Provider.NINE || p==Provider.ALL) return path.startsWith("/film/");
            if(p==Provider.ZENO) return path.startsWith("/movie/");
            if(p==Provider.ZT) return path.startsWith("/filmi/") && path.matches(".*/\\d+-\\d+-\\d+-\\d+/?") && !path.contains("/seriali/");
            return path.startsWith("/filmi/") && path.endsWith(".html");
        } catch(Exception ex) { return false; }
    }
    static MovieCatalog.Entry detail(MovieCatalog.Entry old,Document doc) {
        MovieCatalog.Entry e=MovieStore.decodeEntry(MovieStore.encodeEntry(old));
        Element main=doc.selectFirst("article.pmovie,article.detail-page,#mv-info"); if(main==null)main=doc.selectFirst("main"); if(main==null) main=doc.body();
        String title=text(main,".movie__bg-title");if(title.isEmpty())title=text(main,"h1,h3[itemprop=name]");
        if(!title.isEmpty()) { if(e.provider.equals("ZIP") || e.provider.equals("ZT")) setNames(e,title); else e.title=title.trim(); }
        String original=text(main,".pmovie__original-title,.movie__original-title").replaceAll("\\s*\\((19|20)[0-9]{2}\\)\\s*$","").replaceFirst("^\\s*/\\s*","");
        if(!original.isEmpty()) e.original=original;
        String desc=text(main,".page__text,.detail-overview,[itemprop=description],.full-text,.js-schema-description");
        if(!desc.isEmpty()) e.description=desc;
        int year=year(text(main,".pmovie__quality,.detail-meta,.mvici-right,[itemprop=dateCreated]")); if(year>0) e.year=year;
        for(Element a:main.select(".genre-chip-row a,.pmovie__header-row:first-child a,.mvici-left .entAllCats,[itemprop=genre]")) e.genres.add(a.text().trim());
        // Never read navigation labels or schema inLanguage as evidence of a dubbed soundtrack.
        markLanguage(e,text(main,".detail-meta,.pmovie__quality,.translation,.movie-language,[data-language]")
                +" "+main.select(".genre-chip-row a[href*=/bg-audio],.mvic-info a[href*=/bgaudio],.pmovie__header-row a[href*=/filmi-bg-audio]").text());
        Element tmdb=main.selectFirst("[data-tmdb-id]"); if(tmdb!=null) e.tmdb=tmdb.attr("data-tmdb-id");
        for(Element a:main.select("a[href*=imdb.com/title/],a[href*=themoviedb.org/movie/]")) {
            Matcher imdb=Pattern.compile("tt\\d{5,}").matcher(a.attr("href")); if(imdb.find())e.imdb=imdb.group();
            Matcher tm=Pattern.compile("themoviedb.org/movie/(\\d+)").matcher(a.attr("href")); if(tm.find())e.tmdb=tm.group(1);
        }
        for(Element script:doc.select("script[type=application/ld+json]")) {
            try { readMovie(new JSONTokener(script.data()).nextValue(),e); } catch(Exception ignored) {}
        }
        e.title=e.title.replaceAll("\\s*\\((19|20)[0-9]{2}\\)\\s*$","").trim();
        e.detailed=true; return e;
    }
    private static void readMovie(Object value,MovieCatalog.Entry e) {
        if(value instanceof JSONArray) { JSONArray arr=(JSONArray)value;for(int i=0;i<arr.length();i++)readMovie(arr.opt(i),e); }
        if(!(value instanceof JSONObject))return;
        JSONObject obj=(JSONObject)value;
        if("Movie".equals(obj.optString("@type"))) {
            setNames(e,obj.optString("name",e.title)); e.description=obj.optString("description",e.description);
            e.year=obj.optInt("copyrightYear",e.year);
            if(e.year==0)e.year=year(obj.optString("dateCreated"));
            Object genre=obj.opt("genre"); if(genre instanceof JSONArray)for(int i=0;i<((JSONArray)genre).length();i++)e.genres.add(((JSONArray)genre).optString(i));
            else if(genre instanceof String)e.genres.add((String)genre);
            Matcher imdb=Pattern.compile("tt\\d{5,}").matcher(obj.optString("sameAs"));if(imdb.find())e.imdb=imdb.group();
        }
        if(obj.has("@graph"))readMovie(obj.opt("@graph"),e);
    }
    private static void collectNames(Object v,Map<String,String> names) {
        if(v instanceof JSONArray){JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)collectNames(a.opt(i),names);}
        if(v instanceof JSONObject){JSONObject o=(JSONObject)v;if(o.has("url") && o.has("name"))names.put(o.optString("url"),o.optString("name"));
            if(o.has("itemListElement"))collectNames(o.opt("itemListElement"),names);if(o.has("@graph"))collectNames(o.opt("@graph"),names);}
    }
    static void setNames(MovieCatalog.Entry e,String value) {
        if(value.trim().isEmpty())return;
        String[] names=value.split("\\s+/\\s+",2);
        if(names.length==2) {
            if(names[0].matches(".*[А-Яа-я].*") && !names[1].matches(".*[А-Яа-я].*")){e.title=names[0].trim();e.original=names[1].trim();}
            else {e.original=names[0].trim();e.title=names[1].trim();}
        } else e.title=value.trim();
    }
    static void markLanguage(MovieCatalog.Entry e,String label) {
        String s=label.toLowerCase(Locale.ROOT);
        e.bgAudio|=s.matches("(?s).*(бг[ \\-_]*аудио|bg[ \\-_]*audio|българск[ои] (аудио|озвучаване)|дублаж).*" );
        e.bgSubs|=s.matches("(?s).*(бг[ \\-_]*субтитри|български субтитри|субтитри|bg[ \\-_]*subs).*" );
    }
    static int year(String text) { Matcher m=Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)").matcher(text);return m.find()?Integer.parseInt(m.group()):0; }
    static String text(Element root,String selector) { Element e=root.selectFirst(selector);return e==null?"":e.text().trim(); }
}
