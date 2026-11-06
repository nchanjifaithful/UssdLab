package app.ussdlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the PIN encrypted with an Android Keystore key that only unlocks for 30 seconds
 * after fingerprint / screen-lock authentication. The key never leaves the phone's secure hardware.
 */
final class PinVault {

    private static final String ALIAS = "ussdlab_pin_key";
    private static final String PREFS = "vault";
    private static final String KEY = "pin";
    private static final int AUTH_SECONDS = 30;

    static final class LockedException extends Exception {
        LockedException(String msg) { super(msg); }
    }

    private PinVault() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean has(Context c) { return prefs(c).contains(KEY); }

    /** Creates the key. Fails if the phone has no secure screen lock. */
    static void ensureKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return;
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true);
        if (Build.VERSION.SDK_INT >= 30) {
            b.setUserAuthenticationParameters(AUTH_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
        } else {
            setLegacyValidity(b);
        }
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(b.build());
        g.generateKey();
    }

    @SuppressWarnings("deprecation")
    private static void setLegacyValidity(KeyGenParameterSpec.Builder b) {
        b.setUserAuthenticationValidityDurationSeconds(AUTH_SECONDS);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    /** Call within 30 s of a successful authentication. */
    static void save(Context c, char[] pin) throws Exception {
        ensureKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(pin));
        byte[] plain = Arrays.copyOfRange(bb.array(), bb.position(), bb.limit());
        Arrays.fill(bb.array(), (byte) 0);
        byte[] ct = cipher.doFinal(plain);
        Arrays.fill(plain, (byte) 0);
        String blob = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        prefs(c).edit().putString(KEY, blob).apply();
    }

    /** Call within 30 s of a successful authentication. Caller must wipe the result. */
    static char[] load(Context c) throws Exception {
        String blob = prefs(c).getString(KEY, null);
        if (blob == null) throw new LockedException("No PIN saved");
        String[] parts = blob.split(":");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            CharBuffer cb = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));
            char[] out = Arrays.copyOfRange(cb.array(), cb.position(), cb.limit());
            Arrays.fill(cb.array(), '\0');
            Arrays.fill(plain, (byte) 0);
            return out;
        } catch (KeyPermanentlyInvalidatedException e) {
            clear(c);
            throw new LockedException("Your screen lock or fingerprints changed, so the saved PIN was erased. Save it again.");
        }
    }

    static void clear(Context c) {
        prefs(c).edit().remove(KEY).apply();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(ALIAS);
        } catch (Exception ignored) {
        }
    }
}
