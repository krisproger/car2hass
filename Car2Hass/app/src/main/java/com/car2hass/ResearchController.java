package com.car2hass;

import android.app.Activity;

import com.car2hass.vehicle.ChannelCatalog;
import com.car2hass.vehicle.DataChannel;
import com.car2hass.vehicle.DiPlusChannel;
import com.car2hass.vehicle.NativeChannel;
import com.car2hass.vehicle.RegistryStore;
import com.car2hass.vehicle.SignalProber;
import com.car2hass.vehicle.VehicleProfile;
import com.car2hass.vehicle.VehicleResearch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the vehicle channel research (probe), drives the research button as an
 * inline progress indicator, builds the summary and uploads the report.
 * Extracted from MainActivity to keep that class smaller.
 */
public final class ResearchController {

    public interface Host {
        Activity activity();
        ProgressActionButton button();
        /** Called on the UI thread after a run finishes (first-launch bookkeeping + section re-render). */
        void onResearchFinished();
        /** Shows the research summary dialog (used by the "Show report" button state). */
        void showReportDialog(String summary, String path);
        String str(int res);
        String str(int res, Object... args);
    }

    private final Host host;

    /** True while a research run is in progress (for lifecycle diagnostics). */
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Current research phase label (channel id / sensor key) for lifecycle logs. */
    private static volatile String phase = "";

    public static boolean isRunning() {
        return RUNNING.get();
    }

    public static String currentPhase() {
        return phase;
    }

    public ResearchController(Host host) {
        this.host = host;
    }

