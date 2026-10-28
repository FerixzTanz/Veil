package app.veil.airplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import app.veil.MainActivity;
import app.veil.R;
import app.veil.VeilApp;
import app.veil.dim.Overlays;
import app.veil.settings.Settings;
import io.github.jqssun.airplay.bridge.NativeBridge;
import io.github.jqssun.airplay.bridge.RaopCallbackHandler;

/**
 * AirPlay screen-mirroring receiver. While it runs the head unit shows up in the iPhone's
 * Screen Mirroring list under the receiver name; when the iPhone mirrors, the picture plays
 * in a floating window ({@link AirPlayWindow}) and the sound through the car's speakers.
 *
 * <p>The protocol is UxPlay's, through jqssun/android-airplay-server's native bridge (both
 * GPL-3.0, see NOTICE.md). From 7xDash (GPL-3.0), with these changes: settings live in
 * {@link Settings}; the picture is never hidden for driving and Veil reads nothing from the
 * car (the owner's decision); video casting arrives in step 4, so the receiver does not offer
 * it yet; no outside speaker.</p>
 *
 * <p>Real-car lessons kept from 7xDash's changelog: media audio focus is requested when the
 * iPhone starts streaming, otherwise the car keeps its media channel shut and AirPlay plays
 * silent unless a radio channel was started first; starts and stops run one after another on
 * one thread, and a busy port 7000 is retried, then 7001 to 7003; the receiver keeps the audio
 * feature bit on, without it an iPhone sees the receiver but cannot connect.</p>
 */
public class AirPlayService extends Service implements RaopCallbackHandler {

    private static final String TAG = "AirPlay";
    private static final String CHANNEL_ID = "airplay";
    private static final int NOTIFICATION_ID = 7071;
    private static final String KEY_MAC = "airplay.mac";
    private static final int PORT = 7000;
    /** Other ports to try when 7000 stays taken (another AirPlay receiver on the car, say). */
    private static final int[] SPARE_PORTS = {7001, 7002, 7003};
    private static final int FPS = 30;

    /** One thread for every start and stop, shared by all service instances. */
    private static final Handler WORK;

    static {
        HandlerThread t = new HandlerThread("airplay");
        t.start();
        WORK = new Handler(t.getLooper());
    }

    private static volatile AirPlayService live;
    public static final int STATE_NONE = 0;
    public static final int STATE_WAITING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_MIRRORING = 3;
    public static final int STATE_CASTING = 4;
    private static volatile int state = STATE_NONE;
    /** Port the receiver listens on, 0 when not up. */
    private static volatile int listenPort;
    /** True while Veil's own mDNS responder answers; false when the system one had to. */
    private static volatile boolean ownMdns;
    /** Why the iPhone's picture has nowhere to go, for the AirPlay page; null when all is well. */
    @Nullable
    private static volatile String problem;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Settings settings;
    private long handle;
    /** Held while the iPhone streams audio: the car opens its media channel only for the focus holder. */
    private AudioFocusRequest focus;
    private NsdManager nsd;
    private MdnsResponder mdns;
    private final List<NsdManager.RegistrationListener> registrations = new ArrayList<>();
    private WifiManager.MulticastLock multicast;
    private final MirrorDecoder decoder = new MirrorDecoder();
    private NetworkReport net;
    /** The floating window's surface. */
    private Surface surface;
    private AirPlayWindow window;
    private final AtomicInteger connections = new AtomicInteger();
    private boolean mirroring;
    /** Main thread. The iPhone connected as a speaker (no screen): {@link #problem} says so. */
    private boolean soundOnly;
    /** Set when mirroring starts; the first decoded frame clears it and shows the picture. */
    private volatile boolean firstFrame;
    private String name = Settings.DEFAULT_RECEIVER_NAME;

    // ---- public switch ----

    /** Turn the receiver on or off and remember it; on survives a reboot. */
    public static void setEnabled(Context context, boolean on) {
        VeilApp.settings().setAirPlayOn(on);
        if (on) {
            start(context);
        } else {
            context.stopService(new Intent(context, AirPlayService.class));
        }
    }

