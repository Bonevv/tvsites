package com.example.tvsites;

import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Лек запис на последните събития на плеъра, за да се види защо картината заседва.
 *
 * Пази се само в паметта: най-много 40 къси събития и 8 парчета. Докато картината върви, нищо не се
 * пише на диска. Когато картината заседне, MainActivity взема оттук „снимка“ (snapshot) и я записва
 * в дневника на заден план. Редовете на снимката са разделени със знака ¦.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class StreamTrace implements AnalyticsListener {
    private static final int MAX_EVENTS = 40;
    private static final int MAX_CHUNKS = 8;
    private static final int TYPES = 4; // 0 основен (звук и картина заедно), 1 звук, 2 видео, 3 текст
    private static final int MAX_TEXT = 1000;

    private static final class Ev {
        final long at;
        final String text;

        Ev(long at, String text) {
            this.at = at;
            this.text = text;
        }
    }

    private static final class Chunk {
        long at;
        int type;
        long start = C.TIME_UNSET;
        long end = C.TIME_UNSET;
        long loadMs;
        long bytes;
        long gapMs = C.TIME_UNSET; // разлика до края на предишното парче от същия вид (само ако е голяма)
        String host = "";
    }

    private final ArrayDeque<Ev> events = new ArrayDeque<>();
    private final ArrayDeque<Chunk> chunks = new ArrayDeque<>();
    private final long[] lastEnd = new long[TYPES];
    private long manifestAt;
    private long manifestMs;
    private String manifestHost = "";
    private int manifestCount;
    private long bufAt;      // кога е започнало текущото буфериране (0 = не буферира)
    private long bufPos;     // позицията в този момент
    private DecoderCounters vRef;
    private DecoderCounters aRef;
    private int vq, vr, vd, aq, ar;

    StreamTrace() {
        reset();
    }

    /** Нов опит с плеъра: старото се забравя. */
    synchronized void reset() {
        events.clear();
        chunks.clear();
        for (int i = 0; i < TYPES; i++) lastEnd[i] = C.TIME_UNSET;
        manifestAt = 0;
        manifestMs = 0;
        manifestHost = "";
        manifestCount = 0;
        bufAt = 0;
        bufPos = 0;
        vRef = null;
        aRef = null;
        vq = vr = vd = aq = ar = 0;
    }

    /** Плеърът започна да буферира: запомня се къде са броячите на декодерите. */
    synchronized void bufferingStarted(ExoPlayer p) {
        bufAt = SystemClock.elapsedRealtime();
        try {
            bufPos = p.getCurrentPosition();
            vRef = p.getVideoDecoderCounters();
            aRef = p.getAudioDecoderCounters();
            if (vRef != null) {
                vRef.ensureUpdated();
                vq = vRef.queuedInputBufferCount;
                vr = vRef.renderedOutputBufferCount;
                vd = vRef.droppedBufferCount;
            }
            if (aRef != null) {
                aRef.ensureUpdated();
                aq = aRef.queuedInputBufferCount;
                ar = aRef.renderedOutputBufferCount;
            }
        } catch (Throwable ignored) {
            // без начални стойности снимката показва общите
        }
    }

    private void add(String text) {
        events.addLast(new Ev(SystemClock.elapsedRealtime(), text));
        while (events.size() > MAX_EVENTS) events.removeFirst();
    }

    // ---- събития на плеъра ----

    @Override
    public synchronized void onPlaybackStateChanged(EventTime eventTime, int state) {
        add("състояние " + stateName(state));
    }

    @Override
    public synchronized void onLoadCompleted(EventTime eventTime, LoadEventInfo info, MediaLoadData data) {
        long now = SystemClock.elapsedRealtime();
        if (data.dataType == C.DATA_TYPE_MANIFEST) {
            manifestCount++;
            manifestAt = now;
            manifestMs = info.loadDurationMs;
            manifestHost = hostOf(info);
            return;
        }
        if (data.dataType != C.DATA_TYPE_MEDIA) return;
        Chunk c = new Chunk();
        c.at = now;
        c.host = hostOf(info);
        c.type = typeIndex(data.trackType);
        c.start = data.mediaStartTimeMs;
        c.end = data.mediaEndTimeMs;
        c.loadMs = info.loadDurationMs;
        c.bytes = info.bytesLoaded;
        long prevEnd = lastEnd[c.type];
        if (prevEnd != C.TIME_UNSET && c.start != C.TIME_UNSET && Math.abs(c.start - prevEnd) > 500) {
            c.gapMs = c.start - prevEnd;
        }
        lastEnd[c.type] = c.end;
        chunks.addLast(c);
        while (chunks.size() > MAX_CHUNKS) chunks.removeFirst();
        if (c.gapMs != C.TIME_UNSET) {
            add("дупка във времето на потока " + sec(c.gapMs) + " сек (" + typeName(c.type) + ")");
        }
    }

    @Override
    public synchronized void onLoadError(EventTime eventTime, LoadEventInfo info, MediaLoadData data,
                                         IOException error, boolean wasCanceled) {
        String what = data.dataType == C.DATA_TYPE_MANIFEST ? "плейлист"
                : data.dataType == C.DATA_TYPE_MEDIA ? "парче" : "данни";
        String host = hostOf(info);
        add("грешка при зареждане на " + what + (host.isEmpty() ? "" : " от " + host) + ": " + errorName(error)
                + (wasCanceled ? " (отменено)" : "") + ", " + info.loadDurationMs + " мс");
    }

    @Override
    public synchronized void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
        add("изпуснати кадри " + droppedFrames + " за " + elapsedMs + " мс");
    }

    @Override
    public synchronized void onVideoCodecError(EventTime eventTime, Exception e) {
        add("грешка на видео декодера: " + brief(e));
    }

    @Override
    public synchronized void onAudioCodecError(EventTime eventTime, Exception e) {
        add("грешка на звуковия декодер: " + brief(e));
    }

    @Override
    public synchronized void onVideoDecoderInitialized(EventTime eventTime, String decoderName,
                                                       long initializedTimestampMs, long initializationDurationMs) {
        add("видео декодер " + decoderName + " (" + initializationDurationMs + " мс)");
    }

    @Override
    public synchronized void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
        add("първи кадър");
    }

    @Override
    public synchronized void onPositionDiscontinuity(EventTime eventTime, Player.PositionInfo oldPosition,
                                                     Player.PositionInfo newPosition, int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            // След скок зареждането започва от друго парче: това не е дупка в потока.
            for (int i = 0; i < TYPES; i++) lastEnd[i] = C.TIME_UNSET;
        }
        add("скок на позицията (" + reasonName(reason) + ") " + sec(oldPosition.positionMs) + "→"
                + sec(newPosition.positionMs));
    }

    @Override
    public synchronized void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData data) {
        if (data.trackFormat != null && (data.trackType == C.TRACK_TYPE_VIDEO || data.trackType == C.TRACK_TYPE_DEFAULT)) {
            add("формат " + fmtVideo(data.trackFormat));
        }
    }

    // ---- снимка ----

    /**
     * Състоянието на плеъра в този момент, на няколко реда (разделени с ¦).
     * full = с формати и парчета; иначе само кратко: за втората снимка, към края на заседването.
     */
    synchronized String snapshot(ExoPlayer p, String when, boolean full) {
        long now = SystemClock.elapsedRealtime();
        StringBuilder s = new StringBuilder(when);
        try {
            s.append(" · плеър ").append(stateName(p.getPlaybackState()));
            s.append(p.isLoading() ? ", зарежда" : ", не зарежда");
            s.append(", буфер ").append(Math.max(0, p.getBufferedPosition() - p.getCurrentPosition()) / 1000).append(" сек");
            long live = p.getCurrentLiveOffset();
            if (live != C.TIME_UNSET) s.append(", от живия край ").append(sec(live)).append(" сек");
            if (bufAt != 0) s.append(", позицията е мръднала с ").append(sec(p.getCurrentPosition() - bufPos)).append(" сек");
        } catch (Throwable ignored) {
            // част от редовете може да липсва
        }
        if (full) {
            try {
                s.append("¦Картина: ").append(fmtVideo(p.getVideoFormat()));
                s.append(" · звук: ").append(fmtAudio(p.getAudioFormat()));
            } catch (Throwable ignored) {
                // без формати
            }
        }
        try {
            DecoderCounters v = p.getVideoDecoderCounters();
            DecoderCounters a = p.getAudioDecoderCounters();
            s.append("¦Декодери: картина ").append(counters(v, v != null && v == vRef, vq, vr, vd, true));
            s.append(" · звук ").append(counters(a, a != null && a == aRef, aq, ar, 0, false));
        } catch (Throwable ignored) {
            // без броячи
        }
        if (full) {
            s.append("¦Парчета: ");
            if (chunks.isEmpty()) {
                s.append("няма");
            } else {
                long base = C.TIME_UNSET;
                for (Chunk c : chunks) {
                    if (c.start != C.TIME_UNSET) {
                        base = c.start;
                        break;
                    }
                }
                int skip = Math.max(0, chunks.size() - 6);
                int i = 0;
                for (Chunk c : chunks) {
                    if (i++ < skip) continue;
                    s.append(typeName(c.type)).append(' ');
                    if (c.start != C.TIME_UNSET && c.end != C.TIME_UNSET && base != C.TIME_UNSET) {
                        s.append(sec(c.start - base)).append('–').append(sec(c.end - base));
                    } else {
                        s.append('?');
                    }
                    s.append(" (").append(c.loadMs).append(" мс, ").append(c.bytes / 1024).append(" КБ");
                    if (c.gapMs != C.TIME_UNSET) s.append(", дупка ").append(sec(c.gapMs)).append(" сек");
                    s.append(") · ");
                }
                Chunk last = chunks.peekLast();
                if (last != null) {
                    s.append("последното преди ").append((now - last.at) / 1000).append(" сек");
                    if (!last.host.isEmpty()) s.append(" от ").append(last.host);
                }
            }
        }
        s.append("¦Плейлист: ");
        if (manifestCount == 0) {
            s.append("не е зареждан");
        } else {
            s.append("последно зареждане преди ").append((now - manifestAt) / 1000).append(" сек (")
                    .append(manifestMs).append(" мс");
            if (!manifestHost.isEmpty()) s.append(", ").append(manifestHost);
            s.append("), общо ").append(manifestCount);
        }
        s.append("¦Събития: ");
        int want = full ? 6 : 4;
        int skip = Math.max(0, events.size() - want);
        int i = 0;
        boolean any = false;
        for (Ev e : events) {
            if (i++ < skip) continue;
            any = true;
            s.append('−').append((now - e.at) / 1000).append("с ").append(e.text).append(" · ");
        }
        if (!any) s.append("няма");
        if (s.length() > MAX_TEXT) {
            s.setLength(MAX_TEXT - 1);
            s.append('…');
        }
        return s.toString();
    }

    // ---- помощни ----

    private static String counters(DecoderCounters c, boolean same, int bq, int br, int bd, boolean video) {
        if (c == null) return "няма декодер";
        c.ensureUpdated();
        StringBuilder s = new StringBuilder();
        if (same) {
            s.append("влиза +").append(c.queuedInputBufferCount - bq).append(", излиза +")
                    .append(c.renderedOutputBufferCount - br);
            if (video) s.append(", изпуснати +").append(c.droppedBufferCount - bd);
        } else {
            s.append("влиза ").append(c.queuedInputBufferCount).append(", излиза ").append(c.renderedOutputBufferCount);
            if (video) s.append(", изпуснати ").append(c.droppedBufferCount);
            s.append(" (общо)");
        }
        return s.toString();
    }

    private static int typeIndex(int trackType) {
        return trackType == C.TRACK_TYPE_AUDIO ? 1 : trackType == C.TRACK_TYPE_VIDEO ? 2
                : trackType == C.TRACK_TYPE_TEXT ? 3 : 0;
    }

    private static String typeName(int index) {
        return index == 1 ? "звук" : index == 2 ? "видео" : index == 3 ? "текст" : "осн.";
    }

    private static String stateName(int state) {
        return state == Player.STATE_IDLE ? "IDLE" : state == Player.STATE_BUFFERING ? "BUFFERING"
                : state == Player.STATE_READY ? "READY" : state == Player.STATE_ENDED ? "ENDED" : "?" + state;
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case Player.DISCONTINUITY_REASON_AUTO_TRANSITION:
                return "AUTO";
            case Player.DISCONTINUITY_REASON_SEEK:
                return "SEEK";
            case Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
                return "SEEK_ADJ";
            case Player.DISCONTINUITY_REASON_SKIP:
                return "SKIP";
            case Player.DISCONTINUITY_REASON_REMOVE:
                return "REMOVE";
            case Player.DISCONTINUITY_REASON_INTERNAL:
                return "INTERNAL";
            default:
                return "?" + reason;
        }
    }

    private static String sec(long ms) {
        return String.format(Locale.US, "%.1f", ms / 1000.0);
    }

    /** Само името на сървъра: без път, ключове и бисквитки. */
    private static String hostOf(LoadEventInfo info) {
        try {
            String h = info == null || info.uri == null ? null : info.uri.getHost();
            return h == null ? "" : h;
        } catch (Throwable e) {
            return "";
        }
    }

    private static String brief(Throwable t) {
        return t == null ? "?" : t.getClass().getSimpleName();
    }

    /** HTTP код, ако го има, иначе името на грешката. Адреси и бисквитки не се записват. */
    private static String errorName(Throwable e) {
        Throwable t = e;
        for (int i = 0; t != null && i < 6; i++, t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return "HTTP " + ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
            }
        }
        return brief(e);
    }

    private static String codec(String mime, String codecs) {
        if (mime == null) return codecs == null ? "" : codecs;
        switch (mime) {
            case "video/avc":
                return "H.264";
            case "video/hevc":
                return "H.265";
            case "audio/mp4a-latm":
                return "AAC";
            case "audio/mpeg":
                return "MP3";
            case "audio/ac3":
                return "AC-3";
            case "audio/eac3":
                return "E-AC-3";
            default:
                int slash = mime.indexOf('/');
                return slash >= 0 ? mime.substring(slash + 1) : mime;
        }
    }

    private static String fmtVideo(Format f) {
        if (f == null) return "няма";
        StringBuilder s = new StringBuilder();
        if (f.width > 0 && f.height > 0) s.append(f.width).append('×').append(f.height);
        String c = codec(f.sampleMimeType, f.codecs);
        if (!c.isEmpty()) s.append(s.length() > 0 ? " " : "").append(c);
        int bitrate = f.averageBitrate > 0 ? f.averageBitrate : f.bitrate;
        if (bitrate > 0) s.append(' ').append(String.format(Locale.US, "%.1f", bitrate / 1e6)).append(" Мбит/с");
        if (f.frameRate > 0) s.append(' ').append(Math.round(f.frameRate)).append(" к/с");
        return s.length() == 0 ? "?" : s.toString();
    }

    private static String fmtAudio(Format f) {
        if (f == null) return "няма";
        StringBuilder s = new StringBuilder(codec(f.sampleMimeType, f.codecs));
        if (f.channelCount > 0) s.append(s.length() > 0 ? " " : "").append(f.channelCount).append(" кан.");
        if (f.sampleRate > 0) s.append(s.length() > 0 ? " " : "").append(f.sampleRate / 1000).append(" kHz");
        return s.length() == 0 ? "?" : s.toString();
    }
}
