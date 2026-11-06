package app.ussdlab;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads USSD pop-ups and answers them. It only acts while a run you started is in progress.
 * With Listen mode on it also copies USSD pop-up text into History, without touching anything.
 */
public class UssdService extends AccessibilityService implements Skin.Callbacks {

    public interface Listener { void onJobFinished(Job job); }

    static UssdService instance;
    static final String EXTRA_EDIT = "edit_action";

    private final Handler h = new Handler(Looper.getMainLooper());
    private Skin skin;
    private Job job;
    private Listener listener;

    private String lastActedText;
    private long lastActedAt;
    private String lastLogged;
    private boolean sawDialog;
    /** The custom screen is waiting for the user's answer to this screen text. */
    private String waitingOn;
    private boolean waitingPinStep;
    private String lastHeard;
    private String savedId;

    private static final long SETTLE_MS = 350;
    private static final long STUCK_MS = 8000;
    private static final long STEP_TIMEOUT_MS = 30000;
    private static final long USER_TIMEOUT_MS = 180000;
    private static final long GONE_MS = 2500;

    private final Runnable process = this::processScreen;
    private final Runnable listenRun = this::listen;
    private final Runnable stepTimeout = () -> {
        if (!sawDialog) finish(false, "No USSD menu appeared. Check the code, the SIM and your signal.");
        else handOver(Screen.read(this), "The menu took too long to answer", false);
    };
    private final Runnable userTimeout = () -> finish(false, "Stopped after waiting a long time for an answer");
    private final Runnable goneCheck = () -> {
        if (isBusy() && Screen.read(this) == null) {
            String last = job.lastScreen.isEmpty() ? "The menu closed." : job.lastScreen;
            finish(job.userAnswers > 0 || job.index > 0, last);
        }
    };

    @Override protected void onServiceConnected() {
        instance = this;
        skin = new Skin(this, this);
        Widget.refresh(this);
    }

    @Override public boolean onUnbind(Intent intent) {
        if (isBusy()) finish(false, "Automation was switched off");
        instance = null;
        Widget.refresh(this);
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        instance = null;
        if (skin != null) skin.hide();
        super.onDestroy();
    }

    @Override public void onInterrupt() {}

    public boolean isBusy() { return job != null && !job.done; }

    // ---------------------------------------------------------------- start

    /** Starts watching for the USSD pop-up. Call just before dialling. */
    public void begin(Job j, Listener l, String simLabel) {
        if (isBusy()) finish(false, "Replaced by a new run");
        h.removeCallbacksAndMessages(null);
        job = j;
        listener = l;
        lastActedText = null;
        lastLogged = null;
        sawDialog = false;
        waitingOn = null;
        savedId = null;
        switch (j.mode) {
            case CUSTOM:
                skin.start(j.title, j.code, simLabel);
                break;
            case AUTOPILOT:
                skin.banner("Auto-pilot · " + j.title, "Dialling " + j.code + "…", false, "Stop", this::onCancel);
                break;
            case WATCH:
                skin.banner(j.title, "Use the menu as normal. Each screen is saved to History.", false, "Stop", this::onCancel);
                h.postDelayed(() -> { if (isBusy() && job.mode == Job.Mode.WATCH) skin.hide(); }, 3500);
                break;
        }
        h.postDelayed(stepTimeout, STEP_TIMEOUT_MS);
    }

