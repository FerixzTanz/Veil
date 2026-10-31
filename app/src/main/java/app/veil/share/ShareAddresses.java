package app.veil.share;

import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.veil.airplay.MdnsResponder;
import app.veil.airplay.Visibility;

/**
 * Which of the car's addresses the phone can open the QR code's link on.
 *
 * <p>The iPhone that AirPlays to the car already reaches it, so the address on the iPhone's
 * network comes first; then the Wi-Fi and hotspot addresses, then anything else the AirPlay
 * receiver also answers on. The car's internal links (vlan, veth, ...) are never offered: no
 * phone can reach them. Adapted from CAMCORDER's LocalNetwork (the author's app, GPL-3.0); the
 * ordering is a pure function, tested on its own.</p>
 */
public final class ShareAddresses {

    /** One IPv4 address of the car, with its interface and network size. */
    public static final class Endpoint {
        public final String interfaceName;
        public final String address;
        final int prefix;

        public Endpoint(String interfaceName, String address, int prefix) {
            this.interfaceName = interfaceName;
            this.address = address;
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return interfaceName + " " + address + "/" + prefix;
        }
    }

    private ShareAddresses() {
    }

    /** Every IPv4 address the car has, internal links included (for the "none usable" message). */
    public static List<Endpoint> all() {
        List<Endpoint> found = new ArrayList<>();
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address) {
                        found.add(new Endpoint(iface.getName(), ia.getAddress().getHostAddress(),
                                ia.getNetworkPrefixLength()));
                    }
                }
            }
        } catch (Exception ignored) {
            // no list: the dialog says there is no usable address
        }
        return found;
    }

    /**
     * The ones a phone may reach, best first: on the iPhone's network, then Wi-Fi and hotspot,
     * then the rest the receiver answers on.
     *
     * @param iphone the address the iPhone last AirPlayed from; null when none yet
     */
    public static List<Endpoint> usable(List<Endpoint> all, @Nullable String iphone) {
        List<Endpoint> out = new ArrayList<>();
        for (Endpoint e : all) {
            if (MdnsResponder.answersOn(e.interfaceName)) {
                out.add(e);
            }
        }
        Integer phone = toInt(iphone);
        out.sort((a, b) -> {
            int byRank = rank(a, phone) - rank(b, phone);
            return byRank != 0 ? byRank : a.interfaceName.compareTo(b.interfaceName);
        });
        return out;
    }

    private static int rank(Endpoint e, @Nullable Integer phone) {
        Integer own = toInt(e.address);
        if (phone != null && own != null && sameNetwork(own, phone, e.prefix)) {
            return 0;
        }
        return Visibility.isWifiInterface(e.interfaceName.toLowerCase(Locale.US)) ? 1 : 2;
    }

    static boolean sameNetwork(int a, int b, int prefix) {
        int mask = prefix <= 0 ? 0 : -1 << (32 - Math.min(prefix, 32));
        return ((a ^ b) & mask) == 0;
    }

    @Nullable
    static Integer toInt(@Nullable String dotted) {
        if (dotted == null) {
            return null;
        }
        String[] parts = dotted.trim().split("\\.");
        if (parts.length != 4) {
            return null;
        }
        int out = 0;
        try {
            for (String p : parts) {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) {
                    return null;
                }
                out = (out << 8) | v;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    /** "wlan0 192.168.43.1/24, vlan11 198.18.34.15/22": for the log and the message. */
    public static String describe(List<Endpoint> endpoints) {
        StringBuilder sb = new StringBuilder();
        for (Endpoint e : endpoints) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e);
        }
        return sb.toString();
    }
}
