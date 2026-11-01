package com.example.tvsites;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;

/** Live playback must follow the source's window, rather than a fixed delay. */
final class TvPlaybackPolicy {
    private TvPlaybackPolicy() {}

    static MediaItem.LiveConfiguration liveConfiguration() {
        // HLS uses EXT-X-START / HOLD-BACK, or three target-duration segments.
        // Forcing 18 seconds can put playback at the oldest edge of a short playlist.
        return new MediaItem.LiveConfiguration.Builder().build();
    }

    static boolean shouldSeekForward(long positionMs, long defaultPositionMs) {
        // The default position is a safe distance BEHIND the live edge. It can be
        // behind our current position too, so seeking there is not always recovery.
        return positionMs != C.TIME_UNSET && defaultPositionMs != C.TIME_UNSET
                && positionMs >= 0 && defaultPositionMs - positionMs > 1000;
    }
}
