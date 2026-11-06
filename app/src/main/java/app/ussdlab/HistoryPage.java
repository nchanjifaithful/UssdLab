package app.ussdlab;

import android.app.Dialog;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** History: every reply, grouped by day, with a detail sheet showing the full conversation. */
final class HistoryPage {

    private HistoryPage() {}

    private static int filter;
    private static final String[] FILTERS = {"All", "Done", "Stopped", "Heard"};
    private static final int[] FILTER_ICONS = {R.drawable.ic_history, R.drawable.ic_check, R.drawable.ic_alert, R.drawable.ic_eye};

    static void build(MainActivity a, LinearLayout page) {
        Ui ui = a.ui;
        List<Store.Run> all = a.store.runs();
        a.appBar(page, "History", all.isEmpty() ? "Replies will show up here" : all.size() + " replies on this phone",
                all.isEmpty() ? null : ui.iconButton(R.drawable.ic_delete, ui.onSurfaceVariant, 0x00000000, "Clear history", v -> confirmClear(a)));

        View[] chips = new View[FILTERS.length];
        for (int i = 0; i < FILTERS.length; i++) {
            final int f = i;
            chips[i] = ui.chip(FILTER_ICONS[i], FILTERS[i], filter == i, v -> {
                filter = f;
                a.showTab(MainActivity.HISTORY);
            });
        }
        page.addView(ui.chipRow(chips));

        List<Store.Run> runs = new ArrayList<>();
        for (Store.Run r : all) {
            boolean heard = "listen".equals(r.mode);
            if (filter == 0 || (filter == 1 && r.ok && !heard) || (filter == 2 && !r.ok) || (filter == 3 && heard)) runs.add(r);
        }
        if (runs.isEmpty()) {
            LinearLayout empty = ui.col();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(ui.dp(24), ui.dp(56), ui.dp(24), 0);
            empty.addView(ui.iconTile(R.drawable.ic_history, ui.surfaceContainer, ui.onSurfaceVariant, 64));
            TextView t = ui.titleSmall(filter == 0 ? "No replies yet" : "Nothing here");
            t.setPadding(0, ui.dp(16), 0, ui.dp(6));
            empty.addView(t);
            TextView b = ui.body(filter == 0 ? "Run a code from Home and its reply lands here. Turn on Listen mode in Settings to also keep replies from codes you dial yourself." : "Try another filter.");
            b.setGravity(Gravity.CENTER);
            empty.addView(b);
            page.addView(empty, ui.full());
            return;
        }

        String currentDay = null;
        List<View> rows = new ArrayList<>();
        for (Store.Run r : runs) {
            String day = dayLabel(r.time);
            if (!day.equals(currentDay)) {
                if (!rows.isEmpty()) page.addView(ui.group(rows.toArray(new View[0])), ui.full());
                rows.clear();
                page.addView(ui.section(day.toUpperCase(Locale.getDefault())));
                currentDay = day;
            }
            rows.add(row(a, r));
        }
        if (!rows.isEmpty()) page.addView(ui.group(rows.toArray(new View[0])), ui.full());
    }

