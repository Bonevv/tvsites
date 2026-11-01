package com.example.tvsites;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class TvPlaybackPolicyTest {
    @Test public void sourceChoosesItsOwnLiveDelay() {
        MediaItem.LiveConfiguration config = TvPlaybackPolicy.liveConfiguration();
        assertEquals(C.TIME_UNSET, config.targetOffsetMs);
        assertEquals(C.TIME_UNSET, config.minOffsetMs);
        assertEquals(C.TIME_UNSET, config.maxOffsetMs);
    }

    @Test public void shortWindowDoesNotRewindToOldestSegment() {
        // The reported failure: SEEK 0.8 -> 0 while already buffering.
        assertFalse(TvPlaybackPolicy.shouldSeekForward(800, 0));
        assertFalse(TvPlaybackPolicy.shouldSeekForward(6000, 0));
    }

    @Test public void bufferingNearDefaultPositionKeepsDownloadedData() {
        assertFalse(TvPlaybackPolicy.shouldSeekForward(12000, 12000));
        assertFalse(TvPlaybackPolicy.shouldSeekForward(11500, 12000));
    }

    @Test public void playbackBehindMovingWindowCanCatchUp() {
        assertTrue(TvPlaybackPolicy.shouldSeekForward(2000, 14000));
        assertTrue(TvPlaybackPolicy.shouldSeekForward(0, 6000));
    }

    @Test public void unknownTimelineDoesNotTriggerSeek() {
        assertFalse(TvPlaybackPolicy.shouldSeekForward(0, C.TIME_UNSET));
        assertFalse(TvPlaybackPolicy.shouldSeekForward(C.TIME_UNSET, 10000));
    }
}
