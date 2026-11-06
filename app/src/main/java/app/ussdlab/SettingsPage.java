package app.ussdlab;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;

/** Settings: setup checklist, how runs look, security, commands and about — as grouped lists. */
final class SettingsPage {

    private SettingsPage() {}

    static void build(MainActivity a, LinearLayout page) {
        Ui ui = a.ui;
        Store store = a.store;
        a.appBar(page, "Settings", null);

        boolean phone = a.phoneOk();
        boolean auto = a.serviceOn();
        page.addView(ui.section("GET STARTED"));
        page.addView(ui.group(
                ui.listRow(R.drawable.ic_phone, "Phone access", "Dial codes and see your SIMs", status(ui, phone),
                        phone ? null : v -> a.askPerms(null)),
                ui.listRow(R.drawable.ic_accessibility, "Automation", "Read and answer USSD pop-ups. Powers menus, recording, the widget and Listen mode.",
                        status(ui, auto), v -> a.automationSheet())), ui.full());

        page.addView(ui.section("HOW RUNS LOOK"));
        page.addView(ui.group(
                ui.listRow(R.drawable.ic_layers, "Custom screen over USSD",
                        store.cover() ? "On: USSD Lab's sheet covers the pop-up" : "Off: you see the phone's pop-up; steps are still typed for you",
                        toggle(a, store.cover(), on -> {
                            store.setCover(on);
                            a.toast(on ? "Custom screen on" : "Custom screen off");
                        }), null),
                ui.listRow(R.drawable.ic_sim, "SIM", a.simLabel(), null, v -> a.simSheet()),
                ui.listRow(R.drawable.ic_eye, "Listen mode", "Keep replies from codes you dial yourself. Never taps or types.",
                        toggle(a, store.listen(), on -> {
                            store.setListen(on);
                            if (on && !a.serviceOn()) a.toast("Turn on Automation too, so Listen mode can see the pop-ups");
                        }), null)), ui.full());

        page.addView(ui.section("SECURITY"));
        page.addView(ui.group(
                ui.listRow(R.drawable.ic_lock, "Saved PIN", PinVault.has(a) ? "Saved, unlocks with your fingerprint" : "Not saved: you type it on the secure PIN screen",
                        null, v -> a.pinSheet())), ui.full());

        page.addView(ui.section("COMMANDS"));
        page.addView(ui.group(
                ui.listRow(R.drawable.ic_widgets, "Home-screen widget", "Long-press your home screen → Widgets → USSD Lab. Pick its commands with \"Show on widget\".",
                        null, v -> a.infoSheet(R.drawable.ic_widgets, "Add the widget",
                                "1.  Long-press an empty spot on your home screen\n2.  Tap Widgets\n3.  Find USSD Lab and drag it onto the screen\n\nTapping a command on the widget runs it right there. Silent codes answer on the widget; menus open USSD Lab's sheet over your home screen. Choose which commands appear with \"Show on widget\" (hold a command on Home).")),
                ui.listRow(R.drawable.ic_upload, "Export commands", "Share them as text with another phone", null,
                        v -> a.share("USSD Lab commands", Store.export(store.actions()))),
                ui.listRow(R.drawable.ic_download, "Import commands", "Paste commands shared from another phone", null,
                        v -> RecordPage.importSheet(a))), ui.full());

        page.addView(ui.section("ABOUT"));
        page.addView(ui.group(
                ui.listRow(R.drawable.ic_lock, "Privacy", "No internet permission: nothing leaves your phone", null,
                        v -> a.infoSheet(R.drawable.ic_lock, "Privacy",
                                "USSD Lab has no internet permission, so nothing it reads can leave your phone.\n\nCommands, History and the encrypted PIN stay in the app's private storage and are removed if you uninstall it.\n\nAutomation only acts while a run you started is going. With Listen mode on, it only copies the text of USSD pop-ups into History.")),
                ui.listRow(R.drawable.ic_info, "USSD Lab 0.3", "A lightweight lab and guide for USSD on Android", null,
                        v -> a.showTab(MainActivity.GUIDE))), ui.full());
    }

    private static View status(Ui ui, boolean ok) {
        return ok ? ui.badge("On", ui.successContainer, ui.onSuccessContainer) : ui.badge("Set up", ui.tertiaryContainer, ui.onTertiaryContainer);
    }

    interface Toggle { void set(boolean on); }

    private static View toggle(MainActivity a, boolean on, Toggle t) {
        Switch s = new Switch(a);
        s.setChecked(on);
        s.setOnCheckedChangeListener((b, v) -> {
            t.set(v);
            b.post(() -> a.showTab(MainActivity.SETTINGS));
        });
        return s;
    }
}
