package app.ussdlab;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One run through a USSD menu. Holds the PIN only while running, and never logs it. */
public final class Job {

    /** How the run is shown: our own screen, the phone's pop-up with auto answers, or the phone's pop-up only. */
    public enum Mode { CUSTOM, AUTOPILOT, WATCH }

    public final String title;
    public final String code;
    public final List<String> steps;
    public final Map<String, String> vars;
    public Mode mode;
    private char[] pin;

    public int index;
    /** Set once the user has taken over; automatic steps stop. */
    public boolean manual;
    public boolean done;
    public boolean success;
    public String result = "";
    public String lastScreen = "";
    /** The path actually taken, as reusable steps (for "Save as action"). */
    public final List<String> route = new ArrayList<>();
    public int userAnswers;
    /** The saved action this run came from, if any. */
    public String actionId;
    /** Set when this run is "Record a command": the name to save it under. */
    public String recordName;
    private final StringBuilder log = new StringBuilder();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    public Job(String title, String code, List<String> steps, Map<String, String> vars, char[] pin, Mode mode) {
        this.title = title;
        this.code = code;
        this.steps = steps;
        this.vars = vars;
        this.pin = pin;
        this.mode = mode;
        log("Dial " + code + " · " + mode.name().toLowerCase(Locale.ROOT)
                + (steps.isEmpty() ? "" : " · steps: " + String.join(", ", steps)));
    }

    public StepEngine.Action next(String screen, boolean hasInput) {
        if (manual && hasInput) return StepEngine.decide(steps, steps.size(), vars, true, screen, true);
        StepEngine.Action a = StepEngine.decide(steps, index, vars, pin == null, screen, hasInput);
        if (a.kind == StepEngine.Kind.REPLY) {
            route.add(steps.get(index));
            index++;
        }
        return a;
    }

    /** Expands {PIN} in a reply. The result never goes into the log. */
    public CharSequence fill(String value) {
        if (!value.contains(StepEngine.PIN) || pin == null) return value;
        StringBuilder sb = new StringBuilder();
        int i = value.indexOf(StepEngine.PIN);
        sb.append(value, 0, i).append(pin).append(value.substring(i + StepEngine.PIN.length()));
        return sb;
    }

    public boolean hasPin() { return pin != null; }

    public void wipe() {
        if (pin != null) Arrays.fill(pin, '\0');
        pin = null;
    }

    public void log(String line) {
        log.append(clock.format(new Date())).append("  ").append(line).append('\n');
    }

    public String log() { return log.toString(); }

    public String progress() {
        if (steps.isEmpty()) return "";
        return "Step " + Math.min(index + 1, steps.size()) + " of " + steps.size();
    }

    public float fraction() {
        return steps.isEmpty() ? 0f : Math.min(1f, index / (float) steps.size());
    }
}
