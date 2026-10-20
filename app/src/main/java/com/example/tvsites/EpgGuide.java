package com.example.tvsites;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.*;

/** Streaming XMLTV reader. Keeps at most 12 upcoming events per requested channel. */
final class EpgGuide {
    static final class Event {
        final long start, end;
        final String title, description, image;
        Event(long start, long end, String title, String description, String image) {
            this.start = start;
            this.end = end;
            this.title = title;
            this.description = description;
            this.image = image;
        }
    }

    private final Map<String, List<Event>> events;
    private EpgGuide(Map<String, List<Event>> events) { this.events = events; }

    static String key(String name) {
        String value = name.toLowerCase(Locale.ROOT).replaceFirst("^bg\\s*[-:]\\s*", "")
                .replaceFirst("(?i)\\.bg$", "")
                .replaceAll("(?i)(?:\\s|[-_])*(?:full\\s*hd|uhd|fhd|hd|sd|4k)$", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
        if (value.matches("бнт[1-4]")) return "bnt" + value.charAt(3);
        if (value.equals("нова") || value.equals("novatv")) return "nova";
        if (value.equals("бтв")) return "btv";
        if (value.equals("кинонова")) return "kinonova";
        if (value.equals("новаспорт")) return "novasport";
        if (value.equals("нованюз")) return "novanews";
        if (value.equals("диема")) return "diema";
        if (value.equals("бтвекшън")) return "btvaction";
        if (value.equals("бтвсинема")) return "btvcinema";
        if (value.equals("diemafamily") || value.equals("диемафемили")) return "diemafamily";
        return value;
    }

    List<Event> forChannel(String name, long now) {
        List<Event> list = events.get(key(name));
        if (list == null) return Collections.emptyList();
        List<Event> result = new ArrayList<>();
        for (Event e : list) if (e.end > now) result.add(e);
        return result;
    }

    Event current(String name, long now) {
        for (Event e : forChannel(name, now)) if (e.start <= now && e.end > now) return e;
        return null;
    }

    static long time(String value) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        f.setLenient(false);
        return time(value, f);
    }

    private static long time(String value, SimpleDateFormat f) {
        if (value == null) return 0;
        try {
            // XMLTV without an offset is UTC, never the device's local timezone.
            String v = value.trim();
            if (v.length() == 14) v += " +0000";
            return f.parse(v).getTime();
        } catch (Exception ignored) { return 0; }
    }

    static EpgGuide parse(InputStream input, Collection<String> names, final long now) throws Exception {
        final Set<String> wanted = new HashSet<>();
        for (String name : names) wanted.add(key(name));
        final Map<String, Set<String>> aliases = new HashMap<>();
        final Map<String, List<Event>> result = new HashMap<>();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        org.xml.sax.XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setErrorHandler(new DefaultHandler());
        reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        reader.setContentHandler(new DefaultHandler() {
            String channel, programmeChannel, field;
            Set<String> channelNames, programmeNames;
            final SimpleDateFormat dates = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
            { dates.setLenient(false); }
            long start, end;
            String title = "", description = "", image = "";
            final StringBuilder text = new StringBuilder();
            void check() throws SAXException {
                if (Thread.currentThread().isInterrupted()) throw new SAXException("cancelled");
            }
            @Override public void startElement(String uri, String local, String q, Attributes a) throws SAXException {
                check();
                if (q.equals("channel")) {
                    channel = a.getValue("id");
                    channelNames = new HashSet<>();
                    if (channel != null) {
                        String id = key(channel.replaceFirst("(?i)\\.bg$", ""));
                        if (wanted.contains(id)) channelNames.add(id);
                    }
                } else if (q.equals("programme")) {
                    programmeChannel = a.getValue("channel");
                    programmeNames = aliases.get(programmeChannel);
                    if (programmeNames == null && programmeChannel != null) {
                        String id = key(programmeChannel);
                        if (wanted.contains(id)) programmeNames = Collections.singleton(id);
                    }
                    start = end = 0;
                    if (programmeNames != null && !programmeNames.isEmpty()) {
                        start = time(a.getValue("start"), dates);
                        end = time(a.getValue("stop"), dates);
                    }
                    title = ""; description = ""; image = "";
                }
                if (q.equals("icon") && channel == null && programmeNames != null && !programmeNames.isEmpty()) {
                    String src = a.getValue("src");
                    if (src != null && src.startsWith("https://") && src.length() < 2048) image = src;
                }
                if (q.equals("display-name") || (programmeNames != null && !programmeNames.isEmpty()
                        && (q.equals("title") || q.equals("desc")))) {
                    field = q; text.setLength(0);
                }
            }
            @Override public void characters(char[] ch, int s, int n) throws SAXException {
                check();
                if (field != null && text.length() < 2048) text.append(ch, s, Math.min(n, 2048 - text.length()));
            }
            @Override public void endElement(String uri, String local, String q) throws SAXException {
                check();
                if (q.equals(field)) {
                    String value = text.toString().trim();
                    if (q.equals("display-name") && channel != null && wanted.contains(key(value))) channelNames.add(key(value));
                    else if (q.equals("title") && title.isEmpty()) title = value;
                    else if (q.equals("desc") && description.isEmpty()) description = value;
                    field = null;
                }
                if (q.equals("channel")) {
                    aliases.put(channel, channelNames); channel = null;
                } else if (q.equals("programme") && end > now && end > start && start < now + 48L * 60 * 60 * 1000) {
                    if (programmeNames != null) for (String match : programmeNames) {
                        List<Event> list = result.get(match);
                        if (list == null) { list = new ArrayList<>(); result.put(match, list); }
                        boolean duplicate = false;
                        for (Event e : list) if (e.start == start && e.end == end) duplicate = true;
                        if (!duplicate) {
                            list.add(new Event(start, end, title, description, image));
                            Collections.sort(list, (a, b) -> Long.compare(a.start, b.start));
                            while (list.size() > 12) list.remove(list.size() - 1);
                        }
                    }
                }
            }
        });
        reader.parse(new InputSource(input));
        return new EpgGuide(result);
    }
}
