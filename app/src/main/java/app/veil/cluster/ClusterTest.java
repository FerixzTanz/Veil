package app.veil.cluster;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import app.veil.VeilApp;
import app.veil.settings.Settings;

/**
 * The driver display test: covers the instrument cluster's screen with a pattern for
 * {@link #SHOW_MS}, asks the head unit to project to the cluster, then stops.
 *
 * <h3>What it uses</h3>
 *
 * <p>A {@link Presentation} on the cluster screen, Android's public way to put a window on a second
 * screen (the same thing CAMCORDER's HUD picture is). Then Zeekr's cluster interface
 * ({@link ClusterProjection}) to ask for the projection: REQUEST, START, and STOP before the window
 * closes. No root, no ADB, nothing permanent: the only state is a flag while a request is
 * outstanding, so an interrupted test is tidied up on the next start
 * ({@link #recoverIfInterrupted}).</p>
 *
 * <h3>Only while parked</h3>
 *
 * <p>The cluster carries the speed and the warnings; nothing may cover them while driving. The page
 * checks the gear before calling {@link #start} and refuses unless it reads P (or the driver
 * confirms P when it cannot be read).</p>
 */
public final class ClusterTest {

    /** How long the pattern stays. The interface connection takes about a second of it. */
    public static final long SHOW_MS = 8_000L;
    private static final long CHECK_MS = 1_000L;
    private static final long START_AFTER_REQUEST_MS = 500L;

    /** What the page shows. */
    public interface Listener {
        void onResult(String result);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile String lastResult = "";
    private static Presentation current;
    private static ClusterProjection projection;
    private static boolean running;

    private ClusterTest() {
    }

    public static String lastResult() {
        return lastResult;
    }

    public static boolean isRunning() {
        return running;
    }

    /**
     * Opens the pattern on the cluster screen and asks for the projection. Main thread. The caller
     * has already checked the gear.
     */
    public static void start(Context context, Listener listener) {
        if (running) {
            return;
        }
        running = true;
        final Context app = context.getApplicationContext();
        ClusterLog.init(app);
        Display display = find(app);
        if (display == null) {
            finish(app, "No " + ClusterDisplay.WIDTH + "x" + ClusterDisplay.HEIGHT + " screen is on.", listener);
            return;
        }
        final Point size = new Point();
        try {
            display.getRealSize(size);
        } catch (Exception ignored) {
            size.set(ClusterDisplay.WIDTH, ClusterDisplay.HEIGHT);
        }
        final String where = "display " + display.getDisplayId() + " (" + display.getName()
                + ", flags=0x" + Integer.toHexString(display.getFlags()) + ")";
        Presentation p;
        try {
            p = new Presentation(app, display);
            FrameLayout frame = new FrameLayout(p.getContext());
            frame.setBackgroundColor(0);
            frame.addView(new Pattern(p.getContext()), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            p.setContentView(frame);
            Window window = p.getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setBackgroundDrawable(new ColorDrawable(0));
            }
            p.show();
        } catch (Throwable t) {
            ClusterLog.w("window refused on " + where + ": " + ClusterProjection.describe(t));
            finish(app, "Android refused the window on the cluster screen: " + ClusterProjection.describe(t), listener);
            return;
        }
        ClusterLog.i("window opened on " + where);
        current = p;
        final Presentation shown = p;

        // The window is up: connect the cluster interface off the main thread, then ask to project.
        new Thread(() -> {
            StringBuilder why = new StringBuilder();
            ClusterProjection cp = ClusterProjection.connect(app, why);
            MAIN.post(() -> {
                if (current != shown) {
                    if (cp != null) {
                        cp.close();
                    }
                    return;
                }
                if (cp == null) {
                    ClusterLog.w("cluster interface unavailable: " + why);
                    lastResult = lastResult + "\nProjection request: unavailable (" + why + ").";
                    listener.onResult(lastResult);
                    return;
                }
                projection = cp;
                cp.listen();
                setProjecting(true);
                cp.send(ClusterProjection.ACTION_REQUEST, 0, 0, size.x, size.y);
                MAIN.postDelayed(() -> {
                    if (current == shown && projection == cp) {
                        cp.send(ClusterProjection.ACTION_START, 0, 0, size.x, size.y);
                        lastResult = "Pattern on " + where + "; projection asked for.\nLook at the cluster.";
                        listener.onResult(lastResult);
                    }
                }, START_AFTER_REQUEST_MS);
            });
        }, "VeilCluster-dim").start();

        MAIN.postDelayed(() -> {
            if (current != shown) {
                return;
            }
            if (!shown.isShowing()) {
                ClusterLog.w("window closed by the system within 1 s");
                current = null;
                finish(app, "Android opened the window but closed it again within a second.", listener);
                return;
            }
            lastResult = "Pattern on " + where + ".\nLook at the cluster.";
            listener.onResult(lastResult);
        }, CHECK_MS);

        MAIN.postDelayed(() -> {
            if (current != shown) {
                return;
            }
            current = null;
            ClusterProjection cp = projection;
            projection = null;
            String steps = "";
            if (cp != null) {
                cp.send(ClusterProjection.ACTION_STOP, 0, 0, size.x, size.y);
                setProjecting(false);
                steps = "\nSteps: " + cp.summary();
                cp.close();
            }
            try {
                shown.dismiss();
            } catch (Throwable ignored) {
                // the screen may already be gone
            }
            ClusterLog.i("window closed");
            finish(app, "Done. Window was on " + where + "." + steps, listener);
        }, SHOW_MS);
    }

