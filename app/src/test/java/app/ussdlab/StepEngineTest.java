package app.ussdlab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plain-Java tests (no JUnit needed). See README for how to run. */
public class StepEngineTest {
    static int fails = 0, passes = 0;

    static void eq(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) passes++;
        else { fails++; System.out.println("FAIL " + what + ": want <" + want + "> got <" + got + ">"); }
    }

    public static void main(String[] a) {
        List<String> steps = StepEngine.parse(" @transfer, 1,\n{PHONE} , {AMOUNT}, {PIN} ,, ");
        eq("parse", "[@transfer, 1, {PHONE}, {AMOUNT}, {PIN}]", steps);

        String menu = "MTN MoMo\n1. Transfer money\n2) Buy airtime\n3 - My account\n4:Paiement de factures";
        eq("pick dot", "1", StepEngine.pickOption(menu, "transfer"));
        eq("pick paren", "2", StepEngine.pickOption(menu, "AIRTIME"));
        eq("pick dash", "3", StepEngine.pickOption(menu, "account"));
        eq("pick accents", "4", StepEngine.pickOption(menu, "paiement de facture"));
        eq("pick missing", null, StepEngine.pickOption(menu, "loan"));
        eq("pick inline", "2", StepEngine.pickOption("Choose: 1.Send 2.Balance 3.Exit", "balance"));

        // options + message for the custom screen
        eq("options", "[1=Transfer money, 2=Buy airtime, 3=My account, 4=Paiement de factures]", StepEngine.options(menu));
        eq("message", "MTN MoMo", StepEngine.message(menu));
        eq("inline options", "[1=Send, 2=Balance, 3=Exit]", StepEngine.options("Choose: 1.Send 2.Balance 3.Exit"));
        eq("inline message", "Choose:", StepEngine.message("Choose: 1.Send 2.Balance 3.Exit"));
        eq("no options", "[]", StepEngine.options("Enter amount"));
        eq("question only", "Enter amount", StepEngine.message("Enter amount"));

        // PIN detection
        eq("pin screen", true, StepEngine.asksForPin("Enter your PIN to confirm"));
        eq("pin fr", true, StepEngine.asksForPin("Entrez votre code secret"));
        eq("menu with change pin", false, StepEngine.asksForPin("1. Send\n2. Change PIN"));
        eq("not pin", false, StepEngine.asksForPin("Enter amount"));
        eq("spinning not pin", false, StepEngine.asksForPin("Shopping list"));

        // recording a route
        eq("record option", "@buy airtime", StepEngine.recordStep(menu, "2"));
        eq("record option words", "2", StepEngine.pickOption(menu, "buy airtime"));
        eq("record ambiguous", "1", StepEngine.recordStep("1. MoMo\n2. MoMo Pay", "1"));
        eq("record amount", "{AMOUNT}", StepEngine.recordStep("Enter amount", "500"));
        eq("record phone", "{PHONE}", StepEngine.recordStep("Enter recipient number", "677123456"));
        eq("record short number", "12", StepEngine.recordStep("Enter recipient number", "12"));
        eq("record pin", "{PIN}", StepEngine.recordStep("Enter PIN", "1234"));
        eq("record literal", "1", StepEngine.recordStep("Confirm? Reply 1 to continue", "1"));

        Map<String, String> vars = new HashMap<>();
        vars.put("PHONE", "677123456");
        vars.put("AMOUNT", "500");

        eq("step @", "REPLY(1, picked \"transfer\")", StepEngine.decide(steps, 0, vars, false, menu, true));
        eq("step literal", "REPLY(1)", StepEngine.decide(steps, 1, vars, false, "Enter", true));
        eq("step phone", "REPLY(677123456)", StepEngine.decide(steps, 2, vars, false, "Number?", true));
        eq("step amount", "REPLY(500)", StepEngine.decide(steps, 3, vars, false, "Amount?", true));
        StepEngine.Action p = StepEngine.decide(steps, 4, vars, false, "PIN?", true);
        eq("step pin hidden", "••••", p.display());
        StepEngine.Action stop = StepEngine.decide(steps, 4, vars, true, "PIN?", true);
        eq("no pin -> ask", "HANDOVER", stop.kind);
        eq("no pin -> pinStep", true, stop.pinStep);
        eq("no steps left", "HANDOVER(Your turn)", StepEngine.decide(steps, 5, vars, false, "More?", true));
        eq("final screen", "FINISH(done)", StepEngine.decide(steps, 5, vars, false, "Success", false));
        eq("missing var", "HANDOVER", StepEngine.decide(steps, 2, new HashMap<>(), false, "Number?", true).kind);
        eq("mixed", "REPLY(*500#)", StepEngine.decide(StepEngine.parse("*{amount}#"), 0, vars, false, "x", true));

        // Job
        Job j = new Job("t", "*126#", steps, vars, "1234".toCharArray(), Job.Mode.CUSTOM);
        eq("fill pin", "1234", j.fill(p.value).toString());
        j.next(menu, true);
        eq("route records step", "[@transfer]", j.route);
        eq("log hides pin", false, j.log().contains("1234"));
        j.wipe();
        eq("wiped", "{PIN}", j.fill(p.value).toString());
        Job noPin = new Job("t", "*126#", steps, vars, null, Job.Mode.CUSTOM);
        noPin.index = 4;
        StepEngine.Action np = noPin.next("Enter PIN", true);
        eq("job no pin -> pinStep", true, np.pinStep);
        Job manual = new Job("t", "*126#", steps, vars, null, Job.Mode.CUSTOM);
        manual.manual = true;
        eq("manual -> handover", "HANDOVER", manual.next(menu, true).kind);

        // Reply reader
        eq("amount after", "[1 500 FCFA]", Reply.amounts("Your balance is 1 500 FCFA."));
        eq("amount before", "[NGN 2,000.50]", Reply.amounts("Bal: NGN 2,000.50 valid till"));
        eq("amount naira", "[₦ 300]", Reply.amounts("You have ₦300 airtime"));
        eq("amount plain F", "[2500 F]", Reply.amounts("Solde: 2500 F"));
        eq("no amount", "[]", Reply.amounts("Dial *123# for more"));
        eq("ref", "[8812345678]", Reply.references("Transfer done. Transaction ID: 8812345678. New balance 200 FCFA"));
        eq("ref fr", "[ABC12345]", Reply.references("Paiement reussi. Ref: ABC12345"));

        System.out.println(passes + " passed, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
