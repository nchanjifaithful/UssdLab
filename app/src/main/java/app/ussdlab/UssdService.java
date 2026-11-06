package app.ussdlab;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads USSD dialogs and replies to them, but only while a {@link Job} is running.
 * When idle it ignores every event, so it never looks at other apps.
 */
public class UssdService extends AccessibilityService {

    public interface Listener { void onJobFinished(Job job); }

    static UssdService instance;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Job job;
    private Listener listener;
    private String lastActedText;
    private long lastActedAt;
    private String lastLoggedText;
    private boolean sawDialog;

    private static final long SETTLE_MS = 350;
    private static final long STUCK_MS = 8000;
    private static final long STEP_TIMEOUT_MS = 30000;
    private static final long HANDOVER_WATCH_MS = 120000;

    private final Runnable process = this::processScreen;
    private final Runnable stepTimeout = () -> handOver("Timed out waiting for the menu");
    private final Runnable handoverEnd = () -> finish(false, "Ended watching (no activity)");

    @Override protected void onServiceConnected() { instance = this; }

    @Override public boolean onUnbind(Intent intent) {
        if (job != null) finish(false, "Automation was switched off");
        instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        instance = null;
        hideOverlay();
        super.onDestroy();
    }

    @Override public void onInterrupt() {}

    public boolean isBusy() { return job != null && !job.done; }

    public void begin(Job j, Listener l) {
        if (isBusy()) finish(false, "Replaced by a new run");
        job = j;
        listener = l;
        lastActedText = null;
        lastLoggedText = null;
        sawDialog = false;
        showOverlay(j.title, "Dialling " + j.code + "…", j.cover, false);
        h.removeCallbacks(stepTimeout);
        h.postDelayed(stepTimeout, STEP_TIMEOUT_MS);
    }

