package app.ussdlab;

import android.app.Dialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Record: teach the app a menu path once, then run it in one tap. */
final class RecordPage {

    private RecordPage() {}

    static void build(MainActivity a, LinearLayout page) {
        Ui ui = a.ui;
        Store store = a.store;
        a.appBar(page, "Record", "Teach USSD Lab a menu once");

        // ---- hero
        LinearLayout hero = ui.col();
        hero.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20));
        hero.setBackground(ui.shape(ui.primaryContainer, 28));
        LinearLayout hr = ui.row();
        hr.addView(ui.iconTile(R.drawable.ic_record, ui.primary, ui.onPrimary, 48));
        LinearLayout ht = ui.col();
        ht.setPadding(ui.dp(14), 0, 0, 0);
        TextView h1 = ui.text("Record a command", 20, ui.onPrimaryContainer);
        h1.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ht.addView(h1);
        ht.addView(ui.text("Tap through once. Run it forever.", 14, ui.onPrimaryContainer));
        hr.addView(ht, Ui.weight());
        hero.addView(hr);

        EditText name = ui.field("e.g. Buy 500 airtime", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText code = ui.codeField("*126#");
        code.setText("*126#");
        for (EditText e : new EditText[]{name, code}) e.setBackground(ui.shape(ui.surface, 16));
        hero.addView(ui.labelled("Name", name, null));
        hero.addView(ui.labelled("Code to dial", code, null));
        for (int i = 1; i < hero.getChildCount(); i++) {
            View v = hero.getChildAt(i);
            if (v instanceof LinearLayout) {
                View lbl = ((LinearLayout) v).getChildAt(0);
                if (lbl instanceof TextView) ((TextView) lbl).setTextColor(ui.onPrimaryContainer);
            }
        }
        TextView start = ui.iconText(ui.filled("Start recording", v -> {
            String c = code.getText().toString().trim();
            String n = name.getText().toString().trim();
            if (c.isEmpty()) {
                a.toast("Enter the code to dial");
                return;
            }
            if (!a.serviceOn()) {
                a.automationSheet();
                return;
            }
            a.run(Store.Action.make(n.isEmpty() ? c : n, c, "", Store.CUSTOM), n.isEmpty() ? "Recorded " + c : n);
        }), R.drawable.ic_record);
        start.setMinHeight(ui.dp(52));
        hero.addView(start, ui.full(18));
        if (!a.serviceOn()) {
            TextView need = ui.text("Needs Automation. Tap Start and we'll show you how to turn it on.", 12, ui.onPrimaryContainer);
            need.setPadding(ui.dp(4), ui.dp(10), 0, 0);
            hero.addView(need);
        }
        page.addView(hero, ui.full(4));

        // ---- how it works
        page.addView(ui.section("HOW IT WORKS"));
        page.addView(ui.group(
                stepRow(ui, "1", "Dial", "USSD Lab dials your code on the SIM you picked and covers the pop-up with its own screen."),
                stepRow(ui, "2", "Tap through", "Pick options and type numbers as usual. Each answer becomes a step: options by their words (@airtime), numbers and amounts as {PHONE} and {AMOUNT}."),
                stepRow(ui, "3", "Save", "At the end, tap Save. Your PIN is stored as {PIN}, never the digits, so you'll be asked for it each time.")), ui.full());

        // ---- recorded commands
        page.addView(ui.section("YOUR RECORDED COMMANDS"));
        List<Store.Action> recorded = new ArrayList<>();
        for (Store.Action x : store.actions()) if (x.recorded) recorded.add(x);
        if (recorded.isEmpty()) {
            LinearLayout empty = ui.card(ui.surfaceLow);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(ui.iconTile(R.drawable.ic_record, ui.surfaceHigh, ui.onSurfaceVariant, 44));
            TextView t = ui.body("Nothing recorded yet. Your first recording will appear here, on Home and can go on the widget.");
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, ui.dp(10), 0, 0);
            empty.addView(t);
            page.addView(empty, ui.full());
        } else {
            View[] rows = new View[recorded.size()];
            for (int i = 0; i < recorded.size(); i++) {
                Store.Action x = recorded.get(i);
                int n = StepEngine.parse(x.steps).size();
                rows[i] = ui.listRow(R.drawable.ic_record, x.title(), x.code + " · " + n + " steps · " + x.steps,
                        ui.iconButton(R.drawable.ic_play, ui.onPrimary, ui.primary, "Run " + x.title(), v -> a.run(x)),
                        v -> a.actionSheet(x));
            }
            page.addView(ui.group(rows), ui.full());
        }

        // ---- share
        page.addView(ui.section("SHARE BETWEEN PHONES"));
        page.addView(ui.group(
                ui.listRow(R.drawable.ic_upload, "Export commands", "Send all your commands as text (no PIN is ever included)", null,
                        v -> a.share("USSD Lab commands", Store.export(store.actions()))),
                ui.listRow(R.drawable.ic_download, "Import commands", "Paste text exported from USSD Lab on another phone", null,
                        v -> importSheet(a))), ui.full());
    }

    private static View stepRow(Ui ui, String n, String title, String body) {
        LinearLayout r = ui.row();
        r.setGravity(Gravity.TOP);
        r.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        TextView num = ui.text(n, 14, ui.onSecondaryContainer);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        num.setBackground(ui.shape(ui.secondaryContainer, 14));
        r.addView(num, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        LinearLayout t = ui.col();
        t.setPadding(ui.dp(14), ui.dp(2), 0, 0);
        t.addView(ui.titleSmall(title));
        TextView b = ui.small(body);
        b.setPadding(0, ui.dp(2), 0, 0);
        t.addView(b);
        r.addView(t, Ui.weight());
        return r;
    }

    static void importSheet(MainActivity a) {
        Ui ui = a.ui;
        LinearLayout box = ui.col();
        box.addView(ui.title("Import commands"));
        EditText in = ui.field("Paste here (starts with USSDLAB:)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        in.setMinLines(3);
        android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
            if (clip != null && clip.toString().contains("USSDLAB:")) in.setText(clip);
        }
        box.addView(ui.labelled("Shared text", in, "Commands are added to your list. Nothing is replaced."));
        Dialog[] d = new Dialog[1];
        box.addView(ui.iconText(ui.filled("Import", v -> {
            int n = a.store.importText(in.getText().toString());
            if (n < 0) {
                a.toast("That doesn't look like USSD Lab commands");
                return;
            }
            d[0].dismiss();
            a.toast(n == 1 ? "1 command imported" : n + " commands imported");
            Widget.refresh(a);
            a.showTab(a.tab);
        }), R.drawable.ic_download), ui.full(18));
        d[0] = Sheet.show(a, ui, box);
    }
}
