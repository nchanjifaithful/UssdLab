package app.ussdlab;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One automated run through a USSD menu. Holds the PIN only while running. */
public final class Job {

    public final String title;
    public final String code;
    public final List<String> steps;
    public final Map<String, String> vars;
    public final boolean stopBeforePin;
    public final boolean cover;
    private char[] pin;

    public int index;
    public boolean handedOver;
    public boolean done;
    public boolean success;
    public String result = "";
    private final StringBuilder log = new StringBuilder();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    public Job(String title, String code, List<String> steps, Map<String, String> vars,
               char[] pin, boolean stopBeforePin, boolean cover) {
        this.title = title;
        this.code = code;
        this.steps = steps;
        this.vars = vars;
        this.pin = pin;
        this.stopBeforePin = stopBeforePin;
        this.cover = cover;
        log("Dial " + code + "  (" + steps.size() + " steps: " + String.join(", ", steps) + ")");
    }

    public StepEngine.Action next(String screen, boolean hasInput) {
        StepEngine.Action a = StepEngine.decide(steps, index, vars, stopBeforePin || pin == null && StepEngine.uses(steps, StepEngine.PIN), screen, hasInput);
        if (a.kind == StepEngine.Kind.REPLY) index++;
        return a;
    }

    /** Expands {PIN} in a reply. Kept out of logs. */
    public CharSequence fill(String value) {
        if (!value.contains(StepEngine.PIN) || pin == null) return value;
        StringBuilder sb = new StringBuilder();
        int i = value.indexOf(StepEngine.PIN);
        sb.append(value, 0, i).append(pin).append(value.substring(i + StepEngine.PIN.length()));
        return sb;
    }

    public void wipe() {
        if (pin != null) Arrays.fill(pin, '\0');
        pin = null;
    }

    public void log(String line) {
        log.append(clock.format(new Date())).append("  ").append(line).append('\n');
    }

    public String log() { return log.toString(); }

    public String progress() {
        return "Step " + Math.min(index + 1, Math.max(steps.size(), 1)) + " of " + Math.max(steps.size(), 1);
    }
}
