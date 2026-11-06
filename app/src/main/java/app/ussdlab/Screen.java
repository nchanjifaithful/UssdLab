package app.ussdlab;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A snapshot of the USSD pop-up currently on screen, read through Accessibility. */
final class Screen {

    String pkg = "";
    String text = "";
    boolean busy;
    AccessibilityNodeInfo input;
    final List<AccessibilityNodeInfo> buttons = new ArrayList<>();

    private static final String[] SEND = {"send", "reply", "ok", "answer", "submit", "envoyer", "répondre", "repondre", "valider", "oui", "yes", "confirm", "continue", "continuer"};
    private static final String[] CANCEL = {"cancel", "annuler", "dismiss", "close", "fermer", "no", "non"};
    /** USSD pop-ups come from the phone process; the name differs by brand, so match on hints. */
    private static final String[] PKG_HINTS = {"phone", "telephony", "telecom", "ussd", "stk", "mmi"};

    static Screen read(AccessibilityService svc) {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        AccessibilityNodeInfo active = svc.getRootInActiveWindow();
        if (active != null) roots.add(active);
        try {
            for (AccessibilityWindowInfo w : svc.getWindows()) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null) roots.add(r);
            }
        } catch (RuntimeException ignored) {
        }
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
            if (s.input == null && (low.contains("running") || low.contains("en cours") || low.contains("please wait") || low.contains("patientez"))) {
                s.busy = true;
            }
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
        } else if (cls.contains("Button")) {
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
        if (b == null && input == null) return clickOk();
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
