package app.ussdlab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/**
 * The custom USSD interface: a rounded bottom sheet drawn as an accessibility overlay on top of the
 * phone's USSD pop-up. The pop-up is still there underneath (and receives the answers); a dark
 * backdrop hides it, so you only see this sheet.
 */
final class Skin {

    interface Callbacks {
        /** The user answered. For a PIN the array is wiped after use. */
        void onAnswer(char[] value, boolean pin);
        void onCancel();
        void onShowPhone();
        void onDone();
        /** Save the path just taken as a command. Returns the saved name, or null. */
        String onSaveRoute();
    }

    interface ValueCallback { void onValue(String value); }

    private final Context c;
    private final Ui ui;
    private final WindowManager wm;
    private final Callbacks cb;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private FrameLayout cover;
    private WindowManager.LayoutParams coverLp;
    private View banner;

    private String title = "";
    private String subtitle = "";
    private final char[] buf = new char[40];
    private int len;
    private boolean pinMode;
    private TextView inputView;
    private LinearLayout dots;

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
            cover.setBackgroundColor(0xF20D0E12);
            coverLp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
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
        valueCb = null;
        wipe();
        removeCover();
        removeBanner();
    }

    boolean coverShowing() { return cover != null; }

    // ---------------------------------------------------------------- sheet

    /** Caps a scroll area so the sheet never grows past the screen. */
    private static final class MaxScroll extends ScrollView {
        final int max;

        MaxScroll(Context c, int max) {
            super(c);
            this.max = max;
        }

        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }

    /** Backdrop branding + a floating rounded sheet at the bottom holding body and footer. */
    private void sheet(View body, View footer, boolean secure, boolean showPhoneLink) {
        ensureCover(secure);
        if (cover == null) return;
        cover.removeAllViews();

        LinearLayout brand = ui.col();
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        brand.setPadding(ui.dp(24), ui.dp(72), ui.dp(24), 0);
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.ic_launcher);
        brand.addView(logo, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)));
        TextView name = ui.text("USSD Lab", 18, 0xFFFFFFFF);
        name.setTypeface(medium);
        name.setPadding(0, ui.dp(12), 0, ui.dp(4));
        brand.addView(name);
        TextView tag = ui.text("Your USSD session is running behind this screen", 13, 0xB3FFFFFF);
        tag.setGravity(Gravity.CENTER);
        brand.addView(tag);
        if (showPhoneLink) {
            TextView phone = ui.text("Show the phone's pop-up", 13, 0xFFB8C4FF);
            phone.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
            phone.setBackground(ui.ripple(0x00000000, 20));
            phone.setOnClickListener(v -> cb.onShowPhone());
            brand.addView(phone);
        }
        cover.addView(brand, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout s = ui.col();
        s.setBackground(ui.shape(ui.surfaceLow, 32));
        s.setElevation(ui.dp(12));
        s.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(20));
        View handle = new View(c);
        handle.setBackground(ui.shape(ui.outlineVariant, 2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = ui.dp(12);
        s.addView(handle, hlp);

        LinearLayout head = ui.row();
        LinearLayout titles = ui.col();
        TextView t = ui.titleSmall(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(t);
        titles.addView(ui.small(subtitle));
        head.addView(titles, Ui.weight());
        head.addView(ui.iconButton(R.drawable.ic_close, ui.onSurfaceVariant, ui.surfaceHigh, "Cancel", v -> cb.onCancel()));
        s.addView(head, ui.full());

        int screenH = c.getResources().getDisplayMetrics().heightPixels;
        MaxScroll sv = new MaxScroll(c, (int) (screenH * (footer != null ? 0.32f : 0.55f)));
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout inner = ui.col();
        inner.setPadding(0, ui.dp(14), 0, 0);
        inner.addView(body, ui.full());
        sv.addView(inner);
        s.addView(sv, ui.full());
        if (footer != null) s.addView(footer, ui.full(14));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.leftMargin = lp.rightMargin = ui.dp(10);
        lp.bottomMargin = ui.dp(14);
        cover.addView(s, lp);
    }

    // ---------------------------------------------------------------- pages

    void start(String title, String code, String sim) {
        this.title = title;
        this.subtitle = code + (sim == null ? "" : "  ·  " + sim);
        progress(null, 0, "Connecting", "Dialling " + code + "…");
    }

    void setTitle(String title, String code, String sim) {
        this.title = title;
        this.subtitle = code + (sim == null ? "" : "  ·  " + sim);
    }

    /** Working page: a timeline of the steps being typed behind the sheet. */
    void progress(List<String> steps, int index, String headline, String detail) {
        wipe();
        LinearLayout body = ui.col();
        LinearLayout top = ui.row();
        ProgressBar spin = new ProgressBar(c);
        spin.setIndeterminateTintList(ColorStateList.valueOf(ui.primary));
        top.addView(spin, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        LinearLayout tt = ui.col();
        tt.setPadding(ui.dp(14), 0, 0, 0);
        tt.addView(ui.title(headline));
        if (detail != null) tt.addView(ui.small(detail));
        top.addView(tt, Ui.weight());
        body.addView(top, ui.full());
        if (steps != null && !steps.isEmpty()) {
            LinearLayout list = ui.col();
            list.setPadding(ui.dp(4), ui.dp(16), 0, 0);
            for (int i = 0; i < steps.size(); i++) list.addView(stepRow(steps.get(i), i < index ? 2 : i == index ? 1 : 0));
            body.addView(list, ui.full());
        }
        sheet(body, null, false, true);
    }

    /** state: 0 upcoming, 1 now, 2 done. */
    private View stepRow(String step, int state) {
        LinearLayout r = ui.row();
        r.setPadding(0, ui.dp(7), 0, ui.dp(7));
        FrameLayout dot = new FrameLayout(c);
        int bg = state == 2 ? ui.successContainer : state == 1 ? ui.primaryContainer : ui.surfaceHigh;
        dot.setBackground(ui.shape(bg, 13));
        if (state == 2) {
            dot.addView(ui.icon(R.drawable.ic_check, ui.onSuccessContainer, 16), new FrameLayout.LayoutParams(ui.dp(16), ui.dp(16), Gravity.CENTER));
        } else if (state == 1) {
            View d = new View(c);
            d.setBackground(ui.shape(ui.primary, 5));
            dot.addView(d, new FrameLayout.LayoutParams(ui.dp(10), ui.dp(10), Gravity.CENTER));
        }
        r.addView(dot, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));
        TextView t = ui.text(StepEngine.describe(step), 15, state == 0 ? ui.onSurfaceVariant : ui.onSurface);
        if (state == 1) t.setTypeface(medium);
        t.setPadding(ui.dp(14), 0, 0, 0);
        r.addView(t, Ui.weight());
        return r;
    }

    /** A USSD screen as a clean page: question, options as buttons, keypad or PIN pad. */
    void ask(String screenText, String note, boolean hasInput, boolean pin) {
        wipe();
        pinMode = pin;
        List<StepEngine.Option> options = StepEngine.options(screenText);
        String message = StepEngine.message(screenText);

        LinearLayout body = ui.col();
        if (note != null && !note.isEmpty() && !"Your turn".equals(note) && !"Enter your PIN".equals(note)) {
            LinearLayout n = ui.row();
            n.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
            n.setBackground(ui.shape(ui.tertiaryContainer, 14));
            n.addView(ui.icon(R.drawable.ic_info, ui.onTertiaryContainer, 18));
            TextView nt = ui.text(note, 13, ui.onTertiaryContainer);
            nt.setPadding(ui.dp(8), 0, 0, 0);
            n.addView(nt, Ui.weight());
            body.addView(n, ui.full());
            body.addView(ui.space(12));
        }

        View footer = null;
        if (pin) {
            LinearLayout headRow = ui.row();
            headRow.addView(ui.iconTile(R.drawable.ic_lock, ui.primaryContainer, ui.onPrimaryContainer, 44));
            LinearLayout ht = ui.col();
            ht.setPadding(ui.dp(14), 0, 0, 0);
            ht.addView(ui.title("Enter your PIN"));
            TextView m = ui.small(message);
            m.setMaxLines(4);
            m.setEllipsize(TextUtils.TruncateAt.END);
            ht.addView(m);
            headRow.addView(ht, Ui.weight());
            body.addView(headRow, ui.full());
            LinearLayout f = ui.col();
            dots = ui.row();
            dots.setGravity(Gravity.CENTER);
            dots.setMinimumHeight(ui.dp(40));
            f.addView(dots, ui.full());
            f.addView(keypad("Confirm"), ui.full(8));
            TextView privacy = ui.small("Goes straight into the USSD menu. Never saved, shown or logged.");
            privacy.setGravity(Gravity.CENTER);
            privacy.setPadding(0, ui.dp(10), 0, 0);
            f.addView(privacy, ui.full());
            footer = f;
            refreshInput();
        } else {
            TextView m = ui.text(message.isEmpty() ? "Choose an option" : message, 18, ui.onSurface);
            m.setLineSpacing(0, 1.2f);
            body.addView(m, ui.full());
            if (!options.isEmpty() && hasInput) {
                LinearLayout list = ui.col();
                list.setBackground(ui.shape(ui.surfaceContainer, 22));
                list.setClipToOutline(true);
                for (int i = 0; i < options.size(); i++) {
                    if (i > 0) {
                        View line = new View(c);
                        line.setBackgroundColor(ui.outlineVariant);
                        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                        llp.setMarginStart(ui.dp(64));
                        list.addView(line, llp);
                    }
                    list.addView(optionRow(options.get(i)), ui.full());
                }
                body.addView(list, ui.full(14));
                TextView other = ui.iconText(ui.textButton("Type a different answer", null), R.drawable.ic_dialpad);
                LinearLayout typeArea = ui.col();
                typeArea.setVisibility(View.GONE);
                typeArea.addView(inputBox(), ui.full(4));
                typeArea.addView(keypad("Send"), ui.full(8));
                other.setOnClickListener(v -> {
                    typeArea.setVisibility(View.VISIBLE);
                    other.setVisibility(View.GONE);
                });
                body.addView(other, ui.wrap(0));
                body.addView(typeArea, ui.full());
            } else if (hasInput) {
                LinearLayout f = ui.col();
                f.addView(inputBox(), ui.full());
                f.addView(keypad("Send"), ui.full(8));
                footer = f;
            } else {
                footer = ui.filled("OK", v -> cb.onDone());
            }
        }
        sheet(body, footer, pin, true);
    }

    private View optionRow(StepEngine.Option o) {
        LinearLayout r = ui.row();
        r.setPadding(ui.dp(14), ui.dp(12), ui.dp(10), ui.dp(12));
        r.setMinimumHeight(ui.dp(56));
        r.setBackground(ui.ripple(0x00000000, 0));
        TextView n = ui.text(o.num, 15, ui.onPrimaryContainer);
        n.setTypeface(medium);
        n.setGravity(Gravity.CENTER);
        n.setBackground(ui.shape(ui.primaryContainer, 12));
        r.addView(n, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        TextView l = ui.text(o.label, 16, ui.onSurface);
        l.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        r.addView(l, Ui.weight());
        r.addView(ui.icon(R.drawable.ic_chevron, ui.onSurfaceVariant, 20));
        r.setContentDescription("Option " + o.num + ", " + o.label);
        r.setOnClickListener(v -> answer(o.num.toCharArray()));
        return r;
    }

    private View inputBox() {
        inputView = ui.text("", 26, ui.onSurface);
        inputView.setTypeface(medium);
        inputView.setGravity(Gravity.CENTER);
        inputView.setLetterSpacing(0.04f);
        inputView.setMinHeight(ui.dp(58));
        inputView.setBackground(ui.shape(ui.surfaceContainer, 18));
        inputView.setHint("Your answer");
        inputView.setHintTextColor(ui.outline);
        refreshInput();
        return inputView;
    }

    private static final String[] LETTERS = {"", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};

    private View keypad(String sendLabel) {
        LinearLayout pad = ui.col();
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        LinearLayout r = null;
        for (int i = 0; i < keys.length; i++) {
            if (i % 3 == 0) {
                r = ui.row();
                pad.addView(r, ui.full(i == 0 ? 0 : 8));
            }
            final String k = keys[i];
            LinearLayout key = ui.col();
            key.setGravity(Gravity.CENTER);
            key.setBackground(ui.ripple(ui.surfaceHigh, 16));
            TextView d = ui.text(k, 24, ui.onSurface);
            d.setGravity(Gravity.CENTER);
            key.addView(d);
            if (i < 9 && i > 0) {
                TextView letters = ui.text(LETTERS[i], 9, ui.onSurfaceVariant);
                letters.setLetterSpacing(0.15f);
                key.addView(letters);
            }
            key.setContentDescription(k);
            key.setOnClickListener(v -> press(k.charAt(0)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(54), 1);
            if (i % 3 > 0) lp.setMarginStart(ui.dp(8));
            r.addView(key, lp);
        }
        LinearLayout last = ui.row();
        ImageView del = ui.iconButton(R.drawable.ic_backspace, ui.onSurface, ui.surfaceContainer, "Delete", v -> {
            if (len > 0) buf[--len] = '\0';
            refreshInput();
        });
        del.setOnLongClickListener(v -> {
            wipe();
            refreshInput();
            return true;
        });
        del.setBackground(ui.ripple(ui.surfaceContainer, 16));
        last.addView(del, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
        TextView send = ui.iconText(ui.filled(sendLabel, v -> {
            if (len == 0) return;
            answer(Arrays.copyOf(buf, len));
        }), R.drawable.ic_send);
        send.setBackground(ui.ripple(ui.primary, 16));
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
        if (pinMode && dots != null) {
            dots.removeAllViews();
            int n = Math.max(4, len);
            for (int i = 0; i < n; i++) {
                View d = new View(c);
                android.graphics.drawable.GradientDrawable g = ui.shape(i < len ? ui.primary : 0x00000000, 8);
                if (i >= len) g.setStroke(ui.dp(2), ui.outline);
                d.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16));
                lp.setMarginStart(ui.dp(i == 0 ? 0 : 14));
                dots.addView(d, lp);
            }
        } else if (inputView != null) {
            inputView.setText(new String(buf, 0, len));
        }
    }

    private ValueCallback valueCb;

    private void answer(char[] value) {
        boolean pin = pinMode;
        wipe();
        if (valueCb != null) {
            ValueCallback v = valueCb;
            valueCb = null;
            String s = new String(value);
            Arrays.fill(value, '\0');
            v.onValue(s);
            return;
        }
        cb.onAnswer(value, pin);
    }

    private void wipe() {
        Arrays.fill(buf, '\0');
        len = 0;
        if (inputView != null) inputView.setText("");
    }

    /** Before dialling (widget runs): ask for {PHONE} or {AMOUNT} on the keypad. */
    void askValue(String title, String code, String label, String help, String prefill, ValueCallback cb) {
        this.title = title;
        this.subtitle = code;
        wipe();
        pinMode = false;
        dots = null;
        LinearLayout body = ui.col();
        body.addView(ui.title(label));
        TextView h = ui.small(help);
        h.setPadding(0, ui.dp(4), 0, 0);
        body.addView(h);
        LinearLayout f = ui.col();
        f.addView(inputBox(), ui.full());
        f.addView(keypad("Continue"), ui.full(8));
        if (prefill != null) for (char ch : prefill.toCharArray()) press(ch);
        valueCb = cb;
        sheet(body, f, false, false);
    }

    /** Final page: the reply, amounts / references found, and what to do next. */
    void result(boolean ok, String text, boolean canSave, int steps) {
        wipe();
        pinMode = false;
        valueCb = null;
        LinearLayout body = ui.col();
        LinearLayout top = ui.row();
        top.addView(ui.iconTile(ok ? R.drawable.ic_check : R.drawable.ic_alert, ok ? ui.successContainer : ui.errorContainer,
                ok ? ui.onSuccessContainer : ui.onErrorContainer, 44));
        LinearLayout tt = ui.col();
        tt.setPadding(ui.dp(14), 0, 0, 0);
        tt.addView(ui.title(ok ? "Done" : "Stopped"));
        tt.addView(ui.small(ok ? "Here's what the network replied" : "The session ended"));
        top.addView(tt, Ui.weight());
        body.addView(top, ui.full());
        body.addView(Ui.replyCard(ui, text), ui.full(14));

        LinearLayout footer = ui.col();
        if (canSave) {
            LinearLayout saveRow = ui.row();
            saveRow.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
            saveRow.setBackground(ui.shape(ui.secondaryContainer, 18));
            saveRow.addView(ui.icon(R.drawable.ic_record, ui.onSecondaryContainer, 22));
            LinearLayout st = ui.col();
            st.setPadding(ui.dp(12), 0, ui.dp(8), 0);
            TextView s1 = ui.text("Save as a command", 15, ui.onSecondaryContainer);
            s1.setTypeface(medium);
            st.addView(s1);
            TextView s2 = ui.text(steps + " steps · run it next time in one tap", 12, ui.onSecondaryContainer);
            st.addView(s2);
            saveRow.addView(st, Ui.weight());
            TextView save = ui.filled("Save", null);
            save.setOnClickListener(v -> {
                String name = cb.onSaveRoute();
                if (name != null) {
                    s1.setText("Saved as “" + name + "”");
                    s2.setText("Find it in Record and on Home");
                    save.setVisibility(View.GONE);
                }
            });
            saveRow.addView(save);
            footer.addView(saveRow, ui.full());
        }
        LinearLayout buttons = ui.row();
        buttons.setPadding(0, ui.dp(canSave ? 12 : 0), 0, 0);
        TextView copy = ui.iconText(ui.tonal("Copy", v -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("USSD reply", text));
            Toast.makeText(c, "Reply copied", Toast.LENGTH_SHORT).show();
        }), R.drawable.ic_copy);
        buttons.addView(copy, Ui.weight());
        TextView done = ui.filled("Done", v -> cb.onDone());
        LinearLayout.LayoutParams dlp = Ui.weight();
        dlp.setMarginStart(ui.dp(10));
        buttons.addView(done, dlp);
        footer.addView(buttons, ui.full());
        sheet(body, footer, false, false);
    }

    // --------------------------------------------------------------- banner

    /** Floating card at the bottom, used when the phone's own pop-up should stay visible. */
    void banner(String head, String text, boolean ended, String actionLabel, Runnable action) {
        removeCover();
        removeBanner();
        LinearLayout card = ui.row();
        card.setPadding(ui.dp(16), ui.dp(12), ui.dp(8), ui.dp(12));
        card.setBackground(ui.shape(ui.dark ? ui.surfaceHigh : 0xFF2B2B31, 22));
        card.setElevation(ui.dp(8));
        if (ended) {
            card.addView(ui.icon(R.drawable.ic_check, 0xFF7DDA9C, 22));
        } else {
            ProgressBar spin = new ProgressBar(c);
            spin.setIndeterminateTintList(ColorStateList.valueOf(0xFFB8C4FF));
            card.addView(spin, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        }
        LinearLayout tt = ui.col();
        tt.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        TextView t = ui.text(head, 14, 0xFFFFFFFF);
        t.setTypeface(medium);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        tt.addView(t);
        TextView b = ui.text(text, 13, 0xFFC9C9D1);
        b.setMaxLines(3);
        b.setEllipsize(TextUtils.TruncateAt.END);
        tt.addView(b);
        card.addView(tt, Ui.weight());
        TextView act = ui.text(actionLabel, 14, 0xFFB8C4FF);
        act.setTypeface(medium);
        act.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        act.setBackground(ui.ripple(0x00000000, 16));
        act.setOnClickListener(v -> action.run());
        card.addView(act);
        LinearLayout wrap = ui.col();
        wrap.setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(24));
        wrap.addView(card, ui.full());
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        try {
            wm.addView(wrap, lp);
            banner = wrap;
        } catch (RuntimeException ignored) {
        }
    }
}
