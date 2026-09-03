package com.car2hass.vehicle;

import android.content.Context;
import com.car2hass.AppInfo;
import com.car2hass.LogBuffer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Запускает зондирование каналов и формирует отчёт исследования. */
public final class VehicleResearch {

    private VehicleResearch() {}

    /** Колбэк прогресса зондирования: вызывается после каждого канала. */
    public interface ProgressListener {
        void onChannelDone(int done, int total, String displayName);
    }

    public static List<ChannelResult> run(Context ctx, List<DataChannel> channels, VehicleProfile profile) {
        return run(ctx, channels, profile, null);
    }

    public static List<ChannelResult> run(Context ctx, List<DataChannel> channels,
                                          VehicleProfile profile, ProgressListener listener) {
        List<ChannelResult> results = new ArrayList<>();
        int total = channels.size();
        for (int i = 0; i < total; i++) {
            DataChannel ch = channels.get(i);
            ChannelResult r = probeWithTimeout(ch, ctx);
            results.add(r);
            LogBuffer.i("VehicleResearch", "Канал '" + ch.displayName() + "': " + r.summary());
            if (listener != null) listener.onChannelDone(i + 1, total, ch.displayName());
        }
        return results;
    }

    private static ChannelResult probeWithTimeout(DataChannel ch, Context ctx) {
        final ChannelResult[] holder = new ChannelResult[1];
        Thread t = new Thread(() -> holder[0] = ch.probe(ctx), "probe-" + ch.id());
        t.setDaemon(true);
        t.start();
        try {
            t.join(15000); // таймаут на канал
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (holder[0] == null) {
            return ChannelResult.dead("таймаут зондирования канала");
        }
        return holder[0];
    }

    /** Краткая сводка для диалога. */
    public static String summary(List<ChannelResult> results) {
        StringBuilder sb = new StringBuilder();
        for (ChannelResult r : results) {
            sb.append(r.summary()).append('\n');
        }
        return sb.toString();
    }

    /** Полный отчёт с профилем авто в начале; возвращает имя файла. */
    public static String writeReport(Context ctx, VehicleProfile profile, List<ChannelResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Исследование каналов Car2Hass v")
          .append(AppInfo.getVersionString(ctx)).append(" ===\n");
        sb.append("Производитель: ").append(profile.getProducer().name()).append('\n');
        sb.append("Марка/модель: ").append(profile.getMake()).append('\n');
        sb.append("Год выпуска: ").append(profile.getYear()).append('\n');
        sb.append("Время: ").append(new Date().toString()).append("\n\n");
        for (ChannelResult r : results) {
            sb.append(r.summary()).append('\n');
            for (String e : r.getErrors()) {
                sb.append("  ошибка: ").append(e).append('\n');
            }
        }
        String name = "research_" + safeName(profile.getMake()) + "_"
                + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".txt";
        File dir = ctx.getExternalFilesDir(null);
        if (dir != null) {
            File f = new File(dir, name);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(sb.toString().getBytes("UTF-8"));
                LogBuffer.i("VehicleResearch", "Отчёт исследования: " + f.getAbsolutePath());
                return f.getAbsolutePath();
            } catch (Exception e) {
                LogBuffer.e("VehicleResearch", "writeReport: " + e.getMessage());
            }
        }
        return null;
    }

