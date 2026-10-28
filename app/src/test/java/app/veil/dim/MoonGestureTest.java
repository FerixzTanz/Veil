package app.veil.dim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoonGestureTest {

    private static final int SLOP = 10;
    private static final int STEP = 6;
    private static final long HOLD = 500;

    private MoonGesture gesture() {
        return new MoonGesture(SLOP, STEP, HOLD);
    }

    @Test
    public void shortStillTouchIsATap() {
        MoonGesture g = gesture();
        g.down(100, 100, 0, 50, 10, 20);
        assertEquals(MoonGesture.Kind.NONE, g.move(104, 97, 50).kind);
        MoonGesture.Result up = g.up(120);
        assertEquals(MoonGesture.Kind.TAP, up.kind);
    }

    @Test
    public void dragRightOrDownDarkens() {
        MoonGesture g = gesture();
        g.down(100, 100, 0, 50, 0, 0);
        MoonGesture.Result r = g.move(100 + 6 * 10, 100, 100);
        assertEquals(MoonGesture.Kind.ADJUST, r.kind);
        assertEquals(60, r.level);
        r = g.move(100 + 6 * 10, 100 + 6 * 5, 150);
        assertEquals(65, r.level);
        MoonGesture.Result up = g.up(200);
        assertEquals(MoonGesture.Kind.ADJUST, up.kind);
        assertEquals(65, up.level);
    }

    @Test
    public void dragLeftOrUpLightensButNeverBelowMinimum() {
        MoonGesture g = gesture();
        g.down(500, 500, 0, 20, 0, 0);
        MoonGesture.Result r = g.move(500 - 6 * 5, 500, 100);
        assertEquals(15, r.level);
        r = g.move(500 - 6 * 50, 500 - 6 * 50, 150);
        assertEquals(MoonGesture.LEVEL_MIN, r.level);
    }

    @Test
    public void sameLevelIsReportedOnce() {
        MoonGesture g = gesture();
        g.down(0, 0, 0, 50, 0, 0);
        assertEquals(MoonGesture.Kind.ADJUST, g.move(12, 0, 10).kind);
        assertEquals(MoonGesture.Kind.NONE, g.move(13, 0, 20).kind);
        assertEquals(MoonGesture.Kind.ADJUST, g.move(18, 0, 30).kind);
    }

    @Test
    public void holdThenDragMovesTheButton() {
        MoonGesture g = gesture();
        g.down(100, 100, 0, 50, 300, 400);
        assertFalse(g.arm(200));
        assertTrue(g.arm(500));
        assertTrue(g.isMoving());
        MoonGesture.Result r = g.move(140, 90, 600);
        assertEquals(MoonGesture.Kind.MOVE, r.kind);
        assertEquals(340, r.x);
        assertEquals(390, r.y);
        assertEquals(MoonGesture.Kind.MOVE, g.up(700).kind);
    }

    @Test
    public void holdWithoutTimerStillMovesWhenDragStartsLate() {
        MoonGesture g = gesture();
        g.down(100, 100, 0, 50, 0, 0);
        MoonGesture.Result r = g.move(150, 100, 800);
        assertEquals(MoonGesture.Kind.MOVE, r.kind);
        assertEquals(50, r.x);
    }

    @Test
    public void armDoesNothingOnceADragIsAdjusting() {
        MoonGesture g = gesture();
        g.down(0, 0, 0, 50, 0, 0);
        assertEquals(MoonGesture.Kind.ADJUST, g.move(30, 0, 100).kind);
        assertFalse(g.arm(600));
        assertEquals(MoonGesture.Kind.ADJUST, g.move(60, 0, 700).kind);
    }

    @Test
    public void cancelDropsTheTouch() {
        MoonGesture g = gesture();
        g.down(0, 0, 0, 50, 0, 0);
        g.cancel();
        assertEquals(MoonGesture.Kind.NONE, g.move(100, 100, 10).kind);
        assertEquals(MoonGesture.Kind.NONE, g.up(20).kind);
    }

    @Test
    public void positionStaysOnScreen() {
        assertEquals(0, MoonGesture.clampPosition(-5, 100, 3200));
        assertEquals(3100, MoonGesture.clampPosition(5000, 100, 3200));
        assertEquals(42, MoonGesture.clampPosition(42, 100, 3200));
        assertEquals(0, MoonGesture.clampPosition(42, 100, 80));
    }

    @Test
    public void levelClamp() {
        assertEquals(10, MoonGesture.clampLevel(-3));
        assertEquals(100, MoonGesture.clampLevel(140));
        assertEquals(55, MoonGesture.clampLevel(55));
    }
}
