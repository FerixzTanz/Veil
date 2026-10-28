package app.veil.airplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Why an iPhone might not see the receiver, from what the car's networks look like. Plain
 * rules over plain facts, so the Network page's advice can be tested.
 */
public final class Visibility {

    /** What the page knows at the moment it asks. */
    public static final class Facts {
        public boolean receiverRunning;
        /** The receiver has a usable IPv4 address on a Wi-Fi or hotspot interface. */
        public boolean onWifiOrHotspot;
        /** The car's Wi-Fi is the iPhone's Personal Hotspot (172.20.10.x gateway). */
        public boolean onIphoneHotspot;
        /** Veil's own mDNS responder could bind port 5353; false means the system one answers. */
        public boolean ownMdns;
        /** The Wi-Fi interface reports multicast support. */
        public boolean multicast = true;
        /** The head unit let Veil draw over other apps. */
        public boolean canDrawOverlay = true;
    }

    public enum Reason {
        RECEIVER_OFF,
        NO_WIFI,
        SYSTEM_MDNS,
        NO_MULTICAST,
        NO_OVERLAY,
        NOT_IPHONE_HOTSPOT_HINT
    }

    private Visibility() {
    }

    /** Most likely cause first. Empty when nothing stands in the way. */
    public static List<Reason> reasons(Facts f) {
        List<Reason> out = new ArrayList<>();
        if (!f.receiverRunning) {
            out.add(Reason.RECEIVER_OFF);
            return out;
        }
        if (!f.onWifiOrHotspot) {
            out.add(Reason.NO_WIFI);
            return out;
        }
        if (!f.multicast) {
            out.add(Reason.NO_MULTICAST);
        }
        if (!f.ownMdns) {
            out.add(Reason.SYSTEM_MDNS);
        }
        if (!f.canDrawOverlay) {
            out.add(Reason.NO_OVERLAY);
        }
        if (!f.onIphoneHotspot) {
            out.add(Reason.NOT_IPHONE_HOTSPOT_HINT);
        }
        return out;
    }

    /** "wlan2  192.168.5.79:7001" style lines: is any of them on a Wi-Fi or hotspot interface? */
    public static boolean anyWifi(List<String> addressLines) {
        for (String line : addressLines) {
            String iface = line.trim().split("\\s+")[0];
            if (isWifiInterface(iface)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWifiInterface(String name) {
        return name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan");
    }
}
