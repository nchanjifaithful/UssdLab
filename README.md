# USSD Lab

A small Android app (about 100 KB, no extra libraries, no internet permission) for exploring **every way a mobile app can work with USSD codes** like `*155#` or `*126#`. It's a working tool and a guide: each method is explained in plain language inside the app (the **Lab** tab) and below.

> **Status:** experimental. Built and signed with standalone tools and tested by its logic tests (50 cases), but USSD pop-ups look different on every phone brand, so expect to adjust things after testing on real devices.

---

## The big idea: a custom screen over USSD

Normally, buying airtime means: dial `*126#`, read a menu, type `1`, read another, type `2`, type the amount, type your PIN.

USSD Lab can do this instead:

1. You tap a command (in the app, on a shortcut or on the home-screen widget).
2. The phone opens the normal USSD pop-up, but **USSD Lab immediately covers it** with a dark backdrop and its own **rounded sheet at the bottom of the screen**.
3. Behind that, the app **reads each menu and types the answers** from your saved steps. The sheet shows a timeline: "Choose Airtime ✓ · Enter the amount ✓ · Enter your PIN…".
4. When it needs you, the sheet shows the question, the options as large buttons, a phone-style keypad, or a **PIN pad** (dots only, screenshots blocked).
5. Your answer goes into the hidden pop-up. The result appears on the same sheet, with amounts and reference numbers picked out, plus Copy and **Save as a command**.

Two Android features make this possible:

| Feature | What it does here |
|---|---|
| **Accessibility service** ("Automation" in the app) | Reads the text of the USSD pop-up and types into it / taps Send. |
| **Accessibility overlay** (a "draw over other apps" layer that belongs to the accessibility service) | Paints USSD Lab's sheet above the pop-up. No separate "Display over other apps" permission is needed. |

**The custom screen can be switched off** in Settings → *Custom screen over USSD*. When it's off you see the phone's pop-up, saved steps are still typed for you, and a small card at the bottom shows progress. During a run, *Show the phone's pop-up* uncovers it for that run only.

---

## The app, tab by tab

| Tab | What it's for |
|---|---|
| **Home** | Send any code, choose *how it runs*, pick the SIM (top right), and run your commands from a grid of tiles. Hold a tile for options: edit, show on widget, home-screen shortcut, share, delete. |
| **Record** | The main way to make commands. Name it, enter the code, tap **Start recording**, tap through the menu once on USSD Lab's sheet, then **Save**. Also lists your recorded commands and lets you export/import them. |
| **History** | Every reply grouped by day (Today, Yesterday…), with filters (All, Done, Stopped, Heard). Tap one for a bottom sheet with the full reply and the *conversation with the menu* shown as chat bubbles, plus Copy, Share and Run again. |
| **Guide** | Every way an app can use USSD, in plain words, split into *Ways to run*, *Features* and *Beyond*. Each card has *How it works*, *Good for*, *What it needs*, *Watch out* and a *Try it* button. |
| **Settings** | Phone access, Automation, the custom-screen switch, SIM, Listen mode, saved PIN, widget help, export/import, privacy. |

The **latest reply sits at the bottom of the app**, just above the tabs (like a music "now playing" bar). Tap it to open the full reply.

### Home-screen widget

Long-press the home screen → **Widgets** → **USSD Lab**. It shows up to four commands (choose them with *Show on widget*) and the latest reply at the bottom.
**Tapping a command runs it right there; the app doesn't open:**
- *Silent* codes (like `*155#`) answer on the widget itself.
- Menus open USSD Lab's sheet over the home screen; {PHONE}/{AMOUNT} are asked on the sheet's keypad first.
- *Dial pad* commands open the dialler.
Commands needing {PIN} ask on the secure PIN pad (a *saved* PIN needs the app for the fingerprint check).

### Ways to run a code ("How should it run?")

| Choice | What happens |
|---|---|
| **Custom screen** | USSD Lab's own screen covers the pop-up; steps run hidden; you answer on clean buttons, a keypad and a PIN pad. |
| **Auto-pilot** | The phone's pop-up stays visible; steps are typed into it automatically; a small banner shows progress. |
| **Silent** | Sent in the background; the single reply appears in the app. No pop-up. For one-answer codes only. |
| **Phone's pop-up** | Just dials. You do everything. With Automation on, screens are saved to History. |
| **Dial pad** | Opens the dialler with the code typed; you press call. Needs no permissions. |