    /** Start if the user left it on. Called when the app process starts and after boot. */
    public static void applySaved(Context context) {
        if (VeilApp.settings().airPlayOn() && live == null) {
            start(context);
        }
    }

    public static boolean isRunning() {
        return live != null;
    }

    /** STATE_*; STATE_NONE when off or still starting. */
    public static int state() {
        return live == null ? STATE_NONE : state;
    }

    public static boolean ownMdns() {
        return live != null && ownMdns;
    }

    /**
     * Why the iPhone's picture is not on screen (it connected for sound only, no overlay
     * permission, or the window was refused), or null. The AirPlay page shows it: on the 7X
     * a toast from the service may not appear.
     */
    @Nullable
    public static String problem() {
        return live == null ? null : problem;
    }

    /**
     * Where an iPhone can reach the receiver: "wlan2  192.168.5.79:7001" per IPv4 address, Wi-Fi
     * and hotspot interfaces first. Empty when the receiver is not up.
     */
    public static List<String> addresses() {
        List<String> wifi = new ArrayList<>();
        List<String> other = new ArrayList<>();
        int port = listenPort;
        if (live == null || port <= 0) {
            return wifi;
        }
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || !MdnsResponder.answersOn(iface.getName())) {
                    continue;
                }
                for (java.net.InetAddress a : Collections.list(iface.getInetAddresses())) {
                    if (!(a instanceof java.net.Inet4Address)) {
                        continue;
                    }
                    String n = iface.getName();
                    String line = n + "  " + a.getHostAddress() + ":" + port;
                    (Visibility.isWifiInterface(n) ? wifi : other).add(line);
                }
            }
        } catch (Exception ignored) {
            // nothing to show
        }
        wifi.addAll(other);
        return wifi;
    }

    /** Whether the Wi-Fi interfaces report multicast, which mDNS needs. */
    public static boolean wifiMulticast() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.isUp() && Visibility.isWifiInterface(iface.getName())) {
                    return iface.supportsMulticast();
                }
            }
        } catch (Exception ignored) {
            // assume the usual
        }
        return true;
    }

    public static String deviceName() {
        AirPlayService s = live;
        return s == null ? VeilApp.settings().receiverName() : s.name;
    }

    /** The receiver name changed: the announcement carries it, so the receiver restarts. */
    public static void nameChanged() {
        AirPlayService s = live;
        if (s != null) {
            s.main.post(() -> s.restartReceiver("receiver name changed"));
        }
    }

    private static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context, new Intent(context, AirPlayService.class));
        } catch (RuntimeException e) {
            AirPlayLog.init(context);
            AirPlayLog.w("could not start the service: " + e);
        }
    }

    // ---- lifecycle ----

    @Override
    public void onCreate() {
        super.onCreate();
        live = this;
        settings = VeilApp.settings();
        promote();
        AirPlayLog.init(this);
        window = new AirPlayWindow(this, settings, new AirPlayWindow.Listener() {
            @Override
            public void onSurface(Surface s) {
                surface = s;
                decoder.setSurface(s);
            }

            @Override
            public void onCloseTapped() {
                restartReceiver("close tapped");
            }
        });
        AirPlayLog.prune(this);
        net = new NetworkReport(this);
        WORK.post(this::startServer);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        promote();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        live = null;
        state = STATE_NONE;
        problem = null;
        main.removeCallbacksAndMessages(null);
        if (window != null) {
            window.hide();
        }
        AirPlayLog.i("service stopping");
        if (net != null) {
            net.sessionEnded();
            net.stop();
        }
        WORK.post(this::stopServer);
        super.onDestroy();
    }

    // ---- server (WORK thread) ----

    private void startServer() {
        if (live != this) {
            return; // stopped again before it got going
        }
        name = settings.receiverName();
        AirPlayLog.i("---- starting AirPlay receiver: Veil " + versionName()
                + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                + Build.MANUFACTURER + " " + Build.MODEL + ", log in " + AirPlayLog.where());
        AirPlayLog.lastExits(this);
        AirPlayLog.networks();
        net.stop();
        net.start();
        WifiManager wifi = getSystemService(WifiManager.class);
        multicast = wifi == null ? null : wifi.createMulticastLock("veil_airplay_mdns");
        if (multicast != null) {
            multicast.setReferenceCounted(false);
            multicast.acquire();
        }
        AudioManager am = getSystemService(AudioManager.class);
        NativeBridge.nativeSetDefaultStreamValues(
                parse(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)),
                parse(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)));
        String keyFile = getFilesDir().getAbsolutePath() + "/airplay.pem";
        byte[] mac = hwAddr();
        AirPlayLog.i("device id " + macText(mac));
        handle = NativeBridge.nativeInit(this, mac, name, keyFile, true, false);
        if (handle == 0) {
            fail("native init failed");
            return;
        }
        NativeBridge.nativeSetLogLevel(handle, NativeBridge.LOG_DEBUG);
        NativeBridge.nativeServerAudioConfigure(handle, 0, 95, 0, false, false, true, true, false);
        NativeBridge.nativeSetH265Enabled(handle, false);
        NativeBridge.nativeSetCodecs(handle, false, true);
        // Video casting (the AirPlay button inside an app) comes in step 4: not offered yet,
        // so iOS keeps apps on screen mirroring.
        NativeBridge.nativeSetHlsEnabled(handle, false);
        NativeBridge.nativeSetLang(handle, "", "",
                getResources().getConfiguration().getLocales().toLanguageTags().replace(',', ':'));
        NativeBridge.nativeSetFeature(handle, NativeBridge.FT_MIRROR, true);
        // Keep the audio feature bit on: an iPhone expects it from a mirroring receiver;
        // without it the iPhone can see the receiver but not connect. Music-only AirPlay
        // still cannot play (no ALAC decoder).
        NativeBridge.nativeSetFeature(handle, NativeBridge.FT_AUDIO, true);
        NativeBridge.nativeSetPlist(handle, "maxFPS", FPS);
        NativeBridge.nativeSetPlist(handle, "overscanned", 0);
        int[] size = displaySize();
        NativeBridge.nativeSetDisplaySize(handle, size[0], size[1], FPS);

        int port = listen();
        if (port < 0) {
            fail("could not listen on port " + PORT + " or the spare ports");
            return;
        }
        Map<String, String> raopTxt = NativeBridge.nativeGetRaopTxtRecords(handle);
        Map<String, String> airplayTxt = NativeBridge.nativeGetAirplayTxtRecords(handle);
        String raopName = NativeBridge.nativeGetRaopServiceName(handle);
        String serverName = NativeBridge.nativeGetServerName(handle);
        AirPlayLog.i("airplay txt " + airplayTxt);
        AirPlayLog.i("raop txt " + raopTxt);
        announce(raopName != null ? raopName : name, serverName != null ? serverName : name,
                port, raopTxt, airplayTxt);
        setState(STATE_WAITING);
        listenPort = port;
        AirPlayLog.i("receiver up on port " + port + " as " + name + ", offers "
                + size[0] + "x" + size[1] + "@" + FPS);
    }

    /** Port 7000, waiting a little if the last receiver is still letting go, else a spare one. */
    private int listen() {
        for (int attempt = 0; attempt < 3; attempt++) {
            int port = NativeBridge.nativeStart(handle, PORT);
            if (port > 0) {
                return port;
            }
            AirPlayLog.w("port " + PORT + " busy, try " + (attempt + 1));
            android.os.SystemClock.sleep(700);
        }
        for (int spare : SPARE_PORTS) {
            int port = NativeBridge.nativeStart(handle, spare);
            if (port > 0) {
                return port;
            }
            AirPlayLog.w("port " + spare + " busy");
        }
        return -1;
    }

    /** Main thread. Without media focus the car keeps the media channel shut and AirPlay plays silent. */
    private void holdFocus() {
        if (focus != null) {
            return;
        }
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio == null) {
            return;
        }
        AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(change -> AirPlayLog.i("audio focus changed: " + change), main)
                .build();
        int result = audio.requestAudioFocus(request);
        AirPlayLog.i("audio focus " + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                ? "granted" : "refused (" + result + ")"));
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focus = request;
        }
    }

    /** Main thread. */
    private void releaseFocus() {
        AudioFocusRequest held = focus;
        focus = null;
        AudioManager audio = getSystemService(AudioManager.class);
        if (held != null && audio != null) {
            audio.abandonAudioFocusRequest(held);
            AirPlayLog.i("audio focus released");
        }
    }

    private void stopServer() {
        AirPlayLog.i("receiver stopping");
        main.post(this::releaseFocus);
        listenPort = 0;
        ownMdns = false;
        if (mdns != null) {
            mdns.stop();
            mdns = null;
        }
        if (nsd != null) {
            for (NsdManager.RegistrationListener l : registrations) {
                try {
                    nsd.unregisterService(l);
                } catch (RuntimeException ignored) {
                    // never registered
                }
            }
        }
        registrations.clear();
        if (handle != 0) {
            NativeBridge.nativeServerAudioStop(handle);
            NativeBridge.nativeStop(handle);
            NativeBridge.nativeDestroy(handle);
            handle = 0;
        }
        decoder.release();
        if (multicast != null && multicast.isHeld()) {
            multicast.release();
        }
    }

    private void fail(String why) {
        AirPlayLog.w("start failed: " + why);
        stopServer();
        main.post(() -> {
            Toast.makeText(this, R.string.airplay_failed, Toast.LENGTH_LONG).show();
            stopSelf();
        });
    }

    private void register(String serviceName, String type, int port, @Nullable Map<String, String> txt) {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(serviceName);
        info.setServiceType(type);
        info.setPort(port);
        if (txt != null) {
            for (Map.Entry<String, String> e : txt.entrySet()) {
                info.setAttribute(e.getKey(), e.getValue());
            }
        }
        NsdManager.RegistrationListener l = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo s) {
                AirPlayLog.i(type + " announced as " + s.getServiceName());
                if ("_airplay._tcp".equals(type)) {
                    resolveSelf(s.getServiceName(), type);
                }
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo s, int code) {
                AirPlayLog.w(type + " announce failed: " + code);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo s) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo s, int code) {
            }
        };
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l);
        registrations.add(l);
    }

    /**
     * Tell iPhones we are here. Our own mDNS responder first, answering each network with its
     * own address (the 7X's system one hands out an internal address on every network); the
     * system NsdManager only when the car won't let us share the mDNS port.
     */
    private void announce(String raopName, String airplayName, int port,
                          @Nullable Map<String, String> raopTxt, @Nullable Map<String, String> airplayTxt) {
        List<MdnsResponder.Service> list = new ArrayList<>();
        list.add(new MdnsResponder.Service(airplayName, "_airplay._tcp", port, airplayTxt));
        list.add(new MdnsResponder.Service(raopName, "_raop._tcp", port, raopTxt));
        MdnsResponder responder = new MdnsResponder(list);
        try {
            responder.start();
            mdns = responder;
            ownMdns = true;
            for (String a : addresses()) {
                AirPlayLog.i("reachable at " + a);
            }
            return;
        } catch (java.io.IOException e) {
            AirPlayLog.w("mDNS: own responder could not open port 5353 (" + e.getMessage()
                    + "), using the system one");
        }
        ownMdns = false;
        nsd = getSystemService(NsdManager.class);
        register(raopName, "_raop._tcp", port, raopTxt);
        register(airplayName, "_airplay._tcp", port, airplayTxt);
    }

    /** Look ourselves up the way an iPhone would, and log the address and port it gets. */
    private void resolveSelf(String serviceName, String type) {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(serviceName);
        info.setServiceType(type);
        try {
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo s, int code) {
                    AirPlayLog.w("self lookup failed: " + code);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo s) {
                    AirPlayLog.i("self lookup: " + s.getServiceName() + " -> "
                            + (s.getHost() != null ? s.getHost().getHostAddress() : "?") + ":" + s.getPort());
                }
            });
        } catch (RuntimeException e) {
            AirPlayLog.w("self lookup error: " + e);
        }
        for (String a : addresses()) {
            AirPlayLog.i("reachable at " + a);
        }
    }

    /** Drop any iPhone and start the receiver again (it stays on). Main thread. */
    private void restartReceiver(String why) {
        AirPlayLog.i(why + ": restarting the receiver");
        window.hide();
        WORK.post(() -> {
            stopServer();
            startServer();
        });
    }

    // ---- floating window (main thread) ----

    private void mirrorStarted() {
        mirroring = true;
        soundOnly = false;
        AirPlayLog.i("mirroring started");
        net.sessionStarted("mirroring");
        setState(STATE_MIRRORING);
        if (!Overlays.canDraw(this)) {
            AirPlayLog.w("no permission to draw over other apps: no window");
            problem = getString(R.string.airplay_need_overlay);
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            return;
        }
        window.show();
        String refused = window.refused();
        if (refused != null) {
            problem = getString(R.string.airplay_window_refused, refused);
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            return;
        }
        problem = null;
        window.setPlaying(false);
        Toast.makeText(this, R.string.airplay_started, Toast.LENGTH_SHORT).show();
    }

    private void mirrorStopped() {
        AirPlayLog.i("mirroring stopped");
        net.sessionEnded();
        mirroring = false;
        window.hide();
        setState(connections.get() > 0 ? STATE_CONNECTED : STATE_WAITING);
    }

    // ---- RaopCallbackHandler (native threads) ----

    @Override
    public void onVideoData(byte[] data, long ntpTimeNs, boolean isH265) {
        if (isH265) {
            return;
        }
        decoder.feed(data, ntpTimeNs);
        if (firstFrame) {
            firstFrame = false;
            AirPlayLog.i("first video frame, " + data.length + " bytes");
            main.post(() -> window.setPlaying(true));
        }
    }

    @Override
    public void onVideoSize(float srcW, float srcH, float w, float h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        decoder.setSize((int) w, (int) h);
        main.post(() -> window.setVideoSize((int) w, (int) h));
        AirPlayLog.i("iPhone picture " + (int) srcW + "x" + (int) srcH + " -> " + (int) w + "x" + (int) h);
    }

    @Override
    public void onMirrorRunning(boolean running) {
        AirPlayLog.i("mirror stream " + (running ? "running" : "ended"));
        if (handle != 0) {
            NativeBridge.nativeSetLogLevel(handle, running ? NativeBridge.LOG_INFO : NativeBridge.LOG_DEBUG);
        }
        firstFrame = running;
        if (!running) {
            decoder.release();
        }
        main.post(running ? this::mirrorStarted : this::mirrorStopped);
    }

    @Override
    public void onAudioFormat(int ct, int spf, boolean usingScreen) {
        AirPlayLog.i("audio format ct=" + ct + " spf=" + spf + " screen=" + usingScreen
                + (ct == NativeBridge.CT_ALAC ? " (ALAC: not supported, no sound)" : ""));
        if (handle == 0) {
            return;
        }
        main.post(this::holdFocus);
        if (!usingScreen) {
            // AirPlay from a music player or the audio output picker: sound, never a picture
            main.post(() -> {
                if (!mirroring) {
                    AirPlayLog.w("the iPhone connected for sound only, not screen mirroring");
                    soundOnly = true;
                    problem = getString(R.string.airplay_sound_only);
                }
            });
        }
        NativeBridge.nativeServerAudioStart(handle);
        NativeBridge.nativeServerAudioFormat(handle, ct, spf);
    }

    @Override
    public void onAudioTeardown() {
        AirPlayLog.i("audio stream ended");
        main.post(this::releaseFocus);
    }

    @Override
    public void onConnectionInit() {
        AirPlayLog.i("iPhone connected (" + connections.incrementAndGet() + " open)");
        main.post(() -> {
            if (!mirroring) {
                setState(STATE_CONNECTED);
            }
        });
    }

    @Override
    public void onConnectionDestroy() {
        int left = connections.updateAndGet(n -> Math.max(0, n - 1));
        AirPlayLog.i("iPhone connection closed (" + left + " open)");
        if (left == 0) {
            if (handle != 0) {
                NativeBridge.nativeServerAudioStop(handle);
            }
            main.post(() -> {
                releaseFocus();
                if (soundOnly) {
                    soundOnly = false;
                    problem = null;
                }
                if (mirroring) {
                    mirrorStopped();
                } else {
                    setState(STATE_WAITING);
                }
            });
        }
    }

    @Override
    public void onVolumeChange(float volume) {
        // the car's own volume knob stays in charge
    }

    @Override
    public float onClientVolume() {
        return 0f;
    }

    @Override
    public void onConnectionReset(int reason) {
        AirPlayLog.w("connection reset, reason " + reason);
    }

    @Override
    public void onDisplayPin(String pin) {
        AirPlayLog.w("iPhone asked for a PIN (not expected)");
    }

    @Override
    public void onMetadata(byte[] data) {
    }

    @Override
    public void onCoverArt(byte[] data) {
    }

    @Override
    public void onProgress(long start, long curr, long end) {
    }

    @Override
    public void onDacpId(String dacpId, String activeRemote) {
    }

    // Video casting is not offered until step 4; these cannot fire, but the bridge needs them.

    @Override
    public void onVideoPlay(String location, float startPositionSeconds) {
        AirPlayLog.w("cast requested but video casting is not available yet: " + location);
    }

    @Override
    public void onVideoScrub(float positionSeconds) {
    }

    @Override
    public void onVideoRate(float rate) {
    }

    @Override
    public void onVideoSelectMedia(String mediaType, String language) {
    }

    @Override
    public void onVideoStop() {
    }

    @Override
    public void onVideoSessionPoll() {
    }

    @Override
    public void onLog(String message) {
        AirPlayLog.nativeLine(message);
        if (message.startsWith("Remote: ") && net != null) {
            String ip = message.substring("Remote: ".length());
            if (!ip.startsWith("127.") && !ip.equals("0000:0000:0000:0000:0000:0000:0000:0001")) {
                net.iphoneAt(ip);
            }
        }
    }

    // ---- helpers ----

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String macText(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (byte b : mac) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void setState(int value) {
        state = value;
    }

    private static int parse(@Nullable String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Landscape, at most 1920x1080 so the car's decoder keeps up. */
    private int[] displaySize() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        if (w > 1920) {
            h = Math.round(h * 1920f / w);
            w = 1920;
        }
        if (h > 1080) {
            w = Math.round(w * 1080f / h);
            h = 1080;
        }
        return new int[] {w & ~1, h & ~1};
    }

    /** The Wi-Fi MAC when Android shows it, else a random one kept for this install. */
    private byte[] hwAddr() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String n = iface.getName();
                if (n.startsWith("wlan") || n.startsWith("eth")) {
                    byte[] mac = iface.getHardwareAddress();
                    if (usable(mac)) {
                        return mac;
                    }
                }
            }
        } catch (Exception e) {
            AirPlayLog.w("no hardware address: " + e);
        }
        String saved = settings.getString(KEY_MAC, null);
        if (saved != null && saved.length() == 12) {
            byte[] mac = new byte[6];
            for (int i = 0; i < 6; i++) {
                mac[i] = (byte) Integer.parseInt(saved.substring(i * 2, i * 2 + 2), 16);
            }
            return mac;
        }
        byte[] mac = new byte[6];
        new SecureRandom().nextBytes(mac);
        mac[0] = (byte) ((mac[0] & 0xF0) | 0x0A); // locally administered, unicast
        StringBuilder sb = new StringBuilder();
        for (byte b : mac) {
            sb.append(String.format("%02x", b));
        }
        settings.putString(KEY_MAC, sb.toString());
        return mac;
    }

    private static boolean usable(@Nullable byte[] mac) {
        if (mac == null || mac.length != 6) {
            return false;
        }
        boolean allZero = true;
        boolean placeholder = mac[0] == 0x02;
        for (int i = 0; i < 6; i++) {
            if (mac[i] != 0) {
                allZero = false;
            }
            if (i > 0 && mac[i] != 0) {
                placeholder = false;
            }
        }
        return !allZero && !placeholder;
    }

    private void promote() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.section_airplay), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
        Intent page = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, page,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_airplay)
                .setContentTitle(getString(R.string.section_airplay))
                .setContentText(getString(R.string.airplay_notification))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, note);
        }
    }
}