    private static void finish(Context app, String result, Listener listener) {
        running = false;
        lastResult = result;
        ClusterLog.i("result: " + result.replace('\n', ' '));
        listener.onResult(result);
    }

    /** The cluster screen, if it is on; every screen's size goes into the log. */
    private static Display find(Context app) {
        DisplayManager dm = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) {
            return null;
        }
        Display[] displays = dm.getDisplays();
        List<ClusterDisplay.Candidate> candidates = new ArrayList<>();
        StringBuilder all = new StringBuilder("screens:");
        for (Display d : displays) {
            Point size = new Point();
            try {
                d.getRealSize(size);
            } catch (Exception ignored) {
                // a screen with no size is not the cluster
            }
            all.append(" [").append(d.getDisplayId()).append(' ').append(size.x).append('x').append(size.y)
                    .append(" state=").append(d.getState()).append(" flags=0x")
                    .append(Integer.toHexString(d.getFlags())).append(']');
            if (d.getState() == Display.STATE_ON) {
                candidates.add(new ClusterDisplay.Candidate(d.getDisplayId(), size.x, size.y));
            }
        }
        ClusterLog.i(all.toString());
        int id = ClusterDisplay.find(candidates);
        for (Display d : displays) {
            if (d.getDisplayId() == id) {
                return d;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ interrupted test

    private static void setProjecting(boolean on) {
        Settings settings = VeilApp.settings();
        if (settings != null) {
            settings.setClusterProjecting(on);
        }
    }

    /**
     * On app start: if a test asked for the projection and was killed before its STOP, send STOP
     * and RECOVERY now so the cluster is not left on the last frame. Off the main thread, shows
     * nothing.
     */
    public static void recoverIfInterrupted(Context context) {
        Settings settings = VeilApp.settings();
        if (settings == null || !settings.clusterProjecting()) {
            return;
        }
        final Context app = context.getApplicationContext();
        ClusterLog.init(app);
        new Thread(() -> {
            StringBuilder why = new StringBuilder();
            ClusterProjection cp = ClusterProjection.connect(app, why);
            if (cp == null) {
                ClusterLog.w("an earlier test did not finish; cannot send STOP (" + why + ")");
                return;
            }
            ClusterLog.i("an earlier test did not finish; sending STOP and RECOVERY");
            cp.send(ClusterProjection.ACTION_STOP, 0, 0, ClusterDisplay.WIDTH, ClusterDisplay.HEIGHT);
            cp.send(ClusterProjection.ACTION_RECOVERY, 0, 0, ClusterDisplay.WIDTH, ClusterDisplay.HEIGHT);
            cp.close();
            settings.setClusterProjecting(false);
        }, "VeilCluster-recover").start();
    }

    // ------------------------------------------------------------------ the pattern

    /** Fills the screen: a translucent dark ground, an orange frame, thirds, LEFT / CENTRE / RIGHT. */
    private static final class Pattern extends View {
        private static final String[] ZONES = {"LEFT", "CENTRE", "RIGHT"};
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        Pattern(Context context) {
            super(context);
            fill.setColor(0xB0000000);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(8f);
            stroke.setColor(0xFFF0622D);
            label.setColor(0xFFFFFFFF);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(96f);
            label.setFakeBoldText(true);
            small.setColor(0xFFF0622D);
            small.setTextAlign(Paint.Align.CENTER);
            small.setTextSize(44f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float inset = stroke.getStrokeWidth() / 2f;
            rect.set(inset, inset, w - inset, h - inset);
            canvas.drawRect(rect, fill);
            canvas.drawRect(rect, stroke);
            float third = w / 3f;
            canvas.drawLine(third, 0f, third, h, stroke);
            canvas.drawLine(2f * third, 0f, 2f * third, h, stroke);
            Paint.FontMetrics fm = label.getFontMetrics();
            float mid = h / 2f - (fm.ascent + fm.descent) / 2f;
            for (int i = 0; i < 3; i++) {
                float cx = third * i + third / 2f;
                canvas.drawText(ZONES[i], cx, mid, label);
                canvas.drawText("Veil test", cx, mid + 80f, small);
            }
        }
    }
}
