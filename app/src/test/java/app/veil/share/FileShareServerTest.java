package app.veil.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FileShareServerTest {

    private static List<Boolean> read(byte[] data, int upTo) throws IOException {
        List<Boolean> ends = new ArrayList<>();
        InputStream in = new FileShareServer.Watched(new ByteArrayInputStream(data), data.length, ends::add);
        byte[] buf = new byte[4];
        int total = 0;
        while (total < upTo) {
            int n = in.read(buf, 0, Math.min(buf.length, upTo - total));
            if (n < 0) {
                break;
            }
            total += n;
        }
        in.close();
        in.close();
        return ends;
    }

    @Test
    public void aWholeDownloadIsDoneOnce() throws IOException {
        List<Boolean> ends = read(new byte[10], 10);
        assertEquals(1, ends.size());
        assertTrue(ends.get(0));
    }

    @Test
    public void aCutDownloadIsStopped() throws IOException {
        List<Boolean> ends = read(new byte[10], 6);
        assertEquals(1, ends.size());
        assertFalse(ends.get(0));
    }

    @Test
    public void theExportDownloadsAsJson() {
        assertEquals("application/json; charset=utf-8", FileShareServer.mimeTypeOf("veil_diagnostics_1.json"));
        assertEquals("application/octet-stream", FileShareServer.mimeTypeOf("x.bin"));
    }

    @Test
    public void namesAreEscapedInThePage() {
        assertEquals("a&lt;b&gt;&amp;&quot;", FileShareServer.escape("a<b>&\""));
        assertEquals("1 KB", FileShareServer.readableSize(10));
        assertEquals("2.0 MB", FileShareServer.readableSize(2L * 1024 * 1024));
    }
}
