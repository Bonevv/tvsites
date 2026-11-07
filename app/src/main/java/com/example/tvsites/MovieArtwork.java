package com.example.tvsites;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.*;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** One application-wide download, deduplicated requests and a bounded thumbnail cache. */
final class MovieArtwork {
    private static MovieArtwork shared;
    static synchronized MovieArtwork get(Context context){if(shared==null)shared=new MovieArtwork(context.getApplicationContext());return shared;}
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final File directory;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,20,TimeUnit.SECONDS,new LinkedBlockingQueue<Runnable>(24));
    private final Map<String,Task> pending=new HashMap<>();
    private final Map<String,Long> failures=new HashMap<>();
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(2*1024*1024){@Override protected int sizeOf(String key,Bitmap image){return image.getByteCount();}};
    private MovieArtwork(Context context){directory=new File(context.getCacheDir(),"movie-posters");worker.allowCoreThreadTimeOut(true);}
    void show(ImageView view,String url){
        if(url.isEmpty())return;
        Bitmap bitmap=memory.get(url);if(bitmap!=null){view.setImageBitmap(bitmap);return;}
        if(SystemClock.uptimeMillis()-failures.getOrDefault(url,-120000L)<120000)return;
        Task task=pending.get(url);
        if(task!=null){for(WeakReference<ImageView> ref:task.views)if(ref.get()==view)return;task.views.add(new WeakReference<>(view));return;}
        task=new Task(url);task.views.add(new WeakReference<>(view));pending.put(url,task);
        try{worker.execute(task);}catch(RejectedExecutionException full){pending.remove(url);}
    }
    void cancel(ImageView view){
        for(Iterator<Map.Entry<String,Task>> it=pending.entrySet().iterator();it.hasNext();){
            Task task=it.next().getValue();task.views.removeIf(ref->ref.get()==null || ref.get()==view);
            if(task.views.isEmpty()){task.cancelled=true;worker.remove(task);it.remove();}
        }
    }
    private final class Task implements Runnable {
        final String url;final List<WeakReference<ImageView>> views=new ArrayList<>();volatile boolean cancelled;
        Task(String url){this.url=url;}
        @Override public void run(){
            Bitmap result=null;
            try {
                directory.mkdirs();File file=new File(directory,key(url)+".jpg");
                if(file.isFile()){BitmapFactory.Options cached=new BitmapFactory.Options();cached.inPreferredConfig=Bitmap.Config.RGB_565;result=BitmapFactory.decodeFile(file.getPath(),cached);}
                if(result==null && !cancelled){
                    String small=url.replace("image.tmdb.org/t/p/w780/","image.tmdb.org/t/p/w185/");
                    HttpURLConnection connection=(HttpURLConnection)new URL(small).openConnection();
                    connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream in=connection.getInputStream()){
                        byte[] buf=new byte[4096];int n;while(!cancelled && (n=in.read(buf))!=-1){if(bytes.size()+n>1536*1024)throw new IOException("poster too large");bytes.write(buf,0,n);}
                    }finally{connection.disconnect();}
                    if(!cancelled){
                        byte[] data=bytes.toByteArray();BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,opts);
                        if(opts.outWidth>0 && opts.outHeight>0){opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>256 || opts.outHeight/opts.inSampleSize>384)opts.inSampleSize*=2;
                            opts.inJustDecodeBounds=false;opts.inPreferredConfig=Bitmap.Config.RGB_565;result=BitmapFactory.decodeByteArray(data,0,data.length,opts);}
                        if(result!=null){AtomicFile target=new AtomicFile(file);FileOutputStream out=null;try{out=target.startWrite();result.compress(Bitmap.CompressFormat.JPEG,78,out);target.finishWrite(out);}catch(IOException ex){if(out!=null)target.failWrite(out);}}
                    }
                }
                if(file.exists())file.setLastModified(System.currentTimeMillis());trim();
            }catch(Exception ignored){}
            Bitmap bitmap=result;ui.post(()->{
                if(pending.get(url)!=this)return;pending.remove(url);
                if(cancelled)return;
                if(bitmap==null){if(failures.size()>100)failures.clear();failures.put(url,SystemClock.uptimeMillis());return;}
                memory.put(url,bitmap);
                for(WeakReference<ImageView> ref:views){ImageView view=ref.get();if(view!=null && view.isAttachedToWindow() && view.isShown() && url.equals(view.getTag()))view.setImageBitmap(bitmap);}
            });
        }
    }
    private void trim(){File[] files=directory.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;long total=0;for(File file:files)total+=file.length();
        if(total<=12*1024*1024)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(File file:files){long bytes=file.length();if(file.delete())total-=bytes;if(total<=10*1024*1024)break;}}
    private static String key(String url)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b&255));return name.toString();}
}
