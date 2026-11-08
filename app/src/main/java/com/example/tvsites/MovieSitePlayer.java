package com.example.tvsites;

import android.os.*;
import android.webkit.*;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.util.*;

/** Keeps the site's own player and subtitle controls; no stream interception or second browser. */
final class MovieSitePlayer {
    interface Events {
        void ready();void failed();void captionsChanged();default void progress(int secondsLeft){}
        default void playing(){}default void ended(){}default void seeked(long position){}default void unsupported(){}
    }
    static final String PREFIX="TVSITES_ORIGINAL:";
    static final class Caption {
        final String frame,backend,label,language;final int index;final boolean selected;
        Caption(String frame,String backend,int index,String label,String language,boolean selected){this.frame=frame;this.backend=backend;this.index=index;this.label=label;this.language=language;this.selected=selected;}
    }
    private final WebView web;private final MovieCatalog.Entry source;private final Events events;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,List<Caption>> frames=new LinkedHashMap<>();
    private ScriptHandler script;private String helper="";private boolean closed,ready;private long deadline,position,duration,requestedSeek;
    private final Set<String> restoredFrames=new HashSet<>();
    private String activeFrame="";private int request,pendingRequest;private boolean controlsOnly;
    MovieSitePlayer(WebView web,MovieCatalog.Entry source,Events events){this.web=web;this.source=source;this.events=events;}
    void start(){
        install(false);
        web.getSettings().setSupportMultipleWindows(true);web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        deadline=SystemClock.uptimeMillis()+24000;handler.postDelayed(timeout,24000);handler.postDelayed(progress,1000);
        handler.postDelayed(this::pageReady,2500);handler.postDelayed(this::pageReady,6000);
        String url=source.embed.isEmpty()?source.page:source.embed;
        web.loadUrl(url,Collections.singletonMap("Referer",source.page.split("#")[0]));
    }
    void startControls(){install(true);}
    private void install(boolean onlyControls){
        controlsOnly=onlyControls;
        helper=readScript(web);
        if(onlyControls)helper="window.__tvsitesControlsOnly=true;"+helper;
        try{if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))script=WebViewCompat.addDocumentStartJavaScript(web,helper,Collections.singleton("*"));}catch(RuntimeException ignored){}
    }
    void navigationStarted(){if(!controlsOnly)return;position=duration=requestedSeek=0;activeFrame="";pendingRequest=0;frames.clear();restoredFrames.clear();handler.removeCallbacks(controlTimeout);}
    void pageReady(){if(!closed)web.evaluateJavascript(helper,null);}
    boolean console(String message){
        if(closed || !message.startsWith(PREFIX))return false;
        try {
            JSONObject data=new JSONObject(message.substring(PREFIX.length()));String type=data.optString("type");
            if(type.equals("ready")){
                if(!ready){ready=true;handler.removeCallbacks(timeout);handler.removeCallbacks(progress);events.ready();}
            } else if(type.equals("playing")){
                String frame=data.optString("frame");
                boolean mainMedia=data.optDouble("duration",duration/1000.0)>=duration/1000.0;
                if(!frame.isEmpty() && mainMedia)activeFrame=frame;if(mainMedia)events.playing();
                if(requestedSeek>0 && !frame.isEmpty() && restoredFrames.add(frame)){
                    JSONObject command=new JSONObject().put("action","seek").put("frame",frame).put("position",requestedSeek/1000.0);
                    web.evaluateJavascript("if(window.__tvsitesOriginalCommand)window.__tvsitesOriginalCommand("+command+");",null);
                }
            } else if(type.equals("position")){
                double seconds=data.optDouble("position",-1),length=data.optDouble("duration",0);
                if(seconds>=0 && seconds<=604800 && length>=duration/1000.0){position=(long)(seconds*1000);duration=(long)(length*1000);if(!data.optString("frame").isEmpty())activeFrame=data.optString("frame");}
            } else if(type.equals("ended")){
                if(data.optDouble("duration",0)>=duration/1000.0 && data.optDouble("duration",0)>0)events.ended();
            } else if(type.equals("seeked") || type.equals("controlled") || type.equals("unavailable")){
                if(pendingRequest>0 && data.optInt("request")==pendingRequest){pendingRequest=0;handler.removeCallbacks(controlTimeout);
                    if(type.equals("seeked")){long value=(long)(data.optDouble("position",-1)*1000);if(value>=0){position=value;events.seeked(value);}}
                    else if(type.equals("unavailable"))events.unsupported();
                }
            } else if(type.equals("captions")){
                String frame=data.optString("frame"),backend=data.optString("backend");JSONArray tracks=data.optJSONArray("tracks");List<Caption> captions=new ArrayList<>();
                if(!frame.isEmpty() && tracks!=null && (frames.containsKey(frame) || frames.size()<8)){
                    for(int i=0;i<Math.min(24,tracks.length());i++){JSONObject t=tracks.optJSONObject(i);if(t!=null)captions.add(new Caption(frame,backend,t.optInt("index"),t.optString("label","Субтитри"),t.optString("language"),t.optBoolean("selected")));}
                    frames.put(frame,captions);events.captionsChanged();
                }
            }
        }catch(JSONException ignored){}return true;
    }
    List<Caption> captions(){List<Caption> out=new ArrayList<>();for(List<Caption> list:frames.values())out.addAll(list);return out;}
    long position(){return position;}
    long duration(){return duration;}
    void seekBy(int seconds){requestedSeek=0;control("seekBy",seconds);}
    void toggle(){control("toggle",0);}
    void cancelControl(){pendingRequest=0;handler.removeCallbacks(controlTimeout);}
    private void control(String action,int seconds){
        if(closed)return;try{pendingRequest=++request;JSONObject data=new JSONObject().put("action",action).put("request",pendingRequest).put("seconds",seconds);
            if(!activeFrame.isEmpty())data.put("frame",activeFrame);web.evaluateJavascript("if(window.__tvsitesOriginalCommand)window.__tvsitesOriginalCommand("+data+");",null);
            handler.removeCallbacks(controlTimeout);handler.postDelayed(controlTimeout,2000);
        }catch(JSONException ignored){}
    }
    private final Runnable controlTimeout=this::controlExpired;
    private void controlExpired(){if(!closed && pendingRequest>0){pendingRequest=0;events.unsupported();}}
    void seek(long milliseconds){if(closed || milliseconds<=0)return;requestedSeek=milliseconds;try{JSONObject data=new JSONObject().put("action","seek").put("position",milliseconds/1000.0);web.evaluateJavascript("if(window.__tvsitesOriginalCommand)window.__tvsitesOriginalCommand("+data+");",null);}catch(JSONException ignored){}}
    void refreshCaptions(){command("list",null);}
    void choose(Caption caption){command("select",caption);}
    void off(){command("off",null);}
    void openCaptionControls(){command("open",null);}
    private void command(String action,Caption caption){
        if(closed)return;try{
            JSONObject data=new JSONObject().put("action",action);if(caption!=null)data.put("frame",caption.frame).put("backend",caption.backend).put("index",caption.index);
            web.evaluateJavascript("if(window.__tvsitesOriginalCommand)window.__tvsitesOriginalCommand("+data+");",null);
        }catch(JSONException ignored){}
    }
    boolean allowsNavigation(String url){return allowsNavigation(url,true);}
    boolean allowsNavigation(String url,boolean mainFrame){
        if(!MovieResolver.http(url))return false;
        if(!mainFrame)return true;
        if(!ready)return true;
        try{return Objects.equals(new URI(url).getHost(),new URI(web.getUrl()).getHost());}catch(Exception e){return false;}
    }
    void failed(){if(closed || ready)return;handler.post(()->{if(!closed){close();events.failed();}});}
    private final Runnable progress=new Runnable(){@Override public void run(){if(closed || ready)return;events.progress((int)Math.max(0,(deadline-SystemClock.uptimeMillis()+999)/1000));handler.postDelayed(this,1000);}};
    private final Runnable timeout=this::failed;
    void close(){closed=true;handler.removeCallbacksAndMessages(null);if(script!=null){try{script.remove();}catch(RuntimeException ignored){}script=null;}frames.clear();}
    private static String readScript(WebView web){try(InputStream in=web.getContext().getAssets().open("movie-site-player.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");
    }catch(IOException e){return "";}}
}
