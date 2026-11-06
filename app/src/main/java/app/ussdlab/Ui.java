package app.ussdlab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A tiny Material 3 ("Material You") kit built on plain Android views, so the app needs no libraries.
 * On Android 12+ colours follow the wallpaper; otherwise a calm blue palette is used.
 */
final class Ui {

    final Context c;
    final boolean dark;
    final int primary, onPrimary, primaryContainer, onPrimaryContainer;
    final int secondaryContainer, onSecondaryContainer, tertiaryContainer, onTertiaryContainer;
    final int surface, surfaceLow, surfaceContainer, surfaceHigh, onSurface, onSurfaceVariant, outline, outlineVariant;
    final int error, errorContainer, onErrorContainer, success, successContainer, onSuccessContainer;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    Ui(Context c) {
        this.c = c;
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT >= 31) {
            if (!dark) {
                primary = sys(android.R.color.system_accent1_600);
                onPrimary = sys(android.R.color.system_accent1_0);
                primaryContainer = sys(android.R.color.system_accent1_100);
                onPrimaryContainer = sys(android.R.color.system_accent1_900);
                secondaryContainer = sys(android.R.color.system_accent2_100);
                onSecondaryContainer = sys(android.R.color.system_accent2_900);
                tertiaryContainer = sys(android.R.color.system_accent3_100);
                onTertiaryContainer = sys(android.R.color.system_accent3_900);
                surface = sys(android.R.color.system_neutral1_10);
                surfaceLow = sys(android.R.color.system_neutral1_50);
                surfaceContainer = sys(android.R.color.system_neutral2_50);
                surfaceHigh = sys(android.R.color.system_neutral2_100);
                onSurface = sys(android.R.color.system_neutral1_900);
                onSurfaceVariant = sys(android.R.color.system_neutral2_700);
                outline = sys(android.R.color.system_neutral2_500);
                outlineVariant = sys(android.R.color.system_neutral2_200);
            } else {
                primary = sys(android.R.color.system_accent1_200);
                onPrimary = sys(android.R.color.system_accent1_800);
                primaryContainer = sys(android.R.color.system_accent1_700);
                onPrimaryContainer = sys(android.R.color.system_accent1_100);
                secondaryContainer = sys(android.R.color.system_accent2_700);
                onSecondaryContainer = sys(android.R.color.system_accent2_100);
                tertiaryContainer = sys(android.R.color.system_accent3_700);
                onTertiaryContainer = sys(android.R.color.system_accent3_100);
                surface = sys(android.R.color.system_neutral1_900);
                surfaceLow = sys(android.R.color.system_neutral1_900);
                surfaceContainer = sys(android.R.color.system_neutral1_800);
                surfaceHigh = sys(android.R.color.system_neutral2_800);
                onSurface = sys(android.R.color.system_neutral1_100);
                onSurfaceVariant = sys(android.R.color.system_neutral2_200);
                outline = sys(android.R.color.system_neutral2_400);
                outlineVariant = sys(android.R.color.system_neutral2_700);
            }
        } else if (!dark) {
            primary = 0xFF3755C3; onPrimary = 0xFFFFFFFF; primaryContainer = 0xFFDDE1FF; onPrimaryContainer = 0xFF001257;
            secondaryContainer = 0xFFDFE1F9; onSecondaryContainer = 0xFF171B2C;
            tertiaryContainer = 0xFFFED7F1; onTertiaryContainer = 0xFF2F1129;
            surface = 0xFFFBF8FF; surfaceLow = 0xFFF5F2FA; surfaceContainer = 0xFFEFEDF4; surfaceHigh = 0xFFE9E7EF;
            onSurface = 0xFF1B1B21; onSurfaceVariant = 0xFF45464F; outline = 0xFF767680; outlineVariant = 0xFFC6C5D0;
        } else {
            primary = 0xFFB8C4FF; onPrimary = 0xFF002388; primaryContainer = 0xFF1A3BAA; onPrimaryContainer = 0xFFDDE1FF;
            secondaryContainer = 0xFF424659; onSecondaryContainer = 0xFFDFE1F9;
            tertiaryContainer = 0xFF5D3B57; onTertiaryContainer = 0xFFFED7F1;
            surface = 0xFF131318; surfaceLow = 0xFF1B1B21; surfaceContainer = 0xFF1F1F25; surfaceHigh = 0xFF2A292F;
            onSurface = 0xFFE4E1E9; onSurfaceVariant = 0xFFC6C5D0; outline = 0xFF90909A; outlineVariant = 0xFF45464F;
        }
        error = dark ? 0xFFFFB4AB : 0xFFBA1A1A;
        errorContainer = dark ? 0xFF93000A : 0xFFFFDAD6;
        onErrorContainer = dark ? 0xFFFFDAD6 : 0xFF410002;
        success = dark ? 0xFF7DDA9C : 0xFF146C2E;
        successContainer = dark ? 0xFF0F5223 : 0xFFC4EED0;
        onSuccessContainer = dark ? 0xFFC4EED0 : 0xFF002109;
    }

    private int sys(int id) { return c.getColor(id); }

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    // ------------------------------------------------------------------ text

    TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    TextView display(CharSequence s) {
        TextView t = text(s, 32, onSurface);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setLetterSpacing(-0.01f);
        return t;
    }

    TextView title(CharSequence s) {
        TextView t = text(s, 20, onSurface);
        t.setTypeface(medium);
        return t;
    }

    TextView titleSmall(CharSequence s) {
        TextView t = text(s, 16, onSurface);
        t.setTypeface(medium);
        return t;
    }

    TextView body(CharSequence s) { return text(s, 15, onSurfaceVariant); }

    TextView small(CharSequence s) { return text(s, 13, onSurfaceVariant); }

    TextView label(CharSequence s) {
        TextView t = text(s, 14, primary);
        t.setTypeface(medium);
        t.setPadding(dp(4), dp(24), dp(4), dp(8));
        return t;
    }

    TextView mono(CharSequence s) {
        TextView t = text(s, 12, onSurfaceVariant);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        return t;
    }

    // --------------------------------------------------------------- shapes

    GradientDrawable shape(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    Drawable ripple(int color, float radiusDp) {
        int r = (onSurface & 0x00FFFFFF) | 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(r), shape(color, radiusDp), shape(0xFFFFFFFF, radiusDp));
    }

    LinearLayout col() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    LinearLayout card() { return card(surfaceContainer); }

    LinearLayout card(int color) {
        LinearLayout l = col();
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setBackground(shape(color, 24));
        return l;
    }

    LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams full(int topDp) {
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = dp(topDp);
        return lp;
    }

    LinearLayout.LayoutParams wrap(int startDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(startDp));
        return lp;
    }

    static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    View space(int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return v;
    }

    // -------------------------------------------------------------- buttons

    private TextView baseButton(String s, int bg, int fg, View.OnClickListener l) {
        TextView b = text(s, 15, fg);
        b.setTypeface(medium);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(22), dp(10), dp(22), dp(10));
        b.setBackground(ripple(bg, 22));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    TextView filled(String s, View.OnClickListener l) { return baseButton(s, primary, onPrimary, l); }

    TextView tonal(String s, View.OnClickListener l) { return baseButton(s, secondaryContainer, onSecondaryContainer, l); }

    TextView textButton(String s, View.OnClickListener l) {
        TextView b = baseButton(s, 0x00000000, primary, l);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        return b;
    }

    TextView outlined(String s, View.OnClickListener l) {
        TextView b = baseButton(s, 0x00000000, primary, l);
        GradientDrawable d = shape(0x00000000, 22);
        d.setStroke(dp(1), outline);
        int r = (onSurface & 0x00FFFFFF) | 0x22000000;
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(r), d, shape(0xFFFFFFFF, 22)));
        return b;
    }

    /** Selectable chip. */
    TextView chip(String s, boolean selected, View.OnClickListener l) {
        TextView t = text((selected ? "✓  " : "") + s, 14, selected ? onSecondaryContainer : onSurfaceVariant);
        t.setTypeface(medium);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(36));
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable d = shape(selected ? secondaryContainer : 0x00000000, 10);
        if (!selected) d.setStroke(dp(1), outline);
        int r = (onSurface & 0x00FFFFFF) | 0x22000000;
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(r), d, shape(0xFFFFFFFF, 10)));
        t.setOnClickListener(l);
        return t;
    }

    /** Small status pill, e.g. "Ready" or "Needed". */
    TextView badge(String s, int bg, int fg) {
        TextView t = text(s, 12, fg);
        t.setTypeface(medium);
        t.setPadding(dp(10), dp(3), dp(10), dp(3));
        t.setBackground(shape(bg, 8));
        return t;
    }

    HorizontalScrollView chipRow(View... chips) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        LinearLayout r = row();
        for (int i = 0; i < chips.length; i++) r.addView(chips[i], wrap(i == 0 ? 0 : 8));
        h.addView(r);
        return h;
    }

    ImageView icon(int res, int tint, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(tint));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return i;
    }

    // ---------------------------------------------------------------- fields

    EditText field(String hint, int type) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextColor(onSurface);
        e.setHintTextColor(outline);
        e.setTextSize(17);
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable d = shape(surface, 14);
        d.setStroke(dp(1), outline);
        e.setBackground(d);
        return e;
    }

    EditText codeField(String hint) {
        EditText e = field(hint, InputType.TYPE_CLASS_PHONE);
        e.setTextSize(22);
        e.setTypeface(medium);
        e.setLetterSpacing(0.05f);
        return e;
    }

    /** Label above a field and plain-language help below it. */
    LinearLayout labelled(String label, View field, String help) {
        LinearLayout l = col();
        TextView lt = text(label, 14, onSurface);
        lt.setTypeface(medium);
        lt.setPadding(dp(4), dp(14), 0, dp(6));
        l.addView(lt);
        l.addView(field, full());
        if (help != null) {
            TextView h = small(help);
            h.setPadding(dp(4), dp(6), dp(4), 0);
            l.addView(h);
        }
        return l;
    }
}
