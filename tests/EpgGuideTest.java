package com.example.tvsites;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Standalone regression checks: run with tests/run-epg-tests.sh. */
public final class EpgGuideTest {
    static int checks;
    static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
    static EpgGuide parse(String xml, String... names) throws Exception {
        return EpgGuide.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Arrays.asList(names),
                EpgGuide.time("20261020200000 +0300"));
    }
    public static void main(String[] args) throws Exception {
        long now = EpgGuide.time("20261020200000 +0300");
        check(now == EpgGuide.time("20261020170000 +0000"), "XMLTV timezone offset");
        check(EpgGuide.time("20261020170000") == now, "missing offset means UTC");
        check(EpgGuide.time("bad") == 0, "invalid time");
        check(EpgGuide.key("БНТ 1 HD").equals(EpgGuide.key("BNT1.bg")), "Cyrillic BNT alias");
        check(EpgGuide.key("BG - bTV HD").equals("btv"), "display-name prefix and HD suffix");
        check(EpgGuide.key("NOVA HD").equals("nova"), "NOVA suffix");
        String xml = "<tv><channel id='bTV.bg'><display-name>BG - bTV</display-name></channel>"
                + "<programme channel='bTV.bg' start='20261020200000 +0300' stop='20261020203000 +0300'><title>Новини &amp; спорт</title><desc>Описание</desc></programme>"
                + "<programme channel='bTV.bg' start='20261020193000 +0300' stop='20261020200000 +0300'><title>Изтекло</title></programme>"
                + "<programme channel='bTV.bg' start='20261020203000 +0300' stop='20261020220000 +0300'><title>Следващо</title></programme>"
                + "<programme channel='Unknown' start='20261020200000 +0300' stop='20261020203000 +0300'><title>Чуждо</title></programme></tv>";
        EpgGuide guide = parse(xml, "bTV HD");
        check(guide.forChannel("bTV", now).size() == 2, "only relevant, unexpired programmes");
        check(guide.current("bTV", now).title.equals("Новини & спорт"), "decoded title");
        check(guide.current("bTV", now).description.equals("Описание"), "description");
        check(guide.current("bTV", now + 30 * 60000).title.equals("Следващо"), "boundary selects next event");
        check(guide.current("unknown", now) == null, "no invented listings");
        check(guide.current("bTV", now + 3 * 3600000) == null, "expired listings");
        StringBuilder big = new StringBuilder("<tv>");
        for (int i = 20; i >= 0; i--) {
            String start = String.format(Locale.US, "202610%02d%02d0000 +0000", 20 + (17 + i) / 24, (17 + i) % 24);
            String stop = String.format(Locale.US, "202610%02d%02d0000 +0000", 20 + (18 + i) / 24, (18 + i) % 24);
            big.append("<programme channel='bTV.bg' start='").append(start).append("' stop='").append(stop).append("'><title>Event</title></programme>");
        }
        guide = parse(big.append("</tv>").toString(), "bTV");
        check(guide.forChannel("bTV", now).size() == 12, "bounded memory per channel");
        check(guide.current("bTV", now) != null, "unordered input keeps nearest events");
        try { parse("<tv><programme>", "bTV"); throw new AssertionError("malformed XML accepted"); }
        catch (org.xml.sax.SAXException expected) { checks++; }
        Thread.currentThread().interrupt();
        try { parse(xml, "bTV"); throw new AssertionError("cancellation ignored"); }
        catch (org.xml.sax.SAXException expected) { checks++; }
        finally { Thread.interrupted(); }
        if (args.length != 0) {
            try (InputStream input = new GZIPInputStream(new FileInputStream(args[0]))) {
                long feedNow = System.currentTimeMillis();
                guide = EpgGuide.parse(input, Arrays.asList("bTV HD", "NOVA HD", "БНТ 1"), feedNow);
                check(guide.current("bTV", feedNow) != null, "live feed bTV mapping");
                check(guide.current("NOVA", feedNow) != null, "live feed NOVA mapping");
                check(guide.current("БНТ 1", feedNow) != null, "live feed Cyrillic BNT mapping");
            }
        }
        System.out.println("Passed " + checks + " XMLTV checks");
    }
}
