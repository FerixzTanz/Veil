package app.veil.cluster;

import android.content.Context;

import java.lang.reflect.Method;

/**
 * Reads the gear once from the head unit's ECARX vehicle interface
 * ({@code com.ecarx.xui.adaptapi.car.Car}, on the boot class path, no permission needed: CAMCORDER
 * reads its vehicle signals the same way). Read only. {@code Car.create} takes about a second, so
 * this runs off the main thread.
 *
 * <p>The gear is sensor event {@code 0x00200200}; its value is one of four event codes
 * ({@link #letter}). Anything else, or any failure, is "unknown", and the caller decides what to
 * do with that.</p>
 */
public final class GearReader {

    static final String CAR_CLASS = "com.ecarx.xui.adaptapi.car.Car";
    static final int GEAR_SENSOR = 0x00200200;

    private GearReader() {
    }

    /** "P", "R", "N" or "D" for the four event codes the 7X reports, else null. */
    public static String letter(int event) {
        switch (event) {
            case 0x00200230:
                return "P";
            case 0x00200240:
                return "R";
            case 0x00200210:
                return "N";
            case 0x00200220:
                return "D";
            default:
                return null;
        }
    }

    /** The gear letter, or null when it cannot be read; the reason goes into {@code why}. */
    public static String read(Context context, StringBuilder why) {
        try {
            Class<?> carClass = Class.forName(CAR_CLASS);
            Method create = null;
            for (Method m : carClass.getMethods()) {
                if (m.getName().equals("create") && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0].isAssignableFrom(Context.class)) {
                    create = m;
                    break;
                }
            }
            if (create == null) {
                why.append("Car.create not found");
                return null;
            }
            Object car = create.invoke(null, context.getApplicationContext());
            if (car == null) {
                why.append("Car.create returned null");
                return null;
            }
            Object sensors = car.getClass().getMethod("getSensorManager").invoke(car);
            if (sensors == null) {
                why.append("no sensor manager");
                return null;
            }
            Object raw = sensors.getClass().getMethod("getSensorEvent", int.class).invoke(sensors, GEAR_SENSOR);
            if (!(raw instanceof Number)) {
                why.append("gear event is ").append(raw);
                return null;
            }
            String gear = letter(((Number) raw).intValue());
            if (gear == null) {
                why.append(String.format(java.util.Locale.US, "gear event 0x%08X", ((Number) raw).intValue()));
            }
            return gear;
        } catch (ClassNotFoundException e) {
            why.append("no ECARX vehicle interface on this head unit");
            return null;
        } catch (Throwable t) {
            Throwable e = t;
            while (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            why.append(e.getClass().getSimpleName()).append(e.getMessage() == null ? "" : ": " + e.getMessage());
            return null;
        }
    }
}
