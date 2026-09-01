package com.car2hass;

/**
 * Server API shared constants (site endpoints and the shared intake token).
 * The token is embedded in the APK — it guards against casual/third-party use,
 * not against a determined extractor.
 */
public final class AppApi {

    public static final String BASE = "https://mytechnic.ru/cartelemetry/api";
    public static final String TOKEN = "car2hass_944d5a128a870a2c8120f2a728ce1767";
    public static final String LOG_INTAKE = BASE + "/logs/index.php";
    public static final String PROBE_REPORT = BASE + "/probe-report/index.php";

    private AppApi() {}
}
