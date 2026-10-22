package com.car2hass.vehicle;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort, non-destructive probes for the Voyah access channels from the
 * firmware research: capabilities provider, isSupportFunction, addCallback,
 * AirCondition and AOSP CarPropertyManager. Never calls any set* method.
 */
public final class VoyahExtraProbe {
    private VoyahExtraProbe() {}

    public static JSONObject probe(Context ctx) {
        JSONObject o = new JSONObject();
        set(o, "capabilities", capabilities(ctx));
        set(o, "is_support_function", isSupport());
        set(o, "add_callback", addCallback());
        set(o, "aircondition", airCondition());
        set(o, "car_property", carProperty(ctx));
        return o;
    }

    private static void set(JSONObject o, String k, Object v) {
        try { o.put(k, v); } catch (Exception ignored) {}
    }

    private static JSONObject capabilities(Context ctx) {
        JSONObject r = new JSONObject();
        List<String> uris = new ArrayList<>();
        uris.add("content://qinggan.capabilities");
        try {
            for (ProviderInfo pi : ctx.getPackageManager().queryContentProviders(null, 0, 0)) {
                String n = (pi.packageName + " " + pi.name).toLowerCase();
                if ((n.contains("qinggan") || n.contains("capabilit"))
                        && pi.authority != null && !pi.authority.isEmpty()) {
                    String u = "content://" + pi.authority;
                    if (!uris.contains(u)) uris.add(u);
                }
            }
        } catch (Throwable ignored) {}
        JSONArray tried = new JSONArray();
        for (String u : uris) {
            JSONObject t = new JSONObject();
            set(t, "uri", u);
            try {
                Cursor c = ctx.getContentResolver().query(Uri.parse(u), null, null, null, null);
                if (c == null) {
                    set(t, "ok", false);
                    set(t, "error", "null cursor");
                } else {
                    int rows = c.getCount();
                    String[] cols = c.getColumnNames();
                    JSONArray sample = new JSONArray();
                    int n = 0;
                    while (c.moveToNext() && n < 10) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < cols.length && i < 8; i++) set(row, cols[i], c.getString(i));
                        sample.put(row);
                        n++;
                    }
                    c.close();
                    set(t, "ok", true);
                    set(t, "rows", rows);
                    set(t, "columns", new JSONArray(cols));
                    set(t, "sample", sample);
                    if (rows > 0) {
                        set(r, "ok", true);
                        set(r, "uri", u);
                        set(r, "rows", rows);
                        set(r, "columns", new JSONArray(cols));
                        set(r, "sample", sample);
                        set(r, "tried", tried);
                        return r;
                    }
                }
            } catch (Throwable e) {
                set(t, "ok", false);
                set(t, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            tried.put(t);
        }
        set(r, "ok", false);
        set(r, "tried", tried);
        return r;
    }

