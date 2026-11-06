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
        /** HANDOVER only: the next step is the PIN and we have none, so ask the user for just that. */
        public final boolean pinStep;

        Action(Kind kind, String value, String note) {
            this(kind, value, note, false);
        }

        Action(Kind kind, String value, String note, boolean pinStep) {
            this.kind = kind;
            this.value = value;
            this.note = note;
            this.pinStep = pinStep;
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
            return new Action(Kind.HANDOVER, null, "Your turn");
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
            return new Action(Kind.HANDOVER, null, "Enter your PIN", true);
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

    // ------------------------------------------------------------ screen parsing

    public static final class Option {
        public final String num;
        public final String label;

        Option(String num, String label) {
            this.num = num;
            this.label = label;
        }

        @Override public String toString() { return num + "=" + label; }
    }

    /** Numbered choices on a menu screen, e.g. "1. Transfer money" → (1, Transfer money). */
    public static List<Option> options(String screen) {
        List<Option> out = new ArrayList<>();
        if (screen == null) return out;
        for (String line : screen.split("\\r?\\n")) {
            Matcher m = LINE_OPTION.matcher(line);
            if (m.matches()) out.add(new Option(m.group(1), m.group(2).trim()));
        }
        if (out.size() >= 2) return out;
        out.clear();
        Matcher m = INLINE_OPTION.matcher(screen);
        while (m.find()) out.add(new Option(m.group(1), m.group(2).trim()));
        return out.size() >= 2 ? out : new ArrayList<>();
    }

    /** The screen text without its numbered options: the heading or question. */
    public static String message(String screen) {
        if (screen == null) return "";
        if (options(screen).isEmpty()) return screen.trim();
        int lineOptions = 0;
        for (String line : screen.split("\\r?\\n")) if (LINE_OPTION.matcher(line).matches()) lineOptions++;
        if (lineOptions < 2) {
            Matcher m = INLINE_OPTION.matcher(screen);
            return m.find() ? screen.substring(0, m.start()).trim() : screen.trim();
        }
        StringBuilder sb = new StringBuilder();
        for (String line : screen.split("\\r?\\n")) {
            if (LINE_OPTION.matcher(line).matches()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line.trim());
        }
        return sb.toString().trim();
    }

    private static final Pattern PIN_WORDS = Pattern.compile("\\b(pin|code secret|secret code|mot de passe|password|passcode|mpin)\\b");
    private static final Pattern AMOUNT_WORDS = Pattern.compile("\\b(amount|montant|somme|how much|combien)\\b");
    private static final Pattern PHONE_WORDS = Pattern.compile("\\b(number|numero|phone|telephone|mobile|msisdn|recipient|beneficiaire|destinataire|receiver)\\b");

    /** True for a "please enter your PIN" screen (not for a menu that merely has a "Change PIN" option). */
    public static boolean asksForPin(String screen) {
        return screen != null && options(screen).isEmpty() && PIN_WORDS.matcher(fold(screen)).find();
    }

    /**
     * Turns one answer the user gave on the custom screen into a reusable step.
     * Options become "@words" (survive menu re-ordering), phone numbers / amounts become
     * {PHONE} / {AMOUNT}, and a PIN becomes {PIN} so the real digits are never saved.
     */
    public static String recordStep(String screen, String typed) {
        if (typed == null) return "";
        String t = typed.trim();
        for (Option o : options(screen)) {
            if (o.num.equals(t)) {
                String words = fold(o.label).replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
                String[] parts = words.split("\\s+");
                StringBuilder sb = new StringBuilder("@");
                for (int i = 0; i < parts.length && i < 2; i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(parts[i]);
                }
                String key = sb.toString();
                // Only use the words if they point back to this same option.
                if (key.length() > 2 && t.equals(pickOption(screen, key.substring(1))) && matchCount(screen, key.substring(1)) == 1) return key;
                return t;
            }
        }
        if (asksForPin(screen)) return PIN;
        String f = fold(screen == null ? "" : message(screen));
        String digits = t.replaceAll("[^0-9]", "");
        if (AMOUNT_WORDS.matcher(f).find() && !digits.isEmpty()) return "{AMOUNT}";
        if (PHONE_WORDS.matcher(f).find() && digits.length() >= 8) return "{PHONE}";
        return t;
    }

    private static int matchCount(String screen, String query) {
        int n = 0;
        String q = fold(query);
        for (Option o : options(screen)) if (fold(o.label).contains(q)) n++;
        return n;
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
