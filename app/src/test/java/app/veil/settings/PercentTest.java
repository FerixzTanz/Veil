package app.veil.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PercentTest {

    @Test
    public void clampsToZeroToHundred() {
        assertEquals(0, Percent.clamp(-1));
        assertEquals(0, Percent.clamp(Integer.MIN_VALUE));
        assertEquals(0, Percent.clamp(0));
        assertEquals(42, Percent.clamp(42));
        assertEquals(100, Percent.clamp(100));
        assertEquals(100, Percent.clamp(101));
        assertEquals(100, Percent.clamp(Integer.MAX_VALUE));
    }
}
