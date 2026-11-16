package com.car2hass;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EndpointStore {
    private static final String KEY_ENDPOINTS = "hass_endpoints_v1";

    private EndpointStore() {}

    public static synchronized List<HaEndpointConfig> list(Context ctx) {
        SharedPreferences prefs = prefs(ctx);
        String raw = prefs.getString(KEY_ENDPOINTS, null);
        if (raw != null) {
            try {
                JSONObject root = new JSONObject(raw);
                JSONArray arr = root.optJSONArray("endpoints");
                if (arr != null) {
                    List<HaEndpointConfig> out = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) out.add(HaEndpointConfig.fromJson(o));
                    }
                    return out;
                }
            } catch (Exception e) {
                LogBuffer.w("EndpointStore", "bad endpoint json: " + e.getMessage());
            }
        }

        HaEndpointConfig legacy = EndpointMigration.fromLegacy(
                AppConfig.getHassHost(ctx), AppConfig.getHassPort(ctx),
                AppConfig.isHassHttps(ctx), AppConfig.isHassEnabled(ctx),
                AppConfig.isCarControlEnabled(ctx), AppConfig.getCarName(ctx));
        if (legacy == null) return new ArrayList<>();
        List<HaEndpointConfig> out = new ArrayList<>();
        out.add(legacy);
        String token = AppConfig.getHassToken(ctx);
        if (!token.isEmpty()) {
            new SecureStorage(ctx).saveEndpointToken("primary", token);
        }
        save(ctx, out);
        return out;
    }

    public static synchronized HaEndpointConfig get(Context ctx, String id) {
        for (HaEndpointConfig endpoint : list(ctx)) {
            if (endpoint.id.equals(id)) return endpoint;
        }
        return null;
    }

    public static synchronized void save(Context ctx, List<HaEndpointConfig> endpoints) {
        try {
            JSONArray arr = new JSONArray();
            HaEndpointConfig primary = null;
            for (HaEndpointConfig endpoint : endpoints) {
                arr.put(endpoint.toJson());
                if ("primary".equals(endpoint.id)) primary = endpoint;
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("endpoints", arr);
            prefs(ctx).edit().putString(KEY_ENDPOINTS, root.toString()).apply();
            mirrorLegacy(ctx, primary);
        } catch (Exception e) {
            LogBuffer.e("EndpointStore", "save failed: " + e.getMessage());
        }
    }

    public static synchronized void upsert(Context ctx, HaEndpointConfig endpoint) {
        List<HaEndpointConfig> endpoints = list(ctx);
        boolean replaced = false;
        for (int i = 0; i < endpoints.size(); i++) {
            if (endpoints.get(i).id.equals(endpoint.id)) {
                endpoints.set(i, endpoint);
                replaced = true;
                break;
            }
        }
        if (!replaced) endpoints.add(endpoint);
        save(ctx, endpoints);
    }

    public static synchronized void delete(Context ctx, String id) {
        List<HaEndpointConfig> endpoints = list(ctx);
        endpoints.removeIf(endpoint -> endpoint.id.equals(id));
        save(ctx, endpoints);
        new SecureStorage(ctx).clearEndpointToken(id);
        SnapshotQueue.clear(ctx, id);
    }

    public static synchronized String newId(Context ctx) {
        return list(ctx).isEmpty() ? "primary" : UUID.randomUUID().toString();
    }

    private static void mirrorLegacy(Context ctx, HaEndpointConfig primary) {
        if (primary == null) {
            AppConfig.clearLegacyPrimaryEndpoint(ctx);
            new SecureStorage(ctx).clearToken();
            return;
        }
        HaAddress address = primary.haAddress();
        AppConfig.mirrorLegacyPrimaryEndpoint(ctx, address.host, address.port,
                primary.carName, primary.enabled, address.https);
        String token = new SecureStorage(ctx).getEndpointToken("primary");
        if (!token.isEmpty()) {
            new SecureStorage(ctx).saveToken(token);
        } else {
            new SecureStorage(ctx).clearToken();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE);
    }
}