    /** Starts a research run (no-op while one is already running). */
    public void start() {
        final Activity a = host.activity();
        final ProgressActionButton button = host.button();
        if (button != null && button.isBusy()) return;

        List<DataChannel> channels;
        try {
            channels = ChannelCatalog.createAll(RegistryStore.load(a));
        } catch (Exception e) {
            LogBuffer.e("MainActivity", "ChannelCatalog: " + e.getMessage());
            List<DataChannel> fallback = new ArrayList<>();
            fallback.add(new DiPlusChannel());
            fallback.add(new NativeChannel());
            channels = fallback;
        }
        final List<DataChannel> researchChannels = channels;
        VehicleProfile profile = AppConfig.getVehicleProfile(a);
        int researchSensorCount;
        try {
            researchSensorCount = RegistryStore.load(a).sensorCount();
        } catch (Exception e) {
            researchSensorCount = 0;
        }
        final int researchTotal = researchChannels.size() + researchSensorCount;

        final String researchTitle = host.str(R.string.settings_vehicle_research);
        if (button != null) button.startBusy(R.string.settings_vehicle_research, researchTotal);
        TaskNotifier.progress(a, researchTitle,
                host.str(R.string.settings_vehicle_research_progress, 0, researchTotal), 0, researchTotal);

        LogBuffer.i("ResearchController", "research start: " + researchChannels.size()
                + " channels, " + researchSensorCount + " sensors");
        RUNNING.set(true);
        new Thread(() -> {
            final long t0 = System.currentTimeMillis();
            // Throttle progress to the UI/notification: one update per 300 ms (and
            // always the final one). Per-sensor posts flooded the UI on slow runs.
            final java.util.concurrent.atomic.AtomicLong lastUiMs =
                    new java.util.concurrent.atomic.AtomicLong(0);
            VehicleResearch.ProgressListener listener = (done, total, name) -> {
                phase = name;
                long nowMs = System.currentTimeMillis();
                if (done < total && nowMs - lastUiMs.get() < 300L) return;
                lastUiMs.set(nowMs);
                a.runOnUiThread(() -> {
                    if (button != null) {
                        button.setProgress(done, total);
                        button.setBusyLabel(host.str(R.string.settings_vehicle_research_progress, done, total));
                    }
                    TaskNotifier.progress(a, researchTitle,
                            host.str(R.string.settings_vehicle_research_progress, done, total), done, total);
                });
            };
            VehicleResearch.ResearchOutcome outcome;
            try {
                // When the user picked the profile manually the probe result
                // must not overwrite their choice.
                outcome = VehicleResearch.runWithRegistry(
                        a, RegistryStore.load(a), researchChannels, profile, listener,
                        new SignalProber(),
                        (ctx, p, active, rp) -> AppConfig.saveProbeResult(ctx,
                                AppConfig.isUserOverridden(ctx)
                                        ? AppConfig.getSelectedProfile(ctx) : p,
                                com.car2hass.vehicle.ResearchUiModel.unionActive(
                                        AppConfig.getActiveChannels(ctx), active),
                                rp));
            } catch (Exception e) {
                LogBuffer.e("MainActivity", "runVehicleResearch: " + e.getMessage());
                outcome = new VehicleResearch.ResearchOutcome(
                        AppConfig.getSelectedProfile(a),
                        AppConfig.getActiveChannels(a), null, null);
            }
            String path0 = outcome.reportPath;
            if (outcome.report != null) {
                try {
                    java.util.Map<String, Boolean> avail =
                            com.car2hass.vehicle.ChannelAvailability.fromReport(outcome.report);
                    outcome.report.put("methods",
                            com.car2hass.vehicle.MethodProber.methodsFor(
                                    RegistryStore.load(a), avail));
                    outcome.report.put("write_probe",
                            com.car2hass.vehicle.VoyahWriteProbe.probe(a));
                    String p2 = com.car2hass.vehicle.ProbeReport.writeFile(a, outcome.report);
                    if (p2 != null) path0 = p2;
                } catch (Exception e) {
                    LogBuffer.e("ResearchController", "method canaries: " + e.getMessage());
                }
            }
            final String path = path0;
            String reportSummary = buildSummary(outcome);
            LogBuffer.i("ResearchController", "research finished in "
                    + (System.currentTimeMillis() - t0) + " ms, profile="
                    + outcome.selectedProfile + ", active=" + outcome.activeChannels
                    + ", focus=" + com.car2hass.vehicle.ResearchEnvironment.foregroundFocus(a));
            final String autoProfile = outcome.selectedProfile;
            a.runOnUiThread(() -> {
                TaskNotifier.done(a, researchTitle, host.str(R.string.settings_vehicle_research_done));
                // Research finished: the button becomes "Show report" instead of
                // restarting, and the summary is shown only on tap.
                final String summary = reportSummary;
                final String reportPath = path;
                if (button != null) {
                    button.setAlt(R.string.settings_research_show_report,
                            v -> host.showReportDialog(summary, reportPath));
                }
                host.onResearchFinished();
                RUNNING.set(false);
                uploadReport(path);
                if (AppConfig.isUserOverridden(a)
                        && autoProfile != null
                        && !autoProfile.equals(AppConfig.getSelectedProfile(a))) {
                    android.widget.Toast.makeText(a,
                            host.str(R.string.settings_research_recommend,
                                    AppConfig.getSelectedProfile(a), autoProfile),
                            android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private String buildSummary(VehicleResearch.ResearchOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append(host.str(R.string.settings_vehicle_research_profile,
                outcome.selectedProfile == null ? "—" : outcome.selectedProfile)).append('\n');
        sb.append(host.str(R.string.settings_vehicle_research_channels,
                outcome.activeChannels.isEmpty() ? "—"
                        : String.join(", ", outcome.activeChannels))).append('\n');
        if (outcome.report != null) {
            JSONObject sensors = outcome.report.optJSONObject("sensors");
            sb.append(host.str(R.string.settings_vehicle_research_signals,
                    sensors == null ? 0 : sensors.length())).append('\n');
            // Per-channel ok counts: makes it visible that e.g. voyah/obd were
            // probed even when they returned no data.
            Map<String, Integer> okPer = new java.util.LinkedHashMap<>();
            if (sensors != null) {
                JSONArray keys = sensors.names();
                for (int i = 0; keys != null && i < keys.length(); i++) {
                    JSONObject ch = sensors.optJSONObject(keys.optString(i));
                    if (ch == null) continue;
                    JSONArray chNames = ch.names();
                    for (int j = 0; chNames != null && j < chNames.length(); j++) {
                        String c = chNames.optString(j);
                        if ("ok".equals(ch.optString(c))) okPer.merge(c, 1, Integer::sum);
                    }
                }
            }
            JSONObject wp = outcome.report.optJSONObject("write_probe");
            if (wp != null) {
                boolean capable = wp.optBoolean("write_capable", false);
                sb.append(host.str(capable ? R.string.settings_research_write_ok
                        : R.string.settings_research_write_denied)).append('\n');
            }
            if (!okPer.isEmpty()) {
                StringBuilder line = new StringBuilder();
                for (Map.Entry<String, Integer> e : okPer.entrySet()) {
                    if (line.length() > 0) line.append(", ");
                    line.append(e.getKey()).append('=').append(e.getValue());
                }
                sb.append(host.str(R.string.settings_vehicle_research_ok_by_channel, line)).append('\n');
            }
        }
        // Phone-only case (no DiPlus/Voyah alive): guide the user to OBD2.
        if (outcome.report != null) {
            boolean hasDiplus = channelHasData(outcome.report, "diplus");
            boolean hasVoyah = channelHasData(outcome.report, "voyah");
            boolean hasAdb = channelHasData(outcome.report, "adb");
            String addr = AppConfig.getObdBtAddress(host.activity());
            boolean obdSet = addr != null && !addr.isEmpty();
            if (!hasDiplus && !hasVoyah && !hasAdb && !obdSet) {
                sb.append(host.str(R.string.research_hint_obd2)).append('\n');
            }
        }
        return sb.toString();
    }

    /** True when the channel is available OR reported at least one "ok" sensor. */
    private static boolean channelHasData(JSONObject report, String id) {
        if (report == null) return false;
        JSONArray chans = report.optJSONArray("channels");
        if (chans != null) {
            for (int i = 0; i < chans.length(); i++) {
                JSONObject c = chans.optJSONObject(i);
                if (c != null && id.equals(c.optString("id")) && c.optBoolean("available", false)) {
                    return true;
                }
            }
        }
        JSONObject sensors = report.optJSONObject("sensors");
        if (sensors != null) {
            JSONArray keys = sensors.names();
            for (int i = 0; keys != null && i < keys.length(); i++) {
                JSONObject ch = sensors.optJSONObject(keys.optString(i));
                if (ch != null && "ok".equals(ch.optString(id))) return true;
            }
        }
        return false;
    }

    private void uploadReport(String path) {
        final Activity a = host.activity();
        if (path == null || !AppConfig.isProbeUploadEnabled(a)) return;
        final String title = host.str(R.string.upload_research_title);
        TaskNotifier.progress(a, title, host.str(R.string.upload_in_progress), 0, 0);
        new Thread(() -> {
            ProbeUploader.Result r = ProbeUploader.uploadResult(a, path);
            LogBuffer.i("MainActivity", "probe upload: ok=" + r.ok + " code=" + r.code);
            if (r.ok) AppConfig.setProbeLastUploadMs(a, System.currentTimeMillis());
            final boolean ok = r.ok;
            a.runOnUiThread(() -> TaskNotifier.done(a, title,
                    host.str(ok ? R.string.upload_research_ok : R.string.upload_failed)));
        }, "probe-upload").start();
    }
}
