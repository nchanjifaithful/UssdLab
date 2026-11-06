package app.ussdlab;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Saved actions, settings and run history. Never stores the PIN (see PinVault). */
final class Store {

    static final String SILENT = "silent";
    static final String CUSTOM = "custom";
    static final String AUTOPILOT = "autopilot";
    static final String PHONE = "phone";
    static final String DIALPAD = "dialpad";
    static final String[] MODES = {CUSTOM, AUTOPILOT, SILENT, PHONE, DIALPAD};

    static String modeName(String m) {
        switch (m) {
            case SILENT: return "Silent";
            case AUTOPILOT: return "Auto-pilot";
            case PHONE: return "Phone's pop-up";
            case DIALPAD: return "Dial pad";
            default: return "Custom screen";
        }
    }

    static String modeHelp(String m) {
        switch (m) {
            case SILENT: return "Sends the code in the background and shows the one reply here. No pop-up. Only for codes that answer once, like a balance check.";
            case AUTOPILOT: return "Shows the phone's normal USSD pop-up and types your steps into it for you. If it gets stuck, you finish by hand.";
            case PHONE: return "Dials the code and shows the phone's normal pop-up. You do the typing. If Automation is on, each screen is saved to History.";
            case DIALPAD: return "Opens the dial pad with the code filled in. You press call. Needs no permissions.";
            default: return "Covers the USSD pop-up with USSD Lab's own screen. Your steps run hidden behind it; when it needs you, you get clear buttons, a keypad and a PIN screen. Switched off? You see the phone's pop-up instead.";
        }
    }

    static final class Action {
        String id = UUID.randomUUID().toString();
        String name = "";
        String code = "";
        String steps = "";
        String mode = CUSTOM;
        boolean autoPin;

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("code", code).put("steps", steps)
                    .put("mode", mode).put("autoPin", autoPin);
        }

        static Action of(JSONObject o) {
            Action a = new Action();
            a.id = o.optString("id", a.id);
            a.name = o.optString("name");
            a.code = o.optString("code");
            a.steps = o.optString("steps");
            a.mode = o.optString("mode", CUSTOM);
            a.autoPin = o.optBoolean("autoPin");
            return a;
        }

        static Action make(String name, String code, String steps, String mode) {
            Action a = new Action();
            a.name = name;
            a.code = code;
            a.steps = steps;
            a.mode = mode;
            return a;
        }

        String title() { return name.isEmpty() ? code : name; }
    }

    static final class Run {
        String title, mode, result, log;
        long time;
        boolean ok;

        JSONObject json() throws Exception {
            return new JSONObject().put("title", title).put("mode", mode).put("result", result)
                    .put("log", log).put("time", time).put("ok", ok);
        }

        static Run of(JSONObject o) {
            Run r = new Run();
            r.title = o.optString("title");
            r.mode = o.optString("mode");
            r.result = o.optString("result");
            r.log = o.optString("log");
            r.time = o.optLong("time");
            r.ok = o.optBoolean("ok");
            return r;
        }
    }

    private static Store instance;
    private final SharedPreferences p;

    private Store(Context c) { p = c.getApplicationContext().getSharedPreferences("lab2", Context.MODE_PRIVATE); }

    static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c);
        return instance;
    }

    // ---- actions

    List<Action> actions() {
        String raw = p.getString("actions", null);
        if (raw == null) return defaults();
        List<Action> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(Action.of(a.getJSONObject(i)));
        } catch (Exception e) {
            return defaults();
        }
        return out;
    }

    Action action(String id) {
        for (Action a : actions()) if (a.id.equals(id)) return a;
        return null;
    }

    void saveActions(List<Action> list) {
        JSONArray a = new JSONArray();
        try {
            for (Action x : list) a.put(x.json());
        } catch (Exception ignored) {
        }
        p.edit().putString("actions", a.toString()).apply();
    }

    void upsert(Action x) {
        List<Action> l = actions();
        boolean found = false;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id.equals(x.id)) {
                l.set(i, x);
                found = true;
            }
        }
        if (!found) l.add(x);
        saveActions(l);
    }

    void delete(String id) {
        List<Action> l = actions();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        saveActions(l);
    }

    private static List<Action> defaults() {
        List<Action> l = new ArrayList<>();
        l.add(Action.make("Airtime balance", "*155#", "", SILENT));
        l.add(Action.make("MoMo on the custom screen", "*126#", "", CUSTOM));
        l.add(Action.make("Buy airtime (example)", "*126#", "@airtime, @myself, {AMOUNT}, {PIN}", CUSTOM));
        l.add(Action.make("Send money (example)", "*126#", "@transfer, @momo, {PHONE}, {PHONE}, {AMOUNT}, {PIN}", CUSTOM));
        l.add(Action.make("MoMo in the phone's pop-up", "*126#", "", PHONE));
        l.add(Action.make("Dial pad with *126#", "*126#", "", DIALPAD));
        return l;
    }

    // ---- settings

    int simSlot() { return p.getInt("sim", -1); }
    void setSimSlot(int slot) { p.edit().putInt("sim", slot).apply(); }

    /** Draw USSD Lab's own screen over the USSD pop-up. Off = show the phone's pop-up instead. */
    boolean cover() { return p.getBoolean("cover", true); }
    void setCover(boolean v) { p.edit().putBoolean("cover", v).apply(); }

    boolean listen() { return p.getBoolean("listen", false); }
    void setListen(boolean v) { p.edit().putBoolean("listen", v).apply(); }

    String var(String key) { return p.getString("var_" + key, ""); }
    void setVar(String key, String v) { p.edit().putString("var_" + key, v).apply(); }

    String quickCode() { return p.getString("quick_code", "*155#"); }
    String quickMode() { return p.getString("quick_mode", SILENT); }
    void setQuick(String code, String mode) { p.edit().putString("quick_code", code).putString("quick_mode", mode).apply(); }

    // ---- history

    List<Run> runs() {
        List<Run> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("runs", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Run.of(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    synchronized void addRun(String title, String mode, boolean ok, String result, String log) {
        Run r = new Run();
        r.title = title;
        r.mode = mode;
        r.ok = ok;
        r.result = result == null ? "" : result;
        r.log = log == null ? "" : log;
        r.time = System.currentTimeMillis();
        List<Run> l = runs();
        l.add(0, r);
        while (l.size() > 40) l.remove(l.size() - 1);
        JSONArray a = new JSONArray();
        try {
            for (Run x : l) a.put(x.json());
        } catch (Exception ignored) {
        }
        p.edit().putString("runs", a.toString()).apply();
    }

    void clearRuns() { p.edit().remove("runs").apply(); }
}
