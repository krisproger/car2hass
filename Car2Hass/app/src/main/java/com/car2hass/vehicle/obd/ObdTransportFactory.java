package com.car2hass.vehicle.obd;

import android.content.Context;

import com.car2hass.AppConfig;

/** Builds the active OBD transport: Bluetooth SPP (default) or TCP (WiFi). */
public final class ObdTransportFactory {

    private ObdTransportFactory() {}

    public static ObdTransport create(Context ctx) {
        if ("bt".equals(AppConfig.getObdMode(ctx))) {
            String addr = AppConfig.getObdBtAddress(ctx);
            if (addr != null && !addr.isEmpty()) {
                return new BtSppTransport(addr);
            }
        }
        return new TcpTransport(AppConfig.getObdHost(ctx), AppConfig.getObdPort(ctx));
    }
}