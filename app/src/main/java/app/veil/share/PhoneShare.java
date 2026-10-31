package app.veil.share;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.util.List;

import app.veil.R;
import app.veil.airplay.AirPlayLog;
import app.veil.airplay.NetworkReport;
import app.veil.ui.VeilDialogs;

/**
 * Send to phone: a QR code the phone scans, a download page in its browser, nothing to install.
 *
 * <p>The file server runs only while the dialog is open, and at most {@link #MAX_OPEN_MS}; it
 * also stops when the page goes to the background. The dialog follows the phone: waiting,
 * connected, sending, sent, and after {@link #UNREACHABLE_AFTER_MS} without a request, what to
 * check. From CAMCORDER's PhoneShare (the author's app, GPL-3.0), on Veil's own dialogs.</p>
 */
public final class PhoneShare {

    private static final long MAX_OPEN_MS = 10L * 60 * 1000;
    private static final long UNREACHABLE_AFTER_MS = 20_000L;
    private static final int QR_SIZE_DP = 260;

    private PhoneShare() {
    }

    /** Opens the dialog for this file; {@code owner} closes it when the page stops. */
    public static void show(Context context, LifecycleOwner owner, File file) {
        List<ShareAddresses.Endpoint> all = ShareAddresses.all();
        List<ShareAddresses.Endpoint> endpoints = ShareAddresses.usable(all, NetworkReport.lastIphone());
        if (endpoints.isEmpty()) {
            AirPlayLog.i("share: no address a phone could reach; links: " + ShareAddresses.describe(all));
            VeilDialogs.inform(context, context.getString(R.string.share_no_network_title),
                    context.getString(R.string.share_no_network_message, ShareAddresses.describe(all)));
            return;
        }
        FileShareServer server = new FileShareServer();
        try {
            server.start();
            server.share(file);
        } catch (Exception e) {
            AirPlayLog.w("share: server did not start: " + e);
            VeilDialogs.inform(context, context.getString(R.string.share_title),
                    context.getString(R.string.share_failed));
            return;
        }
        AirPlayLog.i("share: " + file.getName() + " (" + FileShareServer.readableSize(file.length())
                + ") on port " + server.getListeningPort() + ", addresses " + ShareAddresses.describe(endpoints));
        new Presenter(context, owner, server, endpoints, file).show();
    }

    private static final class Presenter {
        private final Context context;
        private final LifecycleOwner owner;
        private final FileShareServer server;
        private final List<ShareAddresses.Endpoint> endpoints;
        private final File file;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Runnable unreachable = this::onUnreachable;

        private int index;
        /** A request from the phone has arrived: no more "cannot reach". */
        private boolean reached;
        /** The phone opened the right link: a later wrong one (another device) does not cover the progress. */
        private boolean opened;
        private boolean stopped;
        private AlertDialog dialog;
        private ImageView qr;
        private TextView qrFailed;
        private TextView status;
        private TextView address;

        private final DefaultLifecycleObserver pageStopped = new DefaultLifecycleObserver() {
            @Override
            public void onStop(@NonNull LifecycleOwner o) {
                close();
            }
        };

        Presenter(Context context, LifecycleOwner owner, FileShareServer server,
                  List<ShareAddresses.Endpoint> endpoints, File file) {
            this.context = context;
            this.owner = owner;
            this.server = server;
            this.endpoints = endpoints;
            this.file = file;
        }

        void show() {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);

            qr = new ImageView(context);
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(QR_SIZE_DP), dp(QR_SIZE_DP));
            qrParams.gravity = Gravity.CENTER_HORIZONTAL;
            root.addView(qr, qrParams);

            qrFailed = text(context.getString(R.string.share_qr_failed), R.dimen.text_caption, true);
            qrFailed.setVisibility(View.GONE);
            root.addView(qrFailed);
            status = text("", R.dimen.text_body, false);
            root.addView(status);
            root.addView(text(context.getString(R.string.share_steps), R.dimen.text_caption, false));
            address = text("", R.dimen.text_micro, true);
            address.setTextIsSelectable(true);
            root.addView(address);
            root.addView(text(context.getString(R.string.share_keep_open), R.dimen.text_micro, true));

