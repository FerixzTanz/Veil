package app.veil.airplay;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AirPlay's own log: {@code files/logs/airplay-yyyyMMdd.log} inside the app, one day per file,
 * rotated at 4 MB, every line written at once so the last line is the last thing that worked
 * if the native receiver takes the process down. The last lines are also kept in memory for
 * the Network page, without the receiver's per-request chatter ({@link LogFilter}). From 7xDash
 * (GPL-3.0), without the USB drive.
 */
public final class AirPlayLog {

    private static final String TAG = "AirPlay";
    private static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final int KEEP_LINES = 200;
    private static final int KEEP_DAYS = 7;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final ArrayDeque<String> RECENT = new ArrayDeque<>(KEEP_LINES);
    private static final LogFilter FILTER = new LogFilter();
    private static Context app;

    private AirPlayLog() {
    }

    public static void init(Context context) {
        synchronized (LOCK) {
            app = context.getApplicationContext();
        }
    }

    public static void i(String message) {
        Log.i(TAG, message);
        write("I", message, true);
    }

    public static void w(String message) {
        Log.w(TAG, message);
        write("W", message, true);
    }

    /** From the native receiver; already in logcat at debug. */
    static void nativeLine(String message) {
        Log.d(TAG + "Native", message);
        synchronized (LOCK) {
            write("N", message, !FILTER.hide(message));
        }
    }

    /** The newest lines, oldest first. */
    public static List<String> recent() {
        synchronized (LOCK) {
            return new ArrayList<>(RECENT);
        }
    }

    /** {@code page}: also keep it for the Network page. */
    private static void write(String level, String message, boolean page) {
        synchronized (LOCK) {
            Date now = new Date();
            String line = TIME.format(now) + " " + level + " " + Thread.currentThread().getName() + ": " + message;
            if (page) {
                if (RECENT.size() == KEEP_LINES) {
                    RECENT.removeFirst();
                }
                RECENT.addLast(line);
            }
            if (app == null) {
                return;
            }
            File dir = dir(app);
            if (dir == null) {
                return;
            }
            String day = DAY.format(now);
            File file = new File(dir, "airplay-" + day + ".log");
            if (file.length() > MAX_BYTES) {
                File old = new File(dir, "airplay-" + day + ".1.log");
                //noinspection ResultOfMethodCallIgnored
                old.delete();
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(old);
            }
            try (FileWriter out = new FileWriter(file, true)) {
                out.write(line + "\n");
            } catch (IOException ignored) {
                // the log must never take AirPlay down
            }
        }
    }

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "logs");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        return dir;
    }

    /** Where the log is going right now, for the first line. */
    static String where() {
        Context context = app;
        File dir = context == null ? null : dir(context);
        return dir == null ? "nowhere" : dir.getAbsolutePath();
    }

    /** Log files older than a week go. */
    static void prune(Context context) {
        File dir = dir(context);
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600_000L;
        for (File f : files) {
            if (f.getName().startsWith("airplay-") && f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** Every interface that is up, with its addresses: tells which network the iPhone must be on. */
    static void networks() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (InetAddress a : Collections.list(iface.getInetAddresses())) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(a.getHostAddress());
                }
                i("network " + iface.getName() + " multicast=" + iface.supportsMulticast() + " [" + sb + "]");
            }
        } catch (Exception e) {
            w("could not list networks: " + e);
        }
    }

    /** How this app's last runs ended (crash, native crash, killed), Android 11 and up. */
    static void lastExits(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(null, 0, 3);
            for (ApplicationExitInfo e : exits) {
                i("earlier exit: " + TIME.format(new Date(e.getTimestamp())) + " process=" + e.getProcessName()
                        + " reason=" + reason(e.getReason())
                        + (e.getDescription() != null ? " (" + e.getDescription() + ")" : ""));
            }
        } catch (RuntimeException e) {
            w("could not read earlier exits: " + e);
        }
    }

    private static String reason(int r) {
        switch (r) {
            case ApplicationExitInfo.REASON_CRASH: return "crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "native-crash";
            case ApplicationExitInfo.REASON_ANR: return "anr";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "low-memory";
            case ApplicationExitInfo.REASON_SIGNALED: return "signaled";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "user-stopped";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "exit-self";
            case ApplicationExitInfo.REASON_OTHER: return "other";
            default: return String.valueOf(r);
        }
    }
}
