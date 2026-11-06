package app.ussdlab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    static final String EXTRA_RUN = "run_action";
    private static final int REQ_PERMS = 1;
    private static final int REQ_KEYGUARD = 2;
    private static final String[] TABS = {"Send", "Lab", "History", "Setup"};
    private static final int[] TAB_ICONS = {R.drawable.ic_send, R.drawable.ic_lab, R.drawable.ic_history, R.drawable.ic_setup};

    private Ui ui;
    private Store store;
    private FrameLayout content;
    private LinearLayout nav;
    private int tab;
    private boolean editorOpen;
    private List<Sims.Sim> sims = new ArrayList<>();
    private Runnable afterAuth;
    private Runnable afterPerms;

    // --------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        store = Store.get(this);
        getWindow().setStatusBarColor(ui.surface);
        getWindow().setNavigationBarColor(ui.surfaceContainer);
        if (!ui.dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        LinearLayout root = ui.col();
        root.setBackgroundColor(ui.surface);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        nav = ui.row();
        nav.setBackgroundColor(ui.surfaceContainer);
        nav.setPadding(0, ui.dp(10), 0, ui.dp(14));
        root.addView(nav, ui.full());
        setContentView(root);
        handleIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!editorOpen) showTab(tab);
    }

    @Override public void onBackPressed() {
        if (editorOpen) closeEditor();
        else if (tab != 0) showTab(0);
        else super.onBackPressed();
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String runId = i.getStringExtra(EXTRA_RUN);
        String route = i.getStringExtra(UssdService.EXTRA_ROUTE);
        i.removeExtra(EXTRA_RUN);
        i.removeExtra(UssdService.EXTRA_ROUTE);
        if (runId != null) {
            Store.Action a = store.action(runId);
            if (a != null) content.post(() -> run(a));
        } else if (route != null) {
            Store.Action a = Store.Action.make(i.getStringExtra(UssdService.EXTRA_ROUTE_TITLE) + " (recorded)",
                    i.getStringExtra(UssdService.EXTRA_ROUTE_CODE), route, Store.CUSTOM);
            content.post(() -> openEditor(a, true));
        }
    }

    // -------------------------------------------------------------- navigation

    private void showTab(int t) {
        tab = t;
        editorOpen = false;
        nav.setVisibility(View.VISIBLE);
        renderNav();
        LinearLayout page = ui.col();
        page.setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(32));
        switch (t) {
            case 1: buildLab(page); break;
            case 2: buildHistory(page); break;
            case 3: buildSetup(page); break;
            default: buildSend(page); break;
        }
        setPage(page);
    }

    private void setPage(View page) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(page);
        content.removeAllViews();
        content.addView(sv);
    }

    private void renderNav() {
        nav.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean sel = i == tab;
            LinearLayout item = ui.col();
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout pill = new FrameLayout(this);
            pill.setBackground(ui.shape(sel ? ui.secondaryContainer : 0x00000000, 16));
            View ic = ui.icon(TAB_ICONS[i], sel ? ui.onSecondaryContainer : ui.onSurfaceVariant, 24);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER);
            pill.addView(ic, ilp);
            item.addView(pill, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(32)));
            TextView l = ui.text(TABS[i], 12, sel ? ui.onSurface : ui.onSurfaceVariant);
            if (sel) l.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            l.setPadding(0, ui.dp(4), 0, 0);
            item.addView(l);
            item.setOnClickListener(v -> showTab(idx));
            item.setContentDescription(TABS[i]);
            nav.addView(item, Ui.weight());
        }
    }

    private void header(LinearLayout page, String title, String intro) {
        page.addView(ui.display(title));
        TextView t = ui.body(intro);
        t.setPadding(0, ui.dp(8), 0, ui.dp(4));
        page.addView(t);
    }

    // ---------------------------------------------------------------- Send tab

    private void buildSend(LinearLayout page) {
        header(page, "USSD Lab", "Send USSD codes the way you want, and see every way an app can work with them.");

        boolean perms = phoneOk();
        boolean auto = serviceOn();
        if (!perms || !auto) {
            LinearLayout c = ui.card(ui.tertiaryContainer);
            TextView h = ui.titleSmall("Finish setting up");
            h.setTextColor(ui.onTertiaryContainer);
            c.addView(h);
            TextView b = ui.text((perms ? "" : "• Allow phone access so the app can dial codes.\n")
                    + (auto ? "" : "• Turn on Automation for menus and the custom screen."), 14, ui.onTertiaryContainer);
            b.setPadding(0, ui.dp(6), 0, ui.dp(10));
            c.addView(b);
            c.addView(ui.filled("Go to Setup", v -> showTab(3)), ui.wrap(0));
            page.addView(c, ui.full(16));
        }

        page.addView(ui.label("SIM"));
        sims = Sims.list(this);
        if (sims.isEmpty()) {
            page.addView(ui.small(perms ? "No SIM found. The phone's default SIM will be used." : "Allow phone access in Setup to choose a SIM."));
        } else {
            int saved = selectedSlot();
            View[] chips = new View[sims.size()];
            for (int i = 0; i < sims.size(); i++) {
                Sims.Sim s = sims.get(i);
                chips[i] = ui.chip(s.label, s.slot == saved, v -> {
                    store.setSimSlot(s.slot);
                    showTab(0);
                });
            }
            page.addView(ui.chipRow(chips));
            TextView h = ui.small("Codes are sent from this SIM.");
            h.setPadding(ui.dp(4), ui.dp(6), 0, 0);
            page.addView(h);
        }

        page.addView(ui.label("Send a code"));
        LinearLayout card = ui.card();
        EditText code = ui.codeField("e.g. *155#");
        code.setText(store.quickCode());
        card.addView(code, ui.full());
        TextView how = ui.text("How should it run?", 14, ui.onSurface);
        how.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        how.setPadding(ui.dp(2), ui.dp(16), 0, ui.dp(8));
        card.addView(how);
        String mode = store.quickMode();
        View[] modeChips = new View[Store.MODES.length];
        for (int i = 0; i < Store.MODES.length; i++) {
            String m = Store.MODES[i];
            modeChips[i] = ui.chip(Store.modeName(m), m.equals(mode), v -> {
                store.setQuick(code.getText().toString().trim(), m);
                showTab(0);
            });
        }
        card.addView(ui.chipRow(modeChips));
        TextView help = ui.small(Store.modeHelp(mode));
        help.setPadding(ui.dp(2), ui.dp(10), 0, ui.dp(14));
        card.addView(help);
        card.addView(ui.filled("Send", v -> {
            String c = code.getText().toString().trim();
            store.setQuick(c, mode);
            Store.Action a = Store.Action.make(c, c, "", mode);
            run(a);
        }), ui.full());
        page.addView(card, ui.full());
        page.addView(coverToggle(true), ui.full(10));

        LinearLayout ah = ui.row();
        TextView al = ui.label("Your actions");
        ah.addView(al, Ui.weight());
        TextView add = ui.textButton("+ New", v -> openEditor(Store.Action.make("", "", "", Store.CUSTOM), true));
        ah.addView(add);
        page.addView(ah, ui.full());
        TextView ahelp = ui.small("Saved codes, with optional steps the app answers for you. Tap to run, hold for more.");
        ahelp.setPadding(ui.dp(4), 0, 0, ui.dp(8));
        page.addView(ahelp);
        for (Store.Action a : store.actions()) page.addView(actionCard(a), ui.full(8));

        List<Store.Run> runs = store.runs();
        if (!runs.isEmpty()) {
            page.addView(ui.label("Last reply"));
            page.addView(runCard(runs.get(0), false), ui.full());
        }
    }

    private View actionCard(Store.Action a) {
        LinearLayout c = ui.row();
        c.setPadding(ui.dp(18), ui.dp(14), ui.dp(8), ui.dp(14));
        c.setBackground(ui.ripple(ui.surfaceContainer, 20));
        LinearLayout txt = ui.col();
        TextView t = ui.titleSmall(a.title());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(t);
        int n = StepEngine.parse(a.steps).size();
        txt.addView(ui.small(a.code + "  ·  " + Store.modeName(a.mode) + (n > 0 ? "  ·  " + n + " steps" : "")));
        c.addView(txt, Ui.weight());
        TextView more = ui.text("⋮", 22, ui.onSurfaceVariant);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("More options for " + a.title());
        more.setBackground(ui.ripple(0x00000000, 20));
        more.setOnClickListener(v -> actionMenu(v, a));
        c.addView(more, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        c.setOnClickListener(v -> run(a));
        c.setOnLongClickListener(v -> {
            actionMenu(more, a);
            return true;
        });
        return c;
    }

    private void actionMenu(View anchor, Store.Action a) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Run");
        pm.getMenu().add(0, 2, 1, "Edit");
        pm.getMenu().add(0, 3, 2, "Add to home screen");
        pm.getMenu().add(0, 4, 3, "Delete");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: run(a); break;
                case 2: openEditor(a, false); break;
                case 3: pinShortcut(a); break;
                case 4:
                    store.delete(a.id);
                    showTab(0);
                    break;
            }
            return true;
        });
        pm.show();
    }

    private View runCard(Store.Run r, boolean expandable) {
        LinearLayout c = ui.card();
        LinearLayout top = ui.row();
        String tag = "listen".equals(r.mode) ? "Heard" : r.ok ? "Done" : "Stopped";
        int bg = "listen".equals(r.mode) ? ui.secondaryContainer : r.ok ? ui.successContainer : ui.errorContainer;
        int fg = "listen".equals(r.mode) ? ui.onSecondaryContainer : r.ok ? ui.onSuccessContainer : ui.onErrorContainer;
        top.addView(ui.badge(tag, bg, fg));
        TextView when = ui.small("  " + DateUtils.getRelativeTimeSpanString(r.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        top.addView(when, Ui.weight());
        c.addView(top);
        TextView t = ui.titleSmall(r.title);
        t.setPadding(0, ui.dp(10), 0, ui.dp(4));
        c.addView(t);
        TextView res = ui.text(r.result, 16, ui.onSurface);
        res.setTextIsSelectable(!expandable);
        if (expandable) {
            res.setMaxLines(3);
            res.setEllipsize(TextUtils.TruncateAt.END);
        }
        c.addView(res);
        List<String> amounts = Reply.amounts(r.result);
        List<String> refs = Reply.references(r.result);
        if (!amounts.isEmpty() || !refs.isEmpty()) {
            LinearLayout chips = ui.row();
            chips.setPadding(0, ui.dp(10), 0, 0);
            for (String a : amounts) chips.addView(ui.badge(a, ui.primaryContainer, ui.onPrimaryContainer), ui.wrap(chips.getChildCount() == 0 ? 0 : 6));
            for (String x : refs) chips.addView(ui.badge("Ref " + x, ui.tertiaryContainer, ui.onTertiaryContainer), ui.wrap(chips.getChildCount() == 0 ? 0 : 6));
            c.addView(chips);
        }
        LinearLayout actions = ui.row();
        actions.setPadding(0, ui.dp(6), 0, 0);
        actions.addView(ui.textButton("Copy reply", v -> copy(r.result, "Reply copied")));
        if (expandable && !r.log.isEmpty()) {
            TextView log = ui.mono(r.log);
            log.setVisibility(View.GONE);
            log.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
            log.setBackground(ui.shape(ui.surface, 14));
            TextView toggle = ui.textButton("Show steps", null);
            toggle.setOnClickListener(v -> {
                boolean show = log.getVisibility() != View.VISIBLE;
                log.setVisibility(show ? View.VISIBLE : View.GONE);
                res.setMaxLines(show ? Integer.MAX_VALUE : 3);
                toggle.setText(show ? "Hide steps" : "Show steps");
            });
            actions.addView(toggle);
            c.addView(actions);
            c.addView(log, ui.full(4));
        } else {
            if (!expandable) actions.addView(ui.textButton("All history", v -> showTab(2)));
            c.addView(actions);
        }
        return c;
    }

    // ----------------------------------------------------------------- Lab tab

    private void buildLab(LinearLayout page) {
        header(page, "The lab", "There are several ways an app can work with USSD. Each card explains one in plain words: what it does, how it works, what it's good for and what to watch out for. Tap a card to open it.");
        for (int i = 0; i < Methods.ALL.length; i++) page.addView(methodCard(i + 1, Methods.ALL[i]), ui.full(10));
        page.addView(ui.label("Beyond this app"));
        TextView intro = ui.small("Other parts of the USSD world, and why they aren't in this app.");
        intro.setPadding(ui.dp(4), 0, 0, ui.dp(8));
        page.addView(intro);
        for (String[] b : Methods.BEYOND) {
            LinearLayout c = ui.card(ui.surfaceLow);
            c.addView(ui.titleSmall(b[0]));
            TextView d = ui.body(b[1]);
            d.setPadding(0, ui.dp(6), 0, 0);
            c.addView(d);
            page.addView(c, ui.full(8));
        }
    }

    private View methodCard(int n, Methods.Method m) {
        LinearLayout c = ui.card();
        LinearLayout top = ui.row();
        TextView num = ui.text(String.valueOf(n), 15, ui.onPrimaryContainer);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        num.setBackground(ui.shape(ui.primaryContainer, 18));
        top.addView(num, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        LinearLayout txt = ui.col();
        txt.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        txt.addView(ui.titleSmall(m.title));
        txt.addView(ui.small(m.tagline));
        top.addView(txt, Ui.weight());
        TextView chev = ui.text("⌄", 20, ui.onSurfaceVariant);
        top.addView(chev);
        c.addView(top);

        LinearLayout details = ui.col();
        details.setVisibility(View.GONE);
        details.setPadding(0, ui.dp(8), 0, 0);
        section(details, "How it works", m.how);
        section(details, "Good for", m.goodFor);
        section(details, "What it needs", m.needs);
        section(details, "Watch out", m.watchOut);
        if (m.tryLabel != null) {
            details.addView(ui.space(14));
            details.addView(ui.tonal(m.tryLabel, v -> tryMethod(m.id)), ui.wrap(0));
        }
        c.addView(details);
        c.setOnClickListener(v -> {
            boolean open = details.getVisibility() != View.VISIBLE;
            details.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.setText(open ? "⌃" : "⌄");
        });
        return c;
    }

    private void section(LinearLayout parent, String head, String body) {
        TextView h = ui.text(head, 13, ui.primary);
        h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        h.setPadding(0, ui.dp(12), 0, ui.dp(2));
        parent.addView(h);
        parent.addView(ui.body(body));
    }

    private void tryMethod(String id) {
        switch (id) {
            case "silent": run(Store.Action.make("Silent *155#", "*155#", "", Store.SILENT)); break;
            case "phone": run(Store.Action.make("*126# in the pop-up", "*126#", "", Store.PHONE)); break;
            case "dialpad": run(Store.Action.make("Dial pad *126#", "*126#", "", Store.DIALPAD)); break;
            case "autopilot": run(Store.Action.make("Auto-pilot test", "*126#", "@airtime", Store.AUTOPILOT)); break;
            case "custom": run(Store.Action.make("*126# on the custom screen", "*126#", "", Store.CUSTOM)); break;
            case "hybrid": run(Store.Action.make("Buy airtime (example)", "*126#", "@airtime, @myself, {AMOUNT}, {PIN}", Store.CUSTOM)); break;
            case "record": run(Store.Action.make("Recording *126#", "*126#", "", Store.CUSTOM)); break;
            default: showTab(3); break;
        }
    }

    // ------------------------------------------------------------- History tab

    private void buildHistory(LinearLayout page) {
        header(page, "History", "Every run and what the network replied. Tap \"Show steps\" to see each screen and each answer. Your PIN never appears here.");
        List<Store.Run> runs = store.runs();
        LinearLayout bar = ui.row();
        bar.setPadding(0, ui.dp(8), 0, ui.dp(4));
        bar.addView(ui.outlined("Copy all", v -> {
            StringBuilder sb = new StringBuilder();
            for (Store.Run r : store.runs()) sb.append("── ").append(r.title).append('\n').append(r.log.isEmpty() ? r.result : r.log).append('\n');
            copy(sb.toString(), "History copied");
        }));
        bar.addView(ui.textButton("Clear", v -> {
            store.clearRuns();
            showTab(2);
        }), ui.wrap(8));
        page.addView(bar);
        if (runs.isEmpty()) {
            LinearLayout c = ui.card(ui.surfaceLow);
            c.addView(ui.titleSmall("Nothing yet"));
            TextView t = ui.body("Run a code from the Send tab and the reply will show up here. Turn on Listen mode in Setup to also keep replies from codes you dial yourself.");
            t.setPadding(0, ui.dp(6), 0, 0);
            c.addView(t);
            page.addView(c, ui.full(12));
        }
        for (Store.Run r : runs) page.addView(runCard(r, true), ui.full(10));
    }

    // --------------------------------------------------------------- Setup tab

    private void buildSetup(LinearLayout page) {
        header(page, "Setup", "Two switches make everything work, and one is optional. Each is explained below.");

        boolean perms = phoneOk();
        LinearLayout c1 = stepCard(1, "Phone access", perms,
                "Lets USSD Lab dial codes and see which SIMs you have. Nothing is sent anywhere.");
        if (!perms) c1.addView(ui.filled("Allow", v -> askPerms(() -> showTab(3))), ui.wrap(0));
        page.addView(c1, ui.full(16));

        boolean auto = serviceOn();
        LinearLayout c2 = stepCard(2, "Automation", auto,
                "Lets USSD Lab read the USSD pop-up and type answers for you. This is what powers Auto-pilot, the Custom screen, recording and Listen mode. It only acts while a run you started is going (or, with Listen mode, only copies text).");
        if (!auto) {
            TextView how = ui.small("In the next screen: Installed apps (or Downloaded apps) → USSD Lab → turn it on."
                    + (Build.VERSION.SDK_INT >= 33 ? "\n\nSwitch greyed out? Open App info → ⋮ (top right) → Allow restricted settings, then try again. Android does this for apps installed from a file." : ""));
            how.setPadding(0, 0, 0, ui.dp(12));
            c2.addView(how);
            LinearLayout r = ui.row();
            r.addView(ui.filled("Open settings", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
            r.addView(ui.textButton("App info", v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)))), ui.wrap(8));
            c2.addView(r);
        }
        page.addView(c2, ui.full(10));

        boolean hasPin = PinVault.has(this);
        LinearLayout c3 = stepCard(3, "Saved PIN (optional)", hasPin,
                hasPin ? "Your PIN is saved, encrypted on this phone. It only unlocks with your fingerprint or screen lock, and only for actions where you turn on \"Fill in my PIN\"."
                        : "Not saved. When a menu asks for your PIN, you type it on USSD Lab's PIN screen and it isn't kept. Save it only if you want runs to finish by themselves.");
        TextView warn = ui.small("Check that your mobile money provider allows apps to store or enter your PIN.");
        warn.setPadding(0, 0, 0, ui.dp(12));
        c3.addView(warn);
        LinearLayout pr = ui.row();
        pr.addView(hasPin ? ui.outlined("Change PIN", v -> savePinFlow()) : ui.tonal("Save PIN", v -> savePinFlow()));
        if (hasPin) pr.addView(ui.textButton("Remove", v -> {
            PinVault.clear(this);
            toast("PIN removed");
            showTab(3);
        }), ui.wrap(8));
        c3.addView(pr);
        page.addView(c3, ui.full(10));

        page.addView(ui.label("Extras"));
        page.addView(coverToggle(true), ui.full());
        LinearLayout c4 = ui.card();
        LinearLayout r4 = ui.row();
        LinearLayout t4 = ui.col();
        t4.addView(ui.titleSmall("Listen mode"));
        TextView d4 = ui.small("Keep a copy of every USSD reply in History, even for codes you dial yourself. Never taps or types.");
        d4.setPadding(0, ui.dp(4), ui.dp(12), 0);
        t4.addView(d4);
        r4.addView(t4, Ui.weight());
        Switch sw = new Switch(this);
        sw.setChecked(store.listen());
        sw.setOnCheckedChangeListener((s, on) -> {
            store.setListen(on);
            if (on && !serviceOn()) toast("Turn on Automation too, so Listen mode can see the pop-ups");
        });
        r4.addView(sw);
        c4.addView(r4);
        page.addView(c4, ui.full(10));

        LinearLayout c5 = ui.card(ui.surfaceLow);
        c5.addView(ui.titleSmall("Privacy"));
        TextView p = ui.body("USSD Lab has no internet permission, so nothing it reads can leave your phone. Actions, History and the encrypted PIN stay in the app's private storage and are removed if you uninstall it.");
        p.setPadding(0, ui.dp(6), 0, 0);
        c5.addView(p);
        page.addView(c5, ui.full(10));

        LinearLayout c6 = ui.card(ui.surfaceLow);
        c6.addView(ui.titleSmall("About"));
        TextView ab = ui.body("USSD Lab 0.2 · a lightweight lab and guide for USSD on Android. No ads, no tracking, no extra libraries.");
        ab.setPadding(0, ui.dp(6), 0, 0);
        c6.addView(ab);
        page.addView(c6, ui.full(10));
    }

    /** The on/off switch for drawing the custom screen over the USSD pop-up. */
    private View coverToggle(boolean inCard) {
        LinearLayout c = inCard ? ui.card() : ui.col();
        LinearLayout r = ui.row();
        LinearLayout t = ui.col();
        t.addView(ui.titleSmall("Custom screen over USSD"));
        TextView d = ui.small(store.cover()
                ? "On: USSD Lab draws its own screen over the USSD pop-up, so you see clear buttons, a keypad and a PIN screen instead."
                : "Off: you see the phone's normal USSD pop-up. Saved steps are still typed for you, and you finish anything else in the pop-up.");
        d.setPadding(0, ui.dp(4), ui.dp(12), 0);
        t.addView(d);
        r.addView(t, Ui.weight());
        Switch sw = new Switch(this);
        sw.setChecked(store.cover());
        sw.setContentDescription("Custom screen over USSD");
        sw.setOnCheckedChangeListener((s, on) -> {
            store.setCover(on);
            toast(on ? "Custom screen on" : "Custom screen off — you'll see the phone's pop-up");
            content.post(() -> { if (!editorOpen) showTab(tab); });
        });
        r.addView(sw);
        c.addView(r);
        return c;
    }

    private LinearLayout stepCard(int n, String title, boolean ok, String body) {
        LinearLayout c = ui.card();
        LinearLayout top = ui.row();
        TextView num = ui.text(ok ? "✓" : String.valueOf(n), 15, ok ? ui.onSuccessContainer : ui.onPrimaryContainer);
        num.setGravity(Gravity.CENTER);
        num.setBackground(ui.shape(ok ? ui.successContainer : ui.primaryContainer, 18));
        top.addView(num, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        TextView t = ui.titleSmall(title);
        t.setPadding(ui.dp(14), 0, 0, 0);
        top.addView(t, Ui.weight());
        top.addView(ok ? ui.badge("Ready", ui.successContainer, ui.onSuccessContainer) : ui.badge("Not set", ui.surfaceHigh, ui.onSurfaceVariant));
        c.addView(top);
        TextView b = ui.body(body);
        b.setPadding(0, ui.dp(10), 0, ui.dp(12));
        c.addView(b);
        return c;
    }

    // ------------------------------------------------------------------ editor

    private void openEditor(Store.Action a, boolean isNew) {
        editorOpen = true;
        nav.setVisibility(View.GONE);
        LinearLayout page = ui.col();
        page.setPadding(ui.dp(12), ui.dp(16), ui.dp(20), ui.dp(32));

        LinearLayout bar = ui.row();
        TextView back = ui.text("←", 24, ui.onSurface);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ui.ripple(0x00000000, 24));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> closeEditor());
        bar.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        TextView title = ui.title(isNew && a.name.isEmpty() ? "New action" : "Edit action");
        title.setPadding(ui.dp(8), 0, 0, 0);
        bar.addView(title);
        page.addView(bar);

        LinearLayout form = ui.col();
        form.setPadding(ui.dp(8), 0, 0, 0);
        page.addView(form);

        EditText name = ui.field("e.g. Buy airtime", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(a.name);
        form.addView(ui.labelled("Name", name, "What you'll see in your list."));

        EditText code = ui.codeField("e.g. *126#");
        code.setText(a.code);
        form.addView(ui.labelled("Code", code, "The code you would normally dial."));

        TextView modeLabel = ui.text("How it runs", 14, ui.onSurface);
        modeLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        modeLabel.setPadding(ui.dp(4), ui.dp(18), 0, ui.dp(8));
        form.addView(modeLabel);
        final String[] mode = {a.mode};
        LinearLayout chipHolder = ui.col();
        TextView modeHelp = ui.small(Store.modeHelp(a.mode));
        modeHelp.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        Runnable[] renderChips = new Runnable[1];
        renderChips[0] = () -> {
            chipHolder.removeAllViews();
            View[] chips = new View[Store.MODES.length];
            for (int i = 0; i < Store.MODES.length; i++) {
                String m = Store.MODES[i];
                chips[i] = ui.chip(Store.modeName(m), m.equals(mode[0]), v -> {
                    mode[0] = m;
                    modeHelp.setText(Store.modeHelp(m));
                    renderChips[0].run();
                });
            }
            chipHolder.addView(ui.chipRow(chips));
        };
        renderChips[0].run();
        form.addView(chipHolder);
        form.addView(modeHelp);

        EditText steps = ui.field("e.g. @airtime, @myself, {AMOUNT}, {PIN}", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        steps.setTypeface(Typeface.MONOSPACE);
        steps.setMinLines(2);
        steps.setText(a.steps);
        form.addView(ui.labelled("Steps (optional)", steps, "The answers to give, in order, separated by commas. Used by Custom screen and Auto-pilot. Leave empty to answer everything yourself."));

        LinearLayout syntax = ui.card(ui.surfaceLow);
        syntax.addView(ui.titleSmall("How to write steps"));
        String[][] rows = {
                {"1", "Types 1, exactly like you would."},
                {"@airtime", "Finds the option whose words contain \"airtime\" and picks its number. Keeps working if the menu order changes."},
                {"{PHONE}", "A phone number you're asked for before each run."},
                {"{AMOUNT}", "An amount you're asked for before each run."},
                {"{PIN}", "Your PIN: typed on the secure PIN screen, or filled from your saved PIN."},
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
        TextView tip = ui.small("Not sure of the steps? Run the code on the Custom screen with no steps, tap through it once, then tap \"Save these steps\" at the end.");
        tip.setPadding(0, ui.dp(12), 0, 0);
        syntax.addView(tip);
        form.addView(syntax, ui.full(12));

        LinearLayout pinRow = ui.row();
        pinRow.setPadding(ui.dp(4), ui.dp(16), 0, 0);
        LinearLayout pt = ui.col();
        pt.addView(ui.titleSmall("Fill in my PIN"));
        TextView pd = ui.small("Uses your saved PIN after a fingerprint check, so the run can finish by itself. Off: you type the PIN on the secure PIN screen.");
        pd.setPadding(0, ui.dp(4), ui.dp(12), 0);
        pt.addView(pd);
        pinRow.addView(pt, Ui.weight());
        Switch autoPin = new Switch(this);
        autoPin.setChecked(a.autoPin);
        pinRow.addView(autoPin);
        form.addView(pinRow, ui.full());

        Runnable collect = () -> {
            a.name = name.getText().toString().trim();
            a.code = code.getText().toString().trim();
            a.steps = steps.getText().toString().trim();
            a.mode = mode[0];
            a.autoPin = autoPin.isChecked();
        };
        LinearLayout buttons = ui.row();
        buttons.setPadding(0, ui.dp(24), 0, 0);
        buttons.addView(ui.filled("Save", v -> {
            collect.run();
            if (a.code.isEmpty()) {
                toast("Add a code first");
                return;
            }
            if (a.autoPin && !PinVault.has(this)) toast("Saved. Save your PIN in Setup to use \"Fill in my PIN\".");
            store.upsert(a);
            closeEditor();
        }), Ui.weight());
        buttons.addView(ui.tonal("Run now", v -> {
            collect.run();
            run(a);
        }), ui.wrap(8));
        form.addView(buttons, ui.full());
        LinearLayout more = ui.row();
        more.setPadding(0, ui.dp(8), 0, 0);
        more.addView(ui.textButton("Add to home screen", v -> {
            collect.run();
            store.upsert(a);
            pinShortcut(a);
        }));
        if (!isNew) {
            TextView del = ui.textButton("Delete", v -> {
                store.delete(a.id);
                closeEditor();
            });
            del.setTextColor(ui.error);
            more.addView(del);
        }
        form.addView(more);
        setPage(page);
    }

    private void closeEditor() {
        hideKeyboard();
        showTab(0);
    }

    // --------------------------------------------------------------------- run

    private void run(Store.Action a) {
        if (a.code == null || a.code.trim().isEmpty()) {
            toast("Enter a USSD code first");
            return;
        }
        if (Store.DIALPAD.equals(a.mode)) {
            Sims.dialPad(this, a.code);
            store.addRun(a.title(), Store.DIALPAD, true, "Opened the dial pad with " + a.code + ".", "");
            return;
        }
        if (!granted(Manifest.permission.CALL_PHONE)) {
            askPerms(() -> run(a));
            return;
        }
        List<String> steps = StepEngine.parse(a.steps);
        List<String> needed = new ArrayList<>();
        if (StepEngine.uses(steps, "{PHONE}")) needed.add("PHONE");
        if (StepEngine.uses(steps, "{AMOUNT}")) needed.add("AMOUNT");
        if (needed.isEmpty()) runWith(a, steps, new HashMap<>());
        else askValues(a, needed, vars -> runWith(a, steps, vars));
    }

    private void runWith(Store.Action a, List<String> steps, Map<String, String> vars) {
        switch (a.mode) {
            case Store.SILENT:
                quick(a, steps, vars);
                return;
            case Store.PHONE:
                if (serviceOn()) start(a, steps, vars, null, Job.Mode.WATCH);
                else {
                    dialSafely(a.code);
                    store.addRun(a.title(), Store.PHONE, true, "Dialled " + a.code + " in the phone's pop-up. Turn on Automation to record the screens.", "");
                }
                return;
            default:
                if (!serviceOn()) {
                    explainAutomation();
                    return;
                }
                Job.Mode m = Store.AUTOPILOT.equals(a.mode) || !store.cover() ? Job.Mode.AUTOPILOT : Job.Mode.CUSTOM;
                boolean wantsPin = StepEngine.uses(steps, StepEngine.PIN) && a.autoPin && PinVault.has(this);
                if (!wantsPin) {
                    start(a, steps, vars, null, m);
                    return;
                }
                authenticate("Unlock your saved PIN", () -> {
                    try {
                        start(a, steps, vars, PinVault.load(this), m);
                    } catch (Exception e) {
                        showError(e);
                    }
                });
        }
    }

    private void quick(Store.Action a, List<String> steps, Map<String, String> vars) {
        toast("Asking the network…");
        Sims.quick(this, a.code, selectedSim(), (ok, reply) -> {
            if (ok) {
                store.addRun(a.title(), Store.SILENT, true, reply, "◀ " + reply.replace("\n", " ⏎ "));
                if (!editorOpen) showTab(tab == 2 ? 2 : 0);
                showReplyDialog(a.title(), reply);
            } else if (serviceOn()) {
                boolean cover = store.cover();
                toast(cover ? "Silent mode can't open this code. Opening it on the custom screen." : "Silent mode can't open this code. Opening the phone's pop-up.");
                start(a, steps, vars, null, cover ? Job.Mode.CUSTOM : Job.Mode.AUTOPILOT);
            } else {
                store.addRun(a.title(), Store.SILENT, false, reply, "✗ " + reply);
                if (!editorOpen) showTab(tab);
                new AlertDialog.Builder(this).setTitle("Silent mode didn't work")
                        .setMessage(reply + "\n\nCodes that open a menu need the custom screen or the phone's pop-up. Turn on Automation in Setup and USSD Lab will switch automatically next time.")
                        .setPositiveButton("Open in pop-up", (d, w) -> dialSafely(a.code))
                        .setNegativeButton("Close", null).show();
            }
        });
    }

    private void start(Store.Action a, List<String> steps, Map<String, String> vars, char[] pin, Job.Mode mode) {
        UssdService svc = UssdService.instance;
        if (svc == null) {
            if (pin != null) Arrays.fill(pin, '\0');
            explainAutomation();
            return;
        }
        Job job = new Job(a.title(), a.code, steps, vars, pin, mode);
        svc.begin(job, j -> runOnUiThread(() -> { if (!editorOpen) showTab(tab); }));
        try {
            Sims.dial(this, a.code, selectedSim());
        } catch (RuntimeException e) {
            svc.onCancel();
            showError(e);
        }
    }

    private void dialSafely(String code) {
        try {
            Sims.dial(this, code, selectedSim());
        } catch (RuntimeException e) {
            showError(e);
        }
    }

    private void showReplyDialog(String title, String reply) {
        List<String> amounts = Reply.amounts(reply);
        String extra = amounts.isEmpty() ? "" : "\n\nFound: " + TextUtils.join(", ", amounts);
        new AlertDialog.Builder(this).setTitle(title).setMessage(reply + extra)
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy", (d, w) -> copy(reply, "Reply copied")).show();
    }

    interface VarsCallback { void onVars(Map<String, String> vars); }

    private void askValues(Store.Action a, List<String> keys, VarsCallback cb) {
        LinearLayout box = ui.col();
        box.setPadding(ui.dp(24), ui.dp(4), ui.dp(24), 0);
        Map<String, EditText> fields = new HashMap<>();
        for (String k : keys) {
            boolean phone = k.equals("PHONE");
            EditText e = ui.field(phone ? "e.g. 677123456" : "e.g. 500", phone ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_NUMBER);
            e.setText(store.var(k));
            fields.put(k, e);
            box.addView(ui.labelled(phone ? "Phone number" : "Amount", e, phone ? "Who it's for. Used wherever the steps say {PHONE}." : "Used wherever the steps say {AMOUNT}."));
        }
        new AlertDialog.Builder(this)
                .setTitle(a.title())
                .setView(box)
                .setPositiveButton("Continue", (d, w) -> {
                    Map<String, String> vars = new HashMap<>();
                    for (String k : keys) {
                        String v = fields.get(k).getText().toString().trim();
                        if (k.equals("PHONE")) v = v.replaceAll("[^0-9+]", "");
                        if (v.isEmpty()) {
                            toast("Fill in every value");
                            return;
                        }
                        store.setVar(k, v);
                        vars.put(k, v);
                    }
                    cb.onVars(vars);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------- shortcuts

    private void pinShortcut(Store.Action a) {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            toast("Your home screen doesn't support shortcuts");
            return;
        }
        Intent i = new Intent(this, MainActivity.class).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_RUN, a.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String label = a.title();
        ShortcutInfo info = new ShortcutInfo.Builder(this, "run_" + a.id)
                .setShortLabel(label.length() > 24 ? label.substring(0, 24) : label)
                .setLongLabel(label + " · " + a.code)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut))
                .setIntent(i)
                .build();
        sm.requestPinShortcut(info, null);
    }

    // -------------------------------------------------------------------- SIMs

    private int selectedSlot() {
        int saved = store.simSlot();
        for (Sims.Sim s : sims) if (s.slot == saved) return saved;
        return sims.isEmpty() ? -1 : sims.get(0).slot;
    }

    private Sims.Sim selectedSim() {
        if (sims.isEmpty()) sims = Sims.list(this);
        int slot = selectedSlot();
        for (Sims.Sim s : sims) if (s.slot == slot) return s;
        return null;
    }

    // --------------------------------------------------------------------- PIN

    interface PinCallback { void onPin(char[] pin); }

    private void askPin(String title, String msg, PinCallback cb) {
        EditText in = ui.field("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout box = ui.col();
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        box.addView(in, ui.full());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    int n = in.getText().length();
                    if (n == 0) return;
                    char[] pin = new char[n];
                    in.getText().getChars(0, n, pin, 0);
                    in.getText().clear();
                    cb.onPin(pin);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void savePinFlow() {
        try {
            PinVault.ensureKey();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Add a screen lock first")
                    .setMessage("Your saved PIN is protected by the phone's screen lock and fingerprint. Add a screen lock in your phone's Settings, then try again.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        askPin("Save your PIN", "It's encrypted on this phone and only unlocks with your fingerprint or screen lock.", pin ->
                authenticate("Confirm to save your PIN", () -> {
                    try {
                        PinVault.save(this, pin);
                        toast("PIN saved");
                    } catch (Exception e) {
                        showError(e);
                    } finally {
                        Arrays.fill(pin, '\0');
                        if (!editorOpen) showTab(tab);
                    }
                }));
    }

    private void authenticate(String title, Runnable ok) {
        afterAuth = ok;
        if (Build.VERSION.SDK_INT >= 28) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this).setTitle(title).setSubtitle("USSD Lab");
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else if (Build.VERSION.SDK_INT == 29) {
                allowCredential29(b);
            } else {
                b.setNegativeButton("Use screen lock", getMainExecutor(), (d, w) -> keyguard(title));
            }
            try {
                b.build().authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { authOk(); }

                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        if (code == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT || code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
                                || code == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                            keyguard(title);
                        } else if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                            toast(msg.toString());
                        }
                    }
                });
                return;
            } catch (RuntimeException ignored) {
            }
        }
        keyguard(title);
    }

    @SuppressWarnings("deprecation")
    private static void allowCredential29(BiometricPrompt.Builder b) { b.setDeviceCredentialAllowed(true); }

    @SuppressWarnings("deprecation")
    private void keyguard(String title) {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent(title, "USSD Lab");
        if (i == null) {
            toast("Add a screen lock to use a saved PIN");
            return;
        }
        startActivityForResult(i, REQ_KEYGUARD);
    }

    private void authOk() {
        Runnable r = afterAuth;
        afterAuth = null;
        if (r != null) r.run();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_KEYGUARD && res == RESULT_OK) authOk();
    }

    // ------------------------------------------------------------ permissions

    private boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    private boolean phoneOk() { return granted(Manifest.permission.CALL_PHONE) && granted(Manifest.permission.READ_PHONE_STATE); }

    private boolean serviceOn() { return UssdService.instance != null; }

    private void askPerms(Runnable then) {
        afterPerms = then;
        requestPermissions(new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE}, REQ_PERMS);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        Runnable r = afterPerms;
        afterPerms = null;
        if (granted(Manifest.permission.CALL_PHONE)) {
            if (r != null) r.run();
            else if (!editorOpen) showTab(tab);
        } else {
            toast("Phone access is needed to dial codes");
            if (!editorOpen) showTab(tab);
        }
    }

    private void explainAutomation() {
        new AlertDialog.Builder(this)
                .setTitle("Turn on Automation")
                .setMessage("Menus, Auto-pilot and the Custom screen need Automation. It lets USSD Lab read the USSD pop-up and type your answers, and only while a run you started is going.")
                .setPositiveButton("Show me how", (d, w) -> showTab(3))
                .setNegativeButton("Not now", null)
                .show();
    }

    // ---------------------------------------------------------------- helpers

    private void copy(String text, String msg) {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("USSD Lab", text));
        toast(msg);
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f == null) return;
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void showError(Exception e) {
        String m = e.getMessage();
        if (e.getClass().getSimpleName().equals("UserNotAuthenticatedException")) m = "The fingerprint check expired. Try again.";
        toast(TextUtils.isEmpty(m) ? e.getClass().getSimpleName() : m);
    }
}
