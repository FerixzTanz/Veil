package app.veil.dim;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** The "draw over other apps" permission both features need. */
public final class Overlays {

    private Overlays() {
    }

    public static boolean canDraw(Context context) {
        return Settings.canDrawOverlays(context);
    }

    /** The adb line that grants the permission when the head unit has no settings page for it. */
    public static String adbGrant(String packageName) {
        return "adb shell appops set " + packageName + " SYSTEM_ALERT_WINDOW allow";
    }

    /**
     * Opens the system page for the permission. Returns false when the head unit
     * has no such page, in which case the caller shows {@link #adbGrant}.
     */
    public static boolean openPermissionPage(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
