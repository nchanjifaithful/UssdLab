package app.ussdlab;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** A rounded bottom sheet that slides up from the bottom of the screen. */
final class Sheet {

    private Sheet() {}

    static Dialog show(Activity a, Ui ui, View content) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = ui.col();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ui.surfaceLow);
        float r = ui.dp(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);
        box.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(24));
        View handle = new View(a);
        handle.setBackground(ui.shape(ui.outline, 2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ui.dp(32), ui.dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = ui.dp(14);
        box.addView(handle, hlp);
        ScrollView sv = new ScrollView(a);
        sv.addView(content);
        box.addView(sv, ui.full());
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
        d.show();
        return d;
    }
}
