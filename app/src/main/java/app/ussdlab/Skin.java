package app.ussdlab;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

/**
 * The custom USSD interface. It is drawn as an accessibility overlay on top of the phone's USSD
 * pop-up, so the pop-up is still there (and still receives the answers) but you only see this screen.
 */
final class Skin {

    interface Callbacks {
        /** The user answered. For a PIN the array is wiped after use. */
        void onAnswer(char[] value, boolean pin);
        void onCancel();
        void onShowPhone();
        void onDone();
        void onSaveRoute();
    }

    private final Context c;
    private final Ui ui;
    private final WindowManager wm;
    private final Callbacks cb;

    private FrameLayout cover;
    private WindowManager.LayoutParams coverLp;
    private View banner;

    private String title = "";
    private String code = "";
    private final char[] buf = new char[40];
    private int len;
    private boolean pinMode;
    private TextView inputView;

    Skin(Context c, Callbacks cb) {
        this.c = c;
        this.ui = new Ui(c);
        this.wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        this.cb = cb;
    }

    // ------------------------------------------------------------- windows

    private void ensureCover(boolean secure) {
        removeBanner();
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | (secure ? WindowManager.LayoutParams.FLAG_SECURE : 0);
        if (cover == null) {
            cover = new FrameLayout(c);
            cover.setBackgroundColor(ui.surface);
            coverLp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.OPAQUE);
            coverLp.gravity = Gravity.TOP;
            try {
                wm.addView(cover, coverLp);
            } catch (RuntimeException e) {
                cover = null;
            }
        } else if (coverLp.flags != flags) {
            coverLp.flags = flags;
            try {
                wm.updateViewLayout(cover, coverLp);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void setPage(View page, boolean secure) {
        ensureCover(secure);
        if (cover == null) return;
        cover.removeAllViews();
        cover.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void removeCover() {
        if (cover == null) return;
        try {
            wm.removeView(cover);
        } catch (RuntimeException ignored) {
        }
        cover = null;
    }

    private void removeBanner() {
        if (banner == null) return;
        try {
            wm.removeView(banner);
        } catch (RuntimeException ignored) {
        }
        banner = null;
    }

    void hide() {
        wipe();
        removeCover();
        removeBanner();
    }

    boolean coverShowing() { return cover != null; }

    // ---------------------------------------------------------------- frame

    /** Top bar + scrolling body + optional fixed bottom area. */
    private LinearLayout frame(View body, View bottom, boolean showPhoneLink) {
        LinearLayout page = ui.col();
        page.setPadding(ui.dp(8), ui.dp(36), ui.dp(8), ui.dp(16));

        LinearLayout bar = ui.row();
        TextView close = ui.text("✕", 22, ui.onSurface);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ui.ripple(0x00000000, 24));
        close.setOnClickListener(v -> cb.onCancel());
        close.setContentDescription("Cancel");
        bar.addView(close, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        LinearLayout titles = ui.col();
        titles.setPadding(ui.dp(4), 0, 0, 0);
        TextView t = ui.titleSmall(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(t);
        titles.addView(ui.small(code + "  ·  USSD Lab"));
        bar.addView(titles, Ui.weight());
        if (showPhoneLink) {
            TextView phone = ui.textButton("Phone view", v -> cb.onShowPhone());
            bar.addView(phone);
        }
        page.addView(bar);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        LinearLayout inner = ui.col();
        inner.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        inner.addView(body, ui.full());
        sv.addView(inner);
        page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (bottom != null) {
            LinearLayout b = ui.col();
            b.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), 0);
            b.addView(bottom, ui.full());
            page.addView(b);
        }
        return page;
    }

    // ---------------------------------------------------------------- pages

    void start(String title, String code) {
        this.title = title;
        this.code = code;
        progress("Connecting to " + code, "Dialling on your chosen SIM…", -1);
    }

    /** "Working" page: steps are running behind this screen. fraction < 0 = spinner only. */
    void progress(String headline, String detail, float fraction) {
        wipe();
        LinearLayout body = ui.col();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, ui.dp(72), 0, 0);
        ProgressBar spin = new ProgressBar(c);
        body.addView(spin, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)));
        TextView h = ui.title(headline);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, ui.dp(24), 0, ui.dp(8));
        body.addView(h, ui.full());
        TextView d = ui.body(detail);
        d.setGravity(Gravity.CENTER);
        body.addView(d, ui.full());
        if (fraction >= 0) {
            ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress((int) (fraction * 100));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.primary));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.surfaceHigh));
            LinearLayout.LayoutParams lp = ui.full(28);
            lp.leftMargin = lp.rightMargin = ui.dp(32);
            body.addView(bar, lp);
        }
        TextView hint = ui.small("Your answers are being typed into the USSD menu behind this screen.");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(ui.dp(24), ui.dp(32), ui.dp(24), 0);
        body.addView(hint, ui.full());
        setPage(frame(body, null, true), false);
    }

    /**
     * Shows a USSD screen as a clean page: the question, the options as buttons, and a keypad.
     * @param pin show a PIN pad (masked, screenshot-blocked)
     */
    void ask(String screenText, String note, boolean hasInput, boolean pin) {
        wipe();
        pinMode = pin;
        List<StepEngine.Option> options = StepEngine.options(screenText);
        String message = StepEngine.message(screenText);

        LinearLayout body = ui.col();
        if (note != null && !note.isEmpty() && !"Your turn".equals(note) && !"Enter your PIN".equals(note)) {
            TextView n = ui.badge(note, ui.tertiaryContainer, ui.onTertiaryContainer);
            n.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
            body.addView(n, ui.wrap(0));
            body.addView(ui.space(12));
        }

        if (pin) {
            TextView lock = ui.text("🔒", 34, ui.primary);
            lock.setGravity(Gravity.CENTER);
            lock.setPadding(0, ui.dp(12), 0, 0);
            body.addView(lock, ui.full());
            TextView h = ui.title("Enter your PIN");
            h.setGravity(Gravity.CENTER);
            h.setPadding(0, ui.dp(8), 0, ui.dp(6));
            body.addView(h, ui.full());
            TextView m = ui.body(message);
            m.setGravity(Gravity.CENTER);
            m.setMaxLines(6);
            body.addView(m, ui.full());
        } else {
            LinearLayout q = ui.card(ui.surfaceContainer);
            TextView m = ui.text(message.isEmpty() ? "Choose an option" : message, 18, ui.onSurface);
            q.addView(m);
            body.addView(q, ui.full());
        }

        boolean showKeypad = hasInput && (pin || options.isEmpty());
        if (!options.isEmpty() && hasInput && !pin) {
            body.addView(ui.space(12));
            for (StepEngine.Option o : options) body.addView(optionRow(o), ui.full(8));
            TextView other = ui.textButton("Type a different answer", null);
            LinearLayout typeArea = ui.col();
            typeArea.setVisibility(View.GONE);
            typeArea.addView(inputBox(false), ui.full(8));
            typeArea.addView(keypad(), ui.full(8));
            other.setOnClickListener(v -> {
                typeArea.setVisibility(View.VISIBLE);
                other.setVisibility(View.GONE);
            });
            body.addView(other, ui.wrap(0));
            body.addView(typeArea, ui.full());
        }
        View bottom = null;
        if (showKeypad) {
            LinearLayout b = ui.col();
            b.addView(inputBox(pin), ui.full());
            b.addView(keypad(), ui.full(10));
            if (pin) {
                TextView privacy = ui.small("Your PIN goes straight into the USSD menu. It isn't saved, shown or logged.");
                privacy.setGravity(Gravity.CENTER);
                privacy.setPadding(0, ui.dp(10), 0, 0);
                b.addView(privacy, ui.full());
            }
            bottom = b;
        } else if (!hasInput) {
            body.addView(ui.space(16));
            body.addView(ui.filled("OK", v -> cb.onDone()), ui.full());
        }
        setPage(frame(body, bottom, true), pin);
    }

    private View optionRow(StepEngine.Option o) {
        LinearLayout r = ui.row();
        r.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        r.setBackground(ui.ripple(ui.surfaceLow, 18));
        GradientDrawableHolder.stroke(r, ui);
        TextView n = ui.text(o.num, 15, ui.onPrimaryContainer);
        n.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        n.setGravity(Gravity.CENTER);
        n.setBackground(ui.shape(ui.primaryContainer, 18));
        r.addView(n, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        TextView l = ui.text(o.label, 17, ui.onSurface);
        l.setPadding(ui.dp(14), 0, 0, 0);
        r.addView(l, Ui.weight());
        r.addView(ui.text("›", 22, ui.onSurfaceVariant));
        r.setClickable(true);
        r.setContentDescription("Option " + o.num + ": " + o.label);
        r.setOnClickListener(v -> answer(o.num.toCharArray()));
        return r;
    }

    private View inputBox(boolean pin) {
        inputView = ui.text(pin ? "" : "", pin ? 30 : 26, ui.onSurface);
        inputView.setGravity(Gravity.CENTER);
        inputView.setLetterSpacing(pin ? 0.4f : 0.05f);
        inputView.setMinHeight(ui.dp(60));
        inputView.setBackground(ui.shape(ui.surfaceContainer, 18));
        inputView.setHint(pin ? "• • • •" : "Your answer");
        inputView.setHintTextColor(ui.outline);
        refreshInput();
        return inputView;
    }

    private View keypad() {
        LinearLayout pad = ui.col();
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] rowKeys : keys) {
            LinearLayout r = ui.row();
            for (int i = 0; i < 3; i++) {
                final String k = rowKeys[i];
                TextView key = ui.text(k, 24, ui.onSurface);
                key.setGravity(Gravity.CENTER);
                key.setBackground(ui.ripple(ui.surfaceHigh, 18));
                key.setOnClickListener(v -> press(k.charAt(0)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(54), 1);
                if (i > 0) lp.setMarginStart(ui.dp(8));
                r.addView(key, lp);
            }
            pad.addView(r, ui.full(8));
        }
        LinearLayout last = ui.row();
        TextView del = ui.text("⌫", 22, ui.onSurface);
        del.setGravity(Gravity.CENTER);
        del.setContentDescription("Delete");
        del.setBackground(ui.ripple(ui.surfaceContainer, 18));
        del.setOnClickListener(v -> {
            if (len > 0) buf[--len] = '\0';
            refreshInput();
        });
        del.setOnLongClickListener(v -> {
            wipe();
            refreshInput();
            return true;
        });
        last.addView(del, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
        TextView send = ui.filled("Send", v -> {
            if (len == 0) return;
            answer(Arrays.copyOf(buf, len));
        });
        send.setTextSize(17);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ui.dp(54), 2);
        slp.setMarginStart(ui.dp(8));
        last.addView(send, slp);
        pad.addView(last, ui.full(8));
        return pad;
    }

    private void press(char ch) {
        if (len < buf.length) buf[len++] = ch;
        refreshInput();
    }

    private void refreshInput() {
        if (inputView == null) return;
        if (pinMode) {
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < len; i++) dots.append('•');
            inputView.setText(dots);
        } else {
            inputView.setText(new String(buf, 0, len));
        }
    }

    private void answer(char[] value) {
        boolean pin = pinMode;
        wipe();
        cb.onAnswer(value, pin);
    }

    private void wipe() {
        Arrays.fill(buf, '\0');
        len = 0;
        if (inputView != null) inputView.setText("");
    }

    /** Final page with the reply, the amounts / references we found, and next actions. */
    void result(boolean ok, String text, boolean canSaveRoute, int routeSteps) {
        wipe();
        pinMode = false;
        LinearLayout body = ui.col();
        body.setPadding(0, ui.dp(16), 0, 0);
        TextView mark = ui.text(ok ? "✓" : "!", 30, ok ? ui.onSuccessContainer : ui.onErrorContainer);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(ui.shape(ok ? ui.successContainer : ui.errorContainer, 36));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ui.dp(72), ui.dp(72));
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(mark, mlp);
        TextView h = ui.title(ok ? "Done" : "Stopped");
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, ui.dp(14), 0, ui.dp(14));
        body.addView(h, ui.full());
        LinearLayout card = ui.card(ui.surfaceContainer);
        TextView msg = ui.text(text, 17, ui.onSurface);
        msg.setTextIsSelectable(false);
        card.addView(msg);
        List<String> amounts = Reply.amounts(text);
        List<String> refs = Reply.references(text);
        if (!amounts.isEmpty() || !refs.isEmpty()) {
            LinearLayout chips = ui.row();
            chips.setPadding(0, ui.dp(12), 0, 0);
            for (String a : amounts) chips.addView(ui.badge(a, ui.primaryContainer, ui.onPrimaryContainer), ui.wrap(chips.getChildCount() == 0 ? 0 : 6));
            for (String r : refs) chips.addView(ui.badge("Ref " + r, ui.tertiaryContainer, ui.onTertiaryContainer), ui.wrap(chips.getChildCount() == 0 ? 0 : 6));
            card.addView(chips);
        }
        body.addView(card, ui.full());

        LinearLayout bottom = ui.col();
        if (canSaveRoute) {
            TextView save = ui.tonal("Save these " + routeSteps + " steps as an action", v -> cb.onSaveRoute());
            bottom.addView(save, ui.full());
            TextView why = ui.small("Next time it can run this path for you. Your PIN is saved as {PIN}, never the digits.");
            why.setGravity(Gravity.CENTER);
            why.setPadding(0, ui.dp(6), 0, ui.dp(10));
            bottom.addView(why, ui.full());
        }
        bottom.addView(ui.filled("Done", v -> cb.onDone()), ui.full());
        setPage(frame(body, bottom, false), false);
    }

    // --------------------------------------------------------------- banner

    /** Small card at the top, used when the phone's own pop-up should stay visible. */
    void banner(String head, String text, boolean ended, Runnable action) {
        removeCover();
        removeBanner();
        LinearLayout card = ui.card(ui.dark ? ui.surfaceHigh : 0xFF2B2B31);
        card.setElevation(ui.dp(6));
        LinearLayout r = ui.row();
        if (!ended) {
            ProgressBar spin = new ProgressBar(c);
            r.addView(spin, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        }
        TextView t = ui.text(head, 15, 0xFFFFFFFF);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(ended ? 0 : ui.dp(10), 0, 0, 0);
        r.addView(t, Ui.weight());
        TextView act = ui.text(ended ? "Open" : "Stop", 15, ui.dark ? ui.primary : 0xFFB8C4FF);
        act.setPadding(ui.dp(12), ui.dp(6), ui.dp(4), ui.dp(6));
        act.setOnClickListener(v -> action.run());
        r.addView(act);
        card.addView(r);
        TextView b = ui.text(text, 14, 0xFFDADAE0);
        b.setMaxLines(5);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(0, ui.dp(4), 0, 0);
        card.addView(b);
        LinearLayout wrap = ui.col();
        wrap.setPadding(ui.dp(12), ui.dp(40), ui.dp(12), 0);
        wrap.addView(card, ui.full());
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        try {
            wm.addView(wrap, lp);
            banner = wrap;
        } catch (RuntimeException ignored) {
        }
    }

    /** Adds a thin outline to option rows without another drawable class. */
    private static final class GradientDrawableHolder {
        static void stroke(View v, Ui ui) {
            android.graphics.drawable.GradientDrawable d = ui.shape(ui.surfaceLow, 18);
            d.setStroke(ui.dp(1), ui.outlineVariant);
            int r = (ui.onSurface & 0x00FFFFFF) | 0x22000000;
            v.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(r), d, ui.shape(0xFFFFFFFF, 18)));
        }
    }
}
