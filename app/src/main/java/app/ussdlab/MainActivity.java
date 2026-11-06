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
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 1;
    private static final int REQ_KEYGUARD = 2;

    private Store store;
    private boolean dark;
    private int fg, muted, cardBg, accent;

    private TextView statusView, pinStatus, resultTitle, resultView, logView;
    private RadioGroup simGroup;
    private LinearLayout recipeList;
    private EditText nameIn, codeIn, stepsIn, phoneIn, amountIn;
    private CheckBox dialCb, stopCb, coverCb;
    private List<Sims.Sim> sims;
    private List<Store.Recipe> recipes;
    private int editing = -1;

    private Runnable afterAuth;
    private Runnable afterPerms;

    // --------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = Store.get(this);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        fg = dark ? 0xFFE8EAED : 0xFF1F1F1F;
        muted = dark ? 0xFF9AA0A6 : 0xFF5F6368;
        cardBg = dark ? 0xFF2A2B2E : 0xFFF1F3F4;
        accent = dark ? 0xFF8AB4F8 : 0xFF1A73E8;
        getWindow().getDecorView().setBackgroundColor(dark ? 0xFF161718 : 0xFFFFFFFF);
        setContentView(build());
        recipes = store.recipes();
        renderRecipes();
        if (!recipes.isEmpty()) load(0);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshSims();
        refreshPin();
        showLastRun();
    }

    @Override protected void onPause() {
        super.onPause();
        store.setVars(phoneIn.getText().toString(), amountIn.getText().toString());
    }

    // --------------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 13, accent, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        t.setPadding(0, dp(22), 0, dp(6));
        return t;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextColor(fg);
        e.setHintTextColor(muted);
        e.setTextSize(16);
        return e;
    }

    private Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = primary ? new Button(this) : new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        if (primary) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(accent);
            d.setCornerRadius(dp(24));
            b.setBackground(d);
            b.setTextColor(dark ? 0xFF0B1F3A : 0xFFFFFFFF);
            b.setTextSize(16);
        } else {
            b.setTextColor(accent);
        }
        return b;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(cardBg);
        d.setCornerRadius(dp(14));
        l.setBackground(d);
        return l;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(20), dp(18), dp(40));
        scroll.addView(col);

        col.addView(text("USSD Lab", 26, fg, true));
        col.addView(text("Run quick codes silently, or let the app walk a MoMo menu for you.", 14, muted, false));

        statusView = text("", 14, fg, false);
        statusView.setPadding(0, dp(12), 0, 0);
        statusView.setOnClickListener(v -> fixSetup());
        col.addView(statusView);

        col.addView(header("SIM"));
        simGroup = new RadioGroup(this);
        simGroup.setOrientation(RadioGroup.VERTICAL);
        simGroup.setOnCheckedChangeListener((g, id) -> store.setSimSlot(id == View.NO_ID ? -1 : id - 1000));
        col.addView(simGroup);

        col.addView(header("Saved codes"));
        recipeList = new LinearLayout(this);
        recipeList.setOrientation(LinearLayout.VERTICAL);
        col.addView(recipeList);

        col.addView(header("Code"));
        LinearLayout ed = card();
        nameIn = field("Name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ed.addView(nameIn);
        codeIn = field("USSD code, e.g. *155#", InputType.TYPE_CLASS_PHONE);
        codeIn.setTextSize(20);
        ed.addView(codeIn);
        stepsIn = field("Menu steps (leave empty for quick codes)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        stepsIn.setTypeface(Typeface.MONOSPACE);
        stepsIn.setMinLines(2);
        ed.addView(stepsIn);
        TextView help = text("Separate steps with commas. 1 = reply 1 · @transfer = pick the option containing \"transfer\" · {PHONE} {AMOUNT} {PIN} = the values below.", 12, muted, false);
        help.setPadding(dp(4), 0, dp(4), dp(6));
        ed.addView(help);
        dialCb = new CheckBox(this);
        dialCb.setText("Use the dialler (needed for menus; optional for quick codes)");
        dialCb.setTextColor(fg);
        ed.addView(dialCb);
        stopCb = new CheckBox(this);
        stopCb.setText("Stop before the PIN — I'll type it myself");
        stopCb.setTextColor(fg);
        ed.addView(stopCb);
        LinearLayout er = row();
        er.addView(button("Save", false, v -> saveRecipe()));
        er.addView(button("Save as new", false, v -> { editing = -1; saveRecipe(); }));
        er.addView(button("Delete", false, v -> deleteRecipe()));
        ed.addView(er);
        col.addView(ed);

        col.addView(header("Values"));
        LinearLayout vals = row();
        phoneIn = field("Phone {PHONE}", InputType.TYPE_CLASS_PHONE);
        phoneIn.setText(store.phone());
        amountIn = field("Amount {AMOUNT}", InputType.TYPE_CLASS_NUMBER);
        amountIn.setText(store.amount());
        vals.addView(phoneIn, weight());
        vals.addView(amountIn, weight());
        col.addView(vals);
        coverCb = new CheckBox(this);
        coverCb.setText("Cover the menu while it runs");
        coverCb.setTextColor(fg);
        coverCb.setChecked(store.cover());
        coverCb.setOnCheckedChangeListener((c, on) -> store.setCover(on));
        col.addView(coverCb);

        Button run = button("Run", true, v -> run());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        rlp.topMargin = dp(10);
        col.addView(run, rlp);

        col.addView(header("Result"));
        LinearLayout res = card();
        resultTitle = text("", 12, muted, false);
        res.addView(resultTitle);
        resultView = text("Nothing yet.", 16, fg, false);
        resultView.setTextIsSelectable(true);
        resultView.setPadding(0, dp(4), 0, 0);
        res.addView(resultView);
        col.addView(res);

        col.addView(header("PIN"));
        LinearLayout pin = card();
        pinStatus = text("", 14, fg, false);
        pin.addView(pinStatus);
        LinearLayout pr = row();
        pr.addView(button("Save PIN", false, v -> savePinFlow()));
        pr.addView(button("Remove", false, v -> {
            PinVault.clear(this);
            refreshPin();
            toast("PIN removed");
        }));
        pin.addView(pr);
        col.addView(pin);

        LinearLayout lh = row();
        TextView lt = header("Log");
        lh.addView(lt, weight());
        lh.addView(button("Copy", false, v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("USSD log", store.log()));
            toast("Log copied");
        }));
        lh.addView(button("Clear", false, v -> {
            store.clearLog();
            showLastRun();
        }));
        col.addView(lh);
        logView = text("", 12, muted, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        col.addView(logView);

        return scroll;
    }

    // ------------------------------------------------------------ state views

    private boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    private boolean serviceOn() { return UssdService.instance != null; }

    private void refreshStatus() {
        boolean perms = granted(Manifest.permission.CALL_PHONE) && granted(Manifest.permission.READ_PHONE_STATE);
        StringBuilder s = new StringBuilder();
        s.append(perms ? "✓ Phone permission" : "✗ Phone permission — tap to allow").append('\n');
        s.append(serviceOn() ? "✓ Menu automation is on" : "✗ Menu automation is off — tap to turn on (only needed for menus)");
        statusView.setText(s);
    }

    private void fixSetup() {
        if (!granted(Manifest.permission.CALL_PHONE) || !granted(Manifest.permission.READ_PHONE_STATE)) {
            askPerms(null);
        } else if (!serviceOn()) {
            explainAccessibility();
        }
    }

    private void refreshSims() {
        sims = Sims.list(this);
        simGroup.removeAllViews();
        int saved = store.simSlot();
        if (sims.isEmpty()) {
            TextView t = text(granted(Manifest.permission.READ_PHONE_STATE) ? "No SIM found — the default SIM will be used." : "Allow phone permission to choose a SIM.", 14, muted, false);
            simGroup.addView(t);
            return;
        }
        for (Sims.Sim s : sims) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + s.slot);
            rb.setText(s.label);
            rb.setTextColor(fg);
            simGroup.addView(rb);
        }
        int pick = 1000 + (saved >= 0 ? saved : sims.get(0).slot);
        if (simGroup.findViewById(pick) == null) pick = 1000 + sims.get(0).slot;
        simGroup.check(pick);
    }

    private Sims.Sim selectedSim() {
        if (sims == null) return null;
        int id = simGroup.getCheckedRadioButtonId();
        for (Sims.Sim s : sims) if (1000 + s.slot == id) return s;
        return sims.isEmpty() ? null : sims.get(0);
    }

    private void refreshPin() {
        pinStatus.setText(PinVault.has(this)
                ? "A PIN is saved, encrypted on this phone. It unlocks only with your fingerprint or screen lock."
                : "No PIN saved. When a step needs {PIN}, you'll be asked for it (it isn't kept).");
    }

    private void showLastRun() {
        String r = store.result();
        resultTitle.setText(store.resultTitle());
        resultView.setText(r.isEmpty() ? "Nothing yet." : r);
        String log = store.log();
        logView.setText(log.isEmpty() ? "Each run's screens and replies appear here (the PIN is never logged)." : log);
    }

    private void renderRecipes() {
        recipeList.removeAllViews();
        for (int i = 0; i < recipes.size(); i++) {
            final int idx = i;
            Store.Recipe r = recipes.get(i);
            LinearLayout c = card();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            c.setLayoutParams(lp);
            TextView title = text(r.name.isEmpty() ? r.code : r.name, 15, fg, true);
            c.addView(title);
            String kind = StepEngine.parse(r.steps).isEmpty() ? (r.dialler ? "dialler · watch" : "quick") : "menu · " + StepEngine.parse(r.steps).size() + " steps";
            c.addView(text(r.code + "   " + kind, 13, muted, false));
            c.setOnClickListener(v -> load(idx));
            if (idx == editing) {
                GradientDrawable d = (GradientDrawable) c.getBackground();
                d.setStroke(dp(2), accent);
            }
            recipeList.addView(c);
        }
    }

    private void load(int i) {
        editing = i;
        Store.Recipe r = recipes.get(i);
        nameIn.setText(r.name);
        codeIn.setText(r.code);
        stepsIn.setText(r.steps);
        dialCb.setChecked(r.dialler);
        stopCb.setChecked(r.stopBeforePin);
        renderRecipes();
    }

    private void saveRecipe() {
        Store.Recipe r = Store.Recipe.make(nameIn.getText().toString().trim(), codeIn.getText().toString().trim(),
                stepsIn.getText().toString().trim(), dialCb.isChecked(), stopCb.isChecked());
        if (r.code.isEmpty()) {
            toast("Enter a code first");
            return;
        }
        if (editing >= 0 && editing < recipes.size()) recipes.set(editing, r);
        else {
            recipes.add(r);
            editing = recipes.size() - 1;
        }
        store.saveRecipes(recipes);
        renderRecipes();
        toast("Saved");
    }

    private void deleteRecipe() {
        if (editing < 0 || editing >= recipes.size()) return;
        recipes.remove(editing);
        editing = -1;
        store.saveRecipes(recipes);
        renderRecipes();
    }

    // -------------------------------------------------------------------- run

    private void run() {
        final String code = codeIn.getText().toString().trim();
        if (code.isEmpty()) {
            toast("Enter a USSD code");
            return;
        }
        if (!granted(Manifest.permission.CALL_PHONE)) {
            askPerms(this::run);
            return;
        }
        store.setVars(phoneIn.getText().toString(), amountIn.getText().toString());
        List<String> steps = StepEngine.parse(stepsIn.getText().toString());
        String title = nameIn.getText().toString().trim().isEmpty() ? code : nameIn.getText().toString().trim();
        if (steps.isEmpty() && !dialCb.isChecked()) runQuick(title, code);
        else runMenu(title, code, steps);
    }

    private void runQuick(String title, String code) {
        resultTitle.setText(title);
        resultView.setText("Waiting for the network…");
        final Job log = new Job(title, code, Arrays.asList(), new HashMap<>(), null, false, false);
        Sims.quick(this, code, selectedSim(), (ok, reply) -> {
            log.log((ok ? "◀ " : "✗ ") + reply.replace("\n", " ⏎ "));
            store.saveRun(title + " (quick)", reply, log.log());
            showLastRun();
        });
    }

    private void runMenu(String title, String code, List<String> steps) {
        if (!serviceOn()) {
            explainAccessibility();
            return;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("PHONE", phoneIn.getText().toString().replaceAll("[^0-9+]", ""));
        vars.put("AMOUNT", amountIn.getText().toString().trim());
        for (String k : new String[]{"PHONE", "AMOUNT"}) {
            if (StepEngine.uses(steps, "{" + k + "}") && vars.get(k).isEmpty()) {
                toast("Fill in " + k.toLowerCase() + " first");
                return;
            }
        }
        boolean needsPin = StepEngine.uses(steps, StepEngine.PIN) && !stopCb.isChecked();
        if (!needsPin) {
            start(title, code, steps, vars, null);
        } else if (PinVault.has(this)) {
            authenticate("Unlock your PIN", () -> {
                try {
                    start(title, code, steps, vars, PinVault.load(this));
                } catch (Exception e) {
                    showError(e);
                    refreshPin();
                }
            });
        } else {
            askPin("Enter PIN for this run", "Used once, then forgotten.", pin -> start(title, code, steps, vars, pin));
        }
    }

    private void start(String title, String code, List<String> steps, Map<String, String> vars, char[] pin) {
        UssdService svc = UssdService.instance;
        if (svc == null) {
            if (pin != null) Arrays.fill(pin, '\0');
            explainAccessibility();
            return;
        }
        Job job = new Job(title, code, steps, vars, pin, stopCb.isChecked(), coverCb.isChecked());
        svc.begin(job, j -> runOnUiThread(this::showLastRun));
        resultTitle.setText(title);
        resultView.setText("Running…");
        try {
            Sims.dial(this, code, selectedSim());
        } catch (RuntimeException e) {
            svc.cancel();
            showError(e);
        }
    }

    // ------------------------------------------------------------------- PIN

    interface PinCallback { void onPin(char[] pin); }

    private void askPin(String title, String msg, PinCallback cb) {
        EditText in = field("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setView(box)
                .setPositiveButton("OK", (d, w) -> {
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
            new AlertDialog.Builder(this).setTitle("Set a screen lock first")
                    .setMessage("The PIN is protected by your phone's screen lock and fingerprint. Add a screen lock in Settings, then try again.\n\n(" + e.getClass().getSimpleName() + ")")
                    .setPositiveButton("OK", null).show();
            return;
        }
        askPin("Save MoMo PIN", "Stored encrypted on this phone and unlocked with your fingerprint or screen lock. Check that your provider's terms allow this.", pin ->
                authenticate("Confirm to save your PIN", () -> {
                    try {
                        PinVault.save(this, pin);
                        toast("PIN saved");
                    } catch (Exception e) {
                        showError(e);
                    } finally {
                        Arrays.fill(pin, '\0');
                        refreshPin();
                    }
                }));
    }

    private void authenticate(String title, Runnable ok) {
        afterAuth = ok;
        if (Build.VERSION.SDK_INT >= 28) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setSubtitle("USSD Lab");
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
                        if (Build.VERSION.SDK_INT == 28 && code == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT
                                || code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
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
            toast("Set a screen lock to use a saved PIN");
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

    private void askPerms(Runnable then) {
        afterPerms = then;
        requestPermissions(new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE}, REQ_PERMS);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        refreshStatus();
        refreshSims();
        Runnable r = afterPerms;
        afterPerms = null;
        if (r != null && granted(Manifest.permission.CALL_PHONE)) r.run();
    }

    private void explainAccessibility() {
        String msg = "To walk through multi-step menus, USSD Lab reads each USSD screen and types your reply. "
                + "It only does this while a run you started is in progress, and ignores everything else.\n\n"
                + "In the next screen open \"Installed apps\" (or \"Downloaded apps\") → USSD Lab → turn it on.";
        if (Build.VERSION.SDK_INT >= 33) {
            msg += "\n\nIf the switch is greyed out: open App info for USSD Lab → ⋮ menu → \"Allow restricted settings\", then try again.";
        }
        new AlertDialog.Builder(this)
                .setTitle("Turn on menu automation")
                .setMessage(msg)
                .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNeutralButton("App info", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", getPackageName(), null))))
                .setNegativeButton("Not now", null)
                .show();
    }

    // ---------------------------------------------------------------- helpers

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void showError(Exception e) {
        String m = e.getMessage();
        if (e.getClass().getSimpleName().equals("UserNotAuthenticatedException")) m = "Authentication expired — try again.";
        toast(TextUtils.isEmpty(m) ? e.getClass().getSimpleName() : m);
    }
}
