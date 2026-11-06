package app.veil.cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The cluster is found by its 2560x536 size, never the centre screen or the HUD; the gear codes map to letters. */
public class ClusterDisplayTest {

    private static ClusterDisplay.Candidate screen(int id, int w, int h) {
        return new ClusterDisplay.Candidate(id, w, h);
    }

    @Test
    public void findsTheClusterBySize() {
        assertEquals(2, ClusterDisplay.find(Arrays.asList(
                screen(0, 3200, 2000), screen(2, 2560, 536), screen(3, 1440, 480))));
    }

    @Test
    public void neverPicksTheCentreScreenOrTheHud() {
        assertEquals(ClusterDisplay.NONE, ClusterDisplay.find(Arrays.asList(
                screen(0, 2560, 536), screen(3, 1440, 480))));
        assertEquals(ClusterDisplay.NONE, ClusterDisplay.find(Collections.emptyList()));
    }

    @Test
    public void gearEventsMapToLetters() {
        assertEquals("P", GearReader.letter(0x00200230));
        assertEquals("R", GearReader.letter(0x00200240));
        assertEquals("N", GearReader.letter(0x00200210));
        assertEquals("D", GearReader.letter(0x00200220));
        assertNull(GearReader.letter(255));
        assertNull(GearReader.letter(0));
    }
}
