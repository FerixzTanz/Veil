package app.veil.airplay;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.JsonWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.veil.VeilApp;
import app.veil.settings.Settings;

/**
 * The diagnostics export: one .json with what is needed to tell from home why AirPlay
 * misbehaved in the car.
 *
 * <p>Unlike the Network page it leaves nothing out and cuts nothing: today's and yesterday's
 * log files go in whole, a line per array entry, with the receiver's state, the car's networks,
 * where the iPhone was, the settings that matter and the versions. It is written as it is read
 * ({@link JsonWriter}), so a 4 MB log never sits in memory as one string. Written to the app's
 * folder on the car's storage ({@code Android/data/app.veil/files/exports}); the newest
 * {@link #KEEP} stay. The format follows CAMCORDER's diagnostics export (the author's app).</p>
 *
 * <p>Call off the main thread.</p>
 */
public final class DiagnosticsExport {

    /** Add one whenever the fields change, so old exports can still be read. */
    static final int SCHEMA_VERSION = 1;
    private static final int KEEP = 5;
    /** Today's log and yesterday's: a drive that started before midnight is still in. */
    private static final int LOG_DAYS = 2;

    private DiagnosticsExport() {
    }

    /** Writes a new export and returns it. */
    public static File write(Context context) throws IOException {
        Context app = context.getApplicationContext();
        File dir = dir(app);
        Date now = new Date();
        File out = new File(dir, "veil_diagnostics_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now) + ".json");
        try (JsonWriter json = new JsonWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            json.setIndent("  ");
            json.beginObject();
            json.name("schema_version").value(SCHEMA_VERSION);
            json.name("generated_at").value(now.getTime());
            json.name("generated_local").value(
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now));
            app(json, app);
            device(json);
            receiver(json);
            settings(json);
            json.name("networks");
            strings(json, new NetworkReport(app).lines());
            logs(json, app);
            json.endObject();
        } catch (IOException | RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        AirPlayLog.i("export: wrote " + out.getAbsolutePath() + " (" + out.length() / 1024 + " KB)");
        prune(dir);
        return out;
    }

    private static File dir(Context app) throws IOException {
        File base = app.getExternalFilesDir(null);
        File dir = new File(base != null ? base : app.getFilesDir(), "exports");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        return dir;
    }

    private static void app(JsonWriter json, Context app) throws IOException {
        json.name("app").beginObject();
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            json.name("version_name").value(info.versionName);
            json.name("version_code").value(info.getLongVersionCode());
        } catch (Exception e) {
            json.name("version_name").value("unknown");
        }
        json.endObject();
    }

    private static void device(JsonWriter json) throws IOException {
        json.name("device").beginObject();
        json.name("manufacturer").value(Build.MANUFACTURER);
        json.name("model").value(Build.MODEL);
        json.name("device").value(Build.DEVICE);
        json.name("build_display_id").value(Build.DISPLAY);
        json.name("android_release").value(Build.VERSION.RELEASE);
        json.name("android_sdk").value(Build.VERSION.SDK_INT);
        json.endObject();
    }

    private static void receiver(JsonWriter json) throws IOException {
        json.name("receiver").beginObject();
        json.name("running").value(AirPlayService.isRunning());
        json.name("state").value(stateName(AirPlayService.state()));
        json.name("name").value(AirPlayService.deviceName());
        json.name("addresses");
        strings(json, AirPlayService.addresses());
        json.name("own_mdns").value(AirPlayService.ownMdns());
        json.name("wifi_multicast").value(AirPlayService.wifiMulticast());
        json.name("problem").value(AirPlayService.problem());
        json.name("iphone_last_seen").value(NetworkReport.lastIphone());
        json.endObject();
    }

    private static void settings(JsonWriter json) throws IOException {
        Settings s = VeilApp.settings();
        json.name("settings").beginObject();
        json.name("airplay_on").value(s.airPlayOn());
        json.name("cast_video").value(s.castVideo());
        json.name("receiver_name").value(s.receiverName());
        json.endObject();
    }

    /** Whole files, oldest first, each line an entry. */
    private static void logs(JsonWriter json, Context app) throws IOException {
        json.name("log_files").beginArray();
        for (File f : recentLogs(AirPlayLog.files(app), LOG_DAYS, new Date())) {
            json.beginObject();
            json.name("name").value(f.getName());
            json.name("bytes").value(f.length());
            json.name("lines").beginArray();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(f), StandardCharsets.UTF_8))) {
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    json.value(line);
                }
            } catch (IOException e) {
                json.value("(could not read further: " + e + ")");
            }
            json.endArray();
            json.endObject();
        }
        json.endArray();
    }

    /** The log files of the last {@code days} days, oldest first ("airplay-yyyyMMdd[.1].log"). */
    static List<File> recentLogs(List<File> all, int days, Date now) {
        List<String> wanted = new ArrayList<>();
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
        for (int i = days - 1; i >= 0; i--) {
            wanted.add(day.format(new Date(now.getTime() - i * 24L * 3600_000L)));
        }
        List<File> out = new ArrayList<>();
        for (String d : wanted) {
            // the rotated part (".1") is the older half of the day
            for (String name : new String[] {"airplay-" + d + ".1.log", "airplay-" + d + ".log"}) {
                for (File f : all) {
                    if (f.getName().equals(name)) {
                        out.add(f);
                    }
                }
            }
        }
        return out;
    }

    private static void prune(File dir) {
        File[] files = dir.listFiles((d, name) -> name.startsWith("veil_diagnostics_") && name.endsWith(".json"));
        if (files == null || files.length <= KEEP) {
            return;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (int i = 0; i < files.length - KEEP; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    private static void strings(JsonWriter json, List<String> values) throws IOException {
        json.beginArray();
        for (String v : values) {
            json.value(v);
        }
        json.endArray();
    }

    static String stateName(int state) {
        switch (state) {
            case AirPlayService.STATE_WAITING:
                return "waiting";
            case AirPlayService.STATE_CONNECTED:
                return "connected";
            case AirPlayService.STATE_MIRRORING:
                return "mirroring";
            case AirPlayService.STATE_CASTING:
                return "casting";
            default:
                return "off";
        }
    }
}
