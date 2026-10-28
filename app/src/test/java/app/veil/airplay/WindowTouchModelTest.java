package app.veil.airplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WindowTouchModelTest {

    private static final int SCREEN = 3200;
    private static final int PEEK = 72;

    @Test
    public void dockedIsReadFromThePosition() {
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.dockedAt(0, 800, SCREEN));
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.dockedAt(2400, 800, SCREEN));
        assertEquals(WindowTouchModel.Dock.LEFT, WindowTouchModel.dockedAt(-1, 800, SCREEN));
        assertEquals(WindowTouchModel.Dock.RIGHT, WindowTouchModel.dockedAt(2401, 800, SCREEN));
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.dockedAt(-1, 0, SCREEN));
    }

    @Test
    public void fullyOnScreenNeverDocksHoweverFastTheFling() {
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.deliberateDock(5, 800, SCREEN, -9000, 1000));
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.deliberateDock(2395, 800, SCREEN, 9000, 1000));
    }

    @Test
    public void pushedHalfOffDocks() {
        assertEquals(WindowTouchModel.Dock.LEFT, WindowTouchModel.deliberateDock(-400, 800, SCREEN, 0, 1000));
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.deliberateDock(-399, 800, SCREEN, 0, 1000));
        assertEquals(WindowTouchModel.Dock.RIGHT, WindowTouchModel.deliberateDock(2800, 800, SCREEN, 0, 1000));
    }

    @Test
    public void flungAtATouchedSideDocks() {
        assertEquals(WindowTouchModel.Dock.LEFT, WindowTouchModel.deliberateDock(-10, 800, SCREEN, -1200, 1000));
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.deliberateDock(-10, 800, SCREEN, -900, 1000));
        assertEquals(WindowTouchModel.Dock.RIGHT, WindowTouchModel.deliberateDock(2410, 800, SCREEN, 1200, 1000));
        assertEquals(WindowTouchModel.Dock.NONE, WindowTouchModel.deliberateDock(-10, 800, SCREEN, 1200, 1000));
    }

    @Test
    public void dockedPositionLeavesAStrip() {
        assertEquals(PEEK - 800, WindowTouchModel.dockedX(WindowTouchModel.Dock.LEFT, 0, 800, SCREEN, PEEK));
        assertEquals(SCREEN - PEEK, WindowTouchModel.dockedX(WindowTouchModel.Dock.RIGHT, 0, 800, SCREEN, PEEK));
        assertEquals(123, WindowTouchModel.dockedX(WindowTouchModel.Dock.NONE, 123, 800, SCREEN, PEEK));
    }

    @Test
    public void undockIsEasierThanDock() {
        assertTrue(WindowTouchModel.shouldUndock(WindowTouchModel.Dock.LEFT, 60, 0, 60, 1000));
        assertFalse(WindowTouchModel.shouldUndock(WindowTouchModel.Dock.LEFT, 59, 0, 60, 1000));
        assertFalse(WindowTouchModel.shouldUndock(WindowTouchModel.Dock.LEFT, -100, 0, 60, 1000));
        assertTrue(WindowTouchModel.shouldUndock(WindowTouchModel.Dock.RIGHT, -60, 0, 60, 1000));
        assertTrue(WindowTouchModel.shouldUndock(WindowTouchModel.Dock.RIGHT, -5, -1500, 60, 1000));
        assertFalse(WindowTouchModel.shouldUndock(WindowTouchModel.Dock.NONE, 500, 5000, 60, 1000));
    }

    @Test
    public void flushPutsTheWindowAgainstItsSide() {
        assertEquals(0, WindowTouchModel.flushX(WindowTouchModel.Dock.LEFT, 800, SCREEN));
        assertEquals(SCREEN - 800, WindowTouchModel.flushX(WindowTouchModel.Dock.RIGHT, 800, SCREEN));
    }

    @Test
    public void clampKeepsAStripOnScreen() {
        assertEquals(PEEK - 800, WindowTouchModel.clampX(-5000, 800, SCREEN, PEEK));
        assertEquals(SCREEN - PEEK, WindowTouchModel.clampX(5000, 800, SCREEN, PEEK));
        assertEquals(100, WindowTouchModel.clampX(100, 800, SCREEN, PEEK));
        // wider than the screen: only panning between the two ends
        assertEquals(0, WindowTouchModel.clampX(50, 4000, SCREEN, PEEK));
        assertEquals(-800, WindowTouchModel.clampX(-2000, 4000, SCREEN, PEEK));
    }

    @Test
    public void clampYNeverLeavesTheScreen() {
        assertEquals(0, WindowTouchModel.clampY(-10, 500, 2000));
        assertEquals(1500, WindowTouchModel.clampY(5000, 500, 2000));
        assertEquals(0, WindowTouchModel.clampY(100, 3000, 2000));
    }

    @Test
    public void pinchKeepsTheAnchorUnderTheFingersAndTheShape() {
        // window 800x450 at (100,100); fingers centred on its middle; factor 2
        WindowTouchModel.PinchResult r = WindowTouchModel.pinch(100, 100, 800, 450, 0.5f, 0.5f,
                500, 325, 2f, 120, SCREEN, 2000);
        assertEquals(1600, r.width);
        assertEquals(900, r.height);
        assertEquals(500 - 800, r.x);
        assertEquals(325 - 450, r.y);
        assertEquals(16f / 9f, (float) r.width / r.height, 0.01f);
    }

    @Test
    public void pinchIsClampedAsAWhole() {
        WindowTouchModel.PinchResult small = WindowTouchModel.pinch(0, 0, 800, 450, 0f, 0f, 0, 0, 0.01f,
                120, SCREEN, 2000);
        assertEquals(120, small.height);
        assertEquals(Math.round(800 * (120f / 450f)), small.width);
        WindowTouchModel.PinchResult big = WindowTouchModel.pinch(0, 0, 800, 450, 0f, 0f, 0, 0, 100f,
                120, SCREEN, 2000);
        assertTrue(big.width <= SCREEN);
        assertTrue(big.height <= 2000);
        assertEquals(16f / 9f, (float) big.width / big.height, 0.01f);
    }

    @Test
    public void glideFollowsTheFling() {
        assertEquals(120, WindowTouchModel.glideDurationMs(0, 0, 120, 380));
        assertEquals(380, WindowTouchModel.glideDurationMs(2000, 0, 120, 380));
        assertEquals(200, WindowTouchModel.glideDurationMs(1000, 5000, 120, 380));
        assertEquals(120, WindowTouchModel.glideDurationMs(100, 5000, 120, 380));
    }
}
