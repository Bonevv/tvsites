package com.example.tvsites.smoke;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import org.json.*;

public class Smoke extends Instrumentation {
    Activity activity;
    int checks;
    Object field(String name) throws Exception { Field f=activity.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    Object call(String name,Object...args) throws Exception {
        for(Method m:activity.getClass().getDeclaredMethods()) if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(activity,args);}
        throw new NoSuchMethodException(name);
    }
    void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    void main(Runnable r){
        final Throwable[] failure={null};
        runOnMainSync(()->{try{r.run();}catch(Throwable t){failure[0]=t;}});
        waitForIdleSync();
        if(failure[0]!=null)throw new AssertionError("UI check failed",failure[0]);
    }
    void screenshot(String name) { getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output(name)); }
    OutputStream output(String name) {try{return new FileOutputStream(new File(getTargetContext().getCacheDir(),name));}catch(Exception e){throw new RuntimeException(e);} }
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            String[] names={"bTV HD","NOVA HD","БНТ 1","DIEMA","NOVA SPORT","KINO NOVA"};
            JSONArray channels=new JSONArray();
            for(int i=0;i<names.length;i++)channels.put(new JSONObject().put("i","smoke"+i).put("n",names[i]).put("s",new JSONArray().put(new JSONObject().put("a","Local test").put("p","http://127.0.0.1:8765/site.html").put("u","http://127.0.0.1:8765/live.m3u8"))));
            android.content.SharedPreferences.Editor fixtures=getTargetContext().getSharedPreferences("tvsites",0).edit();
            fixtures.putString("tvchans",channels.toString()).putString("tvlast","smoke0");
            for(int i=0;i<names.length;i++)fixtures.putBoolean("tvfav:smoke"+i,false);
            fixtures.commit();
            try(InputStream in=new FileInputStream("/data/local/tmp/tvsites-epg.xml.gz");OutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),"epg-bg.xml.gz"))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            Intent intent=new Intent().setClassName("com.example.tvsites","com.example.tvsites.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);waitForIdleSync();
            main(()->{try{call("tvShowPanel");check(field("web")==null,"TV overview must not create WebView");check(field("player")==null,"TV overview must not create player");check(!(boolean)call("catShown"),"catalogue closed on TV overview");}catch(Exception e){throw new RuntimeException(e);}});
            long until=SystemClock.uptimeMillis()+60000;
            while(SystemClock.uptimeMillis()<until){
                final boolean[] loaded={false};main(()->{try{loaded[0]=field("tvGuide")!=null;}catch(Exception e){throw new RuntimeException(e);}});
                if(loaded[0])break;SystemClock.sleep(200);
            }
            main(()->{try{check(field("tvGuide")!=null,"Android SAX parser loads actual XMLTV guide; error="+field("tvEpgError")+", loader="+field("tvEpgLoader"));}catch(Exception e){throw new RuntimeException(e);}});
            screenshot("tv-overview.png");
            main(()->{try{
                Field all=activity.getClass().getDeclaredField("TV_ALL");all.setAccessible(true);call("tvSelect",all.getInt(null));
                check((boolean)call("tvPanelShown"),"all-channel view visible");
                check(field("web")==null&&field("player")==null,"channel grid has no browser or player");
                Field home=activity.getClass().getDeclaredField("TV_HOME");home.setAccessible(true);call("tvSelect",home.getInt(null));
                View fav=(View)field("tvHeroFavourite");fav.performClick();
                check(getTargetContext().getSharedPreferences("tvsites",0).getBoolean("tvfav:smoke0",false),"favorite persisted");
                call("showCatalog",new Object[]{null});
                check((boolean)call("catShown"),"movie catalogue visible");
                check(!(boolean)call("tvPanelShown"),"TV overview closed for movies");
                check(field("tvEpgLoader")==null&&field("tvGuide")==null,"EPG released for movies");
                call("openSite",(Object)new String[]{"Local site","http://127.0.0.1:8765/site.html",""});
                check(!(boolean)call("catShown"),"catalogue closed for site");
                check(field("web")!=null,"site browser created on demand");
                check(field("player")==null,"no hidden player in site mode");
                check(field("catMain")==null,"catalogue button graph released");
                check(((android.view.ViewGroup)field("heroCta")).getChildCount()==0,"catalogue actions released");
            }catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(1500);
            main(()->{try{
                call("tvShowPanel");check(field("web")==null,"browser destroyed when returning to TV");
                Object channel=((java.util.List<?>)field("tvChans")).get(0);
                call("tvTuneUser",channel,-1);
                check((boolean)field("tvMode"),"TV playback mode active");
                check(!(boolean)call("tvPanelShown"),"overview released for playback");
                check(field("tvGuide")==null&&field("tvEpgLoader")==null,"no EPG worker while watching");
                check(field("tvHeroArtwork")==null,"artwork released for playback");
                check(field("web")==null,"direct stream has no browser");
            }catch(Exception e){throw new RuntimeException(e);}});
            long end=SystemClock.uptimeMillis()+25000;
            final boolean[] playing={false};
            while(SystemClock.uptimeMillis()<end){main(()->{try{playing[0]=(boolean)field("tvPlaying");}catch(Exception e){throw new RuntimeException(e);}});if(playing[0])break;SystemClock.sleep(200);}
            check(playing[0],"real local HLS stream reaches playing state");
            main(()->{try{
                call("tvLeave",true);
                check(field("player")==null,"player released on exit");
                call("showCatalog",new Object[]{null});
                call("openSite",(Object)new String[]{"Local site","http://127.0.0.1:8765/site.html",""});
                call("teardownSite");
                check(field("web")==null&&field("player")==null,"teardown releases browser and player");
                check(!(boolean)call("catShown")&&!(boolean)call("tvPanelShown"),"teardown closes sections");
                call("tvShowPanel");
            }catch(Exception e){throw new RuntimeException(e);}});
            result.putString("stream","Passed "+checks+" Android workflow checks; HLS playback passed\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable t){result.putString("error",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}
    }
}
