package app.veil.airplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VisibilityTest {

    private static Visibility.Facts good() {
        Visibility.Facts f = new Visibility.Facts();
        f.receiverRunning = true;
        f.onWifiOrHotspot = true;
        f.onIphoneHotspot = true;
        f.ownMdns = true;
        f.multicast = true;
        f.canDrawOverlay = true;
        return f;
    }

    @Test
    public void nothingToSayWhenAllIsWell() {
        assertTrue(Visibility.reasons(good()).isEmpty());
    }

    @Test
    public void offReceiverIsTheOnlyReason() {
        Visibility.Facts f = good();
        f.receiverRunning = false;
        f.onWifiOrHotspot = false;
        assertEquals(Collections.singletonList(Visibility.Reason.RECEIVER_OFF), Visibility.reasons(f));
    }

    @Test
    public void noWifiStopsEverythingElse() {
        Visibility.Facts f = good();
        f.onWifiOrHotspot = false;
        f.ownMdns = false;
        assertEquals(Collections.singletonList(Visibility.Reason.NO_WIFI), Visibility.reasons(f));
    }

    @Test
    public void lesserReasonsInOrder() {
        Visibility.Facts f = good();
        f.multicast = false;
        f.ownMdns = false;
        f.canDrawOverlay = false;
        f.onIphoneHotspot = false;
        List<Visibility.Reason> r = Visibility.reasons(f);
        assertEquals(Arrays.asList(Visibility.Reason.NO_MULTICAST, Visibility.Reason.SYSTEM_MDNS,
                Visibility.Reason.NO_OVERLAY, Visibility.Reason.NOT_IPHONE_HOTSPOT_HINT), r);
    }

    @Test
    public void wifiInterfacesByName() {
        assertTrue(Visibility.anyWifi(Arrays.asList("eth0  10.0.0.2:7000", "wlan2  192.168.5.79:7000")));
        assertTrue(Visibility.anyWifi(Collections.singletonList("ap0  192.168.43.1:7000")));
        assertFalse(Visibility.anyWifi(Arrays.asList("eth0  10.0.0.2:7000", "vlan11  198.18.34.15:7000")));
        assertFalse(Visibility.anyWifi(Collections.emptyList()));
    }
}
