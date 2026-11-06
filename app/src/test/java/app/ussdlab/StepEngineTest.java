package app.ussdlab;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plain-Java tests (no JUnit needed): java -cp build app.ussdlab.StepEngineTest */
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
        eq("pick no space", "3", StepEngine.pickOption("1.Envoi d'argent\n2.Retrait\n3.Mon compte", "compte"));

        Map<String, String> vars = new HashMap<>();
        vars.put("PHONE", "677123456");
        vars.put("AMOUNT", "500");

        StepEngine.Action x = StepEngine.decide(steps, 0, vars, false, menu, true);
        eq("step @", "REPLY(1, picked \"transfer\")", x);
        eq("step literal", "REPLY(1)", StepEngine.decide(steps, 1, vars, false, "Enter", true));
        eq("step phone", "REPLY(677123456)", StepEngine.decide(steps, 2, vars, false, "Number?", true));
        eq("step amount", "REPLY(500)", StepEngine.decide(steps, 3, vars, false, "Amount?", true));
        StepEngine.Action p = StepEngine.decide(steps, 4, vars, false, "PIN?", true);
        eq("step pin kind", "REPLY", p.kind);
        eq("step pin hidden", "••••", p.display());
        eq("step pin uses", true, p.usesPin());
        eq("stop before pin", "HANDOVER", StepEngine.decide(steps, 4, vars, true, "PIN?", true).kind);
        eq("no steps left", "HANDOVER", StepEngine.decide(steps, 5, vars, false, "More?", true).kind);
        eq("final screen", "FINISH(done)", StepEngine.decide(steps, 5, vars, false, "Success", false));
        eq("early end", "FINISH(menu ended early)", StepEngine.decide(steps, 2, vars, false, "Invalid", false));
        Map<String, String> empty = new HashMap<>();
        eq("missing var", "HANDOVER", StepEngine.decide(steps, 2, empty, false, "Number?", true).kind);
        eq("mixed", "REPLY(*500#)", StepEngine.decide(StepEngine.parse("*{amount}#"), 0, vars, false, "x", true));

        Job j = new Job("t", "*126#", steps, vars, "1234".toCharArray(), false, false);
        eq("fill pin", "1234", j.fill(p.value).toString());
        eq("log hides pin", false, j.log().contains("1234"));
        j.wipe();
        eq("wiped", "{PIN}", j.fill(p.value).toString());
        Job noPin = new Job("t", "*126#", steps, vars, null, false, false);
        noPin.index = 4;
        eq("no pin -> handover", "HANDOVER", noPin.next("PIN?", true).kind);

        System.out.println(passes + " passed, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
