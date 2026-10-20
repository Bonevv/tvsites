package com.example.tvsites;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Only the selected programme's artwork is decoded; leaving the view cancels its load. */
final class TvArtwork extends ImageView {
    private ExecutorService worker;
    private volatile HttpURLConnection connection;
    private volatile int generation;
    private String source = "";

    TvArtwork(Context context) { super(context); setScaleType(ScaleType.CENTER_CROP); }

    void show(String url) {
        if (source.equals(url)) return;
        clear();
        source = url;
        if (!url.startsWith("https://")) return;
        final int token = generation;
        worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                synchronized (this) {
                    if (token != generation) return;
                    connection = c;
                }
                if (c.getResponseCode() != 200) return;
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (token != generation || data.size() + n > 2 * 1024 * 1024) return;
                        data.write(buffer, 0, n);
                    }
                }
                byte[] bytes = data.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > 960 || options.outHeight / options.inSampleSize > 540) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                post(() -> { if (token == generation && isAttachedToWindow()) setImageBitmap(bitmap); });
            } catch (Exception ignored) {
                // Artwork is optional; programme information and playback remain available.
            } finally {
                if (c != null) c.disconnect();
                synchronized (this) { if (connection == c) connection = null; }
            }
        });
        worker.shutdown();
    }

    synchronized void clear() {
        generation++;
        source = "";
        if (worker != null) worker.shutdownNow();
        if (connection != null) connection.disconnect();
        connection = null;
        setImageDrawable(null);
    }

    @Override protected void onDetachedFromWindow() { clear(); super.onDetachedFromWindow(); }
}
