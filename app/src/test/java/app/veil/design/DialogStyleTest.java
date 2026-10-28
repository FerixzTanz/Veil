package app.veil.design;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The head unit hides the framework dialog's button bar, so every dialog goes through
 * VeilDialogs, which draws its buttons itself.
 */
public class DialogStyleTest {

    @Test
    public void onlyVeilDialogsBuildsDialogs() throws IOException {
        for (Path source : Sources.filesUnder(Sources.MAIN.resolve("java"), ".java")) {
            String text = Sources.read(source);
            assertFalse(source + " uses the framework AlertDialog", text.contains("android.app.AlertDialog"));
            if (source.endsWith("VeilDialogs.java")) {
                continue;
            }
            assertFalse(source + " builds a dialog outside VeilDialogs",
                    text.contains("AlertDialog.Builder") || text.contains("MaterialAlertDialogBuilder"));
        }
    }
}
