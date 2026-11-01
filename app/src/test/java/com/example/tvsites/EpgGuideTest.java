package com.example.tvsites;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class EpgGuideTest {
    private EpgGuide guide() throws Exception {
        String xml = "<tv><channel id='nova'><display-name>Nova HD</display-name></channel>"
            + "<programme channel='nova' start='20261101120000 +0000' stop='20261101123000 +0000'><title>Обедни новини</title></programme>"
            + "<programme channel='nova' start='20261101124000 +0000' stop='20261101130000 +0000'><title>След новините</title></programme></tv>";
        return EpgGuide.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                Collections.singletonList("Nova"), EpgGuide.time("20261101110000 +0000"));
    }
    @Test public void currentEventMatchesAliasesAndQualitySuffixes() throws Exception {
        EpgGuide guide = guide(); long now = EpgGuide.time("20261101121000 +0000");
        assertEquals("Обедни новини", guide.current("Нова HD", now).title);
        assertSame(guide.current("Nova", now), guide.current("Nova TV", now));
    }
    @Test public void doesNotTreatFutureProgrammeAsCurrentDuringGap() throws Exception {
        EpgGuide guide = guide(); long now = EpgGuide.time("20261101123500 +0000");
        assertNull(guide.current("Nova", now));
        assertEquals("След новините", guide.forChannel("Nova", now).get(0).title);
    }
    @Test public void endBoundaryAndUnknownChannelsHaveNoCurrentEvent() throws Exception {
        EpgGuide guide = guide();
        assertNull(guide.current("Nova", EpgGuide.time("20261101123000 +0000")));
        assertNull(guide.current("Unknown", EpgGuide.time("20261101121000 +0000")));
        assertTrue(guide.forChannel("Nova", EpgGuide.time("20261101130000 +0000")).isEmpty());
    }
}
