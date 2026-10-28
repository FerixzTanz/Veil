package app.veil.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import app.veil.R;

/**
 * Every dialog in the app comes through here.
 *
 * <p>The head unit's framework dialog hides its whole button bar, and theme
 * attributes resolve differently from one dialog path to the next. So the app
 * draws the buttons itself the moment the dialog shows: a solid orange primary
 * (red when the action cannot be undone), grey secondaries, 52 dp blocks,
 * colours taken straight from the semantic day/night palette.</p>
 *
 * <p>Adapted from the CamDialogs class in the author's CAMCORDER app (GPL-3.0).
 * DialogStyleTest keeps every other dialog path out of the code base.</p>
 */
public final class VeilDialogs {

    private static final int BUTTON_HEIGHT_DP = 52;
    private static final int BUTTON_MIN_WIDTH_DP = 120;
    private static final int BUTTON_PADDING_DP = 24;
    private static final int BUTTON_GAP_DP = 12;
    private static final int BUTTON_BAR_PADDING_DP = 12;
    private static final int CORNER_DP = 10;

    private VeilDialogs() {
    }

    /** A text field for a dialog, in the app's own look. */
    public static EditText input(Context context) {
        EditText input = new EditText(context);
        float density = context.getResources().getDisplayMetrics().density;
        input.setBackgroundResource(R.drawable.bg_card);
        input.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_row));
        int horizontal = Math.round(16 * density);
        int vertical = Math.round(14 * density);
        input.setPadding(horizontal, vertical, horizontal, vertical);
        input.setMinHeight(Math.round(56 * density));
        return input;
    }

    /** Gives a dialog's custom view the same side margins as its title and message. */
    public static View wrap(View content) {
        Context context = content.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        FrameLayout box = new FrameLayout(context);
        int side = Math.round(24 * density);
        box.setPadding(side, Math.round(8 * density), side, 0);
        box.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** A builder already carrying the app's dialog theme. */
    public static MaterialAlertDialogBuilder builder(Context context) {
        return new MaterialAlertDialogBuilder(context, R.style.Theme_Veil_MaterialAlertDialog);
    }

    /** Title, message, one primary action and Cancel. */
    public static AlertDialog confirm(Context context, CharSequence title, CharSequence message,
                                      CharSequence action, Runnable onConfirm) {
        return show(builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(action, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.action_cancel, null), false);
    }

    /** Same, for an action that cannot be undone: the primary button is red. */
    public static AlertDialog confirmDestructive(Context context, CharSequence title, CharSequence message,
                                                 CharSequence action, Runnable onConfirm) {
        return show(builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(action, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.action_cancel, null), true);
    }

    /** A message with a single OK. */
    public static AlertDialog inform(Context context, CharSequence title, CharSequence message) {
        return show(builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_ok, null), false);
    }

    public static AlertDialog show(AlertDialog.Builder builder, boolean destructive) {
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> paintButtons(dialog, destructive));
        dialog.show();
        return dialog;
    }

    // ------------------------------------------------------------------ painting

    private static void paintButtons(Dialog dialog, boolean destructive) {
        paint(button(dialog, DialogInterface.BUTTON_POSITIVE),
                destructive ? R.color.danger : R.color.energy, R.color.on_energy, true);
        paint(button(dialog, DialogInterface.BUTTON_NEGATIVE),
                R.color.sunken, R.color.text_primary, false);
        paint(button(dialog, DialogInterface.BUTTON_NEUTRAL),
                R.color.sunken, R.color.text_primary, false);
        makeRoomForButtons(dialog);
    }

    /** The button bar is laid out for a line of text; the blocks need their own room. */
    private static void makeRoomForButtons(Dialog dialog) {
        Button any = button(dialog, DialogInterface.BUTTON_POSITIVE);
        if (any == null) {
            any = button(dialog, DialogInterface.BUTTON_NEGATIVE);
        }
        if (any == null || !(any.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup bar = (ViewGroup) any.getParent();
        float density = bar.getResources().getDisplayMetrics().density;
        int vertical = Math.round(BUTTON_BAR_PADDING_DP * density);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setPadding(bar.getPaddingLeft(), vertical, bar.getPaddingRight(), vertical);
        bar.setMinimumHeight(Math.round((BUTTON_HEIGHT_DP + 2 * BUTTON_BAR_PADDING_DP) * density));
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params != null && params.height >= 0) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            bar.setLayoutParams(params);
        }
        bar.requestLayout();
    }

    @Nullable
    private static Button button(Dialog dialog, int which) {
        if (dialog instanceof AlertDialog) {
            return ((AlertDialog) dialog).getButton(which);
        }
        return null;
    }

    private static void paint(@Nullable Button button, int backgroundRes, int textRes, boolean primary) {
        if (button == null || button.getVisibility() != View.VISIBLE) {
            return;
        }
        Context context = button.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int background = ContextCompat.getColor(context, backgroundRes);
        int ripple = ContextCompat.getColor(context, R.color.ripple);

        button.setTextColor(ContextCompat.getColor(context, textRes));
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimension(R.dimen.text_row));
        button.setTypeface(Typeface.create(button.getTypeface(), primary ? Typeface.BOLD : Typeface.NORMAL));
        button.setSingleLine(false);
        button.setMaxLines(1);
        button.setHorizontallyScrolling(false);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER);
        button.setMaxWidth(Integer.MAX_VALUE);

        if (button instanceof MaterialButton) {
            MaterialButton material = (MaterialButton) button;
            material.setBackgroundTintList(ColorStateList.valueOf(background));
            material.setRippleColor(ColorStateList.valueOf(ripple));
            material.setCornerRadius(Math.round(CORNER_DP * density));
            material.setInsetTop(0);
            material.setInsetBottom(0);
        }

        int height = Math.round(BUTTON_HEIGHT_DP * density);
        int minWidth = Math.round(BUTTON_MIN_WIDTH_DP * density);
        int padding = Math.round(BUTTON_PADDING_DP * density);
        button.setMinHeight(height);
        button.setMinimumHeight(height);
        button.setMinWidth(minWidth);
        button.setMinimumWidth(minWidth);
        button.setPadding(padding, 0, padding, 0);

        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            margins.setMarginStart(Math.round(BUTTON_GAP_DP * density));
            margins.topMargin = Math.round(8 * density);
            margins.bottomMargin = Math.round(8 * density);
            button.setLayoutParams(margins);
        }
    }
}
