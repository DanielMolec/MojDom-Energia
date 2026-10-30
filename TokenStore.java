package pl.mojdom.energia;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class TokenStore {
    private static final String ALIAS = "mojdom_ha_token";
    private final Context context;
    TokenStore(Context context) { this.context = context; }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS, null);
    }
    void save(String token) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        String enc = Base64.encodeToString(c.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
        context.getSharedPreferences("secret",0).edit().putString("value",enc)
            .putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).commit();
    }
    String read() throws Exception {
        android.content.SharedPreferences p = context.getSharedPreferences("secret",0);
        if (!p.contains("value")) return "";
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(p.getString("value",""),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    void clear() { context.getSharedPreferences("secret",0).edit().clear().commit(); }
}