    public void cancel() {
        if (!isBusy()) return;
        Screen s = Screen.read(this);
        if (s != null) s.clickCancel();
        finish(false, "Cancelled");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (!isBusy()) return;
        CharSequence pkg = e.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) return;
        h.removeCallbacks(process);
        h.postDelayed(process, SETTLE_MS);
    }

    private void processScreen() {
        if (!isBusy()) return;
        Screen s = Screen.read(this);
        if (s == null) {
            if (sawDialog && job.handedOver) finish(job.success, job.result.isEmpty() ? "Menu closed" : job.result);
            return;
        }
        if (s.busy || s.text.isEmpty()) return;
        sawDialog = true;

        if (!s.text.equals(lastLoggedText)) {
            job.log("◀ [" + s.pkg + "] " + s.text.replace("\n", " ⏎ "));
            lastLoggedText = s.text;
        }

        if (job.handedOver) {
            // Just watching while the user types; record screens, never act.
            if (s.input == null) {
                job.result = s.text;
                finish(true, s.text);
            } else {
                h.removeCallbacks(handoverEnd);
                h.postDelayed(handoverEnd, HANDOVER_WATCH_MS);
            }
            return;
        }

        if (lastActedText != null && s.text.equals(lastActedText)) {
            long since = SystemClock.uptimeMillis() - lastActedAt;
            if (since < STUCK_MS) {
                h.postDelayed(process, 1000);
            } else {
                handOver("The menu didn't move on after the last reply");
            }
            return;
        }

        StepEngine.Action a = job.next(s.text, s.input != null);
        h.removeCallbacks(stepTimeout);
        switch (a.kind) {
            case FINISH:
                job.result = s.text;
                s.clickOk();
                finish(job.index >= job.steps.size(), s.text);
                break;
            case HANDOVER:
                handOver(a.note);
                break;
            case REPLY:
                job.log("▶ reply " + a.display() + (a.note == null ? "" : "  (" + a.note + ")"));
                CharSequence value = job.fill(a.value);
                if (!s.type(value)) {
                    handOver("Couldn't type into the menu on this phone");
                    return;
                }
                lastActedText = s.text;
                lastActedAt = SystemClock.uptimeMillis();
                updateOverlay(job.progress(), "Sent " + a.display());
                h.postDelayed(() -> {
                    Screen fresh = Screen.read(this);
                    if (fresh == null || !fresh.clickSend()) {
                        if (!s.clickSend()) handOver("Couldn't find the Send button");
                    }
                }, 150);
                h.postDelayed(stepTimeout, STEP_TIMEOUT_MS);
                break;
        }
    }

    private void handOver(String why) {
        if (!isBusy()) return;
        job.handedOver = true;
        job.wipe();
        job.log("✋ " + why);
        h.removeCallbacks(stepTimeout);
        h.postDelayed(handoverEnd, HANDOVER_WATCH_MS);
        showOverlay("Your turn", why, false, true);
    }

    private void finish(boolean ok, String message) {
        if (job == null || job.done) return;
        job.done = true;
        job.success = ok;
        if (job.result.isEmpty()) job.result = message;
        job.wipe();
        job.log((ok ? "✓ " : "✗ ") + message.replace("\n", " ⏎ "));
        h.removeCallbacks(stepTimeout);
        h.removeCallbacks(handoverEnd);
        h.removeCallbacks(process);
        Store.get(this).saveRun(job.title, job.result, job.log());
        final Job finished = job;
        showOverlay(ok ? "Done" : "Stopped", message, false, true);
        h.postDelayed(this::hideOverlay, 6000);
        if (listener != null) listener.onJobFinished(finished);
        listener = null;
    }

    // ---------------------------------------------------------------- overlay

    private View overlay;
    private TextView ovTitle, ovBody;
    private ProgressBar ovSpin;
    private Button ovAction;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void showOverlay(String title, String body, boolean cover, boolean ended) {
        hideOverlay();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2202124);
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setElevation(dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ovSpin = new ProgressBar(this);
        ovSpin.setVisibility(ended ? View.GONE : View.VISIBLE);
        row.addView(ovSpin, new LinearLayout.LayoutParams(dp(22), dp(22)));
        ovTitle = new TextView(this);
        ovTitle.setTextColor(0xFFFFFFFF);
        ovTitle.setTextSize(16);
        ovTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ovTitle.setPadding(ended ? 0 : dp(10), 0, 0, 0);
        ovTitle.setText("USSD Lab · " + title);
        row.addView(ovTitle);
        card.addView(row);

        ovBody = new TextView(this);
        ovBody.setTextColor(0xFFE0E0E0);
        ovBody.setTextSize(14);
        ovBody.setPadding(0, dp(6), 0, 0);
        ovBody.setMaxLines(8);
        ovBody.setEllipsize(TextUtils.TruncateAt.END);
        ovBody.setText(body);
        card.addView(ovBody);

        ovAction = new Button(this, null, android.R.attr.borderlessButtonStyle);
        ovAction.setTextColor(0xFF8AB4F8);
        ovAction.setText(ended ? "Open app" : "Cancel");
        ovAction.setOnClickListener(v -> {
            if (ended) {
                hideOverlay();
                Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            } else {
                cancel();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.END;
        card.addView(ovAction, blp);

        View root;
        int height;
        if (cover) {
            // Full-screen card hides the USSD menu while it is being driven.
            LinearLayout full = new LinearLayout(this);
            full.setGravity(Gravity.CENTER);
            full.setBackgroundColor(0xEE000000);
            full.setPadding(dp(20), 0, dp(20), 0);
            full.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            root = full;
            height = WindowManager.LayoutParams.MATCH_PARENT;
        } else {
            LinearLayout wrap = new LinearLayout(this);
            wrap.setPadding(dp(12), dp(36), dp(12), 0);
            wrap.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            root = wrap;
            height = WindowManager.LayoutParams.WRAP_CONTENT;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        try {
            wm.addView(root, lp);
            overlay = root;
        } catch (RuntimeException ignored) {
            overlay = null;
        }
    }

    private void updateOverlay(String title, String body) {
        if (overlay == null) return;
        ovTitle.setText("USSD Lab · " + title);
        ovBody.setText(body);
    }

    private void hideOverlay() {
        if (overlay == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
        } catch (RuntimeException ignored) {
        }
        overlay = null;
    }

    // ------------------------------------------------------------- screen read

    /** A snapshot of the USSD dialog currently on screen. */
    static final class Screen {
        String pkg = "";
        String text = "";
        boolean busy;
        AccessibilityNodeInfo input;
        final List<AccessibilityNodeInfo> buttons = new ArrayList<>();

        private static final String[] SEND = {"send", "reply", "ok", "answer", "submit", "envoyer", "répondre", "repondre", "valider", "oui", "yes", "confirm"};
        private static final String[] CANCEL = {"cancel", "annuler", "dismiss", "close", "fermer", "no", "non"};
        private static final String[] PKG_HINTS = {"phone", "telephony", "telecom", "ussd", "stk", "mmi"};

        static Screen read(UssdService svc) {
            List<AccessibilityNodeInfo> roots = new ArrayList<>();
            try {
                for (AccessibilityWindowInfo w : svc.getWindows()) {
                    if (w.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r != null) roots.add(r);
                }
            } catch (RuntimeException ignored) {
            }
            AccessibilityNodeInfo active = svc.getRootInActiveWindow();
            if (active != null) roots.add(0, active);

            Screen best = null;
            for (AccessibilityNodeInfo r : roots) {
                CharSequence p = r.getPackageName();
                if (p == null) continue;
                String pkg = p.toString();
                if (pkg.equals(svc.getPackageName()) || !looksLikePhone(pkg)) continue;
                Screen s = new Screen();
                s.pkg = pkg;
                StringBuilder sb = new StringBuilder();
                s.walk(r, sb, 0);
                s.text = sb.toString().trim();
                String low = s.text.toLowerCase(Locale.ROOT);
                if (s.input == null && (low.contains("running") || low.contains("en cours") || low.contains("please wait") || low.contains("patientez"))) s.busy = true;
                if (s.input != null || !s.buttons.isEmpty() || s.busy) {
                    if (best == null || (s.input != null && best.input == null)) best = s;
                }
            }
            return best;
        }

        static boolean looksLikePhone(String pkg) {
            String p = pkg.toLowerCase(Locale.ROOT);
            if (p.contains("dialer") || p.contains("contacts")) return false;
            for (String hint : PKG_HINTS) if (p.contains(hint)) return true;
            return false;
        }

        private void walk(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
            if (n == null || depth > 30) return;
            String cls = n.getClassName() == null ? "" : n.getClassName().toString();
            if (cls.contains("ProgressBar")) busy = true;
            if (n.isEditable() || cls.contains("EditText")) {
                if (input == null) input = n;
            } else if (cls.contains("Button") || (n.isClickable() && n.getText() != null && n.getChildCount() == 0 && !cls.contains("TextView"))) {
                if (n.getText() != null) buttons.add(n);
            } else if (n.getText() != null && n.getText().length() > 0) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(n.getText());
            }
            for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), sb, depth + 1);
        }

        boolean type(CharSequence value) {
            if (input == null) return false;
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            return input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }

        boolean clickSend() {
            AccessibilityNodeInfo b = find("button1", SEND);
            if (b == null) {
                // Fall back to the last button that isn't a cancel button.
                for (int i = buttons.size() - 1; i >= 0 && b == null; i--) {
                    if (!matches(buttons.get(i), CANCEL)) b = buttons.get(i);
                }
            }
            return b != null && b.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        boolean clickOk() {
            AccessibilityNodeInfo b = find("button1", SEND);
            if (b == null && buttons.size() == 1) b = buttons.get(0);
            return b != null && b.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        boolean clickCancel() {
            AccessibilityNodeInfo b = find("button2", CANCEL);
            return b != null && b.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        private AccessibilityNodeInfo find(String idSuffix, String[] words) {
            for (AccessibilityNodeInfo b : buttons) {
                String id = b.getViewIdResourceName();
                if (id != null && id.endsWith(idSuffix)) return b;
            }
            for (AccessibilityNodeInfo b : buttons) if (matches(b, words)) return b;
            return null;
        }

        private static boolean matches(AccessibilityNodeInfo b, String[] words) {
            if (b.getText() == null) return false;
            String t = b.getText().toString().toLowerCase(Locale.ROOT).trim();
            for (String w : words) if (t.equals(w) || t.startsWith(w + " ")) return true;
            return false;
        }
    }
}
