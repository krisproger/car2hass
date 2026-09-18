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
 * Stores sensitive values (HA Long-Lived Access Token and cloud account tokens)
 * encrypted with a key backed by the Android Keystore. Falls back to plain
 * SharedPreferences only when the keystore is unavailable, and migrates legacy
 * plaintext values on first access.
 */
public class SecureStorage {
    private static final String PREFS_NAME = "secure_hass_config";
    private static final String KEY_TOKEN = "hass_token_encrypted";
    private static final String KEY_IV = "hass_token_iv";
    private static final String LEGACY_TOKEN_KEY = "hass_token";
    private static final String PLAIN_FALLBACK_KEY = "hass_token_plain";

    private static final String KEY_CLOUD_TOKEN = "cloud_token_encrypted";
    private static final String KEY_CLOUD_IV = "cloud_token_iv";
    private static final String CLOUD_PLAIN_FALLBACK_KEY = "cloud_token_plain";
    private static final String KEY_CLOUD_REFRESH = "cloud_refresh_encrypted";
    private static final String KEY_CLOUD_REFRESH_IV = "cloud_refresh_iv";
    private static final String CLOUD_REFRESH_PLAIN_FALLBACK_KEY = "cloud_refresh_plain";

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
        saveSecret(KEY_TOKEN, KEY_IV, PLAIN_FALLBACK_KEY, token);
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
        return getSecret(KEY_TOKEN, KEY_IV, PLAIN_FALLBACK_KEY);
    }

    /** Clear stored HA token. */
    public void clearToken() {
        clearSecret(KEY_TOKEN, KEY_IV, PLAIN_FALLBACK_KEY);
    }

    /** Encrypt and store the cloud (mytechnic.ru) access token. */
    public void saveCloudToken(String token) {
        saveSecret(KEY_CLOUD_TOKEN, KEY_CLOUD_IV, CLOUD_PLAIN_FALLBACK_KEY, token);
    }

    /** Retrieve and decrypt the cloud access token. */
    public String getCloudToken() {
        return getSecret(KEY_CLOUD_TOKEN, KEY_CLOUD_IV, CLOUD_PLAIN_FALLBACK_KEY);
    }

    /** Clear the stored cloud access token. */
    public void clearCloudToken() {
        clearSecret(KEY_CLOUD_TOKEN, KEY_CLOUD_IV, CLOUD_PLAIN_FALLBACK_KEY);
    }

    /** Encrypt and store the cloud refresh token. */
    public void saveCloudRefreshToken(String token) {
        saveSecret(KEY_CLOUD_REFRESH, KEY_CLOUD_REFRESH_IV, CLOUD_REFRESH_PLAIN_FALLBACK_KEY, token);
    }

    /** Retrieve and decrypt the cloud refresh token. */
    public String getCloudRefreshToken() {
        return getSecret(KEY_CLOUD_REFRESH, KEY_CLOUD_REFRESH_IV, CLOUD_REFRESH_PLAIN_FALLBACK_KEY);
    }

    /** Clear the stored cloud refresh token. */
    public void clearCloudRefreshToken() {
        clearSecret(KEY_CLOUD_REFRESH, KEY_CLOUD_REFRESH_IV, CLOUD_REFRESH_PLAIN_FALLBACK_KEY);
    }

    /** Clear both cloud tokens (access and refresh). */
    public void clearCloudTokens() {
        clearCloudToken();
        clearCloudRefreshToken();
    }

    private void saveSecret(String tokenKey, String ivKey, String fallbackKey, String value) {
        if (value == null) value = "";
        if (!keyAvailable) {
            // Fallback if keystore is unavailable on this device
            securePrefs.edit()
                    .putString(fallbackKey, value)
                    .remove(tokenKey)
                    .remove(ivKey)
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
            byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));
            securePrefs.edit()
                    .putString(ivKey, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(tokenKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .remove(fallbackKey)
                    .apply();
        } catch (Exception e) {
            LogBuffer.e("SecureStorage", "Secret encryption failed: " + e.getMessage());
            securePrefs.edit()
                    .putString(fallbackKey, value)
                    .remove(tokenKey)
                    .remove(ivKey)
                    .apply();
        }
    }

    private String getSecret(String tokenKey, String ivKey, String fallbackKey) {
        String plain = securePrefs.getString(fallbackKey, null);
        if (plain != null) {
            return plain;
        }

        String ivB64 = securePrefs.getString(ivKey, null);
        String encB64 = securePrefs.getString(tokenKey, null);
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
            LogBuffer.e("SecureStorage", "Secret decryption failed: " + e.getMessage());
            return "";
        }
    }

    private void clearSecret(String tokenKey, String ivKey, String fallbackKey) {
        securePrefs.edit()
                .remove(ivKey)
                .remove(tokenKey)
                .remove(fallbackKey)
                .apply();
    }
}
