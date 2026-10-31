package app.veil.airplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MirrorDecoderTest {

    @Test
    public void idrAndSpsAreKeyframes() {
        assertTrue(MirrorDecoder.isKeyframe(new byte[] {0, 0, 0, 1, 0x65, 1, 2}));
        assertTrue(MirrorDecoder.isKeyframe(new byte[] {9, 0, 0, 0, 1, 0x67, 1}));
    }

    @Test
    public void otherSlicesAreNot() {
        assertFalse(MirrorDecoder.isKeyframe(new byte[] {0, 0, 0, 1, 0x41, 1, 2}));
        assertFalse(MirrorDecoder.isKeyframe(new byte[] {0, 0, 1}));
    }

    @Test
    public void framesAreSummedUpEveryTenSeconds() {
        MirrorDecoder.FrameStats stats = new MirrorDecoder.FrameStats();
        for (int i = 0; i < 300; i++) {
            long t = i * 33L + (i == 150 ? 400 : 0);
            stats.arrived(t, i == 10 ? 180 : 1, i != 10);
            stats.shown(t + 5);
            if (i < 299) {
                org.junit.Assert.assertNull(stats.report(t));
            }
        }
        String line = stats.report(10_000L);
        org.junit.Assert.assertEquals("mirror 10.0 s: 300 frames in (30.0/s, longest gap 433 ms),"
                + " 300 shown (longest gap 433 ms), 1 dropped, decoder wait up to 180 ms", line);
        org.junit.Assert.assertNull(stats.report(10_001L));
    }

    @Test
    public void whatIsLeftIsLoggedWhenTheDecoderStops() {
        MirrorDecoder.FrameStats stats = new MirrorDecoder.FrameStats();
        org.junit.Assert.assertNull(stats.flush());
        stats.arrived(0, 1, true);
        stats.arrived(2000, 1, true);
        org.junit.Assert.assertEquals("mirror 2.0 s: 2 frames in (1.0/s, longest gap 2000 ms),"
                + " 0 shown (longest gap 0 ms), 0 dropped, decoder wait up to 1 ms", stats.flush());
        org.junit.Assert.assertNull(stats.flush());
    }
}
