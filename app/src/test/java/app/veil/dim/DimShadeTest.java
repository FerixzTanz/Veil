package app.veil.dim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DimShadeTest {

    @Test
    public void blackVeilAtDefaultSliders() {
        int c = DimShade.argb(90, 0, 0);
        assertEquals(229, (c >>> 24));
        assertEquals(0, c & 0x00FFFFFF);
    }

    @Test
    public void brightnessLiftsToGrey() {
        int c = DimShade.argb(50, 100, 0);
        assertEquals(0xFFFFFF, c & 0x00FFFFFF);
        assertEquals(127, (c >>> 24));
    }

    @Test
    public void fullWarmthIsAmber() {
        int c = DimShade.argb(100, 0, 100);
        assertEquals(0xFFFFB040, c);
    }

    @Test
    public void inputsAreClamped() {
        assertEquals(DimShade.argb(100, 100, 100), DimShade.argb(900, 300, 1000));
        assertEquals(DimShade.argb(0, 0, 0), DimShade.argb(-1, -50, -3));
    }

    @Test
    public void passThroughStopsAtBlackout() {
        assertTrue(DimShade.passTouches(true, 0));
        assertTrue(DimShade.passTouches(true, 99));
        assertFalse(DimShade.passTouches(true, 100));
        assertFalse(DimShade.passTouches(false, 50));
    }

    @Test
    public void windowAlphaIsCappedForPassThrough() {
        assertEquals(0.5f, DimShade.windowAlpha(true, 50), 0.0001f);
        assertEquals(0.8f, DimShade.windowAlpha(true, 90), 0.0001f);
        assertEquals(1f, DimShade.windowAlpha(true, 100), 0.0001f);
        assertEquals(1f, DimShade.windowAlpha(false, 30), 0.0001f);
    }

    @Test
    public void opaqueTintKeepsColourDropsAlpha() {
        assertEquals(0xFF123456, DimShade.opaqueTint(0x10123456));
    }
}
