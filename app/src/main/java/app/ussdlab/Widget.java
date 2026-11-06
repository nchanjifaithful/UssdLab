package app.ussdlab;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Home-screen widget. Tapping a command runs it right there: silent codes answer on the widget
 * itself; menus open the custom sheet over the home screen. The app never has to open.
 */
public class Widget extends AppWidgetProvider {

    static final String ACTION_RUN = "app.ussdlab.WIDGET_RUN";
    static final String EXTRA_ID = "id";
    private static final int[] BUTTONS = {R.id.b1, R.id.b2, R.id.b3, R.id.b4};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (!ACTION_RUN.equals(i.getAction())) return;
        Store.Action a = Store.get(c).action(i.getStringExtra(EXTRA_ID));
        if (a == null) {
            refresh(c);
            return;
        }
        run(c, a, goAsync());
    }

    private static void run(Context c, Store.Action a, PendingResult pr) {
        Store store = Store.get(c);
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            store.setWidgetStatus("Open the app to allow phone access");
            refresh(c);
            pr.finish();
            return;
        }
        UssdService svc = UssdService.instance;
        if (Store.SILENT.equals(a.mode)) {
            store.setWidgetStatus("Asking the network…");
            refresh(c);
            Handler h = new Handler(Looper.getMainLooper());
            boolean[] done = {false};
            Runnable giveUp = () -> {
                if (done[0]) return;
                done[0] = true;
                store.setWidgetStatus("No answer yet");
                refresh(c);
                pr.finish();
            };
            h.postDelayed(giveUp, 25000);
            Sims.quick(c, a.code, Sims.selected(c), (ok, reply) -> {
                if (done[0]) return;
                done[0] = true;
                h.removeCallbacks(giveUp);
                if (ok) {
                    store.addRun(a.title(), Store.SILENT, true, reply, "◀ " + reply.replace("\n", " ⏎ "), a.id);
                    store.setWidgetStatus("");
                } else if (svc != null) {
                    store.setWidgetStatus("");
                    svc.launch(Store.Action.make(a.title(), a.code, a.steps, Store.CUSTOM));
                } else {
                    store.addRun(a.title(), Store.SILENT, false, reply, "✗ " + reply, a.id);
                    store.setWidgetStatus("");
                }
                refresh(c);
                pr.finish();
            });
            return;
        }
        if (svc != null) {
            svc.launch(a);
        } else if (Store.PHONE.equals(a.mode)) {
            try {
                Sims.dial(c, a.code, Sims.selected(c));
            } catch (RuntimeException e) {
                store.setWidgetStatus("Couldn't dial");
            }
        } else {
            store.setWidgetStatus("Turn on Automation in the app");
        }
        refresh(c);
        pr.finish();
    }

    /** Redraws every USSD Lab widget from the saved state. Safe to call from anywhere. */
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
        if (ids == null || ids.length == 0) return;
        Store store = Store.get(c);
        List<Store.Action> actions = store.widgetActions();
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        for (int i = 0; i < BUTTONS.length; i++) {
            int id = BUTTONS[i];
            if (i < actions.size()) {
                Store.Action a = actions.get(i);
                v.setViewVisibility(id, View.VISIBLE);
                v.setTextViewText(id, a.title());
                v.setTextViewCompoundDrawablesRelative(id, widgetIcon(a.mode), 0, 0, 0);
                v.setOnClickPendingIntent(id, intentFor(c, a, i));
            } else {
                v.setViewVisibility(id, View.GONE);
            }
        }
        v.setViewVisibility(R.id.row2, actions.size() > 2 ? View.VISIBLE : View.GONE);

        String status = store.widgetStatus();
        Store.Run last = store.lastRun();
        if (status.isEmpty() && last != null) {
            status = DateUtils.getRelativeTimeSpanString(last.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
        }
        v.setTextViewText(R.id.status, status);
        v.setTextViewText(R.id.reply, last == null ? "Tap a command. The reply shows up here." : last.title + ": " + last.result);

        Intent open = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, 2)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.reply, PendingIntent.getActivity(c, 900, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent home = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.head, PendingIntent.getActivity(c, 901, home, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids, v);
    }

    private static PendingIntent intentFor(Context c, Store.Action a, int slot) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Store.DIALPAD.equals(a.mode)) {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", a.code, null));
            return PendingIntent.getActivity(c, 800 + slot, dial, flags);
        }
        Intent i = new Intent(c, Widget.class).setAction(ACTION_RUN).putExtra(EXTRA_ID, a.id);
        return PendingIntent.getBroadcast(c, 700 + slot, i, flags);
    }

    private static int widgetIcon(String mode) {
        switch (mode) {
            case Store.SILENT: return R.drawable.wi_bolt;
            case Store.AUTOPILOT: return R.drawable.wi_play;
            case Store.PHONE: return R.drawable.wi_phone;
            case Store.DIALPAD: return R.drawable.wi_dialpad;
            default: return R.drawable.wi_layers;
        }
    }
}
