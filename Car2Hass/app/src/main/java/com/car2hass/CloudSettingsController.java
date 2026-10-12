package com.car2hass;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/**
 * Cloud (mytechnic.ru account) settings section: wiring, status line, login and
 * disconnect. Owns its views. Extracted from MainActivity to keep that class
 * smaller.
 */
public final class CloudSettingsController {

    public interface Host {
        Activity activity();
        void post(Runnable r);
        void toast(int res);
        void toast(CharSequence text);
        /** Opens the "clone settings from cloud" dialog (stays in MainActivity). */
        void showCloneDialog();
    }

    private final Host host;

    private Switch switchCloudSync, switchCloudSettings, switchCloudStoreSecret;
    private EditText editCloudEmail, editCloudCode, editCloudCarName, editCloudBaseUrl;
    private TextView tvCloudStatus;
    private Button btnCloudSyncNow, btnCloudClone, btnCloudLogin;
    private boolean updatingCloudToggle = false;

    public CloudSettingsController(Host host) {
        this.host = host;
    }

    /** Finds the views in the settings layout and wires the cloud section. */
    public void setup(View settingsView) {
        Activity a = host.activity();
        switchCloudSync = settingsView.findViewById(R.id.switchCloudSync);
        editCloudEmail = settingsView.findViewById(R.id.editCloudEmail);
        editCloudCode = settingsView.findViewById(R.id.editCloudCode);
        editCloudCarName = settingsView.findViewById(R.id.editCloudCarName);
        editCloudBaseUrl = settingsView.findViewById(R.id.editCloudBaseUrl);
        tvCloudStatus = settingsView.findViewById(R.id.tvCloudStatus);
        if (switchCloudSync == null) return;

        editCloudEmail.setText(AppConfig.getCloudEmail(a));
        editCloudCarName.setText(AppConfig.getCloudCarName(a));
        editCloudBaseUrl.setText(AppConfig.getCloudBaseUrl(a));

        switchCloudSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingCloudToggle) return;
            AppConfig.setCloudSyncEnabled(a, isChecked);
            if (isChecked && AppConfig.getCloudAccessToken(a).isEmpty()) {
                host.toast(R.string.settings_cloud_login_required);
            }
            updateStatus();
        });
        editCloudEmail.addTextChangedListener(watcher(
                () -> AppConfig.setCloudEmail(a, editCloudEmail.getText().toString())));
        editCloudCarName.addTextChangedListener(watcher(
                () -> AppConfig.setCloudCarName(a, editCloudCarName.getText().toString())));
        editCloudBaseUrl.addTextChangedListener(watcher(
                () -> AppConfig.setCloudBaseUrl(a, editCloudBaseUrl.getText().toString())));

        switchCloudSettings = settingsView.findViewById(R.id.switchCloudSettings);
        switchCloudStoreSecret = settingsView.findViewById(R.id.switchCloudStoreSecret);
        btnCloudSyncNow = settingsView.findViewById(R.id.btnCloudSyncNow);
        btnCloudClone = settingsView.findViewById(R.id.btnCloudClone);
        if (switchCloudSettings != null) {
            switchCloudSettings.setChecked(AppConfig.isCloudSettingsEnabled(a));
            switchCloudSettings.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    if (AppConfig.getCloudAccessToken(a).isEmpty()) {
                        switchCloudSettings.setChecked(false);
                        host.toast(R.string.settings_cloud_login_needed);
                        return;
                    }
                    CloudSettingsSync.onEnabled(a);
                } else {
                    AppConfig.setCloudSettingsEnabled(a, false);
                }
                updateStatus();
            });
        }
        if (switchCloudStoreSecret != null) {
            switchCloudStoreSecret.setChecked(AppConfig.isCloudStoreSecret(a));
            switchCloudStoreSecret.setOnCheckedChangeListener((b, checked) -> {
                AppConfig.setCloudStoreSecret(a, checked);
                CloudSettingsSync.syncAsync(a, "store_secret");
            });
        }
        if (btnCloudSyncNow != null) {
            btnCloudSyncNow.setOnClickListener(v -> CloudSettingsSync.syncNow(a, (ok, message) ->
                    host.post(() -> host.toast(ok
                            ? a.getString(R.string.settings_cloud_sync_ok)
                            : a.getString(R.string.settings_cloud_sync_failed, message)))));
        }
        if (btnCloudClone != null) {
            btnCloudClone.setOnClickListener(v -> host.showCloneDialog());
        }

        btnCloudLogin = settingsView.findViewById(R.id.btnCloudLogin);
        btnCloudLogin.setOnClickListener(v -> {
            if (!AppConfig.getCloudAccessToken(a).isEmpty()) disconnect();
            else login();
        });
        updateStatus();
    }

    private TextWatcher watcher(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { action.run(); }
        };
    }

    private void login() {
        final Activity a = host.activity();
        final String email = editCloudEmail.getText().toString().trim();
        final String code = editCloudCode.getText().toString().trim();
        if (email.isEmpty() || code.isEmpty()) {
            host.toast(R.string.settings_cloud_enter_email_code);
            return;
        }
        tvCloudStatus.setText(R.string.settings_cloud_signing_in);
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            final String result = CloudSyncClient.login(app, email, code);
            if (result.isEmpty()) {
                CloudSyncClient.ensureCarBound(app);
            }
            host.post(() -> {
                if (result.isEmpty()) {
                    editCloudCode.setText("");
                    AppConfig.setCloudSyncEnabled(a, true);
                    host.toast(R.string.settings_cloud_login_ok);
                } else {
                    host.toast(errorText(result));
                }
                updateStatus();
            });
        }, "cloud-login").start();
    }

    private void disconnect() {
        Activity a = host.activity();
        AppConfig.clearCloudTokens(a);
        AppConfig.setCloudSyncEnabled(a, false);
        host.toast(R.string.settings_cloud_disconnected);
        updateStatus();
    }

    private String errorText(String code) {
        Activity a = host.activity();
        switch (code) {
            case "bad_credentials": return a.getString(R.string.settings_cloud_err_bad_credentials);
            case "rate_limited": return a.getString(R.string.settings_cloud_err_rate_limited);
            case "totp_not_set": return a.getString(R.string.settings_cloud_err_totp_not_set);
            case "bad_input": return a.getString(R.string.settings_cloud_err_bad_input);
            default: return a.getString(R.string.settings_cloud_err_failed, code);
        }
    }

    private String statusText(String status) {
        Activity a = host.activity();
        if (status.startsWith("http_") || status.startsWith("bind_http_")) {
            return a.getString(R.string.settings_cloud_status_sync_error);
        }
        switch (status) {
            case "logged_out": return a.getString(R.string.settings_cloud_status_logged_out);
            case "session_expired": return a.getString(R.string.settings_cloud_status_session_expired);
            case "session_unavailable": return a.getString(R.string.settings_cloud_status_session_unavailable);
            case "car_not_bound": return a.getString(R.string.settings_cloud_status_car_not_bound);
            case "no_car_name": return a.getString(R.string.settings_cloud_status_no_car_name);
            default: return status;
        }
    }

    /** Rebuilds the cloud status line and toggles the auth fields visibility. */
    public void updateStatus() {
        if (tvCloudStatus == null || switchCloudSync == null) return;
        Activity a = host.activity();
        boolean enabled = AppConfig.isCloudSyncEnabled(a);
        boolean loggedIn = !AppConfig.getCloudAccessToken(a).isEmpty();
        String last = AppConfig.getCloudLastSyncMs(a) > 0
                ? DateFormat.getDateTimeInstance().format(new Date(AppConfig.getCloudLastSyncMs(a)))
                : a.getString(R.string.settings_cloud_never);
        StringBuilder sb = new StringBuilder();
        sb.append(loggedIn ? a.getString(R.string.settings_cloud_logged_in)
                           : a.getString(R.string.settings_cloud_logged_out));
        sb.append(" · ").append(a.getString(R.string.settings_cloud_last_sync, last));
        if (!enabled) sb.append(" · ").append(a.getString(R.string.settings_cloud_disabled));
        String status = AppConfig.getCloudLastStatus(a);
        if (!status.isEmpty() && !"ok".equals(status)) {
            sb.append(" · ").append(statusText(status));
        }
        tvCloudStatus.setText(sb.toString());
        tvCloudStatus.setBackgroundResource(0);
        boolean expired = "session_expired".equals(status) || "session_unavailable".equals(status);
        tvCloudStatus.setTextColor(expired
                ? a.getResources().getColor(R.color.accentRed)
                : (loggedIn ? a.getResources().getColor(R.color.accentGreen)
                            : a.getResources().getColor(R.color.textTertiary)));
        int authVis = loggedIn ? View.GONE : View.VISIBLE;
        editCloudEmail.setVisibility(authVis);
        editCloudCode.setVisibility(authVis);
        if (btnCloudLogin != null) {
            btnCloudLogin.setText(loggedIn
                    ? R.string.settings_cloud_disconnect : R.string.settings_cloud_login);
        }
        if (switchCloudSync.isChecked() != enabled) {
            updatingCloudToggle = true;
            switchCloudSync.setChecked(enabled);
            updatingCloudToggle = false;
        }
    }
}
