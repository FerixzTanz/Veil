package app.veil.airplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DrivingGateTest {

    @Test
    public void parkedShowsThePicture() {
        assertFalse(DrivingGate.blocked("P", 0f));
    }

    @Test
    public void inGearHidesItEvenWhenStopped() {
        assertTrue(DrivingGate.blocked("D", 0f));
        assertTrue(DrivingGate.blocked("R", 0f));
        assertTrue(DrivingGate.blocked("N", 0f));
    }

    @Test
    public void rollingHidesItWhateverTheGearSays() {
        assertTrue(DrivingGate.blocked("P", 12f));
        assertTrue(DrivingGate.blocked(null, 12f));
        assertTrue(DrivingGate.blocked("P", 3.1f));
    }

    @Test
    public void creepingUnderThreeKmhIsNotRolling() {
        assertFalse(DrivingGate.blocked("P", 3f));
        assertFalse(DrivingGate.blocked("P", 2.9f));
    }

    @Test
    public void unknownReadingsCountAsParked() {
        assertFalse(DrivingGate.blocked(null, null));
        assertFalse(DrivingGate.blocked(null, 2f));
        assertFalse(DrivingGate.blocked("?", null));
    }
}
