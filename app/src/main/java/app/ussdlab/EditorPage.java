package app.ussdlab;

import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Create or edit a command: name, code, how it runs, steps, and options. */
final class EditorPage {

    private EditorPage() {}

    static void build(MainActivity a, LinearLayout page, Store.Action x, boolean isNew) {
        Ui ui = a.ui;
        Store store = a.store;

        EditText name = ui.field("e.g. Buy airtime", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText code = ui.codeField("e.g. *126#");
        EditText steps = ui.field("e.g. @airtime, @myself, {AMOUNT}, {PIN}", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Switch autoPin = new Switch(a);
        Switch onWidget = new Switch(a);
        final String[] mode = {x.mode};

        Runnable collect = () -> {
            x.name = name.getText().toString().trim();
            x.code = code.getText().toString().trim();
            x.steps = steps.getText().toString().trim();
            x.mode = mode[0];
            x.autoPin = autoPin.isChecked();
            x.onWidget = onWidget.isChecked();
        };
        Runnable save = () -> {
            collect.run();
            if (x.code.isEmpty()) {
                a.toast("Add a code first");
                return;
            }
            if (x.autoPin && !PinVault.has(a)) a.toast("Saved. Save your PIN in Settings to use \"Fill in my PIN\".");
            store.upsert(x);
            Widget.refresh(a);
            a.closeEditor();
        };

        // ---- top bar
        LinearLayout bar = ui.row();
        bar.addView(ui.iconButton(R.drawable.ic_back, ui.onSurface, 0x00000000, "Back", v -> a.closeEditor()));
        TextView title = ui.title(isNew && x.name.isEmpty() ? "New command" : "Edit command");
        title.setPadding(ui.dp(8), 0, 0, 0);
        bar.addView(title, Ui.weight());
        bar.addView(ui.filled("Save", v -> save.run()));
        page.addView(bar, ui.full());

        LinearLayout form = ui.col();
        form.setPadding(ui.dp(8), ui.dp(4), 0, 0);
        page.addView(form);

        name.setText(x.name);
        form.addView(ui.labelled("Name", name, "What you'll see on Home and the widget."));
        code.setText(x.code);
        form.addView(ui.labelled("Code", code, "The code you would normally dial."));

        TextView ml = ui.text("How it runs", 14, ui.onSurface);
        ml.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ml.setPadding(ui.dp(4), ui.dp(18), 0, ui.dp(8));
        form.addView(ml);
        LinearLayout chipHolder = ui.col();
        TextView modeHelp = ui.small(Store.modeHelp(x.mode));
        modeHelp.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            chipHolder.removeAllViews();
            View[] chips = new View[Store.MODES.length];
            for (int i = 0; i < Store.MODES.length; i++) {
                String m = Store.MODES[i];
                chips[i] = ui.chip(Ui.modeIcon(m), Store.modeName(m), m.equals(mode[0]), v -> {
                    mode[0] = m;
                    modeHelp.setText(Store.modeHelp(m));
                    render[0].run();
                });
            }
            chipHolder.addView(ui.chipRow(chips));
        };
        render[0].run();
        form.addView(chipHolder);
        form.addView(modeHelp);

        steps.setTypeface(Typeface.MONOSPACE);
        steps.setMinLines(2);
        steps.setText(x.steps);
        form.addView(ui.labelled("Steps (optional)", steps, "The answers to give, in order, separated by commas. Leave empty to answer everything yourself on the custom screen."));

        LinearLayout syntax = ui.card(ui.surfaceContainer);
        syntax.addView(ui.titleSmall("How to write steps"));
        String[][] rows = {
                {"1", "Types 1, exactly like you would."},
                {"@airtime", "Picks the option whose words contain \"airtime\". Still works if the menu order changes."},
                {"{PHONE}", "A phone number, asked before each run."},
                {"{AMOUNT}", "An amount, asked before each run."},
                {"{PIN}", "Your PIN, typed on the secure PIN screen (or your saved PIN)."},
        };
        for (String[] r : rows) {
            LinearLayout line = ui.row();
            line.setGravity(Gravity.TOP);
            line.setPadding(0, ui.dp(10), 0, 0);
            TextView k = ui.text(r[0], 13, ui.onSecondaryContainer);
            k.setTypeface(Typeface.MONOSPACE);
            k.setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(2));
            k.setBackground(ui.shape(ui.secondaryContainer, 6));
            line.addView(k);
            TextView d = ui.small(r[1]);
            d.setPadding(ui.dp(10), 0, 0, 0);
            line.addView(d, Ui.weight());
            syntax.addView(line);
        }
        LinearLayout tip = ui.row();
        tip.setPadding(0, ui.dp(14), 0, 0);
        tip.addView(ui.icon(R.drawable.ic_record, ui.primary, 18));
        TextView tt = ui.text("Not sure of the steps? Use Record instead: tap through the menu once and the steps are written for you.", 13, ui.primary);
        tt.setPadding(ui.dp(10), 0, 0, 0);
        tip.addView(tt, Ui.weight());
        tip.setOnClickListener(v -> {
            a.closeEditor();
            a.showTab(MainActivity.RECORD);
        });
        syntax.addView(tip);
        form.addView(syntax, ui.full(14));

        autoPin.setChecked(x.autoPin);
        onWidget.setChecked(x.onWidget);
        LinearLayout opts = ui.group(
                ui.listRow(R.drawable.ic_lock, "Fill in my PIN", "Use your saved PIN after a fingerprint check, so the run finishes by itself", autoPin, null),
                ui.listRow(R.drawable.ic_widgets, "Show on widget", "Appear as a button on the home-screen widget", onWidget, null));
        form.addView(opts, ui.full(16));

        LinearLayout buttons = ui.row();
        buttons.setPadding(0, ui.dp(20), 0, 0);
        buttons.addView(ui.iconText(ui.tonal("Run now", v -> {
            collect.run();
            a.run(x);
        }), R.drawable.ic_play), Ui.weight());
        LinearLayout.LayoutParams lp = Ui.weight();
        lp.setMarginStart(ui.dp(10));
        buttons.addView(ui.iconText(ui.tonal("Home screen", v -> {
            collect.run();
            store.upsert(x);
            a.pinShortcut(x);
        }), R.drawable.ic_star), lp);
        form.addView(buttons, ui.full());
        if (!isNew) {
            TextView del = ui.iconText(ui.textButton("Delete command", v -> {
                store.delete(x.id);
                Widget.refresh(a);
                a.closeEditor();
            }), R.drawable.ic_delete);
            del.setTextColor(ui.error);
            ui.iconText(del, R.drawable.ic_delete);
            form.addView(del, ui.wrap(0));
        }
    }
}
