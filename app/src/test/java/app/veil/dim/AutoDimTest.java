package app.veil.dim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoDimTest {

    @Test
    public void windowWrapsMidnight() {
        assertTrue(AutoDim.inWindow(19, 19, 7));
        assertTrue(AutoDim.inWindow(23, 19, 7));
        assertTrue(AutoDim.inWindow(0, 19, 7));
        assertTrue(AutoDim.inWindow(6, 19, 7));
        assertFalse(AutoDim.inWindow(7, 19, 7));
        assertFalse(AutoDim.inWindow(12, 19, 7));
    }

    @Test
    public void windowWithinOneDay() {
        assertTrue(AutoDim.inWindow(9, 8, 17));
        assertFalse(AutoDim.inWindow(17, 8, 17));
        assertFalse(AutoDim.inWindow(3, 8, 17));
    }

    @Test
    public void emptyWindowIsNever() {
        assertFalse(AutoDim.inWindow(5, 5, 5));
        assertFalse(AutoDim.inWindow(0, 0, 0));
    }

    @Test
    public void hoursAreClamped() {
        assertEquals(0, AutoDim.clampHour(-4));
        assertEquals(23, AutoDim.clampHour(30));
        assertTrue(AutoDim.inWindow(23, 30, -1) == AutoDim.inWindow(23, 23, 0));
    }

    @Test
    public void wantedPerMode() {
        assertNull(AutoDim.wanted(AutoDim.Mode.OFF, 22, 19, 7, true));
        assertEquals(Boolean.TRUE, AutoDim.wanted(AutoDim.Mode.CLOCK, 22, 19, 7, false));
        assertEquals(Boolean.FALSE, AutoDim.wanted(AutoDim.Mode.CLOCK, 12, 19, 7, true));
        assertEquals(Boolean.TRUE, AutoDim.wanted(AutoDim.Mode.NIGHT_MODE, 12, 19, 7, true));
        assertEquals(Boolean.FALSE, AutoDim.wanted(AutoDim.Mode.NIGHT_MODE, 22, 19, 7, false));
    }

    @Test
    public void modeKeysRoundTripAndGarbageIsOff() {
        for (AutoDim.Mode m : AutoDim.Mode.values()) {
            assertEquals(m, AutoDim.Mode.fromKey(m.key));
        }
        assertEquals(AutoDim.Mode.OFF, AutoDim.Mode.fromKey("sometimes"));
        assertEquals(AutoDim.Mode.OFF, AutoDim.Mode.fromKey(null == null ? "" : ""));
    }

    @Test
    public void followerReportsOnlyEdges() {
        AutoDim.Follower f = new AutoDim.Follower();
        assertEquals(Boolean.TRUE, f.update(true));
        assertNull(f.update(true));
        assertNull(f.update(true));
        assertEquals(Boolean.FALSE, f.update(false));
        assertNull(f.update(false));
        assertNull(f.update(null));
        // After the rule was off, the next answer is an edge again.
        assertEquals(Boolean.FALSE, f.update(false));
    }

    @Test
    public void manualToggleHoldsUntilNextEdge() {
        AutoDim.Follower f = new AutoDim.Follower();
        assertEquals(Boolean.TRUE, f.update(true));   // night falls: veil on
        // User taps the moon: veil off. The rule still wants true, but says nothing...
        assertNull(f.update(true));
        // ...until morning, when it turns the veil off (already off), and the next night turns it on again.
        assertEquals(Boolean.FALSE, f.update(false));
        assertEquals(Boolean.TRUE, f.update(true));
    }
}