            ScrollView scroll = new ScrollView(context);
            scroll.addView(root);

            // the first guess may be an address the phone cannot reach: offer the others
            // (no text, no button)
            dialog = VeilDialogs.show(VeilDialogs.builder(context)
                    .setTitle(R.string.share_title)
                    .setView(VeilDialogs.wrap(scroll))
                    .setPositiveButton(R.string.action_close, null)
                    .setNeutralButton(endpoints.size() > 1
                            ? context.getString(R.string.share_other_address) : null, null)
                    .setOnDismissListener(d -> stop()), false);
            if (endpoints.size() > 1) {
                // its own click listener, so switching does not close the dialog
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    index = (index + 1) % endpoints.size();
                    render();
                });
            }
            server.setListener(event -> main.post(() -> onEvent(event)));
            owner.getLifecycle().addObserver(pageStopped);
            main.postDelayed(this::close, MAX_OPEN_MS);
            render();
        }

        private void render() {
            ShareAddresses.Endpoint endpoint = endpoints.get(index);
            String url = server.urlFor(endpoint.address);
            Bitmap bitmap = QrCode.encode(url, dp(QR_SIZE_DP));
            qr.setImageBitmap(bitmap);
            qrFailed.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
            address.setText(context.getString(R.string.share_address, url == null ? "" : url,
                    file.getName(), FileShareServer.readableSize(file.length())));
            if (!reached) {
                status.setText(context.getString(R.string.share_status_waiting, label(endpoint)));
                main.removeCallbacks(unreachable);
                main.postDelayed(unreachable, UNREACHABLE_AFTER_MS);
            }
            // never the token: it is the key to the file
            AirPlayLog.i("share: QR shows http://" + endpoint.address + ":" + server.getListeningPort()
                    + "/... (" + endpoint.interfaceName + ")" + (bitmap == null ? ", QR not drawn" : ""));
        }

        private void onUnreachable() {
            if (!reached && !stopped) {
                status.setText(context.getString(R.string.share_status_unreachable,
                        label(endpoints.get(index))));
                AirPlayLog.i("share: no request from the phone after "
                        + UNREACHABLE_AFTER_MS / 1000 + " s on " + endpoints.get(index).address);
            }
        }

        /** Main thread. */
        private void onEvent(FileShareServer.Event event) {
            if (stopped) {
                return;
            }
            reached = true;
            main.removeCallbacks(unreachable);
            switch (event) {
                case PAGE_OPENED:
                    opened = true;
                    status.setText(R.string.share_status_connected);
                    break;
                case DOWNLOAD_STARTED:
                    opened = true;
                    status.setText(R.string.share_status_sending);
                    break;
                case DOWNLOAD_DONE:
                    status.setText(R.string.share_status_done);
                    break;
                case DOWNLOAD_STOPPED:
                    status.setText(R.string.share_status_stopped);
                    break;
                case WRONG_LINK:
                    if (!opened) {
                        status.setText(R.string.share_status_wrong_link);
                    }
                    break;
                default:
                    break;
            }
        }

        private void close() {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            } else {
                stop();
            }
        }

        /** From the dismiss, the page stopping or the time limit: whichever is first. */
        private void stop() {
            if (stopped) {
                return;
            }
            stopped = true;
            main.removeCallbacksAndMessages(null);
            owner.getLifecycle().removeObserver(pageStopped);
            // first the listener: stopping cuts a phone still downloading, which is not news
            server.setListener(null);
            server.stop();
            AirPlayLog.i("share: closed" + (reached ? "" : ", the phone never reached the car"));
        }

        /** "192.168.43.1 on wlan2": the address and the interface it is on. */
        private String label(ShareAddresses.Endpoint e) {
            return context.getString(R.string.share_endpoint, e.address, e.interfaceName);
        }

        private TextView text(String content, int sizeDimen, boolean secondary) {
            TextView view = new TextView(context);
            view.setText(content);
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(sizeDimen));
            view.setTextColor(ContextCompat.getColor(context, secondary ? R.color.text_secondary : R.color.text_primary));
            view.setGravity(Gravity.CENTER_HORIZONTAL);
            view.setPadding(0, dp(10), 0, 0);
            view.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return view;
        }

        private int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
