package com.example.tvsites;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** One cancellable, on-demand XMLTV load. The full guide lives on disk, never in RAM. */
final class EpgLoader {
    static final String URL_BG = "https://iptv-epg.org/files/epg-bg.xml.gz";
    interface Callback { void loaded(EpgGuide guide, String error); }
    private final String source;
    EpgLoader() { this(URL_BG); }
    EpgLoader(String source) { this.source = source; }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tv-epg");
        t.setDaemon(true);
        return t;
    });
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    void load(File directory, Collection<String> names, Callback callback) {
        worker.execute(() -> {
            File cache = new File(directory, "epg-bg.xml.gz");
            String warning = null;
            try {
                if (!cache.isFile() || System.currentTimeMillis() - cache.lastModified() > 6L * 60 * 60 * 1000) {
                    try { download(cache); }
                    catch (IOException e) {
                        if (!cache.isFile()) throw e;
                        warning = "Показана е запазената програма. Няма връзка за обновяване.";
                    }
                }
                try (InputStream in = new LimitedInputStream(new GZIPInputStream(new FileInputStream(cache)), 96L * 1024 * 1024)) {
                    EpgGuide guide = EpgGuide.parse(in, names, System.currentTimeMillis());
                    if (!cancelled) callback.loaded(guide, warning);
                } catch (Exception e) {
                    if (!cancelled) cache.delete(); // A retry must replace a damaged cached guide.
                    throw e;
                }
            } catch (Exception e) {
                if (!cancelled) callback.loaded(null, "Програмата временно не е достъпна. Каналите могат да се гледат.");
            } finally { worker.shutdown(); }
        });
    }

    private void download(File target) throws IOException {
        File temp = new File(target.getParentFile(), "epg-bg.tmp");
        HttpURLConnection c = (HttpURLConnection) new URL(source).openConnection();
        connection = c;
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("Accept-Encoding", "identity");
        try {
            if (cancelled) throw new IOException("cancelled");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = new LimitedInputStream(c.getInputStream(), 12L * 1024 * 1024);
                 OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("cancelled");
                    out.write(buffer, 0, n);
                }
            }
            // Verify the compressed file before replacing a previously usable cache.
            try (InputStream in = new LimitedInputStream(new GZIPInputStream(new FileInputStream(temp)), 96L * 1024 * 1024)) {
                byte[] buffer = new byte[16384];
                while (in.read(buffer) != -1) if (cancelled) throw new IOException("cancelled");
            }
            if (!temp.renameTo(target)) throw new IOException("cache rename");
        } finally {
            c.disconnect(); connection = null; temp.delete();
        }
    }

    void cancel() {
        cancelled = true;
        worker.shutdownNow();
        HttpURLConnection c = connection;
        if (c != null) c.disconnect();
    }

    private static final class LimitedInputStream extends FilterInputStream {
        long remaining;
        LimitedInputStream(InputStream in, long limit) { super(in); remaining = limit; }
        @Override public int read() throws IOException {
            int value = in.read();
            if (value != -1 && --remaining < 0) throw new IOException("EPG too large");
            return value;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0 && (remaining -= n) < 0) throw new IOException("EPG too large");
            return n;
        }
    }
}
