package app.ussdlab;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java rules that decide what to type into each USSD screen. No Android types, so it is unit-tested.
 *
 * Step syntax (comma or newline separated):
 *   1            reply "1"
 *   {PHONE}      reply the phone number entered on the main screen ({AMOUNT} likewise)
 *   {PIN}        reply the PIN (stored + fingerprint, or asked for at run time)
 *   @transfer    find the menu line containing "transfer" and reply with its number
 */
public final class StepEngine {

    public static final String PIN = "{PIN}";

    public enum Kind { REPLY, FINISH, HANDOVER }

    public static final class Action {
        public final Kind kind;
        /** For REPLY: text to type, variables expanded except {PIN}. */
        public final String value;
        public final String note;

        Action(Kind kind, String value, String note) {
            this.kind = kind;
            this.value = value;
            this.note = note;
        }

        public boolean usesPin() { return value != null && value.contains(PIN); }

        /** Safe for logs and the overlay: the PIN is never shown. */
        public String display() { return value == null ? "" : value.replace(PIN, "••••"); }

        @Override public String toString() {
            String v = display();
            String n = note == null ? "" : note;
            return kind + "(" + v + (!v.isEmpty() && !n.isEmpty() ? ", " : "") + n + ")";
        }
    }

    private StepEngine() {}

    public static List<String> parse(String steps) {
        List<String> out = new ArrayList<>();
        if (steps == null) return out;
        for (String s : steps.split("[,\\n]")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static boolean uses(List<String> steps, String token) {
        for (String s : steps) if (s.toUpperCase(Locale.ROOT).contains(token)) return true;
        return false;
    }

    /**
     * @param screen   the text shown on the current USSD screen
     * @param hasInput whether the screen has a reply box
     */
    public static Action decide(List<String> steps, int index, Map<String, String> vars,
                                boolean stopBeforePin, String screen, boolean hasInput) {
        if (!hasInput) {
            boolean complete = index >= steps.size();
            return new Action(Kind.FINISH, null, complete ? "done" : "menu ended early");
        }
        if (index >= steps.size()) {
            return new Action(Kind.HANDOVER, null, "No steps left — your turn");
        }
        String step = steps.get(index);
        if (step.startsWith("@")) {
            String query = step.substring(1).trim();
            String n = pickOption(screen, query);
            if (n == null) return new Action(Kind.HANDOVER, null, "No menu option matching \"" + query + "\"");
            return new Action(Kind.REPLY, n, "picked \"" + query + "\"");
        }
        String upper = step.toUpperCase(Locale.ROOT);
        if (upper.contains(PIN) && stopBeforePin) {
            return new Action(Kind.HANDOVER, null, "Stopped before PIN — type it yourself");
        }
        StringBuilder value = new StringBuilder();
        Matcher m = Pattern.compile("\\{([A-Za-z]+)\\}").matcher(step);
        int last = 0;
        while (m.find()) {
            value.append(step, last, m.start());
            String key = m.group(1).toUpperCase(Locale.ROOT);
            if (key.equals("PIN")) {
                value.append(PIN);
            } else {
                String v = vars == null ? null : vars.get(key);
                if (v == null || v.trim().isEmpty()) {
                    return new Action(Kind.HANDOVER, null, "No value for {" + key + "}");
                }
                value.append(v.trim());
            }
            last = m.end();
        }
        value.append(step.substring(last));
        return new Action(Kind.REPLY, value.toString(), null);
    }

    private static final Pattern LINE_OPTION = Pattern.compile("^\\s*(\\d{1,2})\\s*[.):\\-]?\\s*(\\S.*)$");
    private static final Pattern INLINE_OPTION = Pattern.compile("(?:^|\\s)(\\d{1,2})\\s*[.):\\-]\\s*([^\\n]+?)(?=\\s+\\d{1,2}\\s*[.):\\-]|$)");

    /** Returns the option number whose label contains {@code query}, or null. */
    public static String pickOption(String screen, String query) {
        if (screen == null || query == null || query.isEmpty()) return null;
        String q = fold(query);
        for (String line : screen.split("\\r?\\n")) {
            Matcher m = LINE_OPTION.matcher(line);
            if (m.matches() && fold(m.group(2)).contains(q)) return m.group(1);
        }
        // Some menus put several options on one line: "1.Send 2.Balance 3.Exit"
        Matcher m = INLINE_OPTION.matcher(screen);
        while (m.find()) {
            if (fold(m.group(2)).contains(q)) return m.group(1);
        }
        return null;
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
