package com.example.tvsites;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.function.Predicate;

/** One short-lived, hidden page. Owned callbacks and WebView are destroyed together. */
final class MovieResolver {
    interface Callback { void found(Stream stream); void failed(); }
    static final class Stream {
        final String url; final Map<String,String> headers; final List<String> subtitles;
        Stream(String url,Map<String,String> headers,List<String> subtitles){this.url=url;this.headers=headers;this.subtitles=subtitles;}
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final Callback callback;
    private final Predicate<String> blocked;
    private final String playScript;
    private final LinkedHashMap<String,Map<String,String>> candidates=new LinkedHashMap<>();
    private final LinkedHashSet<String> subtitles=new LinkedHashSet<>();
    private WebView web;
    private volatile boolean closed;
    private boolean scheduled;
    private final MovieCatalog.Entry entry;
    private int ticks;
    MovieResolver(FrameLayout root,MovieCatalog.Entry entry,String playScript,Predicate<String> blocked,Callback callback){
        this.root=root;this.entry=entry;this.playScript=playScript;this.blocked=blocked;this.callback=callback;
    }
    @SuppressLint("SetJavaScriptEnabled") void start(){
        web=new WebView(root.getContext());web.setAlpha(0f);web.setFocusable(false);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        web.setBackgroundColor(Color.BLACK);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setGeolocationEnabled(false);s.setLoadsImagesAutomatically(false);s.setBlockNetworkImage(true);
        s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onCreateWindow(WebView v,boolean dialog,boolean gesture,android.os.Message message){return false;}});
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){
                return !(url.startsWith("https://") || url.startsWith("http://"));
            }
            @Override public void onReceivedError(WebView v,int code,String description,String url){
                if(entry.page.equals(url))fail();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                String url=r.getUrl().toString();
                return !(url.startsWith("https://") || url.startsWith("http://"))
                        || (r.isForMainFrame() && !MovieSources.Provider.valueOf(entry.provider).owns(url));
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                if(closed || blocked.test(r.getUrl().getHost()))return blank();
                String url=r.getUrl().toString();
                if(media(url)){
                    Map<String,String> headers=new HashMap<>();
                    for(Map.Entry<String,String> h:r.getRequestHeaders().entrySet()){
                        if(h.getKey().equalsIgnoreCase("Referer") || h.getKey().equalsIgnoreCase("Origin") || h.getKey().equalsIgnoreCase("User-Agent"))headers.put(h.getKey(),h.getValue());
                    }
                    handler.post(()->record(url,headers));return blank();
                }
                if(entry.bgSubs && url.matches("(?i).*([/_-](bg|bul)[/_?.-]).*\\.(vtt|srt)(\\?.*)?"))handler.post(()->subtitles.add(url));
                return null;
            }
            @Override public void onPageFinished(WebView v,String url){if(!closed)poke();}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail();}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame() && e.getStatusCode()>=400)fail();}
        });
        root.addView(web,0,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(entry.page);handler.postDelayed(timeout,30000);handler.postDelayed(tick,1500);
    }
    static boolean media(String url){
        try{String path=Uri.parse(url).getPath();return path!=null && path.matches("(?i).*\\.(m3u8|mpd|mp4)$") && !url.matches("(?i).*(trailer|preroll|doubleclick|vast).*");}
        catch(Exception e){return false;}
    }
    private final Runnable timeout=this::fail;
    private final Runnable tick=new Runnable(){@Override public void run(){if(closed)return;poke();handler.postDelayed(this,2200);}};
    private void poke(){
        if(closed || web==null)return;
        // Provider landing controls, followed by the same player activation used for TV.
        String activate="(function(){try{var tabs=document.querySelectorAll('.tabs-block__select [data-link],.tab-btn[data-player]:not([data-player=trailer]):not([data-player=help]),.player-picker-menu [data-player],button[data-server],.server-btn');"
                +"if(tabs.length){var ix=Math.min(Math.floor("+ticks+"/4),tabs.length-1);if(window.__tvsitesTab!==ix||!document.querySelector('#video-stage iframe,.video-responsive iframe,#player-box iframe')){var ev=document.createEvent('Event');ev.initEvent('pointerup',true,true);tabs[ix].dispatchEvent(ev);tabs[ix].click();window.__tvsitesTab=ix;}}"
                +"var b=document.querySelector('.container_play .play,.icon_play');if(b&&!document.querySelector('.video-responsive iframe,.video-responsive video'))b.click();"
                +"var bs=document.querySelectorAll('button');for(var i=0;i<bs.length;i++){if(/^Гледай( сега| онлайн)?$/i.test(bs[i].textContent.trim())&&!document.querySelector('iframe[src*=voe],iframe[src*=vid],#player iframe,video')){bs[i].click();break;}}"
                +"var p=document.querySelector('[data-player-option],.episode-player-option');if(p&&!document.querySelector('#player-box iframe')&&!window.__tvsitesOption){p.click();window.__tvsitesOption=true;}"
                +"}catch(e){}})();";
        web.evaluateJavascript(activate+playScript,null);
        // Track language is read from the track itself, not guessed from the page language.
        web.evaluateJavascript("(function(){var o={videos:[],subs:[]};function scan(d,n){try{var v=d.querySelectorAll('video');for(var i=0;i<v.length;i++){if(v[i].currentSrc)o.videos.push(v[i].currentSrc);}var t=d.querySelectorAll('track[src]');for(var i=0;i<t.length;i++){if(/^(bg|bul)$/i.test(t[i].srclang)||/българ|bulgar/i.test(t[i].label))o.subs.push(t[i].src);}if(n<3){var f=d.querySelectorAll('iframe');for(var i=0;i<f.length;i++)if(f[i].contentDocument)scan(f[i].contentDocument,n+1);}}catch(e){}}scan(document,0);return JSON.stringify(o);})()",value->{
            if(closed)return;
            try{Object decoded=new JSONTokener(value).nextValue();JSONObject o=new JSONObject((String)decoded);
                JSONArray subs=o.getJSONArray("subs");for(int i=0;i<subs.length();i++)if(subs.getString(i).startsWith("http"))subtitles.add(subs.getString(i));
                JSONArray videos=o.getJSONArray("videos");for(int i=0;i<videos.length();i++)record(videos.getString(i),new HashMap<>());
            }catch(Exception ignored){}
        });
        if(++ticks==4 || ticks==8){
            web.evaluateJavascript("(function(){var f=document.querySelector('.movieplay iframe,#video-stage iframe,.video-responsive iframe,#player-box iframe,video,.video-responsive,#player-box');if(!f)return '';f.scrollIntoView({block:'center'});var r=f.getBoundingClientRect();return ((r.left+r.right)/2)+','+((r.top+r.bottom)/2);})()",value->{
                if(closed || web==null)return;try{String[] xy=((String)new JSONTokener(value).nextValue()).split(",");float x=Float.parseFloat(xy[0]),y=Float.parseFloat(xy[1]);long t=SystemClock.uptimeMillis();
                    MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+60,MotionEvent.ACTION_UP,x,y,0);web.dispatchTouchEvent(down);web.dispatchTouchEvent(up);down.recycle();up.recycle();}catch(Exception ignored){}
            });
        }
    }
    private void record(String url,Map<String,String> headers){
        if(closed || !media(url))return;
        if(!headers.containsKey("Referer"))headers.put("Referer",entry.page);
        if(!headers.containsKey("User-Agent"))headers.put("User-Agent",web.getSettings().getUserAgentString());
        candidates.put(url,headers);
        if(!scheduled){scheduled=true;handler.postDelayed(this::finish,1000);}
    }
    private void finish(){
        if(closed || candidates.isEmpty())return;
        String chosen=candidates.keySet().iterator().next();
        for(String url:candidates.keySet())if(url.toLowerCase(Locale.ROOT).contains("master")){chosen=url;break;}
        Map<String,String> headers=new HashMap<>(candidates.get(chosen));String cookie=CookieManager.getInstance().getCookie(chosen);if(cookie!=null)headers.put("Cookie",cookie);
        Stream result=new Stream(chosen,headers,new ArrayList<>(subtitles));close();callback.found(result);
    }
    private void fail(){if(closed)return;close();callback.failed();}
    void close(){closed=true;handler.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.setWebChromeClient(null);web.setWebViewClient(new WebViewClient());root.removeView(web);web.destroy();web=null;}candidates.clear();}
    private static WebResourceResponse blank(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
}
