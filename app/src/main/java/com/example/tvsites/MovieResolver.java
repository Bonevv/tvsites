package com.example.tvsites;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.ScriptHandler;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;

/** Owns one page, its frame probes and a bounded discovery deadline. */
final class MovieResolver {
    interface Callback {
        void found(Stream stream);
        void failed();
        default void progress(int secondsLeft) {}
    }
    static final class Stream {
        final String url, mime;
        final Map<String,String> headers;
        final List<String> subtitles;
        Stream(String url,Map<String,String> headers,List<String> subtitles){this(url,"",headers,subtitles);}
        Stream(String url,String mime,Map<String,String> headers,List<String> subtitles){
            this.url=url;this.mime=mime;this.headers=headers;this.subtitles=subtitles;
        }
    }
    static final long SEARCH_MS=24000;
    static final String MEDIA_PREFIX="TVSITES_MOVIE_MEDIA:";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final Callback callback;
    private final Predicate<String> blocked;
    private final String playScript;
    private final LinkedHashMap<String,Map<String,String>> candidates=new LinkedHashMap<>();
    private final Map<String,String> types=new HashMap<>();
    private final LinkedHashSet<String> subtitles=new LinkedHashSet<>();
    private WebView web;
    private ScriptHandler frameScript;
    private volatile boolean closed;
    private boolean scheduled,allFrames,promoted;
    private final MovieCatalog.Entry entry;
    private int ticks;
    private long deadline;
    private String probe;
    MovieResolver(FrameLayout root,MovieCatalog.Entry entry,String playScript,Predicate<String> blocked,Callback callback){
        this.root=root;this.entry=entry;this.promoted=!entry.embed.isEmpty();this.playScript=playScript;this.blocked=blocked;this.callback=callback;
    }
    @SuppressLint("SetJavaScriptEnabled") void start(){
        web=new WebView(root.getContext());
        // An alpha-zero WebView can stop drawing/throttle lazy players. The opaque loading layer covers this page.
        web.setFocusable(false);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        web.setBackgroundColor(Color.BLACK);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setGeolocationEnabled(false);s.setLoadsImagesAutomatically(false);s.setBlockNetworkImage(true);
        s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        probe=readProbe(root.getContext());
        try {
            if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
                frameScript=WebViewCompat.addDocumentStartJavaScript(web,probe,Collections.singleton("*"));allFrames=true;
            }
        }catch(RuntimeException unsupported){allFrames=false;}
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView v,boolean dialog,boolean gesture,android.os.Message message){return false;}
            @Override public boolean onConsoleMessage(ConsoleMessage message){
                if(closed || !message.message().startsWith(MEDIA_PREFIX))return false;
                try {
                    JSONObject data=new JSONObject(message.message().substring(MEDIA_PREFIX.length()));
                    String url=data.optString("url"),mime=data.optString("mime"),page=data.optString("page",entry.page);
                    if(data.optBoolean("subtitle")){if(http(url) && !blocked.test(Uri.parse(url).getHost()))subtitles.add(url);return true;}
                    HashMap<String,String> headers=new HashMap<>();headers.put("Referer",page);
                    Uri origin=Uri.parse(page);if(http(page))headers.put("Origin",origin.getScheme()+"://"+origin.getAuthority());
                    record(url,headers,mime,true);
                }catch(JSONException ignored){}
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return !http(url);}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                String url=r.getUrl().toString();return !http(url) || blocked.test(r.getUrl().getHost())
                        || (r.isForMainFrame() && !promoted && !MovieSources.Provider.valueOf(entry.provider).owns(url));
            }
            @Override public void onReceivedError(WebView v,int code,String description,String url){if(entry.page.equals(url))fail();}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                if(closed || blocked.test(r.getUrl().getHost()))return blank();
                String url=r.getUrl().toString();
                if(media(url)){
                    Map<String,String> headers=new HashMap<>();
                    for(Map.Entry<String,String> h:r.getRequestHeaders().entrySet()){
                        if(h.getKey().equalsIgnoreCase("Referer"))headers.put("Referer",h.getValue());
                        else if(h.getKey().equalsIgnoreCase("Origin"))headers.put("Origin",h.getValue());
                        else if(h.getKey().equalsIgnoreCase("User-Agent"))headers.put("User-Agent",h.getValue());
                    }
                    handler.post(()->record(url,headers,"",false));return blank();
                }
                return null;
            }
            @Override public void onPageFinished(WebView v,String url){if(!closed){web.evaluateJavascript(probe,null);poke();}}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail();}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame() && e.getStatusCode()>=400)fail();}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){fail();return true;}
        });
        root.addView(web,0,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        deadline=SystemClock.uptimeMillis()+SEARCH_MS;
        handler.postDelayed(timeout,SEARCH_MS);handler.postDelayed(tick,1000);if(entry.embed.isEmpty())web.loadUrl(entry.page);else web.loadUrl(entry.embed,Collections.singletonMap("Referer",entry.page.split("#")[0]));
    }
    static String readProbe(android.content.Context context){
        try(InputStream in=context.getAssets().open("movie-player.js");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)bytes.write(buf,0,n);return bytes.toString("UTF-8");
        }catch(IOException ex){return "";}
    }
    static boolean http(String url){return url!=null && (url.startsWith("https://") || url.startsWith("http://"));}
    static boolean media(String url){return supported(url,"",false);}
    static boolean supported(String url,String mime,boolean fromVideo){
        if(!http(url))return false;
        try {
            String path=Uri.parse(url).getPath();if(path==null)return false;
            if(url.matches("(?i).*(trailer|preroll|doubleclick|/vast[/?]).*") || path.matches("(?i).*\\.(ts|m4s|aac|js|css|png|jpe?g|webp)$"))return false;
            return path.matches("(?i).*\\.(m3u8|mpd|mp4)$") || fromVideo
                    || mime.matches("(?i).*(mpegurl|dash\\+xml|video/mp4).*" );
        }catch(Exception e){return false;}
    }
    private final Runnable timeout=this::fail;
    private final Runnable tick=new Runnable(){@Override public void run(){if(closed)return;
        callback.progress((int)Math.max(0,(deadline-SystemClock.uptimeMillis()+999)/1000));poke();handler.postDelayed(this,1800);
    }};
    private void poke(){
        if(closed || web==null)return;
        int step=ticks++;
        String episode=Uri.parse(entry.page).getFragment();
        String selectEpisode="";
        if(episode!=null && episode.startsWith("tvsites-episode=")){
            String number=episode.substring(16).replaceAll("[^0-9]","");
            selectEpisode="(function(){var b=document.querySelectorAll('button');for(var i=0;i<b.length;i++)if(b[i].textContent.trim()==='Епизод "+number+"'){b[i].click();break;}})();";
        }
        String activate=selectEpisode+"(function(){try{var tabs=document.querySelectorAll('.tabs-block__select [data-link],.tab-btn[data-player]:not([data-player=trailer]):not([data-player=help]),.player-picker-menu [data-player],button[data-server],.server-btn');"
                +"if(tabs.length){var ix=Math.min(Math.floor("+step+"/4),tabs.length-1);if(window.__tvsitesTab!==ix||!document.querySelector('#video-stage iframe,.video-responsive iframe,#player-box iframe')){var ev=document.createEvent('Event');ev.initEvent('pointerup',true,true);tabs[ix].dispatchEvent(ev);tabs[ix].click();window.__tvsitesTab=ix;}}"
                +"var b=document.querySelector('.container_play .play,.icon_play');if(b&&!document.querySelector('.video-responsive iframe,.video-responsive video'))b.click();"
                +"var bs=document.querySelectorAll('button');for(var i=0;i<bs.length;i++){if(/^Гледай( сега| онлайн)?$/i.test(bs[i].textContent.trim())&&!document.querySelector('iframe[src*=voe],iframe[src*=vid],#player iframe,video')){bs[i].click();break;}}"
                +"var p=document.querySelector('[data-player-option],.episode-player-option');if(p&&!document.querySelector('#player-box iframe')&&!window.__tvsitesOption){p.click();window.__tvsitesOption=true;}"
                +"}catch(e){}})();";
        web.evaluateJavascript(activate+playScript+probe,null);
        // Older WebViews cannot install frame scripts: open the actual embed as this temporary page.
        if(!allFrames && !promoted && step>=3)promoteFrame();
        if(step%3==2)tapPlayer();
    }
    private void promoteFrame(){
        web.evaluateJavascript("(function(){var f=document.querySelector('.movieplay iframe,#video-stage iframe,.video-responsive iframe,#player-box iframe,iframe[src*=voe],iframe[src*=vid]');return f?f.src:'';})()",value->{
            if(closed || promoted)return;
            try {
                String url=(String)new JSONTokener(value).nextValue();
                if(!http(url) || blocked.test(Uri.parse(url).getHost()) || url.matches("(?i).*(youtube|doubleclick|trailer).*"))return;
                promoted=true;web.loadUrl(url,Collections.singletonMap("Referer",entry.page));
            }catch(Exception ignored){}
        });
    }
    private void tapPlayer(){
        web.evaluateJavascript("(function(){var f=null,area=0,items=document.querySelectorAll('iframe,video,.jw-display-icon-container,.plyr__control--overlaid');for(var i=0;i<items.length;i++){var e=items[i];if(/youtube|doubleclick|googlesyndication/i.test(e.src||''))continue;var r=e.getBoundingClientRect(),a=r.width*r.height;if(a>area){area=a;f=e;}}if(!f)return '';f.scrollIntoView(true);var r=f.getBoundingClientRect();return Math.min(innerWidth-1,Math.max(1,(r.left+r.right)/2))+','+Math.min(innerHeight-1,Math.max(1,(r.top+r.bottom)/2));})()",value->{
            if(closed || web==null)return;
            try{String[] xy=((String)new JSONTokener(value).nextValue()).split(",");float x=Float.parseFloat(xy[0]),y=Float.parseFloat(xy[1]);long t=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+60,MotionEvent.ACTION_UP,x,y,0);web.dispatchTouchEvent(down);web.dispatchTouchEvent(up);down.recycle();up.recycle();
            }catch(Exception ignored){}
        });
    }
    private void record(String url,Map<String,String> headers,String mime,boolean fromVideo){
        if(closed || !supported(url,mime,fromVideo) || blocked.test(Uri.parse(url).getHost()) || candidates.size()>=20)return;
        if(!headers.containsKey("Referer"))headers.put("Referer",entry.page);
        if(!headers.containsKey("User-Agent"))headers.put("User-Agent",web.getSettings().getUserAgentString());
        candidates.put(url,headers);if(!mime.isEmpty())types.put(url,canonicalMime(mime));
        if(!scheduled){scheduled=true;handler.postDelayed(this::finish,450);}
    }
    static String canonicalMime(String mime){String low=mime.toLowerCase(Locale.ROOT);return low.contains("mpegurl")?"application/x-mpegURL":low.contains("dash+xml")?"application/dash+xml":low.contains("video/mp4")?"video/mp4":"";}
    private void finish(){
        if(closed || candidates.isEmpty())return;
        String chosen=candidates.keySet().iterator().next();
        for(String url:candidates.keySet())if(url.toLowerCase(Locale.ROOT).contains("master")){chosen=url;break;}
        Map<String,String> headers=new HashMap<>(candidates.get(chosen));String cookie=CookieManager.getInstance().getCookie(chosen);if(cookie!=null)headers.put("Cookie",cookie);
        Stream result=new Stream(chosen,types.getOrDefault(chosen,""),headers,new ArrayList<>(subtitles));close();callback.found(result);
    }
    private void fail(){if(closed)return;close();callback.failed();}
    void close(){closed=true;handler.removeCallbacksAndMessages(null);
        if(frameScript!=null){try{frameScript.remove();}catch(RuntimeException ignored){}frameScript=null;}
        if(web!=null){web.stopLoading();web.setWebChromeClient(null);web.setWebViewClient(new WebViewClient());root.removeView(web);web.destroy();web=null;}candidates.clear();
    }
    private static WebResourceResponse blank(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
}
