package app.veil.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GearCodeTest {

    @Test
    public void knownCodes() {
        assertEquals("P", GearCode.letter(0x00200230));
        assertEquals("R", GearCode.letter(0x00200240));
        assertEquals("N", GearCode.letter(0x00200210));
        assertEquals("D", GearCode.letter(0x00200220));
    }

    @Test
    public void unknownCodesAreNull() {
        assertNull(GearCode.letter(0));
        assertNull(GearCode.letter(255));
        assertNull(GearCode.letter(0x00200200));
    }

    @Test
    public void speedIsMetresPerSecond() {
        assertEquals(1f, GearCode.kmh(0.27778f), 0.01f);
        assertEquals(36f, GearCode.kmh(10f), 0.001f);
        assertEquals(0f, GearCode.kmh(0), 0f);
    }

    @Test
    public void placeholdersAreUnknown() {
        assertNull(GearCode.kmh(null));
        assertNull(GearCode.kmh(-1f));
        assertNull(GearCode.kmh(255));
        assertNull(GearCode.kmh(Float.NaN));
    }
}
