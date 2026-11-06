package app.veil.cluster;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The driver display's own log: {@code files/logs/cluster-yyyyMMdd.log}, every line written at
 * once, the last lines kept in memory for the Driver display page. Same shape as
 * {@link app.veil.airplay.AirPlayLog}, kept separate so the two pages do not mix.
 */
public final class ClusterLog {

    private static final String TAG = "VeilCluster";
    private static final long MAX_BYTES = 1024L * 1024;
    private static final int KEEP_LINES = 120;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final ArrayDeque<String> RECENT = new ArrayDeque<>(KEEP_LINES);
    private static Context app;

    private ClusterLog() {
    }

    public static void init(Context context) {
        synchronized (LOCK) {
            app = context.getApplicationContext();
        }
    }

    public static void i(String message) {
        Log.i(TAG, message);
        write("I", message);
    }

    public static void w(String message) {
        Log.w(TAG, message);
        write("W", message);
    }

    /** The newest lines, oldest first. */
    public static List<String> recent() {
        synchronized (LOCK) {
            return new ArrayList<>(RECENT);
        }
    }

    private static void write(String level, String message) {
        synchronized (LOCK) {
            Date now = new Date();
            String line = TIME.format(now) + " " + level + " " + message;
            if (RECENT.size() == KEEP_LINES) {
                RECENT.removeFirst();
            }
            RECENT.addLast(line);
            if (app == null) {
                return;
            }
            File dir = new File(app.getFilesDir(), "logs");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            File file = new File(dir, "cluster-" + DAY.format(now) + ".log");
            if (file.length() > MAX_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            try (FileWriter out = new FileWriter(file, true)) {
                out.write(line + "\n");
            } catch (IOException ignored) {
                // the log must never take the test down
            }
        }
    }
}
