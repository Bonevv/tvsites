package com.example.tvsites;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.GZIPOutputStream;

public final class EpgLoaderTest {
    static int checks;
    static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    static byte[] gzip(String xml) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) { out.write(xml.getBytes("UTF-8")); }
        return bytes.toByteArray();
    }
    static Object[] load(String source, File directory) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Object[] result = new Object[2];
        new EpgLoader(source).load(directory, Arrays.asList("bTV HD"), (guide, error) -> {
            result[0] = guide; result[1] = error; done.countDown();
        });
        check(done.await(8, TimeUnit.SECONDS), "loader completes");
        return result;
    }
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("epg-loader-test").toFile();
        File cache = new File(dir, "epg-bg.xml.gz");
        byte[] feed = gzip("<tv><channel id='bTV.bg'><display-name>bTV</display-name></channel></tv>");
        AtomicInteger status = new AtomicInteger(200), requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/epg", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(status.get(), feed.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(feed); }
        });
        server.start();
        String source = "http://127.0.0.1:" + server.getAddress().getPort() + "/epg";
        try {
            Object[] r = load(source, dir);
            check(r[0] != null && r[1] == null && cache.isFile(), "download parsed and cached");
            check(requests.get() == 1, "one initial download");
            r = load(source, dir);
            check(r[0] != null && requests.get() == 1, "fresh cache avoids network");
            status.set(503); cache.setLastModified(1);
            r = load(source, dir);
            check(r[0] != null && r[1] != null && cache.isFile(), "offline stale cache remains usable");
            cache.delete();
            r = load(source, dir);
            check(r[0] == null && r[1] != null, "unavailable guide reported without crashing");
            Files.write(cache.toPath(), new byte[]{1, 2, 3});
            r = load(source, dir);
            check(r[0] == null && !cache.exists(), "corrupt cache removed for retry");
            status.set(200);
            r = load(source, dir);
            check(r[0] != null, "retry recovers after corrupt cache");
            check(!new File(dir, "epg-bg.tmp").exists(), "no partial download retained");
        } finally {
            server.stop(0);
            for (File f : dir.listFiles()) f.delete();
            dir.delete();
        }
        System.out.println("Passed " + checks + " EPG loader checks");
    }
}
