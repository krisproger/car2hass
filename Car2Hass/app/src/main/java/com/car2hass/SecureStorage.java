package com.car2hass;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores sensitive values (HA Long-Lived Access Token) encrypted with a key
 * backed by the Android Keystore. Falls back to plain SharedPreferences only
 * when the keystore is unavailable, and migrates legacy plaintext values on
 * first access.
 */
public class SecureStorage {
    private static final String PREFS_NAME = "secure_hass_config";
    private static final String KEY_TOKEN = "hass_token_encrypted";
    private static final String KEY_IV = "hass_token_iv";
    private static final String LEGACY_TOKEN_KEY = "hass_token";
    private static final String PLAIN_FALLBACK_KEY = "hass_token_plain";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "car2hass_token_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SharedPreferences securePrefs;
    private final SharedPreferences legacyPrefs;
    private volatile boolean keyAvailable = false;

    public SecureStorage(Context ctx) {
        this.securePrefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.legacyPrefs = ctx.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE);
        this.keyAvailable = ensureKey();
    }

    private boolean ensureKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build();
                kg.init(spec);
                kg.generateKey();
            }
            return true;
        } catch (Exception e) {
            LogBuffer.e("SecureStorage", "Keystore key setup failed: " + e.getMessage());
            return false;
        }
    }

    /** Encrypt and store the HA access token. */
    public void saveToken(String token) {
        if (token == null) token = "";
        if (!keyAvailable) {
            // Fallback if keystore is unavailable on this device
            securePrefs.edit()
                    .putString(PLAIN_FALLBACK_KEY, token)
                    .remove(KEY_TOKEN)
                    .remove(KEY_IV)
                    .apply();
            return;
        }
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != GCM_IV_LENGTH) {
                throw new IllegalStateException("Unexpected IV length: " + (iv == null ? 0 : iv.length));
            }
            byte[] encrypted = cipher.doFinal(token.getBytes("UTF-8"));
            securePrefs.edit()
                    .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .remove(PLAIN_FALLBACK_KEY)
                    .apply();
        } catch (Exception e) {
            LogBuffer.e("SecureStorage", "Token encryption failed: " + e.getMessage());
            securePrefs.edit()
                    .putString(PLAIN_FALLBACK_KEY, token)
                    .remove(KEY_TOKEN)
                    .remove(KEY_IV)
                    .apply();
        }
    }

    /** Retrieve and decrypt the HA access token. */
    public String getToken() {
        // Migrate legacy plaintext token if present
        String legacy = legacyPrefs.getString(LEGACY_TOKEN_KEY, null);
        if (legacy != null) {
            saveToken(legacy);
            legacyPrefs.edit().remove(LEGACY_TOKEN_KEY).apply();
            return legacy;
        }

        // Plaintext fallback
        String plain = securePrefs.getString(PLAIN_FALLBACK_KEY, null);
        if (plain != null) {
            return plain;
        }

        String ivB64 = securePrefs.getString(KEY_IV, null);
        String encB64 = securePrefs.getString(KEY_TOKEN, null);
        if (ivB64 == null || encB64 == null) {
            return "";
        }

        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(ivB64, Base64.NO_WRAP)));
            byte[] decrypted = cipher.doFinal(Base64.decode(encB64, Base64.NO_WRAP));
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            LogBuffer.e("SecureStorage", "Token decryption failed: " + e.getMessage());
            return "";
        }
    }

    /** Clear stored token. */
    public void clearToken() {
        securePrefs.edit()
                .remove(KEY_IV)
                .remove(KEY_TOKEN)
                .remove(PLAIN_FALLBACK_KEY)
                .apply();
    }
}
