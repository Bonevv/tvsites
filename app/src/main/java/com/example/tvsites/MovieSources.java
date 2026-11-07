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
        String seriesStart(){
            return base+(this==ZIP || this==FEN?"/seriali/":this==NINE?"/seriali":this==ZENO?"/tv-shows/":this==ALL?"/series":"/filmi/seriali/27");
        }
        boolean owns(String url) {
            try { String host=new URI(url).getHost(); return host!=null && (host.replaceFirst("^www\\.","").equals(new URI(base).getHost()) || this==NINE && host.equals("cdn.filmi9.com") && new URI(url).getPath().startsWith("/movies")); }
            catch(Exception e) { return false; }
        }
    }
    static final class Page {
        final List<MovieCatalog.Entry> entries = new ArrayList<>();
        String next = "";
        final Map<String,String> genres=new LinkedHashMap<>();
    }
    static Document fetch(String url) throws IOException {
        org.jsoup.Connection.Response response=Jsoup.connect(url).ignoreContentType(true).userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000).maxBodySize(3*1024*1024).followRedirects(true).execute();
        if(response.contentType()!=null && response.contentType().contains("json")){Document doc=Document.createShell(response.url().toString());doc.body().appendChild(new TextNode(response.body()));return doc;}
        return response.parse();
    }
    static Page parse(Provider p, String url, String html) { return parse(p, Jsoup.parse(html,url)); }
    static Page parse(Provider p, Document doc) {
        if(p==Provider.NINE && doc.location().startsWith("https://cdn.filmi9.com/movies"))return nineApi(doc);
        Page result=new Page(); result.genres.putAll(genres(p,doc)); Set<String> seen=new HashSet<>();
        String selector=p==Provider.ZIP?".poster":p==Provider.FEN?"a.item-poster":p==Provider.ZENO?"a.media-card":
                p==Provider.NINE?"a[href*=/film/][title],a[href*=/serial/][title]":p==Provider.ALL?"a[name=movie_card]":".ml-item";
        Map<String,String> names=new HashMap<>();
        for (Element script:doc.select("script[type=application/ld+json]")) {
            try { collectNames(new JSONTokener(script.data()).nextValue(),names); } catch(Exception ignored) {}
        }
        for (Element node:doc.select(selector)) {
            Element link=node.tagName().equals("a")?node:node.selectFirst("a[href]");
            if(link==null) continue;
            String page=link.absUrl("href");
            if(!isTitle(p,page) || !seen.add(page)) continue;
            Element card=p==Provider.NINE && node.parent()!=null && node.parent().parent()!=null?node.parent().parent():node;
            MovieCatalog.Entry e=new MovieCatalog.Entry(); e.provider=p.name(); e.page=page;e.series=isSeries(p,page);
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
            String genre=text(card,".item__poster-category"); if(!genre.isEmpty()) e.genres.add(genreName(genre));
            if(p==Provider.NINE) { Element g=card.selectFirst("p:last-child"); if(g!=null) for(String t:g.text().split(" · ")) if(year(t)==0 && !t.matches(".*\\d.*")) e.genres.add(genreName(t)); }
            if(!e.title.isEmpty()) result.entries.add(e);
        }
        for(Element a:doc.select("a[href]")) {
            String t=a.text().trim().toLowerCase(Locale.ROOT), rel=a.attr("rel");
            if(rel.contains("next") || a.hasClass("next") || a.hasClass("swchItem-next") || t.startsWith("напред") || t.contains("следваща") || t.contains("покажи още филми") || a.attr("aria-label").equalsIgnoreCase("Next page")) {
                String next=a.absUrl("href"); if(p.owns(next) && !next.equals(doc.location())) { result.next=next; break; }
            }
        }
        if(p==Provider.NINE && result.next.isEmpty() && doc.location().contains("/genre/")){
            for(Element script:doc.select("script")){
                String data=publicJson(script.data());Matcher id=Pattern.compile("\"genre\"\\s*:\\s*\\{[^}]*\"_id\"\\s*:\\s*\"([^\"]+)\"").matcher(data);
                if(id.find()){result.next="https://cdn.filmi9.com/movies/?page=2&limit=10&sort=-createdAt&genres="+id.group(1);break;}
            }
        }
        for(Map.Entry<String,String> genre:result.genres.entrySet()){
            String category=genre.getValue().replaceAll("/+$","");
            String location=doc.location().replaceAll("/+$","");
            if(location.equals(category) || location.startsWith(category+"/page/") || location.startsWith(category+"-"))
                for(MovieCatalog.Entry e:result.entries)e.genres.add(genre.getKey());
        }
        return result;
    }
    private static String publicJson(String text){for(int i=0;i<3;i++)text=text.replace("\\\"","\"").replace("\\/","/");return text;}
    private static Page nineApi(Document doc){
        Page page=new Page();
        try {
            JSONObject root=new JSONObject(doc.body().wholeText());JSONArray data=root.optJSONArray("data");if(data==null)return page;
            for(int i=0;i<data.length();i++){
                JSONObject item=data.optJSONObject(i);if(item==null)continue;MovieCatalog.Entry entry=new MovieCatalog.Entry();entry.provider="NINE";entry.series="series".equals(item.optString("type"));
                entry.page=Provider.NINE.base+(entry.series?"/serial/":"/film/")+item.optString("slug");if(!isTitle(Provider.NINE,entry.page))continue;
                setNames(entry,item.optString("title"));entry.year=item.optInt("year");entry.poster=item.optString("movieImage");entry.bgAudio=item.optBoolean("isBgAudio");entry.description=item.optString("description");
                JSONArray genres=item.optJSONArray("genres");if(genres!=null)for(int g=0;g<genres.length();g++){Object value=genres.opt(g);String name=genreName(value instanceof JSONObject?((JSONObject)value).optString("name"):String.valueOf(value));if(COMMON_GENRES.contains(name))entry.genres.add(name);}
                if(!entry.title.isEmpty())page.entries.add(entry);
            }
            Matcher limit=Pattern.compile("[?&]limit=([0-9]+)").matcher(doc.location());int size=limit.find()?Integer.parseInt(limit.group(1)):10;
            Matcher number=Pattern.compile("([?&]page=)([0-9]+)").matcher(doc.location());if(data.length()>=size && number.find())page.next=number.replaceFirst(number.group(1)+(Integer.parseInt(number.group(2))+1));
        }catch(JSONException ignored){}
        return page;
    }
    static boolean isSeries(Provider p,String url){
        try{String path=new URI(url).getPath();return p==Provider.ZENO?path.startsWith("/tv-show/"):p==Provider.ZT?path.startsWith("/filmi/seriali/"):path.startsWith("/serial/") || path.startsWith("/seriali/");}catch(Exception e){return false;}
    }
    static boolean isTitle(Provider p,String url){
        if(isMovie(p,url))return true;
        if(!p.owns(url) || !isSeries(p,url))return false;
        try{String path=new URI(url).getPath();return p==Provider.ZIP || p==Provider.FEN?path.endsWith(".html"):p==Provider.ZT?path.matches(".*/[0-9]+-[0-9]+-[0-9]+-[0-9]+/?"):true;}catch(Exception e){return false;}
    }
    static Map<String,String> genres(Provider p,Document doc){
        Map<String,String> result=new LinkedHashMap<>();
        for(Element a:doc.select("a[href]")){
            String url=a.absUrl("href"),label=genreName(a.text());
            if(!p.owns(url) || isTitle(p,url) || label.isEmpty() || label.length()>35 || label.matches(".*[0-9].*"))continue;
            String path=URI.create(url).getPath();
            boolean category=path.startsWith("/genre/") || path.startsWith("/g/") || ((p==Provider.ZT || p==Provider.FEN) && path.startsWith("/filmi/"));
            if((category || p==Provider.ZIP) && COMMON_GENRES.contains(label))result.putIfAbsent(label,url);
        }
        return result;
    }
    static final List<String> COMMON_GENRES=Arrays.asList("Екшън","Комедия","Драма","Ужаси","Трилър","Фантастика","Научна фантастика","Анимация","Приключенски","Криминален","Романтични","Семейни","Документални","Исторически","Военни","Музикални","Уестърн","Фентъзи","Мистерия","Спортни","Биографични","Детски","Коледни","Еротични","Тийн");
    static String genreName(String raw){
        String s=raw.trim().toLowerCase(Locale.ROOT).replace("научна-фантастика","научна фантастика");
        if(s.startsWith("комеди"))return "Комедия";if(s.startsWith("драм"))return "Драма";if(s.startsWith("трил"))return "Трилър";
        if(s.startsWith("приключ"))return "Приключенски";if(s.startsWith("кримин"))return "Криминален";if(s.startsWith("роман") || s.equals("романс"))return "Романтични";
        if(s.startsWith("документ"))return "Документални";if(s.startsWith("истор"))return "Исторически";if(s.startsWith("воен"))return "Военни";
        if(s.startsWith("семе"))return "Семейни";if(s.startsWith("музик"))return "Музикални";if(s.startsWith("биограф"))return "Биографични";
        if(s.startsWith("спорт"))return "Спортни";if(s.startsWith("детски, анима"))return "Анимация";
        return s.isEmpty()?"":s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);
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
        for(Element a:main.select(".genre-chip-row a,.pmovie__header-row:first-child a,.mvici-left .entAllCats,[itemprop=genre]")) e.genres.add(genreName(a.text()));
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
        if(e.series)readEpisodes(e,doc);
        else if(e.provider.equals("NINE") || e.provider.equals("ALL")){
            for(Element script:doc.select("script")){
                Matcher embed=Pattern.compile("\"videoUrl\"[ ]*:[ ]*\"(https?://[^\"]+)\"").matcher(publicJson(script.data()));
                if(embed.find()){e.embed=embed.group(1);break;}
            }
        }
        if(e.embed.isEmpty() && e.provider.equals("ZT")){
            Element frame=doc.selectFirst(".movieplay iframe[src]");if(frame!=null && frame.absUrl("src").startsWith("http"))e.embed=frame.absUrl("src");
        }
        e.detailed=true; return e;
    }
    static void readEpisodes(MovieCatalog.Entry e,Document doc){
        LinkedHashMap<String,MovieCatalog.Episode> found=new LinkedHashMap<>();
        for(Element a:doc.select("a.episode-card,a.series-episode")){
            String url=a.absUrl("href");if(!MovieSources.Provider.valueOf(e.provider).owns(url))continue;
            String label=a.hasAttr("data-episode-number")?"Епизод "+a.attr("data-episode-number"):"Епизод "+a.text().trim();
            found.put(url,new MovieCatalog.Episode(label,url,""));
        }
        // The Next.js sources publish their episode metadata in the initial document.
        for(Element script:doc.select("script")){
            String data=script.data();if(!data.contains("episodes"))continue;
            for(int layer=0;layer<3;layer++)data=data.replace("\\\"","\"").replace("\\/","/");
            int start=data.indexOf("\"episodes\":[");if(start<0)continue;start=data.indexOf('[',start);
            int depth=0,end=-1;boolean quoted=false,escaped=false;
            for(int i=start;i<data.length();i++){char c=data.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;continue;}if(c=='\"')quoted=true;else if(c=='[')depth++;else if(c==']' && --depth==0){end=i+1;break;}}
            if(end<0)continue;
            try{JSONArray episodes=new JSONArray(data.substring(start,end));for(int i=0;i<episodes.length();i++){
                JSONObject item=episodes.optJSONObject(i);if(item==null)continue;int number=item.optInt("episodeNumber",i+1);String embed=item.optString("videoUrl");
                if(!embed.startsWith("http")){JSONArray sources=item.optJSONArray("sources");if(sources!=null && sources.length()>0)embed=sources.getJSONObject(0).optString("url");}
                String page=e.page+"#tvsites-episode="+number;found.put(page,new MovieCatalog.Episode("Епизод "+number,page,embed));
            }}catch(JSONException ignored){}
        }
        if(found.isEmpty())for(Element button:doc.select("button"))if(button.text().trim().matches("Епизод [0-9]+")){
            String number=button.text().trim().substring(7);String page=e.page+"#tvsites-episode="+number;found.put(page,new MovieCatalog.Episode(button.text().trim(),page,""));
        }
        e.episodes.clear();e.episodes.addAll(found.values());
    }
    private static void readMovie(Object value,MovieCatalog.Entry e) {
        if(value instanceof JSONArray) { JSONArray arr=(JSONArray)value;for(int i=0;i<arr.length();i++)readMovie(arr.opt(i),e); }
        if(!(value instanceof JSONObject))return;
        JSONObject obj=(JSONObject)value;
        if("Movie".equals(obj.optString("@type"))) {
            setNames(e,obj.optString("name",e.title)); e.description=obj.optString("description",e.description);
            e.year=obj.optInt("copyrightYear",e.year);
            if(e.year==0)e.year=year(obj.optString("dateCreated"));
            Object genre=obj.opt("genre"); if(genre instanceof JSONArray)for(int i=0;i<((JSONArray)genre).length();i++)e.genres.add(genreName(((JSONArray)genre).optString(i)));
            else if(genre instanceof String)e.genres.add(genreName((String)genre));
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
