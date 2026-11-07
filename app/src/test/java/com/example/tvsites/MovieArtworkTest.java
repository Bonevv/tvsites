package com.example.tvsites;

import android.app.Activity;
import android.widget.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MovieArtworkTest {
    @Test public void duplicateVisiblePostersShareOneDownloadAndReturnUsesCache()throws Exception{
        AtomicInteger downloads=new AtomicInteger();ServerSocket server=new ServerSocket();server.bind(new InetSocketAddress("127.0.0.1",0));
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==");
        Thread responder=new Thread(()->{while(!server.isClosed())try(Socket socket=server.accept()){
            BufferedReader request=new BufferedReader(new InputStreamReader(socket.getInputStream()));String line;while((line=request.readLine())!=null && !line.isEmpty()){}
            downloads.incrementAndGet();OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "+png.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(png);out.flush();
        }catch(IOException closed){break;}});responder.setDaemon(true);responder.start();
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout row=new LinearLayout(activity);ImageView a=new ImageView(activity),b=new ImageView(activity),back=new ImageView(activity);row.addView(a);row.addView(b);row.addView(back);activity.setContentView(row);
        String url="http://127.0.0.1:"+server.getLocalPort()+"/poster";a.setTag(url);b.setTag(url);back.setTag(url);MovieArtwork artwork=MovieArtwork.get(activity);
        try{
            artwork.show(a,url);artwork.show(b,url);long deadline=System.currentTimeMillis()+4000;
            while(a.getDrawable()==null && System.currentTimeMillis()<deadline){Thread.sleep(20);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
            assertNotNull(a.getDrawable());assertNotNull(b.getDrawable());assertEquals(1,downloads.get());artwork.show(back,url);assertNotNull(back.getDrawable());assertEquals(1,downloads.get());
        }finally{artwork.cancel(a);artwork.cancel(b);artwork.cancel(back);server.close();activity.finish();}
    }
}
