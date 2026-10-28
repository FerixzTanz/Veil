package app.veil.airplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WifiRouteTest {

    @Test
    public void theReceiversOwnPlaylistProxyGoesDirect() {
        assertTrue(WifiRoute.isLocal("localhost"));
        assertTrue(WifiRoute.isLocal("127.0.0.1"));
        assertTrue(WifiRoute.isLocal("::1"));
        assertTrue(WifiRoute.isLocal(null));
    }

    @Test
    public void everythingElseMayTakeTheWifi() {
        assertFalse(WifiRoute.isLocal("manifest.googlevideo.com"));
        assertFalse(WifiRoute.isLocal("192.168.5.1"));
    }
}
