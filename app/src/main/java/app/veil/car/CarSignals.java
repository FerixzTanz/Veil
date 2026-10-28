package app.veil.car;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gear and speed from the head unit's own ECARX car interface
 * ({@code com.ecarx.xui.adaptapi.car.Car}), read by reflection and read only. The jar is on the
 * boot class path, so any app may {@code Car.create(context)} without a permission.
 *
 * <p>The two readings Veil needs, out of the forty the dashcam app reads: the gear sensor
 * event ({@link GearCode#SENSOR_GEAR}, subscribed) and the speed sensor value
 * ({@link GearCode#SENSOR_SPEED}, polled every {@link #POLL_MS}, since it changes with every
 * sample). Everything runs on one background thread; listeners are called on the main thread.
 * When the car cannot be read the readings stay null, which {@code DrivingGate} treats as
 * parked. {@link #status()} says why, for the Network page.</p>
 *
 * <p>Distilled from 7xDash's EcarxSource (GPL-3.0), including the lessons recorded there:
 * methods are looked up on the public interfaces (the implementation classes are not
 * public), sensor listeners are registered once per car object because unregistering does
 * not take, and a car object that stops answering is dropped so the next connect runs
 * {@code Car.create} again.</p>
 */
public final class CarSignals {

    public interface Listener {
        void onCar(@Nullable String gear, @Nullable Float speedKmh);
    }

    public enum Link { IDLE, CONNECTING, CONNECTED, UNAVAILABLE, FAILED }

    private static final String TAG = "CarSignals";
    static final String CAR_CLASS = "com.ecarx.xui.adaptapi.car.Car";
    static final long POLL_MS = 200L;
    private static final long RETRY_MS = 15_000L;

    private static CarSignals instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final List<String> users = new ArrayList<>();
    private Handler worker;
    private Context app;

    private volatile Link link = Link.IDLE;
    private volatile String status = "not asked yet";
    @Nullable
    private volatile String gear;
    @Nullable
    private volatile Float speedKmh;

    private Object car;
    private Object sensor;
    private Method getSensorEvent;
    private Method getSensorLatestValue;
    private Object sensorProxy;
    private boolean subscribed;
    private int emptyPolls;

    private final Runnable poll = this::pollOnce;
    private final Runnable connectTask = this::connect;

    private CarSignals() {
    }

    public static synchronized CarSignals get() {
        if (instance == null) {
            instance = new CarSignals();
        }
        return instance;
    }

    @Nullable
    public String gear() {
        return gear;
    }

    @Nullable
    public Float speedKmh() {
        return speedKmh;
    }

    public Link link() {
        return link;
    }

    /** One line on the car link, for the Network page. */
    public String status() {
        return status;
    }

    public void addListener(Listener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /** Start reading while at least one user needs it. */
    public void acquire(Context context, String who) {
        synchronized (users) {
            app = context.getApplicationContext();
            if (!users.contains(who)) {
                users.add(who);
            }
            if (worker == null) {
                HandlerThread t = new HandlerThread("car-signals");
                t.start();
                worker = new Handler(t.getLooper());
                worker.post(connectTask);
            }
        }
    }

    public void release(String who) {
        synchronized (users) {
            users.remove(who);
            if (users.isEmpty() && worker != null) {
                Handler w = worker;
                worker = null;
                w.post(() -> {
                    w.removeCallbacksAndMessages(null);
                    subscribed = false;
                    sensor = null;
                    getSensorEvent = null;
                    getSensorLatestValue = null;
                    link = Link.IDLE;
                    status = "stopped";
                    w.getLooper().quitSafely();
                });
            }
        }
    }

    // ------------------------------------------------------------------ worker thread

    private void connect() {
        Handler w = worker;
        if (w == null || app == null) {
            return;
        }
        link = Link.CONNECTING;
        try {
            Class<?> carClass = Class.forName(CAR_CLASS);
            if (car == null) {
                Method create = match(carClass.getMethods(), "create", Context.class);
                if (create == null) {
                    fail(Link.UNAVAILABLE, "no Car.create(Context) on this head unit");
                    return;
                }
                Object created = create.invoke(null, app);
                if (created == null) {
                    fail(Link.FAILED, "Car.create returned null (car service not up yet?)");
                    w.postDelayed(connectTask, RETRY_MS);
                    return;
                }
                car = created;
            }
            sensor = call(car, "getSensorManager");
            if (sensor == null) {
                fail(Link.FAILED, "no sensor manager");
                car = null;
                w.postDelayed(connectTask, RETRY_MS);
                return;
            }
            getSensorEvent = findMethod(sensor, "getSensorEvent", int.class);
            getSensorLatestValue = findMethod(sensor, "getSensorLatestValue", int.class);
            if (getSensorEvent == null && getSensorLatestValue == null) {
                fail(Link.FAILED, "sensor manager has no getSensorEvent / getSensorLatestValue");
                car = null;
                w.postDelayed(connectTask, RETRY_MS);
                return;
            }
            String sub = subscribeGear();
            boolean answered = pollOnce(false);
            if (!answered) {
                fail(Link.FAILED, "connected but no reading answered");
                car = null;
                w.postDelayed(connectTask, RETRY_MS);
                return;
            }
            link = Link.CONNECTED;
            status = "connected: gear " + (gear == null ? "?" : gear) + ", speed "
                    + (speedKmh == null ? "?" : String.format(java.util.Locale.US, "%.0f km/h", speedKmh))
                    + "; " + sub;
            Log.i(TAG, status);
            w.postDelayed(poll, POLL_MS);
        } catch (ClassNotFoundException e) {
            fail(Link.UNAVAILABLE, "no " + CAR_CLASS + " on this head unit");
        } catch (Throwable t) {
            fail(Link.FAILED, "connect failed: " + describe(t));
            car = null;
            w.postDelayed(connectTask, RETRY_MS);
        }
    }

    private void fail(Link result, String why) {
        link = result;
        status = why;
        Log.w(TAG, why);
        publish(null, null);
    }

    private String subscribeGear() {
        if (subscribed) {
            return "gear listener kept";
        }
        try {
            Method register = findMethodWithInterfaceFirst(sensor, "registerListener", int.class);
            if (register == null) {
                return "no registerListener (polling gear too)";
            }
            Class<?> type = register.getParameterTypes()[0];
            if (sensorProxy == null) {
                sensorProxy = Proxy.newProxyInstance(CarSignals.class.getClassLoader(),
                        new Class<?>[] {type}, new SensorCallback());
            }
            Object r = register.invoke(sensor, sensorProxy, GearCode.SENSOR_GEAR);
            subscribed = !(r instanceof Boolean) || (Boolean) r;
            return subscribed ? "gear listener on" : "gear listener refused (polling gear too)";
        } catch (Throwable t) {
            return "gear listener failed: " + describe(t);
        }
    }

    private void pollOnce() {
        Handler w = worker;
        if (w == null || link != Link.CONNECTED) {
            return;
        }
        boolean answered = pollOnce(true);
        emptyPolls = answered ? 0 : emptyPolls + 1;
        if (emptyPolls > 25) {
            // five seconds of nothing: the car service is gone; reconnect with a fresh object
            fail(Link.FAILED, "car stopped answering");
            car = null;
            subscribed = false;
            w.postDelayed(connectTask, RETRY_MS);
            return;
        }
        w.postDelayed(poll, POLL_MS);
    }

    /** Reads speed, and gear too (the listener may be late or refused). True when anything answered. */
    private boolean pollOnce(boolean publishChanges) {
        boolean answered = false;
        Float speed = null;
        String g = gear;
        try {
            if (getSensorLatestValue != null) {
                Object v = getSensorLatestValue.invoke(sensor, GearCode.SENSOR_SPEED);
                if (v instanceof Number) {
                    answered = true;
                    speed = GearCode.kmh((Number) v);
                }
            }
            if (getSensorEvent != null) {
                Object v = getSensorEvent.invoke(sensor, GearCode.SENSOR_GEAR);
                if (v instanceof Number) {
                    answered = true;
                    String letter = GearCode.letter(((Number) v).intValue());
                    if (letter != null) {
                        g = letter;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "read failed: " + describe(t));
        }
        boolean changed = !same(speed, speedKmh) || !same(g, gear);
        speedKmh = speed;
        gear = g;
        if (changed || !publishChanges) {
            publish(g, speed);
        }
        return answered;
    }

    private void onGearEvent(int value) {
        String letter = GearCode.letter(value);
        if (letter != null && !letter.equals(gear)) {
            gear = letter;
            publish(letter, speedKmh);
        }
    }

    private void publish(@Nullable String g, @Nullable Float speed) {
        main.post(() -> {
            List<Listener> copy;
            synchronized (listeners) {
                copy = new ArrayList<>(listeners);
            }
            for (Listener l : copy) {
                l.onCar(g, speed);
            }
        });
    }

    private static boolean same(@Nullable Object a, @Nullable Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /** The sensor callback: gear events in, anything else answered with a zero value. */
    private final class SensorCallback implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("onSensorEventChanged".equals(name) && args != null && args.length == 2
                    && args[0] instanceof Integer && args[1] instanceof Number) {
                if ((Integer) args[0] == GearCode.SENSOR_GEAR) {
                    int value = ((Number) args[1]).intValue();
                    Handler w = worker;
                    if (w != null) {
                        w.post(() -> onGearEvent(value));
                    }
                }
                return null;
            }
            if ("toString".equals(name)) {
                return "CarSignals.SensorCallback";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return args != null && args.length == 1 && proxy == args[0];
            }
            Class<?> r = method.getReturnType();
            if (r == boolean.class) {
                return false;
            }
            if (r == int.class || r == short.class || r == byte.class) {
                return 0;
            }
            if (r == long.class) {
                return 0L;
            }
            if (r == float.class) {
                return 0f;
            }
            if (r == double.class) {
                return 0d;
            }
            if (r == char.class) {
                return '\0';
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ reflection helpers (from 7xDash)

    @Nullable
    private static Object call(Object target, String name) {
        Method m = findMethod(target, name);
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(target);
        } catch (Throwable t) {
            Log.w(TAG, name + " failed: " + describe(t));
            return null;
        }
    }

    /** Public interfaces first: the implementation classes are not public and refuse the call. */
    @Nullable
    static Method findMethod(Object target, String name, Class<?>... params) {
        for (Class<?> type : publicInterfaces(target)) {
            Method m = match(type.getMethods(), name, params);
            if (m != null) {
                return m;
            }
        }
        Method m = match(target.getClass().getMethods(), name, params);
        if (m != null) {
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
                // try anyway
            }
        }
        return m;
    }

    /** "(some interface, second)": the sensor manager's registerListener(listener, type). */
    @Nullable
    private static Method findMethodWithInterfaceFirst(Object target, String name, Class<?> second) {
        for (Class<?> type : publicInterfaces(target)) {
            for (Method m : type.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals(name) && p.length == 2 && p[0].isInterface() && p[1] == second) {
                    return m;
                }
            }
        }
        return null;
    }

    private static List<Class<?>> publicInterfaces(Object target) {
        List<Class<?>> types = new ArrayList<>();
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            collectInterfaces(c.getInterfaces(), types);
        }
        List<Class<?>> pub = new ArrayList<>();
        for (Class<?> t : types) {
            if (Modifier.isPublic(t.getModifiers())) {
                pub.add(t);
            }
        }
        return pub;
    }

    private static void collectInterfaces(Class<?>[] interfaces, List<Class<?>> into) {
        for (Class<?> i : interfaces) {
            if (!into.contains(i)) {
                into.add(i);
                collectInterfaces(i.getInterfaces(), into);
            }
        }
    }

    @Nullable
    private static Method match(Method[] methods, String name, Class<?>... params) {
        for (Method m : methods) {
            if (!m.getName().equals(name)) {
                continue;
            }
            Class<?>[] types = m.getParameterTypes();
            if (types.length != params.length) {
                continue;
            }
            boolean same = true;
            for (int i = 0; i < types.length; i++) {
                if (types[i] != params[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return m;
            }
        }
        return null;
    }

    private static String describe(Throwable e) {
        Throwable t = e;
        while ((t instanceof InvocationTargetException || t instanceof UndeclaredThrowableException)
                && t.getCause() != null) {
            t = t.getCause();
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