    /**
     * Runs a saved action without opening the app (home-screen widget, shortcuts).
     * Missing {PHONE}/{AMOUNT} values are asked for on the custom sheet first.
     */
    public void launch(Store.Action a) {
        Store store = Store.get(this);
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            store.setWidgetStatus("Open the app to allow phone access");
            Widget.refresh(this);
            return;
        }
        List<String> steps = StepEngine.parse(a.steps);
        List<String> keys = new ArrayList<>();
        if (StepEngine.uses(steps, "{PHONE}")) keys.add("PHONE");
        if (StepEngine.uses(steps, "{AMOUNT}")) keys.add("AMOUNT");
        askValues(a, steps, keys, 0, new HashMap<>());
    }

    private void askValues(Store.Action a, List<String> steps, List<String> keys, int i, Map<String, String> vars) {
        if (i >= keys.size()) {
            startFromBackground(a, steps, vars);
            return;
        }
        String k = keys.get(i);
        boolean phone = k.equals("PHONE");
        Store store = Store.get(this);
        skin.askValue(a.title(), a.code, phone ? "Phone number" : "Amount",
                phone ? "Who it's for. Used wherever the steps say {PHONE}." : "Used wherever the steps say {AMOUNT}.",
                store.var(k), v -> {
                    String val = phone ? v.replaceAll("[^0-9+]", "") : v.trim();
                    if (val.isEmpty()) {
                        askValues(a, steps, keys, i, vars);
                        return;
                    }
                    store.setVar(k, val);
                    vars.put(k, val);
                    askValues(a, steps, keys, i + 1, vars);
                });
    }

    private void startFromBackground(Store.Action a, List<String> steps, Map<String, String> vars) {
        Store store = Store.get(this);
        Sims.Sim sim = Sims.selected(this);
        Job.Mode mode = Store.PHONE.equals(a.mode) ? Job.Mode.WATCH
                : Store.AUTOPILOT.equals(a.mode) || !store.cover() ? Job.Mode.AUTOPILOT : Job.Mode.CUSTOM;
        Job j = new Job(a.title(), a.code, steps, vars, null, mode);
        j.actionId = a.id;
        begin(j, null, sim == null ? null : sim.label);
        store.setWidgetStatus("Running " + a.title() + "…");
        Widget.refresh(this);
        try {
            Sims.dial(this, a.code, sim);
        } catch (RuntimeException e) {
            finish(false, "Couldn't dial: " + e.getMessage());
        }
    }

    // --------------------------------------------------------------- events

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) return;
        if (isBusy()) {
            h.removeCallbacks(process);
            h.postDelayed(process, SETTLE_MS);
        } else if (Store.get(this).listen() && pkg != null && Screen.looksLikePhone(pkg.toString())) {
            h.removeCallbacks(listenRun);
            h.postDelayed(listenRun, 600);
        }
    }

    /** Listen mode: copy USSD pop-ups into History. Never taps or types. */
    private void listen() {
        Screen s = Screen.read(this);
        if (s == null || s.busy || s.text.isEmpty() || s.text.equals(lastHeard)) return;
        lastHeard = s.text;
        Store.get(this).addRun("Heard on screen", "listen", true, s.text, "◀ " + s.text);
        Widget.refresh(this);
    }

    private void processScreen() {
        if (!isBusy()) return;
        Screen s = Screen.read(this);
        if (s == null) {
            if (sawDialog) {
                h.removeCallbacks(goneCheck);
                h.postDelayed(goneCheck, GONE_MS);
            }
            return;
        }
        h.removeCallbacks(goneCheck);
        if (s.busy || s.text.isEmpty()) return;
        sawDialog = true;
        job.lastScreen = s.text;
        if (!s.text.equals(lastLogged)) {
            job.log("◀ " + s.text.replace("\n", " ⏎ "));
            lastLogged = s.text;
        }

        if (waitingOn != null) {
            if (waitingOn.equals(s.text)) return;
            if (s.input == null) {
                s.clickOk();
                finish(job.userAnswers > 0 || job.index > 0, s.text);
            } else {
                askUser(s, null, false);
            }
            return;
        }

        if (lastActedText != null && s.text.equals(lastActedText)) {
            if (SystemClock.uptimeMillis() - lastActedAt < STUCK_MS) h.postDelayed(process, 1000);
            else handOver(s, "The menu didn't change after the last answer", false);
            return;
        }
        lastActedText = null;

        if (job.mode == Job.Mode.WATCH) {
            if (s.input == null) finish(true, s.text);
            return;
        }

        StepEngine.Action a = job.next(s.text, s.input != null);
        h.removeCallbacks(stepTimeout);
        switch (a.kind) {
            case FINISH:
                s.clickOk();
                finish(true, s.text);
                break;
            case HANDOVER:
                handOver(s, a.note, a.pinStep);
                break;
            case REPLY:
                job.log("▶ " + a.display() + (a.note == null ? "" : "  (" + a.note + ")"));
                if (!send(s, job.fill(a.value))) {
                    handOver(s, "Couldn't type into the menu on this phone", false);
                    return;
                }
                if (job.mode == Job.Mode.CUSTOM) {
                    skin.progress(job.steps, job.index, "Working on it", job.progress());
                } else {
                    skin.banner("Auto-pilot · " + job.progress(), StepEngine.describe(job.steps.get(job.index - 1)), false, "Stop", this::onCancel);
                }
                break;
        }
    }

    private boolean send(Screen s, CharSequence value) {
        if (!s.type(value)) return false;
        lastActedText = s.text;
        lastActedAt = SystemClock.uptimeMillis();
        h.postDelayed(() -> {
            Screen fresh = Screen.read(this);
            boolean ok = fresh != null && fresh.clickSend();
            if (!ok && !s.clickSend()) handOver(fresh != null ? fresh : s, "Couldn't find the Send button", false);
        }, 150);
        h.removeCallbacks(stepTimeout);
        h.postDelayed(stepTimeout, STEP_TIMEOUT_MS);
        return true;
    }

    /** Automation can't continue on its own: ask on the custom sheet, or reveal the pop-up. */
    private void handOver(Screen s, String why, boolean pinStep) {
        if (!isBusy()) return;
        if (!pinStep) job.manual = true;
        job.log("✋ " + why);
        h.removeCallbacks(stepTimeout);
        if (job.mode == Job.Mode.CUSTOM && s != null && s.input != null) {
            waitingPinStep = pinStep;
            askUser(s, why, pinStep);
        } else {
            job.mode = Job.Mode.WATCH;
            job.wipe();
            skin.banner("Your turn", why + ". Finish in the menu above.", true, "Hide", () -> skin.hide());
            h.postDelayed(() -> { if (isBusy() && job.mode == Job.Mode.WATCH) skin.hide(); }, 5000);
            h.postDelayed(userTimeout, USER_TIMEOUT_MS);
        }
    }

    private void askUser(Screen s, String note, boolean pinStep) {
        waitingOn = s.text;
        boolean pin = pinStep || StepEngine.asksForPin(s.text);
        skin.ask(s.text, note, s.input != null, pin);
        h.removeCallbacks(userTimeout);
        h.postDelayed(userTimeout, USER_TIMEOUT_MS);
    }

    // ----------------------------------------------------- custom sheet input

    @Override public void onAnswer(char[] value, boolean pin) {
        if (!isBusy()) {
            Arrays.fill(value, '\0');
            return;
        }
        Screen s = Screen.read(this);
        String asked = waitingOn;
        if (s == null || s.input == null) {
            Arrays.fill(value, '\0');
            finish(false, "The menu closed before your answer could be sent");
            return;
        }
        waitingOn = null;
        h.removeCallbacks(userTimeout);
        String recorded;
        if (pin) {
            recorded = StepEngine.PIN;
            job.log("▶ you: ••••");
        } else {
            String typed = new String(value);
            recorded = StepEngine.recordStep(asked, typed);
            job.log("▶ you: " + typed + "  (saved as " + recorded + ")");
        }
        if (waitingPinStep) {
            job.index++;
            waitingPinStep = false;
        }
        job.route.add(recorded);
        job.userAnswers++;
        StringBuilder sb = new StringBuilder(value.length);
        sb.append(value);
        Arrays.fill(value, '\0');
        boolean ok = send(s, sb);
        sb.setLength(0);
        if (!ok) {
            handOver(s, "Couldn't type into the menu on this phone", false);
            return;
        }
        skin.progress(null, 0, pin ? "Checking your PIN" : "Sending your answer", "Waiting for the network…");
    }

    @Override public void onCancel() {
        if (isBusy()) {
            Screen s = Screen.read(this);
            if (s != null) s.clickCancel();
            finish(false, "Cancelled");
        }
        skin.hide();
    }

    @Override public void onShowPhone() {
        if (isBusy()) {
            job.mode = Job.Mode.WATCH;
            job.manual = true;
            job.wipe();
            waitingOn = null;
            job.log("✋ Switched to the phone's pop-up");
            skin.hide();
            h.removeCallbacks(userTimeout);
            h.postDelayed(userTimeout, USER_TIMEOUT_MS);
        } else {
            skin.hide();
        }
    }

    @Override public void onDone() { skin.hide(); }

    @Override public String onSaveRoute() {
        if (job == null || job.route.isEmpty()) return null;
        Store store = Store.get(this);
        String name = job.recordName != null && !job.recordName.isEmpty() ? job.recordName : job.title + " (recorded)";
        Store.Action a = Store.Action.make(name, job.code, String.join(", ", job.route), Store.CUSTOM);
        a.recorded = true;
        store.upsert(a);
        savedId = a.id;
        Widget.refresh(this);
        return name;
    }

    // ---------------------------------------------------------------- finish

    private void finish(boolean ok, String message) {
        if (job == null || job.done) return;
        job.done = true;
        job.success = ok;
        job.result = message;
        job.wipe();
        waitingOn = null;
        job.log((ok ? "✓ " : "✗ ") + message.replace("\n", " ⏎ "));
        h.removeCallbacks(stepTimeout);
        h.removeCallbacks(userTimeout);
        h.removeCallbacks(goneCheck);
        h.removeCallbacks(process);
        Store store = Store.get(this);
        store.addRun(job.title, job.mode.name().toLowerCase(), ok, message, job.log(), job.actionId);
        store.setWidgetStatus("");
        Widget.refresh(this);
        Job finished = job;
        boolean canSave = finished.userAnswers > 0 && !finished.route.isEmpty();
        if (finished.mode == Job.Mode.CUSTOM || skin.coverShowing()) {
            skin.result(ok, message, canSave, finished.route.size());
        } else {
            skin.banner(ok ? "Done · " + finished.title : "Stopped · " + finished.title, message, true, "OK", () -> skin.hide());
            h.postDelayed(() -> { if (!isBusy()) skin.hide(); }, 7000);
        }
        if (listener != null) listener.onJobFinished(finished);
        listener = null;
    }
}
