package app.veil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import app.veil.airplay.AirPlayService;
import app.veil.dim.DimService;
import app.veil.dim.Overlays;

/** After a reboot: AirPlay comes back if it was left on, and so do the veil and the moon. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        AirPlayService.applySaved(context);
        if (Overlays.canDraw(context)) {
            DimService.sync(context);
        }
    }
}
