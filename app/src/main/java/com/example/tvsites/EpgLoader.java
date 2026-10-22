package com.example.tvsites;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.TreeSet;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** One cancellable, on-demand XMLTV load. The full guide lives on disk, never in RAM. */
final class EpgLoader {
    static final String URL_BG = "https://iptv-epg.org/files/epg-bg.xml.gz";
    interface Callback { void loaded(EpgGuide guide, String error); }
    /** Reports how a load went and how long it took (for the diagnostics journal). */
    interface Timing { void done(String what, long millis); }
    private static final String MINI = "epg-mini.json";
    private volatile Timing timing;
    void setTiming(Timing value) { timing = value; }
    private void note(String what, long startNanos) {
        Timing t = timing;
        if (t != null) t.done(what, (System.nanoTime() - startNanos) / 1000000L);
    }
    private final String source;
    EpgLoader() { this(URL_BG); }
    EpgLoader(String source) { this.source = source; }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tv-epg");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    void load(File directory, Collection<String> names, Callback callback) {
        worker.execute(() -> {
            long t0 = System.nanoTime();
            File cache = new File(directory, "epg-bg.xml.gz");
            File mini = new File(directory, MINI);
            String warning = null;
            String how = "пълно четене";
            try {
                if (!cache.isFile() || System.currentTimeMillis() - cache.lastModified() > 6L * 60 * 60 * 1000) {
                    try { download(cache); how = "изтегляне и пълно четене"; }
                    catch (IOException e) {
                        if (!cache.isFile()) throw e;
                        warning = "Показана е запазената програма. Няма връзка за обновяване.";
                    }
                }
                // Малкият файл е записан след последното изтегляне и е за същите канали: същото съдържание
                // без да се чете и разгъва целият файл.
                EpgGuide fast = readMini(mini, cache, namesKey(names));
                if (fast != null) {
                    note("малък файл", t0);
                    if (!cancelled) callback.loaded(fast, warning);
                    return;
                }
                try (InputStream in = new LimitedInputStream(new GZIPInputStream(new FileInputStream(cache)), 96L * 1024 * 1024)) {
                    EpgGuide guide = EpgGuide.parse(in, names, System.currentTimeMillis());
                    writeMini(mini, guide, namesKey(names));
                    note(how, t0);
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

    /** Reads only an already downloaded guide. It never makes a network request. */
    void loadCached(File directory, Collection<String> names, Callback callback) {
        worker.execute(() -> {
            try {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                long t0 = System.nanoTime();
                File cache = new File(directory, "epg-bg.xml.gz");
                EpgGuide fast = readMiniAny(new File(directory, MINI));
                if (fast != null && hasAny(fast, names)) {
                    note("малък файл", t0);
                    if (!cancelled) callback.loaded(fast, null);
                    return;
                }
                if (!cache.isFile()) { if (!cancelled) callback.loaded(null, null); return; }
                try (InputStream in = new LimitedInputStream(new GZIPInputStream(new FileInputStream(cache)), 96L * 1024 * 1024)) {
                    EpgGuide guide = EpgGuide.parse(in, names, System.currentTimeMillis());
                    note("пълно четене", t0);
                    if (!cancelled) callback.loaded(guide, null);
                }
            } catch (Exception e) {
                if (!cancelled) callback.loaded(null, null);
            } finally { worker.shutdown(); }
        });
    }

    /** For use while a channel is playing: reads only the small prepared file, never the big one and never the network. */
    void loadMini(File directory, Callback callback) {
        worker.execute(() -> {
            try {
                long t0 = System.nanoTime();
                EpgGuide guide = readMiniAny(new File(directory, MINI));
                note("малък файл", t0);
                if (!cancelled) callback.loaded(guide, null);
            } catch (Exception e) {
                if (!cancelled) callback.loaded(null, null);
            } finally { worker.shutdown(); }
        });
    }

    private static int namesKey(Collection<String> names) {
        TreeSet<String> keys = new TreeSet<>();
        for (String n : names) keys.add(EpgGuide.key(n));
        return keys.hashCode();
    }

    private static boolean hasAny(EpgGuide guide, Collection<String> names) {
        long now = System.currentTimeMillis();
        for (String n : names) if (!guide.forChannel(n, now).isEmpty()) return true;
        return false;
    }

    private static String readText(File f) throws IOException {
        if (f.length() > 8L * 1024 * 1024) throw new IOException("too large");
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** The compact copy, whatever channels it was made for. */
    private static EpgGuide readMiniAny(File mini) {
        try {
            if (!mini.isFile()) return null;
            String all = readText(mini);
            int nl = all.indexOf('\n');
            return nl < 0 ? null : EpgGuide.fromJson(all.substring(nl + 1));
        } catch (Exception e) {
            return null;
        }
    }

    /** The compact copy, only if it was written after the big file and for the same channels. */
    private static EpgGuide readMini(File mini, File cache, int key) {
        try {
            if (!mini.isFile() || mini.lastModified() < cache.lastModified()) return null;
            String all = readText(mini);
            int nl = all.indexOf('\n');
            if (nl < 0 || Integer.parseInt(all.substring(0, nl).trim()) != key) return null;
            return EpgGuide.fromJson(all.substring(nl + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeMini(File mini, EpgGuide guide, int key) {
        try {
            File tmp = new File(mini.getParentFile(), MINI + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write((key + "\n").getBytes("UTF-8"));
                out.write(guide.toJson().getBytes("UTF-8"));
            }
            if (!tmp.renameTo(mini)) tmp.delete();
        } catch (Exception ignored) {
            // без малък файл следващото четене пак е пълно
        }
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
