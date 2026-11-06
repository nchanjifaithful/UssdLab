package app.ussdlab;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Saved codes, settings and the last run's log. Never stores the PIN (see PinVault). */
final class Store {

    static final class Recipe {
        String name = "";
        String code = "";
        String steps = "";
        boolean dialler;
        boolean stopBeforePin;

        JSONObject json() throws Exception {
            return new JSONObject().put("name", name).put("code", code).put("steps", steps)
                    .put("dialler", dialler).put("stop", stopBeforePin);
        }

        static Recipe of(JSONObject o) {
            Recipe r = new Recipe();
            r.name = o.optString("name");
            r.code = o.optString("code");
            r.steps = o.optString("steps");
            r.dialler = o.optBoolean("dialler");
            r.stopBeforePin = o.optBoolean("stop");
            return r;
        }

        static Recipe make(String name, String code, String steps, boolean dialler, boolean stop) {
            Recipe r = new Recipe();
            r.name = name;
            r.code = code;
            r.steps = steps;
            r.dialler = dialler;
            r.stopBeforePin = stop;
            return r;
        }
    }

    private static Store instance;
    private final SharedPreferences p;

    private Store(Context c) { p = c.getApplicationContext().getSharedPreferences("lab", Context.MODE_PRIVATE); }

    static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c);
        return instance;
    }

    List<Recipe> recipes() {
        List<Recipe> out = new ArrayList<>();
        String raw = p.getString("recipes", null);
        if (raw == null) return defaults();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(Recipe.of(a.getJSONObject(i)));
        } catch (Exception e) {
            return defaults();
        }
        return out;
    }

    void saveRecipes(List<Recipe> list) {
        JSONArray a = new JSONArray();
        try {
            for (Recipe r : list) a.put(r.json());
        } catch (Exception ignored) {
        }
        p.edit().putString("recipes", a.toString()).apply();
    }

    private static List<Recipe> defaults() {
        List<Recipe> l = new ArrayList<>();
        l.add(Recipe.make("Airtime balance", "*155#", "", false, false));
        l.add(Recipe.make("Peek MoMo menu", "*126#", "", false, false));
        l.add(Recipe.make("Explore MoMo menu", "*126#", "", true, true));
        l.add(Recipe.make("MoMo send (example)", "*126#", "@transfer, @momo, {PHONE}, {PHONE}, {AMOUNT}, {PIN}", true, true));
        l.add(Recipe.make("MoMo balance (example)", "*126#", "@account, @balance, {PIN}", true, true));
        return l;
    }

    int simSlot() { return p.getInt("sim", -1); }
    void setSimSlot(int slot) { p.edit().putInt("sim", slot).apply(); }

    boolean cover() { return p.getBoolean("cover", true); }
    void setCover(boolean v) { p.edit().putBoolean("cover", v).apply(); }

    String phone() { return p.getString("phone", ""); }
    String amount() { return p.getString("amount", ""); }
    void setVars(String phone, String amount) { p.edit().putString("phone", phone).putString("amount", amount).apply(); }

    void saveRun(String title, String result, String log) {
        String stamp = new SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(new Date());
        String all = "── " + title + " · " + stamp + "\n" + log + "\n" + p.getString("log", "");
        if (all.length() > 20000) all = all.substring(0, 20000);
        p.edit().putString("result", result).putString("resultTitle", title).putString("log", all).apply();
    }

    String result() { return p.getString("result", ""); }
    String resultTitle() { return p.getString("resultTitle", ""); }
    String log() { return p.getString("log", ""); }
    void clearLog() { p.edit().remove("log").apply(); }
}
