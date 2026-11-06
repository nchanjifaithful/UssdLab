package app.ussdlab;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
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
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hosts the five tabs, the reply bar, bottom sheets, and everything that starts a run. */
public class MainActivity extends Activity {

    static final String EXTRA_RUN = "run_action";
    static final String EXTRA_TAB = "tab";
    static final int HOME = 0, RECORD = 1, HISTORY = 2, GUIDE = 3, SETTINGS = 4;
    private static final int REQ_PERMS = 1;
    private static final int REQ_KEYGUARD = 2;
    private static final String[] TABS = {"Home", "Record", "History", "Guide", "Settings"};
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_record, R.drawable.ic_history, R.drawable.ic_guide, R.drawable.ic_settings};

    Ui ui;
    Store store;
    private FrameLayout content;
    private LinearLayout replyBar;
    private LinearLayout nav;
    int tab;
    boolean editorOpen;
    private Runnable afterAuth;
    private Runnable afterPerms;
    private final int[] scroll = new int[5];
    private ScrollView currentScroll;

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
        replyBar = ui.row();
        root.addView(replyBar, ui.full());
        nav = ui.row();
        nav.setBackgroundColor(ui.surfaceContainer);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(80)));
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
        else if (tab != HOME) showTab(HOME);
        else super.onBackPressed();
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String runId = i.getStringExtra(EXTRA_RUN);
        int t = i.getIntExtra(EXTRA_TAB, -1);
        i.removeExtra(EXTRA_RUN);
        i.removeExtra(EXTRA_TAB);
        if (t >= 0) tab = t;
        if (runId != null) {
            Store.Action a = store.action(runId);
            if (a != null) content.post(() -> run(a));
        }
    }

    // -------------------------------------------------------------- navigation

    void showTab(int t) {
        if (currentScroll != null && !editorOpen) scroll[tab] = currentScroll.getScrollY();
        tab = t;
        editorOpen = false;
        nav.setVisibility(View.VISIBLE);
        renderNav();
        LinearLayout page = ui.col();
        page.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(28));
        switch (t) {
            case RECORD: RecordPage.build(this, page); break;
            case HISTORY: HistoryPage.build(this, page); break;
            case GUIDE: GuidePage.build(this, page); break;
            case SETTINGS: SettingsPage.build(this, page); break;
            default: HomePage.build(this, page); break;
        }
        setPage(page, scroll[t]);
        renderReplyBar();
    }

    void setPage(View page, int scrollY) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(page);
        content.removeAllViews();
        content.addView(sv);
        currentScroll = sv;
        if (scrollY > 0) sv.post(() -> sv.scrollTo(0, scrollY));
    }

    private void renderNav() {
        nav.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean sel = i == tab;
            LinearLayout item = ui.col();
            item.setGravity(Gravity.CENTER);
            FrameLayout pill = new FrameLayout(this);
            pill.setBackground(ui.ripple(sel ? ui.secondaryContainer : 0x00000000, 16));
            pill.addView(ui.icon(TAB_ICONS[i], sel ? ui.onSecondaryContainer : ui.onSurfaceVariant, 24),
                    new FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER));
            pill.setOnClickListener(v -> showTab(idx));
            item.addView(pill, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(32)));
            TextView l = ui.text(TABS[i], 12, sel ? ui.onSurface : ui.onSurfaceVariant);
            l.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            l.setGravity(Gravity.CENTER);
            l.setPadding(0, ui.dp(4), 0, 0);
            item.addView(l);
            item.setOnClickListener(v -> showTab(idx));
            item.setContentDescription(TABS[i] + (sel ? ", selected" : ""));
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    /** The latest USSD reply, docked above the tabs like a "now playing" bar. */
    void renderReplyBar() {
        replyBar.removeAllViews();
        Store.Run r = store.lastRun();
        if (r == null || tab == HISTORY || editorOpen) {
            replyBar.setVisibility(View.GONE);
            return;
        }
        replyBar.setVisibility(View.VISIBLE);
        replyBar.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(8));
        LinearLayout card = ui.row();
        card.setPadding(ui.dp(10), ui.dp(8), ui.dp(4), ui.dp(8));
        card.setBackground(ui.ripple(ui.surfaceHigh, 20));
        card.setElevation(ui.dp(2));
        card.addView(statusTile(r, 36));
        LinearLayout t = ui.col();
        t.setPadding(ui.dp(12), 0, ui.dp(4), 0);
        TextView title = ui.text(r.title + "  ·  " + ago(r.time), 12, ui.onSurfaceVariant);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(title);
        TextView reply = ui.text(r.result.replace('\n', ' '), 14, ui.onSurface);
        reply.setSingleLine(true);
        reply.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(reply);
        card.addView(t, Ui.weight());
        card.addView(ui.iconButton(R.drawable.ic_copy, ui.onSurfaceVariant, 0x00000000, "Copy reply", v -> copy(r.result, "Reply copied")));
        card.setOnClickListener(v -> HistoryPage.showDetail(this, r));
        card.setContentDescription("Last reply from " + r.title + ": " + r.result);
        replyBar.addView(card, Ui.weight());
    }

    View statusTile(Store.Run r, int size) {
        boolean heard = "listen".equals(r.mode);
        int icon = heard ? R.drawable.ic_eye : r.ok ? R.drawable.ic_check : R.drawable.ic_alert;
        int bg = heard ? ui.secondaryContainer : r.ok ? ui.successContainer : ui.errorContainer;
        int fg = heard ? ui.onSecondaryContainer : r.ok ? ui.onSuccessContainer : ui.onErrorContainer;
        return ui.iconTile(icon, bg, fg, size);
    }

    static String ago(long time) {
        return DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
    }

    /** Large page title with an optional subtitle and trailing buttons. */
    void appBar(LinearLayout page, String title, String subtitle, View... trailing) {
        LinearLayout r = ui.row();
        r.setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(12));
        LinearLayout t = ui.col();
        TextView h = ui.text(title, 30, ui.onSurface);
        t.addView(h);
        if (subtitle != null) {
            TextView s = ui.body(subtitle);
            s.setPadding(0, ui.dp(2), 0, 0);
            t.addView(s);
        }
        r.addView(t, Ui.weight());
        for (View v : trailing) if (v != null) r.addView(v);
        page.addView(r, ui.full());
    }

    // ------------------------------------------------------------------ editor

    void openEditor(Store.Action a, boolean isNew) {
        if (currentScroll != null) scroll[tab] = currentScroll.getScrollY();
        editorOpen = true;
        nav.setVisibility(View.GONE);
        replyBar.setVisibility(View.GONE);
        LinearLayout page = ui.col();
        page.setPadding(ui.dp(8), ui.dp(8), ui.dp(16), ui.dp(32));
        EditorPage.build(this, page, a, isNew);
        setPage(page, 0);
    }

    void closeEditor() {
        View f = getCurrentFocus();
        if (f != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
        editorOpen = false;
        showTab(tab);
    }

    // ------------------------------------------------------------ action sheet

    void actionSheet(Store.Action a) {
        LinearLayout box = ui.col();
        LinearLayout head = ui.row();
        head.addView(ui.iconTile(Ui.modeIcon(a.mode), ui.primaryContainer, ui.onPrimaryContainer, 44));
        LinearLayout ht = ui.col();
        ht.setPadding(ui.dp(14), 0, 0, 0);
        ht.addView(ui.title(a.title()));
        int n = StepEngine.parse(a.steps).size();
        ht.addView(ui.small(a.code + "  ·  " + Store.modeName(a.mode) + (n > 0 ? "  ·  " + n + " steps" : "")));
        head.addView(ht, Ui.weight());
        box.addView(head, ui.full());
        Dialog[] d = new Dialog[1];
        LinearLayout g = ui.group(
                ui.listRow(R.drawable.ic_play, "Run", null, null, v -> { d[0].dismiss(); run(a); }),
                ui.listRow(R.drawable.ic_edit, "Edit", "Name, code, steps and options", null, v -> { d[0].dismiss(); openEditor(a, false); }),
                ui.listRow(R.drawable.ic_widgets, a.onWidget ? "Remove from widget" : "Show on widget", "Up to four commands appear on the home-screen widget", null, v -> {
                    d[0].dismiss();
                    a.onWidget = !a.onWidget;
                    store.upsert(a);
                    Widget.refresh(this);
                    toast(a.onWidget ? "Added to the widget" : "Removed from the widget");
                    showTab(tab);
                }),
                ui.listRow(R.drawable.ic_star, "Add to home screen", "A one-tap shortcut icon", null, v -> { d[0].dismiss(); pinShortcut(a); }),
                ui.listRow(R.drawable.ic_share, "Share", "Send this command to another phone", null, v -> {
                    d[0].dismiss();
                    List<Store.Action> one = new ArrayList<>();
                    one.add(a);
                    share(a.title(), Store.export(one));
                }),
                ui.listRow(R.drawable.ic_delete, "Delete", null, null, v -> {
                    d[0].dismiss();
                    store.delete(a.id);
                    Widget.refresh(this);
                    showTab(tab);
                }));
        box.addView(g, ui.full(16));
        d[0] = Sheet.show(this, ui, box);
    }

    void simSheet() {
        List<Sims.Sim> sims = Sims.list(this);
        LinearLayout box = ui.col();
        box.addView(ui.title("Send codes from"));
        TextView s = ui.small("Silent requests and dialled codes use this SIM.");
        s.setPadding(0, ui.dp(4), 0, ui.dp(14));
        box.addView(s);
        if (sims.isEmpty()) {
            box.addView(ui.body(phoneOk() ? "No SIM found. The phone's default SIM will be used." : "Allow phone access in Settings to choose a SIM."));
            Sheet.show(this, ui, box);
            return;
        }
        Dialog[] d = new Dialog[1];
        Sims.Sim cur = Sims.selected(this);
        View[] rows = new View[sims.size()];
        for (int i = 0; i < sims.size(); i++) {
            Sims.Sim sim = sims.get(i);
            boolean sel = cur != null && cur.slot == sim.slot;
            rows[i] = ui.listRow(R.drawable.ic_sim, sim.label, null, sel ? ui.icon(R.drawable.ic_check, ui.primary, 22) : null, v -> {
                store.setSimSlot(sim.slot);
                d[0].dismiss();
                showTab(tab);
            });
        }
        box.addView(ui.group(rows), ui.full());
        d[0] = Sheet.show(this, ui, box);
    }

    String simLabel() {
        Sims.Sim s = Sims.selected(this);
        return s == null ? "Default SIM" : s.label;
    }

    // --------------------------------------------------------------------- run

    void run(Store.Action a) { run(a, null); }

    /** @param recordName non-null for "Record a command": always uses the custom sheet. */
    void run(Store.Action a, String recordName) {
        if (a.code == null || a.code.trim().isEmpty()) {
            toast("Enter a USSD code first");
            return;
        }
        if (Store.DIALPAD.equals(a.mode) && recordName == null) {
            Sims.dialPad(this, a.code);
            store.addRun(a.title(), Store.DIALPAD, true, "Opened the dial pad with " + a.code + ".", "", a.id);
            return;
        }
        if (!granted(Manifest.permission.CALL_PHONE)) {
            askPerms(() -> run(a, recordName));
            return;
        }
        List<String> steps = StepEngine.parse(recordName != null ? "" : a.steps);
        List<String> needed = new ArrayList<>();
        if (StepEngine.uses(steps, "{PHONE}")) needed.add("PHONE");
        if (StepEngine.uses(steps, "{AMOUNT}")) needed.add("AMOUNT");
        if (needed.isEmpty()) runWith(a, steps, new HashMap<>(), recordName);
        else askValues(a, needed, vars -> runWith(a, steps, vars, recordName));
    }

    private void runWith(Store.Action a, List<String> steps, Map<String, String> vars, String recordName) {
        if (recordName != null) {
            if (!serviceOn()) {
                automationSheet();
                return;
            }
            start(a, steps, vars, null, Job.Mode.CUSTOM, recordName);
            return;
        }
        switch (a.mode) {
            case Store.SILENT:
                quick(a, steps, vars);
                return;
            case Store.PHONE:
                if (serviceOn()) start(a, steps, vars, null, Job.Mode.WATCH, null);
                else {
                    dialSafely(a.code);
                    store.addRun(a.title(), Store.PHONE, true, "Dialled " + a.code + " in the phone's pop-up. Turn on Automation to record the screens.", "", a.id);
                }
                return;
            default:
                if (!serviceOn()) {
                    automationSheet();
                    return;
                }
                Job.Mode m = Store.AUTOPILOT.equals(a.mode) || !store.cover() ? Job.Mode.AUTOPILOT : Job.Mode.CUSTOM;
                boolean wantsPin = StepEngine.uses(steps, StepEngine.PIN) && a.autoPin && PinVault.has(this);
                if (!wantsPin) {
                    start(a, steps, vars, null, m, null);
                    return;
                }
                authenticate("Unlock your saved PIN", () -> {
                    try {
                        start(a, steps, vars, PinVault.load(this), m, null);
                    } catch (Exception e) {
                        showError(e);
                    }
                });
        }
    }

    private void quick(Store.Action a, List<String> steps, Map<String, String> vars) {
        toast("Asking the network…");
        Sims.quick(this, a.code, Sims.selected(this), (ok, reply) -> {
            if (ok) {
                store.addRun(a.title(), Store.SILENT, true, reply, "◀ " + reply.replace("\n", " ⏎ "), a.id);
                Widget.refresh(this);
                if (!editorOpen) showTab(tab);
                Store.Run r = store.lastRun();
                if (r != null) HistoryPage.showDetail(this, r);
            } else if (serviceOn()) {
                boolean cover = store.cover();
                toast(cover ? "This code opens a menu. Showing it on USSD Lab's screen." : "This code opens a menu. Showing the phone's pop-up.");
                start(a, steps, vars, null, cover ? Job.Mode.CUSTOM : Job.Mode.AUTOPILOT, null);
            } else {
                store.addRun(a.title(), Store.SILENT, false, reply + " Turn on Automation so menus open on their own.", "✗ " + reply, a.id);
                Widget.refresh(this);
                if (!editorOpen) showTab(tab);
                Store.Run r = store.lastRun();
                if (r != null) HistoryPage.showDetail(this, r);
            }
        });
    }

    private void start(Store.Action a, List<String> steps, Map<String, String> vars, char[] pin, Job.Mode mode, String recordName) {
        UssdService svc = UssdService.instance;
        if (svc == null) {
            if (pin != null) Arrays.fill(pin, '\0');
            automationSheet();
            return;
        }
        Job job = new Job(recordName != null ? recordName : a.title(), a.code, steps, vars, pin, mode);
        job.actionId = a.id;
        job.recordName = recordName;
        svc.begin(job, j -> runOnUiThread(() -> { if (!editorOpen) showTab(tab); }), simLabel());
        try {
            Sims.dial(this, a.code, Sims.selected(this));
        } catch (RuntimeException e) {
            svc.onCancel();
            showError(e);
        }
    }

    private void dialSafely(String code) {
        try {
            Sims.dial(this, code, Sims.selected(this));
        } catch (RuntimeException e) {
            showError(e);
        }
    }

    interface VarsCallback { void onVars(Map<String, String> vars); }

    private void askValues(Store.Action a, List<String> keys, VarsCallback cb) {
        LinearLayout box = ui.col();
        box.addView(ui.title(a.title()));
        TextView s = ui.small("Fill these in for this run. They're remembered for next time.");
        s.setPadding(0, ui.dp(4), 0, ui.dp(4));
        box.addView(s);
        Map<String, EditText> fields = new HashMap<>();
        for (String k : keys) {
            boolean phone = k.equals("PHONE");
            EditText e = ui.field(phone ? "e.g. 677123456" : "e.g. 500", phone ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_NUMBER);
            e.setText(store.var(k));
            fields.put(k, e);
            box.addView(ui.labelled(phone ? "Phone number" : "Amount", e, null));
        }
        Dialog[] d = new Dialog[1];
        TextView go = ui.iconText(ui.filled("Continue", v -> {
            Map<String, String> vars = new HashMap<>();
            for (String k : keys) {
                String val = fields.get(k).getText().toString().trim();
                if (k.equals("PHONE")) val = val.replaceAll("[^0-9+]", "");
                if (val.isEmpty()) {
                    toast("Fill in every value");
                    return;
                }
                store.setVar(k, val);
                vars.put(k, val);
            }
            d[0].dismiss();
            cb.onVars(vars);
        }), R.drawable.ic_play);
        box.addView(go, ui.full(20));
        d[0] = Sheet.show(this, ui, box);
    }

    // -------------------------------------------------------------- shortcuts

    void pinShortcut(Store.Action a) {
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

    // --------------------------------------------------------------------- PIN

    void pinSheet() {
        boolean has = PinVault.has(this);
        LinearLayout box = ui.col();
        LinearLayout head = ui.row();
        head.addView(ui.iconTile(R.drawable.ic_lock, ui.primaryContainer, ui.onPrimaryContainer, 44));
        LinearLayout ht = ui.col();
        ht.setPadding(ui.dp(14), 0, 0, 0);
        ht.addView(ui.title("Saved PIN"));
        ht.addView(ui.small(has ? "Saved and encrypted on this phone" : "Not saved"));
        head.addView(ht, Ui.weight());
        box.addView(head);
        TextView b = ui.body("You don't need to save your PIN: when a menu asks for it, you type it on USSD Lab's secure PIN screen and it's never kept.\n\nSave it only if you want commands with \"Fill in my PIN\" to finish by themselves. It's encrypted with a key in the phone's secure hardware and unlocks only for 30 seconds after your fingerprint or screen lock.\n\nCheck that your mobile money provider allows this.");
        b.setPadding(0, ui.dp(14), 0, ui.dp(18));
        box.addView(b);
        Dialog[] d = new Dialog[1];
        LinearLayout r = ui.row();
        r.addView(ui.filled(has ? "Change PIN" : "Save PIN", v -> { d[0].dismiss(); savePinFlow(); }), Ui.weight());
        if (has) {
            TextView rm = ui.tonal("Remove", v -> {
                d[0].dismiss();
                PinVault.clear(this);
                toast("PIN removed");
                showTab(tab);
            });
            LinearLayout.LayoutParams lp = Ui.weight();
            lp.setMarginStart(ui.dp(10));
            r.addView(rm, lp);
        }
        box.addView(r, ui.full());
        d[0] = Sheet.show(this, ui, box);
    }

    private void savePinFlow() {
        try {
            PinVault.ensureKey();
        } catch (Exception e) {
            infoSheet(R.drawable.ic_lock, "Add a screen lock first", "Your saved PIN is protected by the phone's screen lock and fingerprint. Add a screen lock in your phone's Settings, then try again.");
            return;
        }
        LinearLayout box = ui.col();
        box.addView(ui.title("Save your PIN"));
        EditText in = ui.field("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        box.addView(ui.labelled("PIN", in, "Next, confirm with your fingerprint or screen lock."));
        Dialog[] d = new Dialog[1];
        box.addView(ui.iconText(ui.filled("Save", v -> {
            int n = in.getText().length();
            if (n == 0) return;
            char[] pin = new char[n];
            in.getText().getChars(0, n, pin, 0);
            in.getText().clear();
            d[0].dismiss();
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
            });
        }), R.drawable.ic_lock), ui.full(20));
        d[0] = Sheet.show(this, ui, box);
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

    boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    boolean phoneOk() { return granted(Manifest.permission.CALL_PHONE) && granted(Manifest.permission.READ_PHONE_STATE); }

    boolean serviceOn() { return UssdService.instance != null; }

    void askPerms(Runnable then) {
        afterPerms = then;
        requestPermissions(new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE}, REQ_PERMS);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        Runnable r = afterPerms;
        afterPerms = null;
        if (granted(Manifest.permission.CALL_PHONE)) {
            Widget.refresh(this);
            if (r != null) r.run();
            else if (!editorOpen) showTab(tab);
        } else {
            toast("Phone access is needed to dial codes");
            if (!editorOpen) showTab(tab);
        }
    }

    void automationSheet() {
        LinearLayout box = ui.col();
        LinearLayout head = ui.row();
        head.addView(ui.iconTile(R.drawable.ic_accessibility, ui.primaryContainer, ui.onPrimaryContainer, 44));
        LinearLayout ht = ui.col();
        ht.setPadding(ui.dp(14), 0, 0, 0);
        ht.addView(ui.title("Turn on Automation"));
        ht.addView(ui.small(serviceOn() ? "It's on" : "Needed for menus, recording and the widget"));
        head.addView(ht, Ui.weight());
        box.addView(head);
        String how = "Automation lets USSD Lab read the USSD pop-up and type your answers. It only acts while a run you started is going; with Listen mode on it only copies text.\n\n"
                + "1.  Tap Open settings\n2.  Choose Installed apps (or Downloaded apps)\n3.  Tap USSD Lab and turn it on";
        if (Build.VERSION.SDK_INT >= 33) how += "\n\nSwitch greyed out? Tap App info → ⋮ (top right) → Allow restricted settings, then try again. Android does this for apps installed from a file.";
        TextView b = ui.body(how);
        b.setPadding(0, ui.dp(14), 0, ui.dp(18));
        box.addView(b);
        Dialog[] d = new Dialog[1];
        LinearLayout r = ui.row();
        r.addView(ui.filled("Open settings", v -> {
            d[0].dismiss();
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }), Ui.weight());
        TextView info = ui.tonal("App info", v -> {
            d[0].dismiss();
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", getPackageName(), null)));
        });
        LinearLayout.LayoutParams lp = Ui.weight();
        lp.setMarginStart(ui.dp(10));
        r.addView(info, lp);
        box.addView(r, ui.full());
        d[0] = Sheet.show(this, ui, box);
    }

    void infoSheet(int icon, String title, String body) {
        LinearLayout box = ui.col();
        LinearLayout head = ui.row();
        head.addView(ui.iconTile(icon, ui.secondaryContainer, ui.onSecondaryContainer, 44));
        TextView t = ui.title(title);
        t.setPadding(ui.dp(14), 0, 0, 0);
        head.addView(t, Ui.weight());
        box.addView(head);
        TextView b = ui.body(body);
        b.setPadding(0, ui.dp(14), 0, ui.dp(18));
        box.addView(b);
        Dialog[] d = new Dialog[1];
        box.addView(ui.filled("OK", v -> d[0].dismiss()), ui.full());
        d[0] = Sheet.show(this, ui, box);
    }

    // ---------------------------------------------------------------- helpers

    void copy(String text, String msg) {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("USSD Lab", text));
        toast(msg);
    }

    void share(String subject, String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Share"));
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    void showError(Exception e) {
        String m = e.getMessage();
        if (e.getClass().getSimpleName().equals("UserNotAuthenticatedException")) m = "The fingerprint check expired. Try again.";
        toast(TextUtils.isEmpty(m) ? e.getClass().getSimpleName() : m);
    }
}
