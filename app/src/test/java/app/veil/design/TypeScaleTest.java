package app.veil.design;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

/** Layouts pick one of the six text appearances; they never write a size of their own. */
public class TypeScaleTest {

    @Test
    public void noLiteralTextSizeInLayouts() throws IOException {
        for (Path layout : Sources.filesUnder(Sources.RES.resolve("layout"), ".xml")) {
            String text = Sources.read(layout);
            assertFalse(layout + " sets android:textSize", text.contains("android:textSize"));
            assertFalse(layout + " has a literal sp value", text.matches("(?s).*\"\\d+(\\.\\d+)?sp\".*"));
        }
    }
}
