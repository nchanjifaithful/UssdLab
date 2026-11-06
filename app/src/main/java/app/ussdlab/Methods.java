package app.ussdlab;

/** The Lab: every way this app can work with USSD, explained in plain language. Also mirrored in README.md. */
final class Methods {

    static final class Method {
        final String id, title, tagline, how, goodFor, needs, watchOut, tryLabel;

        Method(String id, String title, String tagline, String how, String goodFor, String needs, String watchOut, String tryLabel) {
            this.id = id;
            this.title = title;
            this.tagline = tagline;
            this.how = how;
            this.goodFor = goodFor;
            this.needs = needs;
            this.watchOut = watchOut;
            this.tryLabel = tryLabel;
        }
    }

    static final Method[] ALL = {
            new Method("silent", "Silent request",
                    "Ask the network quietly and get one reply back. No pop-up appears.",
                    "Android (8.0 and newer) has a built-in way for apps to send a USSD code and receive the answer directly. The phone's USSD pop-up never shows; the reply arrives inside the app.",
                    "Balance checks, data left, your own number — anything that answers in one message. Showing a balance on a card, in a widget or from a home-screen shortcut.",
                    "Phone permission.",
                    "It only works for codes that answer once. Menus such as *126# fail (that's the \"error 1\" you saw). Some phones and networks block it completely. When it fails, USSD Lab switches to the custom screen by itself if Automation is on.",
                    "Try *155# silently"),
            new Method("phone", "The phone's own pop-up",
                    "Dial the code and use the normal USSD pop-up, as if you typed it yourself.",
                    "The app asks Android to dial the code on the SIM you picked. You see the usual pop-up and do the typing. If Automation is on, USSD Lab quietly writes each screen to History.",
                    "Exploring a new menu, writing down its screens, or as a safe fallback when automation doesn't work on a phone.",
                    "Phone permission. Automation is optional (only for recording screens).",
                    "You do all the typing; the app only watches.",
                    "Try *126# in the pop-up"),
            new Method("dialpad", "Dial pad only",
                    "Open the dial pad with the code already typed. You press call.",
                    "The app hands the code to the phone's dialler but doesn't call. Nothing happens until you press the call button.",
                    "Apps that shouldn't ask for phone permission at all, or when you want people to confirm before anything is dialled.",
                    "Nothing.",
                    "One extra tap, and the app can't see the reply unless Automation is on.",
                    "Open the dial pad"),
            new Method("autopilot", "Auto-pilot",
                    "The app answers the menu for you while you watch.",
                    "After dialling, the Automation (Accessibility) service reads each pop-up and types the next answer from your steps, then taps Send. A small banner shows progress. If a screen doesn't match, it stops and you finish in the normal pop-up.",
                    "Repeating the same path through a menu: buying airtime, paying a bill, checking MoMo balance. Testing how a menu behaves on different phones.",
                    "Phone permission and Automation.",
                    "Menus change over time. Use words (@airtime) instead of numbers where you can. Pop-ups look a little different on every phone brand.",
                    "Try an auto-pilot run"),
            new Method("custom", "Custom screen",
                    "USSD Lab draws its own clean screen over the USSD pop-up.",
                    "The pop-up still opens, but USSD Lab covers it with its own full-screen design. Steps you saved run behind the cover. When the menu needs you, you see the question, the options as big buttons, and a keypad. Your answer is passed to the hidden pop-up for you.",
                    "Making USSD feel like an app: tap \"Airtime\" instead of typing 1, then 2, then 3. A friendlier experience for people who find USSD menus hard.",
                    "Phone permission and Automation.",
                    "It depends on reading the pop-up, so it needs testing on many phones. Google Play is strict about apps that use Accessibility for this. You can switch it off any time in Settings; runs then show the phone's pop-up. During a run, \"Phone view\" uncovers the pop-up for that run only.",
                    "Open *126# on the custom screen"),
            new Method("hybrid", "Hidden steps, then our PIN screen",
                    "Everything runs behind the cover; you only see a proper PIN screen.",
                    "This is the custom screen with saved steps: the app picks the options and types the number and amount behind the cover. When it reaches the PIN, it shows a secure PIN pad (screenshots blocked). Your PIN goes straight into the hidden pop-up and is never stored.",
                    "One-tap payments and airtime: \"Buy 500 airtime\" → enter PIN → done.",
                    "Phone permission and Automation.",
                    "The example steps are guesses at MTN's menu. Record your own route first (next card).",
                    "Try the airtime example"),
            new Method("record", "Record a route",
                    "Tap through a menu once; the app writes the steps for you.",
                    "Run a code on the custom screen with no steps. Each option you tap is saved by its words (\"@airtime\", not \"2\", so it still works if the menu order changes). Phone numbers and amounts become {PHONE} and {AMOUNT}, asked fresh each time. Your PIN is saved as {PIN}, never the digits. At the end, tap \"Save these steps\".",
                    "Building actions without knowing the menu by heart. Making a shortcut for something you do every week.",
                    "Phone permission and Automation.",
                    "Check the saved steps once. If a menu uses the same words twice, the app keeps the number instead.",
                    "Go to Record"),
            new Method("pin", "Saved PIN with fingerprint",
                    "Store your PIN locked to your fingerprint, so runs can finish by themselves.",
                    "The PIN is encrypted with a key kept in the phone's secure hardware (Android Keystore). The key only unlocks for 30 seconds after your fingerprint or screen lock, and only on this phone.",
                    "Fully automatic runs from a shortcut: fingerprint, and it's done.",
                    "A screen lock on the phone.",
                    "Your mobile money provider's terms may not allow apps to store or type your PIN. Typing it on the PIN screen each time is the safer default.",
                    "Open PIN settings"),
            new Method("sim", "Choose the SIM",
                    "Send codes from the SIM you pick.",
                    "Silent requests use that SIM's own connection. Dialled codes use that SIM's calling account, so the right network receives them.",
                    "Dual-SIM phones: MTN on one SIM, Orange on the other.",
                    "Phone permission.",
                    "A few phones ignore the choice and ask you which SIM to use.",
                    null),
            new Method("reader", "Reply reader",
                    "Pick out amounts and reference numbers from replies.",
                    "Simple text patterns look for things like \"1 500 FCFA\", \"NGN 2,000\" or \"Transaction ID: 12345\" and show them as tags under the reply.",
                    "Balance cards, receipts, spending history, low-balance reminders.",
                    "Nothing extra.",
                    "Every network words its replies differently, so some amounts may be missed.",
                    null),
            new Method("listen", "Listen mode",
                    "Save USSD replies even when you dial codes yourself.",
                    "When it's on, the Automation service copies the text of any USSD pop-up into History, including ones you open from the normal dialler. In this mode it never taps or types anything.",
                    "Keeping receipts automatically and building a history of balances.",
                    "Automation, and Listen mode switched on in Settings.",
                    "History stays on this phone only; clear it any time.",
                    "Open Settings"),
            new Method("widget", "Home-screen widget",
                    "Run commands from the home screen without opening the app.",
                    "The widget shows up to four commands (mark them with \"Show on widget\"). Tapping one runs it right there: silent codes answer on the widget itself, menus open USSD Lab's sheet over your home screen, and the latest reply is shown at the bottom of the widget.",
                    "Daily checks like airtime balance, and one-tap commands you recorded.",
                    "Phone access; Automation for menus. Long-press your home screen → Widgets → USSD Lab.",
                    "Commands that need {PIN} ask for it on the secure PIN screen (a saved PIN needs the app for the fingerprint check).",
                    null),
            new Method("shortcut", "Home-screen shortcuts",
                    "Run a saved action with one tap from your home screen.",
                    "Android lets apps pin shortcuts to the home screen. Each shortcut opens USSD Lab and runs one action straight away.",
                    "\"Check balance\", \"Buy 1 000 airtime\" or \"Pay electricity\" right on the home screen.",
                    "A launcher that supports pinned shortcuts (most do).",
                    "Actions with {PIN} still ask for your PIN or fingerprint.",
                    null),
    };

    /** Things that exist in the USSD world but are outside what this app does. */
    static final String[][] BEYOND = {
            {"SIM Toolkit menus", "Some money services live in the SIM menu app (SIM Toolkit) instead of USSD. Accessibility could drive that app the same way, but its screens are different, so it isn't built here."},
            {"Reading confirmation SMS", "Many services confirm by text message. Reading SMS needs SMS permission, which Google Play only allows for default SMS apps and a few exceptions."},
            {"Background USSD without a pop-up", "Android has hidden functions for multi-step USSD with no pop-up at all, but only system and carrier apps are allowed to use them."},
            {"Running your own USSD code", "To create a code people dial (like *384*123#), you rent it through a USSD gateway provider and answer it from a server. That's the service side of USSD, not the phone side."},
            {"Official mobile money APIs", "Providers such as MTN offer developer APIs so businesses can collect and send money without USSD at all. That's the right route for anything at scale."},
    };

    private Methods() {}
}
