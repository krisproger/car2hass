package com.car2hass;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * Helper for in-app language switching.
 *
 * <p>The app supports English (default) and Russian. If the system locale cannot
 * be matched, English is used. The user can override the locale manually in
 * settings; the choice is persisted and applied to every Activity via
 * {@link #attach(Context)}.</p>
 */
public class LocaleHelper {

    private static final String PREFS_NAME = "locale_prefs";
    private static final String KEY_LANGUAGE = "app_language";

    public static final String LANG_EN = "en";
    public static final String LANG_RU = "ru";

    /**
     * Apply the persisted (or default) locale to the given context and return a
     * wrapped context that uses the selected locale for resource lookups.
     */
    public static Context attach(Context context) {
        String lang = getLanguage(context);
        return setLocale(context, lang);
    }

    /**
     * Persist a new language and return a context configured with it.
     *
     * @param context base context
     * @param language {@link #LANG_EN} or {@link #LANG_RU}
     * @return context wrapped with the new locale
     */
    public static Context setLocale(Context context, String language) {
        persist(context, language);
        return updateResources(context, language);
    }

    /**
     * Return the currently selected language code.
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_LANGUAGE, "");
        if (!saved.isEmpty()) {
            return saved;
        }
        // No manual override: use the system locale if it is Russian, otherwise default to English.
        Locale systemLocale = getSystemLocale(context.getResources().getConfiguration());
        if (LANG_RU.equals(systemLocale.getLanguage())) {
            return LANG_RU;
        }
        return LANG_EN;
    }

    private static void persist(Context context, String language) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, language)
                .apply();
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            config.setLayoutDirection(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            config.setLayoutDirection(locale);
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }

    private static Locale getSystemLocale(Configuration config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return config.getLocales().get(0);
        } else {
            return config.locale;
        }
    }
}
