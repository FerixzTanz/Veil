package app.veil.design;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

/** Veil must never be able to open a camera or a microphone: that is the dashcam app's. */
public class ManifestGuardTest {

    @Test
    public void noCameraOrMicrophoneAnywhereInTheManifest() throws IOException {
        String manifest = Sources.read(Sources.MAIN.resolve("AndroidManifest.xml"));
        assertFalse(manifest.contains("android.permission.CAMERA"));
        assertFalse(manifest.contains("android.permission.RECORD_AUDIO"));
        assertFalse(manifest.contains("FOREGROUND_SERVICE_CAMERA"));
        assertFalse(manifest.contains("FOREGROUND_SERVICE_MICROPHONE"));
        assertFalse(manifest.contains("foregroundServiceType=\"camera"));
        assertFalse(manifest.contains("foregroundServiceType=\"microphone"));
        assertFalse(manifest.contains("android.hardware.camera"));
    }

    @Test
    public void noSourceTouchesTheCameraApis() throws IOException {
        for (java.nio.file.Path source : Sources.filesUnder(Sources.MAIN.resolve("java"), ".java")) {
            String text = Sources.read(source);
            assertFalse(source + " imports a camera API",
                    text.contains("android.hardware.camera2") || text.contains("android.hardware.Camera")
                            || text.contains("androidx.camera"));
            assertFalse(source + " records audio", text.contains("android.media.AudioRecord"));
        }
        assertTrue(true);
    }
}