### Writing steps

Steps are the answers to give, in order, separated by commas:

| Step | Meaning |
|---|---|
| `1` | Type 1. |
| `@airtime` | Find the option whose words contain "airtime" and type its number. Survives menu re-ordering. |
| `{PHONE}` | A phone number you're asked for before each run. |
| `{AMOUNT}` | An amount you're asked for before each run. |
| `{PIN}` | Your PIN: typed on the secure PIN screen, or filled from the saved PIN after a fingerprint check. |

Example: `@airtime, @myself, {AMOUNT}, {PIN}`

**Don't know the steps?** Use the **Record** tab: tap through once, then tap **Save**. The app records options as `@words`, numbers and amounts as `{PHONE}`/`{AMOUNT}`, and the PIN as `{PIN}` (never the digits).

If a screen doesn't match (option not found, menu changed, too slow), automation stops and **hands over to you**: on the custom screen you get buttons and a keypad; in Auto-pilot you finish in the phone's pop-up.

---

## Every method, explained

### 1. Silent request
- **What:** ask the network quietly and get one reply back. No pop-up.
- **How:** Android 8+ has `TelephonyManager.sendUssdRequest`, which sends a code and returns the answer to the app.
- **Good for:** balance, data left, own number; balance cards, widgets, shortcuts.
- **Needs:** phone permission.
- **Watch out:** only for codes that answer once. Menus like `*126#` fail. Phones report this with their own error numbers (for example "error 1"). When it fails, USSD Lab switches to the custom screen (or the pop-up if the custom screen is off) automatically, provided Automation is on.

### 2. The phone's own pop-up
- **What:** dial and use the normal pop-up.
- **How:** the app places the call through Android's Telecom service on the chosen SIM.
- **Good for:** exploring new menus; a safe fallback.
- **Needs:** phone permission; Automation only for recording screens.

### 3. Dial pad only
- **What:** open the dialler with the code filled in; you press call.
- **How:** `ACTION_DIAL` intent.
- **Good for:** apps that shouldn't hold phone permission; letting people confirm first.
- **Needs:** nothing.

### 4. Auto-pilot
- **What:** the app answers the menu while you watch.
- **How:** the accessibility service reads each pop-up, types the next step, taps Send.
- **Good for:** repeating the same path (airtime, bills, balance); testing menus across phones.
- **Watch out:** menus change; prefer `@words`. Pop-ups differ by brand.

### 5. Custom screen
- **What:** USSD Lab's own interface drawn over the pop-up.
- **How:** accessibility overlay covers the pop-up; answers are typed into the hidden pop-up.
- **Good for:** making USSD feel like an app; easier for people who struggle with menus.
- **Watch out:** needs wide device testing; Google Play's Accessibility rules (see below). Can be switched off.

### 6. Hidden steps, then our PIN screen
- **What:** everything runs behind the cover; you only see a PIN pad.
- **Good for:** one-tap airtime and payments.
- **Watch out:** the bundled examples guess MTN's menu wording. Record your own route.

### 7. Record a route
- **What:** tap through once; the app writes the steps.

### 8. Saved PIN with fingerprint
- **How:** AES key in the Android Keystore (secure hardware), usable for 30 s after fingerprint/screen-lock; PIN stored encrypted.
- **Watch out:** your provider's terms may forbid storing/entering the PIN. Typing it on the PIN screen each time is the default.

### 9. Choose the SIM
- **How:** silent requests use `createForSubscriptionId`; dialled codes use the SIM's `PhoneAccountHandle`.
- **Watch out:** some phones still ask which SIM.

### 10. Reply reader
- **What:** pulls amounts ("1 500 FCFA", "NGN 2,000") and reference numbers ("Transaction ID: …") out of replies and shows them as tags.

### 11. Listen mode
- **What:** saves the text of any USSD pop-up to History, even codes you dial yourself. Never taps or types.

### 12. Home-screen widget
- **What:** up to four commands on the home screen that run without opening the app; shows the latest reply.

### 13. Home-screen shortcuts
- **What:** pin a command to the home screen as its own icon; one tap runs it.

