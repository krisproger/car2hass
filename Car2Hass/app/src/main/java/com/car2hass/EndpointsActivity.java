package com.car2hass;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.car2hass.service.TelemetryService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EndpointsActivity extends Activity {
    private final List<HaEndpointConfig> endpoints = new ArrayList<>();
    private final Map<String, EndpointStatus> statuses = new LinkedHashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshStatuses = new Runnable() {
        @Override
        public void run() {
            loadStatuses();
            refreshHandler.postDelayed(this, 5000L);
        }
    };
    private ArrayAdapter<HaEndpointConfig> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.endpoints_title);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        Button add = new Button(this);
        add.setText(R.string.endpoint_add);
        add.setOnClickListener(v -> editEndpoint(null));
        root.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        Button testAll = new Button(this);
        testAll.setText(R.string.endpoint_test_all);
        testAll.setOnClickListener(v -> testAllEndpoints());
        root.addView(testAll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<HaEndpointConfig>(this, android.R.layout.simple_list_item_1, endpoints) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                HaEndpointConfig endpoint = endpoints.get(position);
                String state = endpoint.enabled ? "on" : "off";
                String commands = endpoint.commandsEnabled ? "commands" : "read-only";
                view.setText(endpoint.name + "\n"
                        + endpoint.haAddress().baseUrl() + " · "
                        + endpoint.carName + " · " + state + " · " + commands
                        + "\n" + statusLine(endpoint));
                view.setTextSize(14);
                view.setPadding(dp(8), dp(8), dp(8), dp(8));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> actions(endpoints.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        refreshHandler.removeCallbacks(refreshStatuses);
        refreshHandler.postDelayed(refreshStatuses, 5000L);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshStatuses);
        super.onPause();
    }

    private void reload() {
        endpoints.clear();
        endpoints.addAll(EndpointStore.list(this));
        loadStatuses();
    }

    private void loadStatuses() {
        statuses.clear();
        TelemetryService service = MainActivity.getTelemetryService();
        if (service != null) {
            for (EndpointStatus status : service.endpointStatuses()) {
                statuses.put(status.endpointId, status);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private String statusLine(HaEndpointConfig endpoint) {
        EndpointStatus status = statuses.get(endpoint.id);
        if (status == null) return getString(R.string.endpoint_status_no_service);
        if (!endpoint.enabled) return getString(R.string.endpoint_status_disabled);
        String line;
        if (status.connected()) {
            String integration = status.integrationVersion.isEmpty()
                    ? "" : " · HA " + status.integrationVersion;
            line = getString(R.string.endpoint_status_ok, formatTime(status.lastSuccessMs),
                    integration, status.queueCount);
        } else if (status.failed()) {
            String error = status.lastError;
            if (error.length() > 90) error = error.substring(0, 90) + "…";
            if (status.backoffMs > 0) {
                line = getString(R.string.endpoint_status_error_retry, error,
                        status.backoffMs / 1000, status.queueCount);
            } else {
                line = getString(R.string.endpoint_status_error, error, status.queueCount);
            }
        } else {
            line = getString(R.string.endpoint_status_waiting, status.queueCount);
        }
        if (status.deadLetterCount > 0) {
            line += getString(R.string.endpoint_status_dead, status.deadLetterCount);
        }
        return line;
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "—";
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }

    private void actions(HaEndpointConfig endpoint) {
        new AlertDialog.Builder(this)
                .setTitle(endpoint.name)
                .setItems(new CharSequence[]{
                        getString(R.string.endpoint_edit),
                        getString(R.string.endpoint_test),
                        getString(R.string.endpoint_delete)
                }, (dialog, which) -> {
                    if (which == 0) editEndpoint(endpoint);
                    else if (which == 1) testEndpoint(endpoint);
                    else confirmDelete(endpoint);
                })
                .show();
    }

    private void confirmDelete(HaEndpointConfig endpoint) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.endpoint_delete_confirm)
                .setPositiveButton(R.string.endpoint_delete, (d, w) -> {
                    EndpointStore.delete(this, endpoint.id);
                    restartService();
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void editEndpoint(HaEndpointConfig existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText name = field(form, R.string.endpoint_name, false,
                existing != null ? existing.name : "Home Assistant");
        EditText address = field(form, R.string.endpoint_address, false,
                existing != null ? existing.address : "");
        address.setHint(R.string.endpoint_address_hint);
        EditText token = field(form, R.string.endpoint_token, true,
                existing != null ? new SecureStorage(this).getEndpointToken(existing.id) : "");
        EditText carName = field(form, R.string.endpoint_car_name, false,
                existing != null ? existing.carName : AppConfig.getCarName(this));
        CheckBox https = check(form, R.string.settings_https,
                existing != null && existing.https);
        CheckBox enabled = check(form, R.string.endpoint_enabled,
                existing == null || existing.enabled);
        CheckBox commands = check(form, R.string.endpoint_commands,
                existing != null && existing.commandsEnabled);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.endpoint_add : R.string.endpoint_edit)
                .setView(form)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        HaAddress parsed = HaAddress.parse(
                                address.getText().toString().trim(), https.isChecked());
                        String tokenValue = token.getText().toString().trim();
                        if (tokenValue.isEmpty()) {
                            toast(R.string.endpoint_missing_token);
                            return;
                        }
                        String car = carName.getText().toString().trim();
                        if (car.isEmpty()) {
                            car = AppConfig.getCarName(this);
                        }
                        String id = existing != null ? existing.id : EndpointStore.newId(this);
                        HaEndpointConfig endpoint = new HaEndpointConfig(
                                id,
                                name.getText().toString().trim().isEmpty()
                                        ? "Home Assistant" : name.getText().toString().trim(),
                                parsed.canonicalAddress(), parsed.https, car,
                                enabled.isChecked(), commands.isChecked());
                        new SecureStorage(this).saveEndpointToken(id, tokenValue);
                        EndpointStore.upsert(this, endpoint);
                        restartService();
                        reload();
                    } catch (IllegalArgumentException e) {
                        toast(R.string.endpoint_invalid_address);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void testAllEndpoints() {
        List<HaEndpointConfig> snapshot = new ArrayList<>(endpoints);
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (HaEndpointConfig endpoint : snapshot) {
                String token = new SecureStorage(this).getEndpointToken(endpoint.id);
                String baseUrl = endpoint.haAddress().baseUrl();
                HaDiagnostics.Result diag = HaDiagnostics.run(baseUrl, token);
                IntegrationInfo info = IntegrationInfo.query(baseUrl, token);
                sb.append(endpoint.name).append(": ")
                        .append(diag.httpOk ? "OK" : "FAIL")
                        .append(" loaded=")
                        .append(info == null ? "?" : String.valueOf(info.loaded))
                        .append('\n');
            }
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(R.string.endpoint_test_all)
                    .setMessage(sb.toString())
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        }, "endpoint-test-all").start();
    }

    private void testEndpoint(HaEndpointConfig endpoint) {
        String token = new SecureStorage(this).getEndpointToken(endpoint.id);
        String baseUrl = endpoint.haAddress().baseUrl();
        new Thread(() -> {
            HaDiagnostics.Result diag = HaDiagnostics.run(baseUrl, token);
            IntegrationInfo info = IntegrationInfo.query(baseUrl, token);
            String loaded = info == null ? "unknown" : String.valueOf(info.loaded);
            String text = diag.report + "\nloaded=" + loaded;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle(endpoint.name)
                    .setMessage(text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
        }, "endpoint-test").start();
    }

    private EditText field(LinearLayout root, int hintRes, boolean password, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hintRes);
        edit.setText(value);
        edit.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    private CheckBox check(LinearLayout root, int textRes, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(textRes);
        box.setChecked(checked);
        root.addView(box);
        return box;
    }

    private void restartService() {
        TelemetryService.stop(this);
        TelemetryService.start(this);
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