    private static String safeName(String s) {
        if (s == null) return "auto";
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** Per-sensor probe strategy; production uses {@code SignalProber}, tests can stub it. */
    public interface SensorProbe {
        ProbeResult probe(android.content.Context ctx, RegistryStore reg, String sensorKey, String channel)
                throws org.json.JSONException;
    }

    /** Persists the research outcome; production delegates to {@code AppConfig}. */
    public interface Persister {
        void save(android.content.Context ctx, String selectedProfile,
                  List<String> activeChannels, String reportPath);
    }

    /** Result of a full registry-based research run. */
    public static final class ResearchOutcome {
        public final String selectedProfile;
        public final List<String> activeChannels;
        public final JSONObject report;
        public final String reportPath;

        public ResearchOutcome(String selectedProfile, List<String> activeChannels,
                               JSONObject report, String reportPath) {
            this.selectedProfile = selectedProfile;
            this.activeChannels = activeChannels;
            this.report = report;
            this.reportPath = reportPath;
        }
    }

    private static final List<String> CHANNEL_PRIORITY_FALLBACK = Arrays.asList(
            "diplus", "adb", "dumpsys", "system", "obd", "diplus_push", "byd_cloud");

    /** Channel priority from the registry, legacy constant as fallback. */
    private static String safeKeyChannel(RegistryStore reg, String profileId) {
        try {
            return reg.profileKeyChannel(profileId);
        } catch (org.json.JSONException e) {
            LogBuffer.e("VehicleResearch", "key_channel: " + e.getMessage());
            return null;
        }
    }

    /** Calls a static no-arg(Context) AppConfig getter via reflection (harness-safe). */
    private static String appConfigGet(Context ctx, String method) {
        try {
            java.lang.reflect.Method m = Class.forName("com.car2hass.AppConfig")
                    .getMethod(method, Context.class);
            Object v = m.invoke(null, ctx);
            return v == null ? "" : v.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> channelPriority(RegistryStore reg) {
        try {
            List<String> ids = reg.channelIds();
            if (!ids.isEmpty()) return ids;
        } catch (org.json.JSONException e) {
            LogBuffer.d("VehicleResearch", "channelIds: " + e.getMessage());
        }
        return CHANNEL_PRIORITY_FALLBACK;
    }

    /**
     * Full Phase-2 research: probes every sensor on every channel from the
     * registry, scores car profiles by signal responses, writes a structured
     * {@code probe_report.json}, and persists the selected profile + active channels.
     *
     * @param probe injected probe strategy (production passes {@code SignalProber})
     */
    public static ResearchOutcome runWithRegistry(Context ctx, RegistryStore reg,
            List<DataChannel> channels, VehicleProfile profile,
            ProgressListener listener, SensorProbe probe, Persister persister) {
        Map<String, Boolean> channelAvailability = new java.util.LinkedHashMap<>();
        List<ChannelResult> channelResults = new ArrayList<>();
        for (DataChannel ch : channels) {
            ChannelResult r = probeWithTimeout(ch, ctx);
            channelResults.add(r);
            channelAvailability.put(ch.id(), r.isAlive());
        }
        List<String> brandProfiles = BrandSelector.detect(reg, channelAvailability);

        Map<String, Map<String, ProbeResult>> sensorResults = new java.util.LinkedHashMap<>();
        Map<String, Set<String>> okByChannel = new java.util.HashMap<>();
        List<String> sensorKeys = new ArrayList<>();
        try {
            sensorKeys = reg.sensorKeys();
        } catch (org.json.JSONException e) {
            LogBuffer.e("VehicleResearch", "registry sensorKeys: " + e.getMessage());
        }
        List<String> priority = channelPriority(reg);
        int total = sensorKeys.size();
        int done = 0;
        for (String key : sensorKeys) {
            Map<String, ProbeResult> perCh = new java.util.LinkedHashMap<>();
            for (String ch : priority) {
                ProbeResult r;
                try {
                    r = probe.probe(ctx, reg, key, ch);
                } catch (Exception e) {
                    r = ProbeResult.error(e.getMessage());
                }
                perCh.put(ch, r);
                if (r != null && r.isOk()) {
                    okByChannel.computeIfAbsent(key, k -> new HashSet<>()).add(ch);
                }
            }
            sensorResults.put(key, perCh);
            done++;
            if (listener != null) listener.onChannelDone(done, total, key);
        }

        ProfileScorer scorer = new ProfileScorer(reg, okByChannel);
        String selected = null;
        Map<String, Integer> scores = new java.util.LinkedHashMap<>();
        String brandChannel = brandProfiles.isEmpty() ? null
                : safeKeyChannel(reg, brandProfiles.get(0));
        try {
            if (brandChannel != null) {
                // Heavy pass: re-verify every expected non-generic sensor of the
                // detected brand; failures (ok²/(ok+fail)) lower the score.
                Set<String> heavyKeys = new java.util.LinkedHashSet<>();
                for (String pid : brandProfiles) {
                    heavyKeys.addAll(reg.profileNonGenericSensors(pid));
                }
                Map<String, Set<String>> heavyOk = new java.util.HashMap<>();
                int doneH = 0;
                for (String key : heavyKeys) {
                    for (String ch : priority) {
                        ProbeResult r;
                        try {
                            r = probe.probe(ctx, reg, key, ch);
                        } catch (Exception e) {
                            r = ProbeResult.error(e.getMessage());
                        }
                        if (r != null && r.isOk()) {
                            heavyOk.computeIfAbsent(key, k -> new HashSet<>()).add(ch);
                        }
                    }
                    doneH++;
                    if (listener != null) listener.onChannelDone(doneH, heavyKeys.size(), key);
                }
                ProfileScorer heavyScorer = new ProfileScorer(reg, heavyOk);
                for (String pid : brandProfiles) scores.put(pid, heavyScorer.score(pid));
                selected = heavyScorer.selectBestForBrand(brandProfiles);
            } else {
                for (String pid : reg.profileIds()) scores.put(pid, scorer.score(pid));
                selected = scorer.selectBest();
            }
        } catch (org.json.JSONException e) {
            LogBuffer.e("VehicleResearch", "scoring: " + e.getMessage());
        }

        List<String> active = new ArrayList<>();
        for (String id : priority) {
            Boolean a = channelAvailability.get(id);
            if (a != null && a) active.add(id);
        }
        for (Map.Entry<String, Boolean> e : channelAvailability.entrySet()) {
            if (e.getValue() && !active.contains(e.getKey())) active.add(e.getKey());
        }

        Map<String, List<String>> cmdCallable = new java.util.LinkedHashMap<>();
        try {
            for (String cid : reg.commandIds()) cmdCallable.put(cid, CommandProber.callableBy(reg, cid));
        } catch (org.json.JSONException e) {
            LogBuffer.e("VehicleResearch", "commands: " + e.getMessage());
        }

        String version = null;
        try {
            version = AppInfo.getVersionString(ctx);
        } catch (Exception e) {
            LogBuffer.d("VehicleResearch", "version: " + e.getMessage());
        }
        String deviceAnon = "anon";
        if (ctx != null) {
            try {
                deviceAnon = DeviceAnon.fromContext(ctx);
            } catch (Exception e) {
                LogBuffer.d("VehicleResearch", "device anon: " + e.getMessage());
            }
        }
        JSONObject report = null;
        String reportPath = null;
        try {
            report = ProbeReport.build(reg, channelAvailability, sensorResults,
                    cmdCallable, scores, selected, version, deviceAnon);
            if (ctx != null && report != null) {
                // Per-report link diagnostics: why an OBD/Voyah channel may be down.
                // Read via reflection so the plain-java test harness does not need
                // the whole AppConfig dependency chain at compile time.
                JSONObject diag = new JSONObject();
                diag.put("obd_enabled", appConfigGet(ctx, "isObdEnabled"));
                diag.put("obd_mode", appConfigGet(ctx, "getObdMode"));
                diag.put("obd_status", appConfigGet(ctx, "getObdStatus"));
                diag.put("obd_protocol", appConfigGet(ctx, "getObdProtocol"));
                diag.put("obd_last_error", appConfigGet(ctx, "getObdLastError"));
                diag.put("obd_bt_name", appConfigGet(ctx, "getObdBtName"));
                diag.put("obd_bt_addr", appConfigGet(ctx, "getObdBtAddress"));
                String pids = appConfigGet(ctx, "getObdSupportedPids");
                int pidCount = 0;
                if (!pids.isEmpty()) {
                    try {
                        pidCount = new org.json.JSONArray(pids).length();
                    } catch (Exception ignored) {
                    }
                }
                diag.put("obd_supported_pids", pidCount);
                report.put("diagnostics", diag);
            }
            if (ctx != null) reportPath = ProbeReport.writeFile(ctx, report);
        } catch (Exception e) {
            LogBuffer.e("VehicleResearch", "report: " + e.getMessage());
        }
        if (ctx != null && selected != null && persister != null) {
            persister.save(ctx, selected, active, reportPath);
        }
        return new ResearchOutcome(selected, active, report, reportPath);
    }
}