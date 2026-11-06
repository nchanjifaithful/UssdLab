package app.ussdlab;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Guide: every way an app can work with USSD, in plain language, split into three tabs. */
final class GuidePage {

    private GuidePage() {}

    private static int section;
    private static final String[] SECTIONS = {"Ways to run", "Features", "Beyond"};
    private static final String[] WAYS = {"silent", "phone", "dialpad", "autopilot", "custom", "hybrid"};

    static void build(MainActivity a, LinearLayout page) {
        Ui ui = a.ui;
        a.appBar(page, "Guide", "How USSD works with apps, in plain words");

        // segmented control
        LinearLayout seg = ui.row();
        seg.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        seg.setBackground(ui.shape(ui.surfaceContainer, 22));
        for (int i = 0; i < SECTIONS.length; i++) {
            final int s = i;
            boolean sel = section == i;
            TextView t = ui.text(SECTIONS[i], 14, sel ? ui.onSecondaryContainer : ui.onSurfaceVariant);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(ui.dp(40));
            t.setBackground(ui.ripple(sel ? ui.secondaryContainer : 0x00000000, 18));
            t.setOnClickListener(v -> {
                section = s;
                a.showTab(MainActivity.GUIDE);
            });
            seg.addView(t, Ui.weight());
        }
        page.addView(seg, ui.full(4));

        if (section == 2) {
            TextView intro = ui.body("Other parts of the USSD world, and why they aren't in this app.");
            intro.setPadding(ui.dp(4), ui.dp(16), 0, ui.dp(4));
            page.addView(intro);
            for (String[] b : Methods.BEYOND) {
                LinearLayout c = ui.card(ui.surfaceContainer);
                LinearLayout r = ui.row();
                r.addView(ui.iconTile(R.drawable.ic_info, ui.surfaceHigh, ui.onSurfaceVariant, 36));
                TextView t = ui.titleSmall(b[0]);
                t.setPadding(ui.dp(12), 0, 0, 0);
                r.addView(t, Ui.weight());
                c.addView(r);
                TextView d = ui.body(b[1]);
                d.setPadding(0, ui.dp(10), 0, 0);
                c.addView(d);
                page.addView(c, ui.full(10));
            }
            return;
        }
        TextView intro = ui.body(section == 0
                ? "Five ways to send a code, from fully manual to fully automatic. Tap a card to learn more and try it."
                : "Extras that build on those ways: recording, PIN safety, SIM choice, reading replies, and running from the home screen.");
        intro.setPadding(ui.dp(4), ui.dp(16), 0, ui.dp(4));
        page.addView(intro);
        int n = 0;
        for (Methods.Method m : Methods.ALL) {
            boolean isWay = java.util.Arrays.asList(WAYS).contains(m.id);
            if ((section == 0) != isWay) continue;
            page.addView(card(a, ++n, m), ui.full(10));
        }
    }

    static int icon(String id) {
        switch (id) {
            case "silent": return R.drawable.ic_bolt;
            case "phone": return R.drawable.ic_phone;
            case "dialpad": return R.drawable.ic_dialpad;
            case "autopilot": return R.drawable.ic_play;
            case "custom": return R.drawable.ic_layers;
            case "hybrid": return R.drawable.ic_lock;
            case "record": return R.drawable.ic_record;
            case "pin": return R.drawable.ic_lock;
            case "sim": return R.drawable.ic_sim;
            case "reader": return R.drawable.ic_copy;
            case "listen": return R.drawable.ic_eye;
            case "widget": return R.drawable.ic_widgets;
            default: return R.drawable.ic_star;
        }
    }

    private static View card(MainActivity a, int n, Methods.Method m) {
        Ui ui = a.ui;
        LinearLayout c = ui.col();
        c.setPadding(ui.dp(16), ui.dp(14), ui.dp(14), ui.dp(14));
        c.setBackground(ui.ripple(ui.surfaceContainer, 24));
        LinearLayout top = ui.row();
        top.addView(ui.iconTile(icon(m.id), ui.primaryContainer, ui.onPrimaryContainer, 44));
        LinearLayout txt = ui.col();
        txt.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        txt.addView(ui.titleSmall(m.title));
        txt.addView(ui.small(m.tagline));
        top.addView(txt, Ui.weight());
        ImageView chev = ui.icon(R.drawable.ic_expand, ui.onSurfaceVariant, 24);
        top.addView(chev);
        c.addView(top);

        LinearLayout details = ui.col();
        details.setVisibility(View.GONE);
        details.setPadding(0, ui.dp(6), 0, 0);
        detail(ui, details, R.drawable.ic_settings, "How it works", m.how);
        detail(ui, details, R.drawable.ic_star, "Good for", m.goodFor);
        detail(ui, details, R.drawable.ic_check, "What it needs", m.needs);
        detail(ui, details, R.drawable.ic_alert, "Watch out", m.watchOut);
        if (m.tryLabel != null) {
            TextView tryIt = ui.iconText(ui.filled(m.tryLabel, v -> tryMethod(a, m.id)), R.drawable.ic_play);
            LinearLayout.LayoutParams lp = ui.wrap(0);
            lp.topMargin = ui.dp(16);
            details.addView(tryIt, lp);
        }
        c.addView(details);
        c.setOnClickListener(v -> {
            boolean open = details.getVisibility() != View.VISIBLE;
            details.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.setRotation(open ? 180 : 0);
        });
        return c;
    }

    private static void detail(Ui ui, LinearLayout parent, int icon, String head, String body) {
        LinearLayout r = ui.row();
        r.setGravity(Gravity.TOP);
        r.setPadding(0, ui.dp(14), 0, 0);
        r.addView(ui.icon(icon, ui.primary, 18));
        LinearLayout t = ui.col();
        t.setPadding(ui.dp(12), 0, 0, 0);
        TextView h = ui.text(head, 13, ui.primary);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.addView(h);
        TextView b = ui.body(body);
        b.setPadding(0, ui.dp(2), 0, 0);
        t.addView(b);
        r.addView(t, Ui.weight());
        parent.addView(r);
    }

    private static void tryMethod(MainActivity a, String id) {
        switch (id) {
            case "silent": a.run(Store.Action.make("Silent *155#", "*155#", "", Store.SILENT)); break;
            case "phone": a.run(Store.Action.make("*126# in the pop-up", "*126#", "", Store.PHONE)); break;
            case "dialpad": a.run(Store.Action.make("Dial pad *126#", "*126#", "", Store.DIALPAD)); break;
            case "autopilot": a.run(Store.Action.make("Auto-pilot test", "*126#", "@airtime", Store.AUTOPILOT)); break;
            case "custom": a.run(Store.Action.make("*126# on the custom screen", "*126#", "", Store.CUSTOM)); break;
            case "hybrid": a.run(Store.Action.make("Buy airtime (example)", "*126#", "@airtime, @myself, {AMOUNT}, {PIN}", Store.CUSTOM)); break;
            case "record": a.showTab(MainActivity.RECORD); break;
            case "pin": a.pinSheet(); break;
            default: a.showTab(MainActivity.SETTINGS); break;
        }
    }
}