    private static String dayLabel(long t) {
        if (DateUtils.isToday(t)) return "Today";
        if (DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS)) return "Yesterday";
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        c.setTimeInMillis(t);
        String pattern = c.get(Calendar.YEAR) == year ? "EEEE d MMMM" : "d MMMM yyyy";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(t));
    }

    private static View row(MainActivity a, Store.Run r) {
        Ui ui = a.ui;
        LinearLayout row = ui.row();
        row.setPadding(ui.dp(14), ui.dp(12), ui.dp(16), ui.dp(12));
        row.setBackground(ui.ripple(0x00000000, 0));
        row.addView(a.statusTile(r, 40));
        LinearLayout t = ui.col();
        t.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        TextView title = ui.titleSmall(r.title);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(title);
        TextView prev = ui.small(r.result.replace('\n', ' '));
        prev.setMaxLines(2);
        prev.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(prev);
        row.addView(t, Ui.weight());
        TextView time = ui.small(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(r.time)));
        row.addView(time);
        row.setOnClickListener(v -> showDetail(a, r));
        return row;
    }

    /** Bottom sheet with the full reply and the conversation with the menu, chat style. */
    static void showDetail(MainActivity a, Store.Run r) {
        Ui ui = a.ui;
        LinearLayout box = ui.col();
        LinearLayout head = ui.row();
        head.addView(a.statusTile(r, 44));
        LinearLayout ht = ui.col();
        ht.setPadding(ui.dp(14), 0, 0, 0);
        TextView title = ui.title(r.title);
        title.setMaxLines(2);
        ht.addView(title);
        String when = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(new Date(r.time));
        ht.addView(ui.small(when + "  ·  " + modeLabel(r.mode)));
        head.addView(ht, Ui.weight());
        box.addView(head, ui.full());
        box.addView(Ui.replyCard(ui, r.result), ui.full(14));

        List<String[]> chat = parseLog(r.log);
        if (chat.size() > 1) {
            TextView s = ui.section("CONVERSATION WITH THE MENU");
            s.setPadding(ui.dp(4), ui.dp(20), 0, ui.dp(8));
            box.addView(s);
            for (String[] line : chat) box.addView(bubble(ui, line[0], line[1]), ui.full(6));
        }

        Dialog[] d = new Dialog[1];
        LinearLayout buttons = ui.row();
        buttons.setPadding(0, ui.dp(20), 0, 0);
        buttons.addView(ui.iconText(ui.tonal("Copy", v -> a.copy(r.result, "Reply copied")), R.drawable.ic_copy), Ui.weight());
        LinearLayout.LayoutParams lp = Ui.weight();
        lp.setMarginStart(ui.dp(8));
        buttons.addView(ui.iconText(ui.tonal("Share", v -> a.share(r.title, r.title + "\n" + r.result)), R.drawable.ic_share), lp);
        Store.Action action = r.actionId == null || r.actionId.isEmpty() ? null : a.store.action(r.actionId);
        if (action != null) {
            LinearLayout.LayoutParams lp2 = Ui.weight();
            lp2.setMarginStart(ui.dp(8));
            buttons.addView(ui.iconText(ui.filled("Again", v -> {
                d[0].dismiss();
                a.run(action);
            }), R.drawable.ic_replay), lp2);
        }
        box.addView(buttons, ui.full());
        d[0] = Sheet.show(a, ui, box);
    }

    private static String modeLabel(String m) {
        if (m == null) return "";
        switch (m) {
            case "listen": return "Heard with Listen mode";
            case "watch": return "Phone's pop-up";
            case "autopilot": return "Auto-pilot";
            case "custom": return "Custom screen";
            case Store.SILENT: return "Silent";
            case Store.DIALPAD: return "Dial pad";
            default: return Store.modeName(m);
        }
    }

    /** Log lines → (who, text). "menu" = what the USSD screen said, "you" = an answer, "note" = status. */
    static List<String[]> parseLog(String log) {
        List<String[]> out = new ArrayList<>();
        if (log == null) return out;
        for (String raw : log.split("\n")) {
            String line = raw.replaceFirst("^\\d\\d:\\d\\d:\\d\\d\\s+", "").trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("◀")) out.add(new String[]{"menu", line.substring(1).trim().replace(" ⏎ ", "\n")});
            else if (line.startsWith("▶")) {
                String t = line.substring(1).trim();
                int p = t.indexOf("  (");
                out.add(new String[]{"you", p > 0 ? t.substring(0, p) : t});
            } else if (line.startsWith("✋")) out.add(new String[]{"note", line.substring(1).trim()});
        }
        return out;
    }

    private static View bubble(Ui ui, String who, String text) {
        LinearLayout wrap = ui.row();
        TextView b = ui.text(text, 14, who.equals("you") ? ui.onPrimary : who.equals("note") ? ui.onTertiaryContainer : ui.onSurface);
        b.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        int bg = who.equals("you") ? ui.primary : who.equals("note") ? ui.tertiaryContainer : ui.surfaceContainer;
        b.setBackground(ui.shape(bg, 18));
        if (who.equals("you")) {
            wrap.setGravity(Gravity.END);
            b.setText(text.replace("you: ", ""));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (who.equals("you")) lp.setMarginStart(ui.dp(48));
        else lp.setMarginEnd(ui.dp(48));
        wrap.addView(b, lp);
        return wrap;
    }

    private static void confirmClear(MainActivity a) {
        Ui ui = a.ui;
        LinearLayout box = ui.col();
        box.addView(ui.title("Clear history?"));
        TextView b = ui.body("All saved replies on this phone will be removed. Your commands stay.");
        b.setPadding(0, ui.dp(8), 0, ui.dp(18));
        box.addView(b);
        Dialog[] d = new Dialog[1];
        LinearLayout r = ui.row();
        r.addView(ui.tonal("Cancel", v -> d[0].dismiss()), Ui.weight());
        TextView clear = ui.filled("Clear", v -> {
            d[0].dismiss();
            a.store.clearRuns();
            Widget.refresh(a);
            a.showTab(MainActivity.HISTORY);
        });
        clear.setBackground(ui.ripple(ui.error, 22));
        clear.setTextColor(ui.dark ? 0xFF690005 : 0xFFFFFFFF);
        LinearLayout.LayoutParams lp = Ui.weight();
        lp.setMarginStart(ui.dp(10));
        r.addView(clear, lp);
        box.addView(r, ui.full());
        d[0] = Sheet.show(a, ui, box);
    }
}
