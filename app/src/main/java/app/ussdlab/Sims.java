package app.ussdlab;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.List;

/** SIM listing, silent single-reply USSD, and dialling on a chosen SIM. */
final class Sims {

    static final class Sim {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        int slot = -1;
        String label;
        PhoneAccountHandle handle;
    }

    interface Reply { void onReply(boolean ok, String text); }

    private Sims() {}

    @SuppressWarnings("MissingPermission")
    static List<Sim> list(Context c) {
        List<Sim> out = new ArrayList<>();
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return out;
        try {
            SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
            TelecomManager tc = c.getSystemService(TelecomManager.class);
            TelephonyManager tm = c.getSystemService(TelephonyManager.class);
            List<SubscriptionInfo> infos = sm == null ? null : sm.getActiveSubscriptionInfoList();
            List<PhoneAccountHandle> handles = tc == null ? new ArrayList<>() : tc.getCallCapablePhoneAccounts();
            if (infos == null) return out;
            for (int i = 0; i < infos.size(); i++) {
                SubscriptionInfo info = infos.get(i);
                Sim s = new Sim();
                s.subId = info.getSubscriptionId();
                s.slot = info.getSimSlotIndex();
                CharSequence name = info.getDisplayName() != null ? info.getDisplayName() : info.getCarrierName();
                s.label = "SIM " + (s.slot + 1) + (name == null ? "" : " · " + name);
                for (PhoneAccountHandle h : handles) {
                    if (Build.VERSION.SDK_INT >= 30 && tm != null) {
                        if (tm.getSubscriptionId(h) == s.subId) s.handle = h;
                    } else if (String.valueOf(s.subId).equals(h.getId())
                            || (info.getIccId() != null && !info.getIccId().isEmpty() && h.getId().startsWith(info.getIccId()))) {
                        s.handle = h;
                    }
                }
                if (s.handle == null && handles.size() == infos.size()) s.handle = handles.get(i);
                out.add(s);
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    /** The SIM chosen in the app, or the first one. */
    static Sim selected(Context c) {
        List<Sim> all = list(c);
        int slot = Store.get(c).simSlot();
        for (Sim s : all) if (s.slot == slot) return s;
        return all.isEmpty() ? null : all.get(0);
    }

    /** Android's built-in silent USSD: returns the first reply only. Works for codes like *155#. */
    @SuppressWarnings("MissingPermission")
    static void quick(Context c, String code, Sim sim, Reply reply) {
        TelephonyManager tm = c.getSystemService(TelephonyManager.class);
        if (sim != null && sim.subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) tm = tm.createForSubscriptionId(sim.subId);
        try {
            tm.sendUssdRequest(code, new TelephonyManager.UssdResponseCallback() {
                @Override public void onReceiveUssdResponse(TelephonyManager t, String req, CharSequence response) {
                    reply.onReply(true, response == null ? "" : response.toString());
                }

                @Override public void onReceiveUssdResponseFailed(TelephonyManager t, String req, int failureCode) {
                    String why = failureCode == TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL
                            ? "USSD service unavailable on this SIM"
                            : "This code can't be answered silently on this phone (error " + failureCode + "). It probably opens a menu.";
                    reply.onReply(false, why);
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException e) {
            reply.onReply(false, "Couldn't send: " + e.getMessage());
        }
    }

    /** Opens the dial pad with the code filled in. Needs no permission; the user presses call. */
    static void dialPad(Context c, String code) {
        c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Dials a USSD code through the phone app, on the chosen SIM when possible. */
    @SuppressWarnings("MissingPermission")
    static void dial(Context c, String code, Sim sim) {
        Uri uri = Uri.fromParts("tel", code, null);
        TelecomManager tc = c.getSystemService(TelecomManager.class);
        Bundle extras = new Bundle();
        if (sim != null && sim.handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.handle);
        try {
            tc.placeCall(uri, extras);
            return;
        } catch (RuntimeException ignored) {
        }
        Intent i = new Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (sim != null) {
            i.putExtra("com.android.phone.extra.slot", sim.slot);
            i.putExtra("simSlot", sim.slot);
            if (sim.handle != null) i.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.handle);
        }
        c.startActivity(i);
    }
}
