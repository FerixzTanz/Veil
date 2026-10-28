package app.veil.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Day and night palettes must define the same names, in the same order. A missing night value is a day colour at night. */
public class ColorParityTest {

    @Test
    public void dayAndNightDefineTheSameColours() throws IOException {
        List<String> day = Sources.colorNames(Sources.RES.resolve("values/colors.xml"));
        List<String> night = Sources.colorNames(Sources.RES.resolve("values-night/colors.xml"));
        assertFalse(day.isEmpty());
        assertEquals(day, night);
    }

    @Test
    public void layoutsUseOnlyNamedColours() throws IOException {
        for (Path layout : Sources.filesUnder(Sources.RES.resolve("layout"), ".xml")) {
            String text = Sources.read(layout);
            assertFalse(layout + " has a literal colour", text.matches("(?s).*\"#[0-9A-Fa-f]{3,8}\".*"));
        }
    }

    @Test
    public void energyColourNeverWritesText() throws IOException {
        for (Path layout : Sources.filesUnder(Sources.RES.resolve("layout"), ".xml")) {
            String text = Sources.read(layout);
            assertFalse(layout + " uses @color/energy as a text colour",
                    text.contains("android:textColor=\"@color/energy\""));
        }
        assertTrue(true);
    }
}