    private static JSONObject isSupport() {
        JSONObject r = new JSONObject();
        try {
            Class<?> iface = load("com.qinggan.canbus.ICanBusService");
            if (iface == null) { set(r, "ok", false); set(r, "error", "iface absent"); return r; }
            Method m = null;
            for (Method mm : iface.getMethods()) {
                if ("isSupportFunction".equals(mm.getName())) { m = mm; break; }
            }
            if (m == null) { set(r, "ok", false); set(r, "error", "method absent"); return r; }
            Class<?>[] pt = m.getParameterTypes();
            set(r, "ok", true);
            set(r, "params", pt.length);
            if (pt.length > 0) set(r, "param_type", pt[0].getName());
            Object asInterface = asInterface(iface);
            if (asInterface == null) { set(r, "error", "no binder"); return r; }
            JSONArray results = new JSONArray();
            if (pt.length == 1) {
                Class<?> t = pt[0];
                if (t == int.class || t == Integer.class) {
                    for (int v : new int[]{0, 1, 2, 3, 4, 5, 10, 100}) {
                        invokeResult(m, asInterface, results, "int " + v, v);
                    }
                } else if (t == String.class || t == CharSequence.class) {
                    for (String v : new String[]{"speed", "range", "gear", "soc"}) {
                        invokeResult(m, asInterface, results, "str " + v, v);
                    }
                } else {
                    Object constant = firstStateConstant(t);
                    if (constant != null) invokeResult(m, asInterface, results, "state " + constant, constant);
                }
            }
            set(r, "results", results);
        } catch (Throwable e) {
            set(r, "ok", false);
            set(r, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return r;
    }

    private static void invokeResult(Method m, Object target, JSONArray out, String label, Object arg) {
        try {
            out.put(label + "=" + m.invoke(target, arg));
        } catch (Throwable e) {
            try { out.put(label + "=" + e.getClass().getSimpleName()); } catch (Exception ignored) {}
        }
    }

    private static Object firstStateConstant(Class<?> type) {
        try {
            for (Field f : type.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                    return f.get(null);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static JSONObject addCallback() {
        JSONObject r = new JSONObject();
        try {
            Class<?> iface = load("com.qinggan.canbus.ICanBusService");
            if (iface == null) { set(r, "ok", false); set(r, "error", "iface absent"); return r; }
            Object asInterface = asInterface(iface);
            if (asInterface == null) { set(r, "ok", false); set(r, "error", "no binder"); return r; }
            Method add = null;
            for (Method mm : iface.getMethods()) {
                if ("addCallback".equals(mm.getName())
                        && mm.getParameterTypes().length == 1
                        && mm.getParameterTypes()[0].isInterface()) {
                    add = mm;
                    break;
                }
            }
            if (add == null) { set(r, "ok", false); set(r, "error", "addCallback(iface) absent"); return r; }
            final Class<?> ci = add.getParameterTypes()[0];
            set(r, "callback_iface", ci.getName());
            Object proxy = Proxy.newProxyInstance(ci.getClassLoader(), new Class<?>[]{ci},
                    (p, method, args) -> {
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        return null;
                    });
            try {
                Object res = add.invoke(asInterface, proxy);
                set(r, "ok", true);
                set(r, "result", String.valueOf(res));
            } catch (Throwable e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                set(r, "ok", false);
                set(r, "error", c.getClass().getSimpleName() + ": " + c.getMessage());
            }
        } catch (Throwable e) {
            set(r, "ok", false);
            set(r, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return r;
    }

    private static JSONObject airCondition() {
        JSONObject r = new JSONObject();
        try {
            Class<?> ac = load("com.qinggan.canbus.AirCondition");
            set(r, "ac_class", ac != null);
            if (ac != null) set(r, "ac_methods", ac.getMethods().length);
            Class<?> mgr = load("com.qinggan.canbus.CanBusManager");
            set(r, "manager_class", mgr != null);
            if (mgr != null) {
                JSONArray statics = new JSONArray();
                for (Method m : mgr.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers())) statics.put(m.getName());
                }
                set(r, "manager_static_methods", statics);
                for (Method m : mgr.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers())
                            && m.getParameterTypes().length == 0
                            && ac != null && m.getReturnType().isAssignableFrom(ac)) {
                        try { set(r, "manager_instance", String.valueOf(m.invoke(null))); }
                        catch (Throwable e) { set(r, "manager_instance_error", e.getClass().getSimpleName()); }
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            set(r, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return r;
    }

    private static JSONObject carProperty(Context ctx) {
        JSONObject r = new JSONObject();
        try {
            Class<?> carCls = Class.forName("android.car.Car");
            Object car = carCls.getMethod("createCar", Context.class).invoke(null, ctx);
            set(r, "car_created", car != null);
            if (car != null) {
                Object pm = null;
                String via = null;
                try {
                    pm = carCls.getMethod("getCarManager", String.class).invoke(car, "property");
                    via = "getCarManager(property)";
                } catch (Throwable e) {
                    try {
                        pm = carCls.getMethod("getCarPropertyManager").invoke(car);
                        via = "getCarPropertyManager";
                    } catch (Throwable ignored) {}
                }
                set(r, "via", via);
                set(r, "property_manager", pm != null);
                if (pm != null) {
                    JSONArray methods = new JSONArray();
                    for (Method m : pm.getClass().getMethods()) {
                        String n = m.getName();
                        if (n.startsWith("get") && m.getParameterTypes().length >= 1
                                && m.getParameterTypes()[0] == int.class) {
                            methods.put(n);
                        }
                    }
                    set(r, "get_methods", methods);
                }
            }
        } catch (Throwable e) {
            set(r, "ok", false);
            set(r, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
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
