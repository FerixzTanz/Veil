package app.veil.cluster;

import android.content.Context;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Asks the head unit to show Android's cluster screen on the instrument cluster.
 *
 * <p>The cluster is an ordinary second screen that any app can draw on, but ECARX clusters keep
 * their own dials over it until the head unit is told to "project" (DiPlay measured this on a
 * Geely head unit; CAMCORDER's 2.15.0 test on the 7X opened a window there that never showed).
 * Zeekr's own SDK has the switch: {@code com.ecarx.xui.adaptapi.diminteraction.DimInteraction},
 * {@code dimProjectionScreenRequest(action, x, y, width, height)} with the actions below, and a
 * callback that reports the cluster's state. The class is on the boot class path; CAMCORDER uses
 * it for the steering-wheel keys without any permission. Everything here is by reflection and every
 * step is logged. No root, no ADB, no system setting is changed.</p>
 */
final class ClusterProjection {

    static final String CLASS = "com.ecarx.xui.adaptapi.diminteraction.DimInteraction";
    static final String CALLBACK_CLASS = CLASS + "$IDimProjectionScreenCallback";

    /** {@code PROJECT_SCREEN_ACTION_TYPE_*} in the 6.7.0 SDK. */
    static final int ACTION_REQUEST = 1;
    static final int ACTION_START = 2;
    static final int ACTION_STOP = 3;
    static final int ACTION_RECOVERY = 4;
    /** {@code PROJECT_SCREEN_STATE_TYPE_*}. */
    static final int STATE_IDLE = 1;
    static final int STATE_IN_PROGRESS = 2;
    static final int STATE_FORCE_STOP = 3;

    /**
     * {@code INaviInteraction.ACTION_DIM_DISPLAY_*}: the cluster's navigation layout, as the car's own
     * navigation app sets it through {@code notifyProjectScreenAction}. The 7X's steering-wheel menu
     * offers only the small window; whether its cluster still knows the full layout, and whether it
     * takes the request from another app, is what the test finds out.
     */
    static final int LAYOUT_OFF = 0x21;
    static final int LAYOUT_SMALL = 0x22;
    static final int LAYOUT_FULL = 0x23;
    static final int LAYOUT_AR = 0x24;
    static final String NAVI_INFO_CLASS = "com.ecarx.xui.adaptapi.diminteraction.INaviInteraction$IProjectionScreenInfo";

    private final Object dim;
    private final Method request;
    private final Method register;
    private final Method unregister;
    private Object callback;
    private final StringBuilder summary = new StringBuilder();

    private ClusterProjection(Object dim, Method request, Method register, Method unregister) {
        this.dim = dim;
        this.request = request;
        this.register = register;
        this.unregister = unregister;
    }

    /**
     * Connects to the cluster interface ({@code DimInteraction.create}, about a second: not on the
     * main thread). Null when this head unit has no such interface or it lacks the projection
     * methods; the reason goes into {@code why}.
     */
    static ClusterProjection connect(Context context, StringBuilder why) {
        try {
            Class<?> cls = Class.forName(CLASS);
            Object dim = cls.getMethod("create", Context.class).invoke(null, context.getApplicationContext());
            if (dim == null) {
                why.append("DimInteraction.create returned null");
                return null;
            }
            Method request = cls.getMethod("dimProjectionScreenRequest",
                    int.class, int.class, int.class, int.class, int.class);
            Class<?> cb = Class.forName(CALLBACK_CLASS);
            Method register = cls.getMethod("registerProjectionScreenEventCallback", cb);
            Method unregister = cls.getMethod("unregisterProjectionScreenEventCallback", cb);
            ClusterProjection out = new ClusterProjection(dim, request, register, unregister);
            ClusterLog.i("cluster interface: " + dim.getClass().getName());
            try {
                Object option = cls.getMethod("getShowPresentationOption").invoke(dim);
                ClusterLog.i("showPresentationOption=" + option);
                out.summary.append("showPresentationOption=").append(option);
            } catch (Throwable t) {
                ClusterLog.i("showPresentationOption: " + describe(t));
            }
            return out;
        } catch (ClassNotFoundException e) {
            why.append("no ").append(CLASS).append(" on this head unit");
            return null;
        } catch (NoSuchMethodException e) {
            why.append("the cluster interface has no projection methods: ").append(e.getMessage());
            return null;
        } catch (Throwable t) {
            why.append("cluster interface failed: ").append(describe(t));
            return null;
        }
    }

    /** Subscribes to the cluster's state reports; a failure is logged, not an error. */
    void listen() {
        try {
            Class<?> cb = Class.forName(CALLBACK_CLASS);
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "onProjectionScreenStateChange":
                        int state = args != null && args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : -1;
                        ClusterLog.i("cluster reports state " + state + describeState(state));
                        synchronized (summary) {
                            summary.append("; state=").append(state);
                        }
                        return null;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return args != null && proxy == args[0];
                    case "toString":
                        return "ClusterProjection.callback";
                    default:
                        return null;
                }
            };
            callback = Proxy.newProxyInstance(cb.getClassLoader(), new Class<?>[]{cb}, handler);
            Object ok = register.invoke(dim, callback);
            ClusterLog.i("state callback registered: " + ok);
        } catch (Throwable t) {
            callback = null;
            ClusterLog.w("state callback not registered: " + describe(t));
        }
    }

    /** Sends one action for the given region; true when the head unit took the call. */
    boolean send(int action, int x, int y, int width, int height) {
        String name = name(action);
        try {
            request.invoke(dim, action, x, y, width, height);
            ClusterLog.i(name + " " + x + "," + y + " " + width + "x" + height + ": sent");
            synchronized (summary) {
                summary.append("; ").append(name).append(" ok");
            }
            return true;
        } catch (Throwable t) {
            ClusterLog.w(name + " failed: " + describe(t));
            synchronized (summary) {
                summary.append("; ").append(name).append(" failed: ").append(describe(t));
            }
            return false;
        }
    }

    /**
     * Asks the cluster for a navigation layout: {@link #LAYOUT_FULL} to try the whole panel,
     * {@link #LAYOUT_SMALL} to put the small window back. The call the car's navigation uses, with a
     * full-panel region. True when the head unit took the call.
     */
    boolean setNaviLayout(int layout, int width, int height) {
        String name = layoutName(layout);
        try {
            Object navi = dim.getClass().getMethod("getNaviInteraction").invoke(dim);
            if (navi == null) {
                ClusterLog.w("layout " + name + ": no navigation interaction");
                note("layout " + name + " unavailable");
                return false;
            }
            Class<?> info = Class.forName(NAVI_INFO_CLASS);
            Object args = Proxy.newProxyInstance(info.getClassLoader(), new Class<?>[]{info}, (proxy, method, a) -> {
                switch (method.getName()) {
                    case "getAction":
                        return layout;
                    case "getType":
                    case "getScreenX":
                    case "getScreenY":
                        return 0;
                    case "getScreenWidth":
                        return width;
                    case "getScreenHeight":
                        return height;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return a != null && proxy == a[0];
                    case "toString":
                        return "ClusterProjection.layout(" + name + ")";
                    default:
                        return null;
                }
            });
            navi.getClass().getMethod("notifyProjectScreenAction", info).invoke(navi, args);
            ClusterLog.i("layout " + name + " " + width + "x" + height + ": sent");
            note("layout " + name + " ok");
            return true;
        } catch (Throwable t) {
            ClusterLog.w("layout " + name + " failed: " + describe(t));
            note("layout " + name + " failed: " + describe(t));
            return false;
        }
    }

    private void note(String step) {
        synchronized (summary) {
            summary.append("; ").append(step);
        }
    }

    static String layoutName(int layout) {
        switch (layout) {
            case LAYOUT_OFF:
                return "OFF";
            case LAYOUT_SMALL:
                return "SMALL";
            case LAYOUT_FULL:
                return "FULL";
            case LAYOUT_AR:
                return "AR";
            default:
                return String.valueOf(layout);
        }
    }

    void close() {
        Object cb = callback;
        callback = null;
        if (cb != null) {
            try {
                unregister.invoke(dim, cb);
            } catch (Throwable ignored) {
                // the test is over either way
            }
        }
    }

    /** One line with every step's outcome, for the page. */
    String summary() {
        synchronized (summary) {
            return summary.toString();
        }
    }

    static String name(int action) {
        switch (action) {
            case ACTION_REQUEST:
                return "REQUEST";
            case ACTION_START:
                return "START";
            case ACTION_STOP:
                return "STOP";
            case ACTION_RECOVERY:
                return "RECOVERY";
            default:
                return String.valueOf(action);
        }
    }

    static String describeState(int state) {
        switch (state) {
            case STATE_IDLE:
                return " (idle)";
            case STATE_IN_PROGRESS:
                return " (in progress)";
            case STATE_FORCE_STOP:
                return " (force stop)";
            default:
                return "";
        }
    }

    static String describe(Throwable t) {
        Throwable e = t;
        while (e instanceof InvocationTargetException && e.getCause() != null) {
            e = e.getCause();
        }
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }
}
