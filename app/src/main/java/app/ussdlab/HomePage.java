package app.ussdlab;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/** Home: quick send, how it runs, and your commands as tiles. */
final class HomePage {

    private HomePage() {}

    static void build(MainActivity a, LinearLayout page) {
        Ui ui = a.ui;
        Store store = a.store;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

        TextView sim = ui.iconText(ui.tonal(shortSim(a.simLabel()), v -> a.simSheet()), R.drawable.ic_sim);
        sim.setTextSize(13);
        sim.setMinHeight(ui.dp(36));
        sim.setPadding(ui.dp(12), ui.dp(4), ui.dp(14), ui.dp(4));
        a.appBar(page, "USSD Lab", hello, sim);

        if (!a.phoneOk() || !a.serviceOn()) {
            LinearLayout c = ui.row();
            c.setPadding(ui.dp(14), ui.dp(14), ui.dp(10), ui.dp(14));
            c.setBackground(ui.ripple(ui.tertiaryContainer, 22));
            c.addView(ui.icon(R.drawable.ic_info, ui.onTertiaryContainer, 24));
            LinearLayout t = ui.col();
            t.setPadding(ui.dp(12), 0, ui.dp(8), 0);
            TextView h = ui.text("Finish setting up", 15, ui.onTertiaryContainer);
            h.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            t.addView(h);
            t.addView(ui.text(!a.phoneOk() ? "Allow phone access so the app can dial codes." : "Turn on Automation for menus, recording and the widget.", 13, ui.onTertiaryContainer));
            c.addView(t, Ui.weight());
            c.addView(ui.icon(R.drawable.ic_chevron, ui.onTertiaryContainer, 22));
            c.setOnClickListener(v -> {
                if (!a.phoneOk()) a.askPerms(null);
                else a.automationSheet();
            });
            page.addView(c, ui.full(4));
        }

        // ---- quick send
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
        card.setBackground(ui.shape(ui.surfaceContainer, 28));
        TextView qt = ui.titleSmall("Send a code");
        card.addView(qt);
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, 0);
        EditText code = ui.codeField("*155#");
        code.setText(store.quickCode());
        code.setBackground(ui.shape(ui.surface, 18));
        row.addView(code, Ui.weight());
        String mode = store.quickMode();
        ImageView go = ui.iconButton(R.drawable.ic_send, ui.onPrimary, ui.primary, "Send", v -> {
            String c = code.getText().toString().trim();
            store.setQuick(c, mode);
            a.run(Store.Action.make(c, c, "", mode));
        });
        go.setBackground(ui.ripple(ui.primary, 18));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ui.dp(58), ui.dp(58));
        glp.setMarginStart(ui.dp(10));
        row.addView(go, glp);
        card.addView(row, ui.full());

        TextView how = ui.text("How it runs", 13, ui.onSurfaceVariant);
        how.setPadding(ui.dp(2), ui.dp(16), 0, ui.dp(8));
        card.addView(how);
        View[] chips = new View[Store.MODES.length];
        for (int i = 0; i < Store.MODES.length; i++) {
            String m = Store.MODES[i];
            chips[i] = ui.chip(Ui.modeIcon(m), Store.modeName(m), m.equals(mode), v -> {
                store.setQuick(code.getText().toString().trim(), m);
                a.showTab(MainActivity.HOME);
            });
        }
        card.addView(ui.chipRow(chips));
        TextView help = ui.small(Store.modeHelp(mode));
        help.setPadding(ui.dp(2), ui.dp(10), 0, 0);
        card.addView(help);
        page.addView(card, ui.full(8));

        // ---- commands
        LinearLayout head = ui.row();
        head.setPadding(0, ui.dp(20), 0, ui.dp(10));
        LinearLayout ht = ui.col();
        ht.addView(ui.title("Your commands"));
        ht.addView(ui.small("Tap to run · hold for options"));
        head.addView(ht, Ui.weight());
        head.addView(ui.iconText(ui.textButton("New", v -> a.openEditor(Store.Action.make("", "", "", Store.CUSTOM), true)), R.drawable.ic_add));
        page.addView(head, ui.full());

        List<Store.Action> list = store.actions();
        LinearLayout grid = ui.col();
        LinearLayout r = null;
        for (int i = 0; i <= list.size(); i++) {
            if (i % 2 == 0) {
                r = ui.row();
                r.setGravity(Gravity.TOP);
                grid.addView(r, ui.full(i == 0 ? 0 : 10));
            }
            View tile = i < list.size() ? tile(a, list.get(i)) : newTile(a);
            LinearLayout.LayoutParams lp = Ui.weight();
            if (i % 2 == 1) lp.setMarginStart(ui.dp(10));
            r.addView(tile, lp);
        }
        if (list.size() % 2 == 1) {
            // grid already balanced by the "new" tile
        } else {
            View spacer = new View(a);
            LinearLayout.LayoutParams lp = Ui.weight();
            lp.setMarginStart(ui.dp(10));
            r.addView(spacer, lp);
        }
        page.addView(grid, ui.full());
    }

    private static String shortSim(String label) {
        return label.length() > 18 ? label.substring(0, 17) + "…" : label;
    }

    private static View tile(MainActivity a, Store.Action x) {
        Ui ui = a.ui;
        LinearLayout t = ui.col();
        t.setPadding(ui.dp(14), ui.dp(14), ui.dp(6), ui.dp(16));
        t.setBackground(ui.ripple(ui.surfaceContainer, 24));
        t.setMinimumHeight(ui.dp(132));
        LinearLayout top = ui.row();
        top.setGravity(Gravity.TOP);
        top.addView(ui.iconTile(Ui.modeIcon(x.mode), x.recorded ? ui.tertiaryContainer : ui.primaryContainer,
                x.recorded ? ui.onTertiaryContainer : ui.onPrimaryContainer, 40));
        View sp = new View(a);
        top.addView(sp, Ui.weight());
        ImageView more = ui.iconButton(R.drawable.ic_more, ui.onSurfaceVariant, 0x00000000, "Options for " + x.title(), v -> a.actionSheet(x));
        more.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        top.addView(more);
        t.addView(top, ui.full());
        TextView name = ui.titleSmall(x.title());
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, ui.dp(12), ui.dp(8), ui.dp(2));
        t.addView(name);
        int n = StepEngine.parse(x.steps).size();
        TextView sub = ui.small(x.code + (n > 0 ? " · " + n + " steps" : "") + (x.onWidget ? " · ★" : ""));
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(sub);
        t.setOnClickListener(v -> a.run(x));
        t.setOnLongClickListener(v -> {
            a.actionSheet(x);
            return true;
        });
        t.setContentDescription(x.title() + ", " + x.code + ". Tap to run.");
        return t;
    }

    private static View newTile(MainActivity a) {
        Ui ui = a.ui;
        LinearLayout t = ui.col();
        t.setGravity(Gravity.CENTER);
        t.setMinimumHeight(ui.dp(132));
        android.graphics.drawable.GradientDrawable d = ui.shape(0x00000000, 24);
        d.setStroke(ui.dp(1.5f), ui.outlineVariant, ui.dp(6), ui.dp(5));
        t.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf((ui.onSurface & 0x00FFFFFF) | 0x22000000), d, ui.shape(0xFFFFFFFF, 24)));
        t.addView(ui.iconTile(R.drawable.ic_add, ui.secondaryContainer, ui.onSecondaryContainer, 40));
        TextView l = ui.text("New command", 14, ui.onSurfaceVariant);
        l.setPadding(0, ui.dp(10), 0, 0);
        t.addView(l);
        TextView r = ui.text("or record one", 12, ui.primary);
        r.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4));
        r.setOnClickListener(v -> a.showTab(MainActivity.RECORD));
        t.addView(r);
        t.setOnClickListener(v -> a.openEditor(Store.Action.make("", "", "", Store.CUSTOM), true));
        return t;
    }
}
