package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.CANDataItem;
import com.car2hass.CANDataReader;
import com.car2hass.LogBuffer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * DiPlus push-events channel. The local DiPlus HTTP API is the availability
 * gate; actual push subscriptions are not supported by current DiPlus builds,
 * so the channel reports honestly as unavailable.
 */
public class DiPlusPushChannel implements DataChannel {

    private static final String DIPLUS_BASE = com.car2hass.DiPlusCommandSender.DIPLUS_BASE;

    @Override
    public String id() { return "diplus_push"; }

    @Override
    public String displayName() { return "DiPlus Push-события"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        if (!CANDataReader.isDiplusAlive()) {
            return ChannelResult.dead("DiPlus не запущен (нет приложения на 127.0.0.1:8988)");
        }
        // Service is alive, but no documented subscription API yet.
        return ChannelResult.dead("push-события не поддержаны текущей версией DiPlus");
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return new ArrayList<>();
    }

    /** Kept for future use: minimal HTTP reachability check. */
    @SuppressWarnings("unused")
    private static boolean diplusReachable() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(DIPLUS_BASE + "/").openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (is != null) is.read(new byte[256]);
            return code > 0 && code < 500;
        } catch (Exception e) {
            LogBuffer.d("DiPlusPushChannel", "reachable: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
