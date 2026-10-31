package app.veil.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ShareAddressesTest {

    private static ShareAddresses.Endpoint e(String name, String address, int prefix) {
        return new ShareAddresses.Endpoint(name, address, prefix);
    }

    /** The links a 7X head unit has (from the car's Network page). */
    private static final List<ShareAddresses.Endpoint> CAR = Arrays.asList(
            e("vlan11", "198.18.34.15", 22),
            e("veth0", "192.168.55.15", 24),
            e("eth0", "10.0.0.5", 24),
            e("wlan0", "172.20.10.4", 28),
            e("ap0", "192.168.43.1", 24));

    @Test
    public void theCarsInternalLinksAreNeverOffered() {
        for (ShareAddresses.Endpoint x : ShareAddresses.usable(CAR, null)) {
            assertFalse(x.toString(), x.interfaceName.startsWith("vlan") || x.interfaceName.startsWith("veth"));
        }
    }

    @Test
    public void theIphonesNetworkComesFirst() {
        List<ShareAddresses.Endpoint> out = ShareAddresses.usable(CAR, "192.168.43.7");
        assertEquals("ap0", out.get(0).interfaceName);
        out = ShareAddresses.usable(CAR, "172.20.10.2");
        assertEquals("wlan0", out.get(0).interfaceName);
    }

    @Test
    public void withoutAnIphoneWifiAndHotspotComeBeforeTheRest() {
        List<ShareAddresses.Endpoint> out = ShareAddresses.usable(CAR, null);
        assertEquals(3, out.size());
        assertEquals("ap0", out.get(0).interfaceName);
        assertEquals("wlan0", out.get(1).interfaceName);
        assertEquals("eth0", out.get(2).interfaceName);
    }

    @Test
    public void nothingUsableIsEmpty() {
        assertTrue(ShareAddresses.usable(Arrays.asList(e("vlan11", "198.18.34.15", 22)), null).isEmpty());
    }

    @Test
    public void addressesAndNetworks() {
        assertEquals(Integer.valueOf(0xC0A82B01), ShareAddresses.toInt("192.168.43.1"));
        assertNull(ShareAddresses.toInt("::1"));
        assertNull(ShareAddresses.toInt("300.1.1.1"));
        assertNull(ShareAddresses.toInt(null));
        assertTrue(ShareAddresses.sameNetwork(0xAC140A04, 0xAC140A02, 28));
        assertFalse(ShareAddresses.sameNetwork(0xAC140A04, 0xAC140A12, 28));
    }
}