### Beyond this app
- **SIM Toolkit menus:** some services live in the SIM menu app; could be automated similarly, not built here.
- **Confirmation SMS:** reading SMS needs SMS permission, which Google Play restricts to default SMS apps and a few exceptions.
- **Background USSD with no pop-up:** hidden Android functions exist, but only system/carrier apps may use them.
- **Your own USSD code:** rented through a USSD gateway provider and answered from a server — the service side of USSD.
- **Official mobile money APIs:** providers such as MTN offer developer APIs to collect and send money without USSD; the right route at scale.

---

## Install and set up

1. Download `UssdLab.apk` from the [latest release](https://github.com/nchanjifaithful/UssdLab/releases/latest) and install it (allow installs from your file manager when asked).
2. **Settings → Phone access → Allow.**
3. **Settings → Automation → Open settings →** Installed/Downloaded apps → USSD Lab → On.
   On Android 13+, if the switch is greyed out: **App info → ⋮ → Allow restricted settings**, then try again.
4. Optional: **Settings → Saved PIN** (needs a screen lock).
5. Optional: add the widget.

Signed with a self-made key: fine for your own phones, not for the Play Store.

## Things to know

- **Google Play:** apps using Accessibility for anything other than helping disabled users get strict review: a prominent in-app disclosure, a Play Console declaration and approval. Check the current policy before publishing. The service declares `isAccessibilityTool="false"`.
- **Provider terms:** MTN, Orange and others may not allow apps to store or type the PIN.
- **Device differences:** USSD pop-ups vary by brand and Android version. Use History → Show steps to see exactly what the app read.
- **Privacy:** no internet permission. Actions, History and the encrypted PIN stay in app storage. The service ignores everything unless a run you started is going (or Listen mode is on, which only copies text).

---

## For developers

| File | Role |
|---|---|
| `StepEngine.java` | Pure Java: parses steps, decides each reply, finds options, detects PIN screens, records routes, describes steps. |
| `Reply.java` | Pure Java: amounts and reference numbers in replies. |
| `Job.java` | One run: steps, progress, recorded route, PIN (wiped after use), log. |
| `UssdService.java` | Accessibility service: reads pop-ups, types answers, hands over, Listen mode, runs started from the widget. |
| `Screen.java` | Reads a USSD pop-up (text, input box, buttons) and acts on it. |
| `Skin.java` | The custom USSD interface: bottom sheet with step timeline, options, keypad, PIN pad, value entry, result, bottom banner. |
| `Widget.java` | Home-screen widget; runs commands without opening the app. |
| `Sims.java` | SIM list, silent requests, dialling on a chosen SIM, dial pad. |
| `PinVault.java` | Keystore-backed PIN storage. |
| `Store.java` | Commands, settings, History, export/import (SharedPreferences). |
| `Ui.java`, `Sheet.java` | Tiny Material 3 kit on plain views (wallpaper colours on Android 12+) and an in-app bottom sheet. |
| `MainActivity.java` | Tab host, bottom navigation, reply bar, sheets, and starting runs. |
| `HomePage`, `RecordPage`, `HistoryPage`, `GuidePage`, `SettingsPage`, `EditorPage` | One file per screen. |
| `Methods.java` | The Guide's text (mirrors this README). |

**Build in Android Studio:** open the folder and Run (Gradle files included).

**Build without Gradle** (what produced the APK): Java + `android.jar` + `aapt2` + `d8` + `apksigner`:

```
AAPT2=… D8_JAR=… APKSIGNER_JAR=… ANDROID_JAR=… [ECJ_JAR=…] [SHRINK=0] ./build.sh
```

By default the build runs R8, which removes unused code (about a third smaller). `SHRINK=0` skips it.

**Logic tests** (no Android needed):

```
javac -d /tmp/t app/src/main/java/app/ussdlab/{StepEngine,Job,Reply}.java app/src/test/java/app/ussdlab/StepEngineTest.java
java -cp /tmp/t app.ussdlab.StepEngineTest
```

**Releases:** GitHub Actions builds and tests every push. Pushing a tag like `v0.4` publishes a release with `UssdLab.apk` attached, signed with the project's release key (stored as repo secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`).
