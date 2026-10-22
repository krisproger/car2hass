package com.car2hass.vehicle;

import android.content.Context;
import android.net.Uri;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Best-effort, non-destructive probes for the remaining Voyah access channels
 * described in the firmware research but not yet used:
 *   1) ContentProvider qinggan.capabilities
 *   2) ICanBusService.isSupportFunction
 *   3) ICanBusService addCallback (subscription availability)
 *   4) ICarSignal/AirCondition extra paths
 *   5) AOSP CarPropertyManager (android.car)
 * Never calls any set* method.
 */
public final class VoyahExtraProbe {
    private static final String[] CAP_URIS = {
            "content://qinggan.capabilities",
            "content://com.qinggan.providers.capabilities",
            "content://com.qinggan.providers.capabilities/capabilities",
    };
    private static final String[] CALLBACK_IFACES = {
            "com.qinggan.canbus.ICanBusCallback",
            "com.qinggan.canbus.ICanBusServiceCallback",
            "com.qinggan.canbus.ICanBusService$ICanBusCallback",
    };

    private VoyahExtraProbe() {}

    public static JSONObject probe(Context ctx) {
        JSONObject o = new JSONObject();
        put(o, "capabilities", capabilities(ctx));
        put(o, "is_support_function", isSupport());
        put(o, "add_callback", addCallback());
        put(o, "aircondition", airCondition());
        put(o, "car_property", carProperty(ctx));
        return o;
    }

    private static void put(JSONObject o, String k, JSONObject v) {
        try { o.put(k, v); } catch (Exception ignored) {}
    }

    /** 1) Try the exported capabilities ContentProvider. */
    private static JSONObject capabilities(Context ctx) {
        JSONObject r = new JSONObject();
        for (String u : CAP_URIS) {
            try {
                Cursor c = ctx.getContentResolver().query(Uri.parse(u), null, null, null, null);
                if (c == null) continue;
                int rows = c.getCount();
                JSONArray sample = new JSONArray();
                String[] cols = c.getColumnNames();
                int n = 0;
                while (c.moveToNext() && n < 10) {
                    JSONObject row = new JSONObject();
                    for (int i = 0; i < cols.length && i < 8; i++) {
                        row.put(cols[i], c.getString(i));
                    }
                    sample.put(row);
                    n++;
                }
                c.close();
                r.put("uri", u);
                r.put("ok", true);
                r.put("rows", rows);
                r.put("columns", new JSONArray(cols));
                r.put("sample", sample);
                return r;
            } catch (Throwable e) {
                try {
                    r.put("uri", u);
                    r.put("ok", false);
                    r.put("error", String.valueOf(e.getMessage()));
                } catch (Exception ignored) {}
            }
        }
        return r;
    }

    /** 2) Probe isSupportFunction with a few integer arguments. */
    private static JSONObject isSupport() {
        JSONObject r = new JSONObject();
        try {
            Class<?> iface = load("com.qinggan.canbus.ICanBusService");
            if (iface == null) { r.put("ok", false); r.put("error", "iface absent"); return r; }
            Method m = null;
            for (Method mm : iface.getMethods()) {
                if ("isSupportFunction".equals(mm.getName())) { m = mm; break; }
            }
            if (m == null) { r.put("ok", false); r.put("error", "method absent"); return r; }
            r.put("ok", true);
            r.put("params", m.getParameterTypes().length);
            Object asInterface = asInterface(iface);
            if (asInterface == null) { r.put("error", "no binder"); return r; }
            JSONArray results = new JSONArray();
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 1 && (pt[0] == int.class || pt[0] == Integer.class)) {
                for (int v : new int[]{0, 1, 2, 3, 4}) {
                    try { results.put(v + "=" + m.invoke(asInterface, v)); }
                    catch (Throwable e) {
                        try { results.put(v + "=" + e.getClass().getSimpleName()); } catch (Exception ignored) {}
                    }
                }
            }
            r.put("results", results);
        } catch (Throwable e) {
            try { r.put("ok", false); r.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        return r;
    }

    /** 3) Probe whether a callback interface can be registered (no value use yet). */
    private static JSONObject addCallback() {
        JSONObject r = new JSONObject();
        try {
            Class<?> iface = load("com.qinggan.canbus.ICanBusService");
            if (iface == null) { r.put("ok", false); r.put("error", "iface absent"); return r; }
            Object asInterface = asInterface(iface);
            if (asInterface == null) { r.put("ok", false); r.put("error", "no binder"); return r; }
            Class<?> cbIface = null;
            for (String name : CALLBACK_IFACES) {
                cbIface = load(name);
                if (cbIface != null && cbIface.isInterface()) break;
                cbIface = null;
            }
            if (cbIface == null) { r.put("ok", false); r.put("error", "callback iface absent"); return r; }
            final Class<?> ci = cbIface;
            Object proxy = Proxy.newProxyInstance(ci.getClassLoader(), new Class<?>[]{ci},
                    (p, method, args) -> {
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        return null;
                    });
            Method add = null;
            for (Method mm : iface.getMethods()) {
                if ("addCallback".equals(mm.getName())) { add = mm; break; }
            }
            if (add == null) { r.put("ok", false); r.put("error", "addCallback absent"); return r; }
            try {
                Object res = add.invoke(asInterface, proxy);
                r.put("ok", true);
                r.put("result", String.valueOf(res));
                r.put("callback_iface", ci.getName());
            } catch (Throwable e) {
                try {
                    r.put("ok", false);
                    r.put("error", String.valueOf(e.getMessage()));
                } catch (Exception ignored) {}
            }
        } catch (Throwable e) {
            try { r.put("ok", false); r.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        return r;
    }

    /** 4) AirCondition alternative access paths. */
    private static JSONObject airCondition() {
        JSONObject r = new JSONObject();
        try {
            Class<?> ac = load("com.qinggan.canbus.AirCondition");
            if (ac == null) { r.put("class", false); return r; }
            r.put("class", true);
            r.put("methods", ac.getMethods().length);
            JSONArray statics = new JSONArray();
            for (Method m : ac.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) statics.put(m.getName());
            }
            r.put("static_methods", statics);
        } catch (Throwable e) {
            try { r.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        return r;
    }

    /** 5) AOSP CarPropertyManager via reflection (read-only). */
    private static JSONObject carProperty(Context ctx) {
        JSONObject r = new JSONObject();
        try {
            Class<?> carCls = Class.forName("android.car.Car");
            Method create = carCls.getMethod("createCar", Context.class);
            Object car = create.invoke(null, ctx);
            r.put("car_created", car != null);
            if (car != null) {
                Method getPm = carCls.getMethod("getCarPropertyManager");
                Object pm = getPm.invoke(car);
                r.put("property_manager", pm != null);
                r.put("perms", new JSONArray(new String[]{
                        "android.car.permission.CAR_SPEED",
                        "android.car.permission.CAR_ENERGY"}));
            }
        } catch (Throwable e) {
            try { r.put("ok", false); r.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {}
        }
        return r;
    }

    private static Class<?> load(String name) {
        return VoyahChannel.loadDeviceClassPublic(name);
    }

    private static Object asInterface(Class<?> iface) {
        try {
            Class<?> stub = Class.forName(iface.getName() + "$Stub", false, iface.getClassLoader());
            Object binder = VoyahChannel.findBinderPublic();
            if (binder == null) return null;
            Method m = stub.getMethod("asInterface", android.os.IBinder.class);
            return m.invoke(null, binder);
        } catch (Throwable e) {
            return null;
        }
    }
}
