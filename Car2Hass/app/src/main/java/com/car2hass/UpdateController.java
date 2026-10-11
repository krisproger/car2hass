package com.car2hass;

import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;

/**
 * Owns the app-update flow (check → download → install) and the state of the
 * settings update button. Extracted from MainActivity to keep that class smaller.
 */
public final class UpdateController {

    public interface Listener {
        /** Called when a quiet (on-open) check finds an update, to show the notes dialog. */
        void onUpdateAvailable(UpdateChecker.UpdateInfo info);
    }

    private final Activity activity;
    private final ProgressActionButton button;
    private final Listener listener;

    public UpdateController(Activity activity, ProgressActionButton button, Listener listener) {
        this.activity = activity;
        this.button = button;
        this.listener = listener;
    }

    /** Quiet check on app open: reports an update via the listener, never toasts. */
    public void autoCheck() {
        if (UpdateDownloader.checkedRecently(activity)) return;
        runCheck(true, false);
    }

    /** Manual check ("Check for updates"); downloads immediately when one is found. */
    public void check(boolean force) {
        if (!force && UpdateDownloader.checkedRecently(activity)) return;
        runCheck(false, true);
    }

    /** Wires the update-channel spinner (stable/beta) in the tech settings section. */
    public void setupChannelSpinner(View settingsView) {
        Spinner spinner = settingsView.findViewById(R.id.spUpdateChannel);
        final String[] channelValues = {"stable", "beta"};
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                new String[]{activity.getString(R.string.settings_update_channel_stable),
                        activity.getString(R.string.settings_update_channel_beta)});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String current = AppConfig.getUpdateChannel(activity);
        spinner.setSelection("beta".equals(current) ? 1 : 0);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                AppConfig.setUpdateChannel(activity, channelValues[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void runCheck(boolean quiet, boolean toastUpToDate) {
        if (!quiet) {
            Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
            if (button != null) button.startBusy(R.string.update_checking, 0);
        }
        new Thread(() -> {
            UpdateChecker.UpdateInfo info = null;
            boolean failed = false;
            try {
                info = UpdateDownloader.check(activity, AppConfig.getUpdateChannel(activity));
            } catch (Exception first) {
                // Device DNS/TLS to the site is flaky — retry once on failure.
                LogBuffer.w("Main", "update check retry after: " + first.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ie) { return; }
                try {
                    info = UpdateDownloader.check(activity, AppConfig.getUpdateChannel(activity));
                } catch (Exception e) {
                    LogBuffer.w("Main", "update check: " + e.getMessage());
                    failed = true;
                }
            }
            final UpdateChecker.UpdateInfo result = info;
            final boolean didFail = failed;
            activity.runOnUiThread(() -> {
                if (result != null) {
                    // Remember the published version+sha so a downloaded APK can
                    // later be offered for install (SHA-verified) without a re-check.
                    AppConfig.setPendingUpdate(activity, result.version, result.sha256);
                    if (quiet) {
                        if (listener != null) listener.onUpdateAvailable(result);
                    } else {
                        download(result);
                    }
                    return;
                }
                if (!quiet) {
                    refreshButton();
                    Toast.makeText(activity, didFail
                            ? R.string.update_check_failed : R.string.update_up_to_date,
                            Toast.LENGTH_SHORT).show();
                } else if (toastUpToDate) {
                    Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show();
                }
            });
        }, "update-check").start();
    }

    /**
     * Puts the settings update button into its idle state: "Install update" when a
     * verified APK is already downloaded, otherwise "Check for updates".
     */
    public void refreshButton() {
        if (button == null) return;
        String version = AppConfig.getPendingUpdateVersion(activity);
        String sha = AppConfig.getPendingUpdateSha(activity);
        if (!version.isEmpty()) {
            File f = UpdateDownloader.findDownloadedFile(activity, -1, version, sha);
            if (f != null) {
                button.setAlt(R.string.update_install,
                        v -> UpdateDownloader.installFile(activity, f, sha));
                return;
            }
        }
        button.setIdle(R.string.update_check, v -> check(true));
    }

    /** Downloads (or offers to install an already-downloaded) update. */
    public void download(UpdateChecker.UpdateInfo info) {
        File existing = UpdateDownloader.findDownloadedFile(activity, -1, info.version, info.sha256);
        LogBuffer.i("Main", "update: version=" + info.version + " size=" + info.size
                + " sha256=" + info.sha256 + " existing=" + existing
                + (existing != null ? " length=" + existing.length() : ""));
        if (existing != null) {
            final File file = existing;
            if (button != null) {
                button.setAlt(R.string.update_install,
                        v -> UpdateDownloader.installFile(activity, file, info.sha256));
            }
            return;
        }

        // Button becomes the download indicator; a notification mirrors it.
        if (button != null) button.startBusy(R.string.update_downloading, 100);
        TaskNotifier.progress(activity, activity.getString(R.string.update_downloading, "0%"), "", 0, 100);
        new Thread(() -> {
            try {
                File apk = UpdateDownloader.download(activity, info, (done, total) ->
                        activity.runOnUiThread(() -> {
                            int pct = total > 0 ? (int) (done * 100 / total) : 0;
                            if (button != null) {
                                button.setProgress(pct, 100);
                                button.setBusyLabel(activity.getString(R.string.update_downloading,
                                        total > 0 ? pct + "%" : "..."));
                            }
                            TaskNotifier.progress(activity, activity.getString(R.string.update_downloading,
                                    total > 0 ? pct + "%" : "..."), "", pct, 100);
                        }));
                LogBuffer.i("Main", "update: download returned " + apk + " length="
                        + (apk == null ? -1 : apk.length()));
                final File apkFile = apk;
                activity.runOnUiThread(() -> {
                    TaskNotifier.clear(activity);
                    if (apkFile != null && button != null) {
                        button.setAlt(R.string.update_install,
                                v -> UpdateDownloader.installFile(activity, apkFile, info.sha256));
                    } else {
                        refreshButton();
                        Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                LogBuffer.e("Main", "update download failed: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage() + " — showing update_download_failed toast");
                activity.runOnUiThread(() -> {
                    TaskNotifier.clear(activity);
                    refreshButton();
                    Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                });
            }
        }, "update-download").start();
    }
}
