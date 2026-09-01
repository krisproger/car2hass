package com.car2hass;

import android.content.Context;

/**
 * Test-only no-op stub for the production LogBuffer.
 */
public class LogBuffer {
    public static synchronized void init(Context context) {}
    public static synchronized void i(String tag, String msg) {}
    public static synchronized void w(String tag, String msg) {}
    public static synchronized void e(String tag, String msg) {}
    public static synchronized void d(String tag, String msg) {}
}
