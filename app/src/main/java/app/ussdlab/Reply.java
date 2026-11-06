package app.ussdlab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Reply reader": pulls amounts and reference numbers out of a USSD reply. Pure Java. */
public final class Reply {

    private Reply() {}

    private static final String CUR = "FCFA|XAF|XOF|CFA|NGN|GHS|KES|KSH|UGX|RWF|TZS|ZMW|MWK|USD|EUR|GH₵|₦|\\$|€";
    private static final String NUM = "\\d{1,3}(?:[ ,.\\u00A0]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?";
    private static final Pattern BEFORE = Pattern.compile("(?i)(?<![A-Z])(" + CUR + ")\\s?(" + NUM + ")");
    private static final Pattern AFTER = Pattern.compile("(?i)(" + NUM + ")\\s?(" + CUR + "|F)(?![A-Z])");
    private static final Pattern REF = Pattern.compile("(?i)\\b(?:trans(?:action)?\\s*id|txn\\s*id|txn|ref(?:erence)?|financial transaction id|id transaction)\\s*[:#.]?\\s*([A-Z0-9][A-Z0-9.\\-]{4,})");

    /** Amounts with their currency, e.g. "1 500 FCFA", "NGN 2,000.50". */
    public static List<String> amounts(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return new ArrayList<>(out);
        Matcher m = BEFORE.matcher(text);
        while (m.find()) out.add(m.group(1).toUpperCase() + " " + m.group(2).trim());
        m = AFTER.matcher(text);
        while (m.find()) {
            String cur = m.group(2).toUpperCase();
            if (cur.equals("F")) cur = "F";
            out.add(m.group(1).trim() + " " + cur);
        }
        return new ArrayList<>(out);
    }

    /** Transaction / reference ids, e.g. "Transaction ID: 123456789". */
    public static List<String> references(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = REF.matcher(text);
        while (m.find()) {
            String r = m.group(1).replaceAll("[.\\-]+$", "");
            if (r.matches(".*\\d.*") && !out.contains(r)) out.add(r);
        }
        return out;
    }
}
