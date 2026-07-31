package com.diplustohass;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.TypedValue;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.diplustohass.rules.Rule;
import com.diplustohass.rules.RuleRegistry;
import com.diplustohass.rules.RulesListAdapter;
import com.diplustohass.service.TelemetryService;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends BaseLocalizedActivity {
    private CANDataAdapter adapter;
    private TextView statusText, vinText, firmwareText, appVersionText, refreshTimeText, locationText;
    private Button btnSendLog;
    private CheckBox checkSelectAll;
    private CheckBox headerCheckAll;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<CANDataItem> knownItems = createSignalItems();
    private long lastRefresh = 0;
    private long lastUiUpdateMs = 0;
    private static final long UI_UPDATE_MIN_INTERVAL_MS = 1000;

    // Signal enable filter state (in-memory; persisted every 5 s when changed)
    private final Set<String> pendingDisabledKeys = new HashSet<>();
    private boolean enabledDirty = false;
    private final Runnable saveEnabledRunnable = this::saveEnabledState;

    // Bottom navigation
    private View dashboardView, telemetryView, commandsView, rulesView, settingsView;
    private LinearLayout navDashboard, navTelemetry, navCommands, navRules, navSettings, navAbout;
    private View[] contentViews;
    private LinearLayout[] navItems;
    private int currentTab = 0;

    private static MainActivity instance;
    private RulesListAdapter rulesAdapter;
    private ListView rulesList;

    // Dashboard
    private GridView dashboardGrid;
    private TextView dashUpdateTime;
    private Button btnConfigureDashboard;
    private ImageButton fabDashboardDone;
    private ImageButton fabDashboardAdd;
    private DashboardAdapter dashboardAdapter;
    private final List<DashboardTile> dashboardTiles = new ArrayList<>();
    private final Map<String, String> pendingCommandValues = new HashMap<>();
    private final Map<String, Integer> lastSelectOptionIdx = new HashMap<>();
    private boolean dashboardEditMode = false;
    private static final int DASHBOARD_GRID_CAPACITY = 16;
    private static final long DASHBOARD_EDIT_TIMEOUT_MS = 15000;
    private final Runnable dashboardEditTimeoutRunnable = this::exitDashboardEditMode;
    private Handler dashboardEditHandler;

    // Storage permission continuation (used by log save on API < Q).
    private Runnable pendingStorageAction;

    // Commands UI
    private Spinner spinnerCommandCategory, spinnerCommand, spinnerCommandEnum;
    private EditText editCommandValue;
    private Button btnSendCommand;
    private TextView textCommandResult, textCommandJournal;
    private ImageButton btnExpandJournal;

    // Settings UI
    private EditText editHost, editPort, editToken, editCarName;
    private Switch switchEnabled;
    private CompoundButton switchHttps;
    private Switch switchBootAutoStart, switchCarControl, switchDetailedLog, switchQueueEnabled, switchBackgroundMode;
    private EditText editQueueMaxMb, editQueueMaxDays;
    private EditText editAdbHost, editAdbPort, editDiplusAuth;
    private TextView tvTestResult;
    private TextView tvPresetVersion;

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;

    private String savedHost, savedToken;
    private int savedPort;
    private boolean savedEnabled, savedHttps, savedCarControl;

    private TelemetryService telemetryService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                TelemetryService.LocalBinder binder = (TelemetryService.LocalBinder) service;
                telemetryService = binder.getService();
                telemetryService.setCallback(telemetryCallback);
                serviceBound = true;
                updateLocationText();
                LogBuffer.i("Main", getString(R.string.service_bound));
            } catch (Exception e) {
                LogBuffer.e("Main", "onServiceConnected failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            try {
                telemetryService = null;
                serviceBound = false;
                LogBuffer.i("Main", getString(R.string.service_unbound));
            } catch (Exception e) {
                LogBuffer.e("Main", "onServiceDisconnected failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    };

    private final TelemetryService.TelemetryCallback telemetryCallback = new TelemetryService.TelemetryCallback() {
        @Override
        public void onDataUpdated(List<CANDataItem> items, long timestamp) {
            try {
                lastRefresh = timestamp;
                applyEnabledStateToItems(items);
                handler.post(() -> {
                    try {
                        long now = System.currentTimeMillis();
                        if (now - lastUiUpdateMs < UI_UPDATE_MIN_INTERVAL_MS) {
                            return;
                        }
                        lastUiUpdateMs = now;
                        adapter.setData(items);
                        if (AppConfig.isHassEnabled(MainActivity.this)) {
                            statusText.setText(R.string.status_active_ha);
                            statusText.setTextColor(0xFF4CAF50);
                        } else {
                            statusText.setText(R.string.status_active_no_ha);
                            statusText.setTextColor(0xFFFFC107);
                        }
                        vinText.setText(getString(R.string.vvin_prefix, CANDataReader.sVin));
                        firmwareText.setText(getString(R.string.fw_prefix, CANDataReader.sFirmware));
                        updateTimestamp();
                        updateLocationText();
                        updateDashboard(items);
                    } catch (Exception e) {
                        LogBuffer.e("Main", "UI update failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                LogBuffer.e("Main", "onDataUpdated failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        @Override
        public void onError(String message) {
            try {
                handler.post(() -> {
                    try {
                        statusText.setText(getString(R.string.status_error, message));
                        statusText.setTextColor(0xFFF44336);
                        vinText.setText(getString(R.string.vvin_prefix, CANDataReader.sVin));
                        firmwareText.setText(getString(R.string.fw_prefix, CANDataReader.sFirmware));
                        updateLocationText();
                    } catch (Exception e) {
                        LogBuffer.e("Main", "Error UI update failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                LogBuffer.e("Main", "onError dispatch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogBuffer.init(this);
        LogBuffer.i("Main", "DiPlus-to-hass " + AppInfo.getVersionString(this) + " starting, taskId=" + getTaskId());
        new CrashLogger(this).register();
        try {
            setContentView(R.layout.activity_main);
            com.diplustohass.rules.RuleRegistry.seedExamplesIfFirstRun(this);
            initStatusViews();
            initContentViews();
            initTelemetryList();
            initTelemetryControls();
            initRulesControls();
            initListeners();
            requestAllRuntimePermissions();

            HassClient.init(this);
            ConfigLoader.applyBundledPresets(this);

            safeCall("setupBottomNavigation", this::setupBottomNavigation);
            safeCall("setupDashboard", this::setupDashboard);
            safeCall("setupCommands", this::setupCommands);
            safeCall("setupSettings", this::setupSettings);

            // Start telemetry service only after all UI is wired up.
            TelemetryService.start(this);
            bindTelemetryService();

            // Default screen is dashboard.
            selectTab(0);
            showAboutIfFirstRun();

            if (getIntent() != null && getIntent().getBooleanExtra("from_boot", false)) {
                getIntent().removeExtra("from_boot");
                minimizeAfterBoot();
            }
            LogBuffer.i("Main", "onCreate completed");
        } catch (Exception e) {
            LogBuffer.e("Main", "onCreate fatal: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    private void initStatusViews() {
        statusText = findViewById(R.id.statusText);
        vinText = findViewById(R.id.vinText);
        firmwareText = findViewById(R.id.firmwareText);
        appVersionText = findViewById(R.id.appVersionText);
        refreshTimeText = findViewById(R.id.refreshTimeText);
        locationText = findViewById(R.id.locationText);
        btnSendLog = findViewById(R.id.btnSendLog);
    }

    private void initContentViews() {
        dashboardView = findViewById(R.id.dashboardView);
        telemetryView = findViewById(R.id.telemetryView);
        commandsView = findViewById(R.id.commandsView);
        rulesView = findViewById(R.id.rulesView);
        settingsView = findViewById(R.id.settingsView);
        contentViews = new View[]{dashboardView, telemetryView, commandsView, rulesView, settingsView};

        navDashboard = findViewById(R.id.navDashboard);
        navTelemetry = findViewById(R.id.navTelemetry);
        navCommands = findViewById(R.id.navCommands);
        navRules = findViewById(R.id.navRules);
        navSettings = findViewById(R.id.navSettings);
        navAbout = findViewById(R.id.navAbout);
        navItems = new LinearLayout[]{navDashboard, navTelemetry, navCommands, navRules, navSettings, navAbout};
    }

    private void initTelemetryList() {
        ListView listView = findViewById(R.id.dataListView);
        adapter = new CANDataAdapter(this);
        adapter.setData(knownItems);
        listView.setAdapter(adapter);

        checkSelectAll = findViewById(R.id.checkSendToHa);
        headerCheckAll = findViewById(R.id.rowHeaderCheck);
        loadAndApplyEnabledFilter();
        setupHeaderCheckAll();
        setupColumnSorting();
    }

    private void initTelemetryControls() {
        View btnFilterSignals = findViewById(R.id.btnFilterSignals);
        if (btnFilterSignals != null) {
            btnFilterSignals.setOnClickListener(v -> startActivity(new Intent(this, AttributeFilterActivity.class)));
        }
    }

    private void initRulesControls() {
        rulesList = findViewById(R.id.rulesListView);
        rulesAdapter = new RulesListAdapter(this, new ArrayList<>(),
                (rule, enabled) -> {
                    RuleRegistry.upsert(this, rule);
                    notifyRuleEngineChanged();
                },
                rule -> {
                    showRuleActionsDialog(rule);
                    return true;
                });
        rulesList.setAdapter(rulesAdapter);

        rulesList.setOnItemClickListener((parent, view, position, id) -> {
            Rule rule = rulesAdapter.getItem(position);
            if (rule == null || rule.id == null) return;
            Intent intent = new Intent(this, com.diplustohass.rules.RuleEditActivity.class);
            intent.putExtra(com.diplustohass.rules.RuleEditActivity.EXTRA_RULE_ID, rule.id);
            startActivity(intent);
        });

        rulesList.setOnItemLongClickListener((parent, view, position, id) -> {
            Rule rule = rulesAdapter.getItem(position);
            if (rule == null) return true;
            showRuleActionsDialog(rule);
            return true;
        });

        findViewById(R.id.btnRulesAdd).setOnClickListener(v -> {
            Intent intent = new Intent(this, com.diplustohass.rules.RuleEditActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnRulesExport).setOnClickListener(v -> exportRules());
        findViewById(R.id.btnRulesImport).setOnClickListener(v -> importRules());
    }

    private void setupRules() {
        if (rulesAdapter == null) return;
        List<Rule> rules = RuleRegistry.load(this);
        rulesAdapter.updateRules(rules);
        TextView emptyText = findViewById(R.id.rulesEmptyText);
        if (emptyText != null) {
            emptyText.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
            rulesList.setVisibility(rules.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void exportRules() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rules_export)
                .setItems(new String[]{
                        getString(R.string.rules_export_share),
                        getString(R.string.rules_export_file)
                }, (dialog, which) -> {
                    if (which == 0) exportRulesShare();
                    else exportRulesSave();
                })
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void exportRulesSave() {
        if (!ensureStoragePermission()) return;
        List<ConfigStorageHelper.Folder> folders = ConfigStorageHelper.getFolders(this);
        String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) labels[i] = folders.get(i).label;

        new AlertDialog.Builder(this)
                .setTitle(R.string.rules_export_file)
                .setItems(labels, (dialog, which) -> {
                    ConfigStorageHelper.Folder folder = folders.get(which);
                    new Thread(() -> rulesSaveToFolder(folder)).start();
                })
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void rulesSaveToFolder(ConfigStorageHelper.Folder folder) {
        try {
            List<Rule> rules = RuleRegistry.load(this);
            org.json.JSONObject wrapper = buildRulesJson(rules);
            ConfigStorageHelper.ConfigRef ref = ConfigStorageHelper.writeConfig(
                    this, folder, wrapper, ConfigStorageHelper.newFileName(ConfigStorageHelper.RULES_PREFIX));
            String msg = ref.isUri()
                    ? getString(R.string.rules_export_success, ref.name)
                    : getString(R.string.rules_export_success, ref.file.getAbsolutePath());
            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            LogBuffer.e("Main", "Export rules failed: " + e.getMessage());
            runOnUiThread(() -> Toast.makeText(this,
                    getString(R.string.rules_import_error), Toast.LENGTH_LONG).show());
        }
    }

    private void exportRulesShare() {
        new Thread(() -> {
            try {
                List<Rule> rules = RuleRegistry.load(this);
                org.json.JSONObject wrapper = buildRulesJson(rules);
                byte[] bytes = wrapper.toString(2).getBytes("UTF-8");
                handler.post(() -> shareRulesBytes(bytes, ConfigStorageHelper.RULES_PREFIX));
            } catch (Exception e) {
                LogBuffer.e("Main", "Share rules failed: " + e.getMessage());
            }
        }).start();
    }

    private void shareRulesBytes(byte[] rulesBytes, String filePrefix) {
        Uri uri = null;
        try {
            String name = ConfigStorageHelper.newFileName(filePrefix);
            uri = ConfigStorageHelper.writeConfig(
                    this, ConfigStorageHelper.getDefaultFolder(this),
                    buildRulesJson(RuleRegistry.load(this)), name).uri;
        } catch (Exception e) {
            LogBuffer.w("Main", "shareRulesBytes: save failed: " + e.getMessage());
        }
        if (uri != null && "content".equals(uri.getScheme())) {
            try {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/json");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                share.setClipData(android.content.ClipData.newUri(getContentResolver(), "rules", uri));
                startActivity(Intent.createChooser(share, getString(R.string.rules_export_share)));
                return;
            } catch (Exception e) {
                LogBuffer.w("Main", "shareRulesBytes: stream share fell back: " + e.getMessage());
            }
        }
        // Fallback: share as text (rules JSON is compact, safe for EXTRA_TEXT).
        String text = new String(rulesBytes);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, getString(R.string.rules_export_share)));
    }

    private static String ruleFirstSensorKey(Rule rule) {
        if (rule.conditions != null && !rule.conditions.isEmpty()
                && rule.conditions.get(0).sensorKey != null
                && !rule.conditions.get(0).sensorKey.isEmpty()) {
            return rule.conditions.get(0).sensorKey;
        }
        return "";
    }

    // ---- single-rule actions (long-press menu) ----

    private void showRuleActionsDialog(final Rule rule) {
        String displayName = rule.name != null && !rule.name.isEmpty() ? rule.name : ruleFirstSensorKey(rule);
        new AlertDialog.Builder(this)
                .setTitle(displayName)
                .setItems(new String[]{
                        getString(R.string.rules_export_file),
                        getString(R.string.rules_export_share),
                        getString(R.string.rules_delete)
                }, (dialog, which) -> {
                    if (which == 0) exportSingleRuleToFile(rule);
                    else if (which == 1) shareSingleRule(rule);
                    else confirmDeleteRule(rule);
                })
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void confirmDeleteRule(final Rule rule) {
        String displayName = rule.name != null && !rule.name.isEmpty() ? rule.name : ruleFirstSensorKey(rule);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rules_delete)
                .setMessage(getString(R.string.rules_delete_confirm, displayName))
                .setPositiveButton(R.string.rules_delete, (d, w) -> {
                    RuleRegistry.delete(this, rule.id);
                    setupRules();
                    notifyRuleEngineChanged();
                    Toast.makeText(this, getString(R.string.rules_deleted), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void exportSingleRuleToFile(final Rule rule) {
        if (!ensureStoragePermission()) return;
        List<ConfigStorageHelper.Folder> folders = ConfigStorageHelper.getFolders(this);
        String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) labels[i] = folders.get(i).label;

        new AlertDialog.Builder(this)
                .setTitle(R.string.rules_export_file)
                .setItems(labels, (dialog, which) -> {
                    ConfigStorageHelper.Folder folder = folders.get(which);
                    new Thread(() -> {
                        try {
                            List<Rule> single = java.util.Collections.singletonList(rule);
                            org.json.JSONObject wrapper = buildRulesJson(single);
                            ConfigStorageHelper.ConfigRef ref = ConfigStorageHelper.writeConfig(
                                    this, folder, wrapper, ruleFileName(rule));
                            String msg = ref.isUri()
                                    ? getString(R.string.rules_export_success, ref.name)
                                    : getString(R.string.rules_export_success, ref.file.getAbsolutePath());
                            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            LogBuffer.e("Main", "Export single rule failed: " + e.getMessage());
                            runOnUiThread(() -> Toast.makeText(this,
                                    getString(R.string.rules_import_error), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void shareSingleRule(final Rule rule) {
        new Thread(() -> {
            try {
                List<Rule> single = java.util.Collections.singletonList(rule);
                org.json.JSONObject wrapper = buildRulesJson(single);
                byte[] bytes = wrapper.toString(2).getBytes("UTF-8");
                handler.post(() -> shareRulesBytes(bytes, ConfigStorageHelper.RULES_PREFIX));
            } catch (Exception e) {
                LogBuffer.e("Main", "Share single rule failed: " + e.getMessage());
            }
        }).start();
    }

    private static String ruleFileName(Rule rule) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US);
        String base = rule.name != null && !rule.name.isEmpty() ? rule.name : rule.id;
        if (base == null || base.isEmpty()) base = "rule";
        base = base.replaceAll("[^A-Za-z0-9\\u0400-\\u04FF_-]", "_");
        if (base.length() > 30) base = base.substring(0, 30);
        return ConfigStorageHelper.RULES_PREFIX + base + "_" + fmt.format(new java.util.Date()) + ".json";
    }

    private static org.json.JSONObject buildRulesJson(java.util.List<Rule> rules) throws Exception {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (Rule r : rules) arr.put(r.toJson());
        org.json.JSONObject wrapper = new org.json.JSONObject();
        wrapper.put("version", 2);
        wrapper.put("rules", arr);
        return wrapper;
    }

    // ---- import ----

    private void importRules() {
        if (!ensureStoragePermission()) return;
        List<ConfigStorageHelper.Folder> folders = ConfigStorageHelper.getFolders(this);
        String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) labels[i] = folders.get(i).label;

        new AlertDialog.Builder(this)
                .setTitle(R.string.rules_import)
                .setItems(labels, (dialog, which) -> showRuleFilePicker(folders.get(which)))
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void showRuleFilePicker(ConfigStorageHelper.Folder folder) {
        new Thread(() -> {
            // Show both new rules_* and legacy config_diplus2hass_* files.
            java.util.List<ConfigStorageHelper.ConfigRef> refs = new java.util.ArrayList<>();
            refs.addAll(ConfigStorageHelper.listConfigRefs(this, folder, ConfigStorageHelper.RULES_PREFIX));
            refs.addAll(ConfigStorageHelper.listConfigRefs(this, folder));
            if (refs.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.rules_import_error), Toast.LENGTH_LONG).show());
                return;
            }
            final String[] names = new String[refs.size()];
            for (int i = 0; i < refs.size(); i++) names[i] = refs.get(i).name;
            final ConfigStorageHelper.ConfigRef[] refArray = refs.toArray(new ConfigStorageHelper.ConfigRef[0]);

            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.rules_import)
                        .setItems(names, (dialog, which) -> doImportRules(refArray[which]))
                        .setNegativeButton(R.string.menu_cancel, null)
                        .show();
            });
        }).start();
    }

    private void doImportRules(ConfigStorageHelper.ConfigRef ref) {
        new Thread(() -> {
            try {
                String text = ConfigStorageHelper.readConfigRef(this, ref);
                org.json.JSONObject wrapper = new org.json.JSONObject(text);
                int version = wrapper.optInt("version", 0);
                if (version != 1 && version != 2) {
                    runOnUiThread(() -> Toast.makeText(this,
                            getString(R.string.rules_import_error), Toast.LENGTH_LONG).show());
                    return;
                }
                org.json.JSONArray arr = wrapper.optJSONArray("rules");
                if (arr == null) {
                    runOnUiThread(() -> Toast.makeText(this,
                            getString(R.string.rules_import_error), Toast.LENGTH_LONG).show());
                    return;
                }
                java.util.List<Rule> imported = new java.util.ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) imported.add(Rule.fromJson(obj));
                }
                RuleRegistry.save(this, imported);
                runOnUiThread(() -> {
                    setupRules();
                    notifyRuleEngineChanged();
                    Toast.makeText(this, getString(R.string.rules_import_success), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                LogBuffer.e("Main", "Import rules failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.rules_import_error), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void notifyRuleEngineChanged() {
        if (telemetryService != null && telemetryService.getRuleEngine() != null) {
            telemetryService.getRuleEngine().onRulesChanged();
        }
    }

    private void initListeners() {
        btnSendLog.setOnClickListener(v -> showLogExportDialog());
        vinText.setOnClickListener(v -> copyToClipboard("VVIN", CANDataReader.sVin));
        firmwareText.setOnClickListener(v -> copyToClipboard("FW", CANDataReader.sFirmware));
        appVersionText.setText(getString(R.string.app_version, AppInfo.getVersionString(this)));
    }

    private void copyToClipboard(String label, String text) {
        try {
            if (text == null || "---".equals(text)) return;
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cb != null) {
                cb.setPrimaryClip(ClipData.newPlainText(label, text));
            }
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            LogBuffer.e("Main", "copyToClipboard failed: " + e.getMessage());
        }
    }

    private void showLogExportDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.log_export_title)
            .setItems(new String[]{getString(R.string.log_export_share), getString(R.string.log_export_save)}, (d, which) -> {
                if (which == 0) exportLogShare();
                else exportLogSave();
            })
            .show();
    }

    private void exportLogShare() {
        new Thread(() -> {
            try {
                final byte[] logBytes = LogExportHelper.buildLogBytes(this);
                handler.post(() -> shareLog(logBytes));
            } catch (Exception e) {
                LogBuffer.e("Main", "exportLogShare failed: " + e.getMessage());
            }
        }).start();
    }

    // Binder transaction limit is ~1 MB; keep EXTRA_TEXT well below it.
    private static final int MAX_SHARE_TEXT_LENGTH = 500_000;

    private void shareLog(byte[] logBytes) {
        // Preferred path: share the log file saved to Downloads so the full
        // log never travels through a binder transaction.
        Uri uri = null;
        try {
            uri = LogExportHelper.saveLogToDownloads(this, logBytes);
        } catch (Exception e) {
            LogBuffer.w("Main", "shareLog: save to Downloads failed: " + e.getMessage());
        }
        if (uri != null && "content".equals(uri.getScheme())) {
            try {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // ClipData is required for the grant to propagate through the chooser.
                share.setClipData(ClipData.newUri(getContentResolver(), "log", uri));
                startActivity(Intent.createChooser(share, getString(R.string.log_export_share)));
                return;
            } catch (Exception e) {
                LogBuffer.w("Main", "shareLog: stream share failed, falling back to text: " + e.getMessage());
            }
        }
        // Fallback (API 24-28 or MediaStore failure): share as truncated text.
        String text = new String(logBytes);
        if (text.length() > MAX_SHARE_TEXT_LENGTH) {
            text = getString(R.string.log_share_truncated)
                    + text.substring(text.length() - MAX_SHARE_TEXT_LENGTH);
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, getString(R.string.log_export_share)));
    }

    private void exportLogSave() {
        pendingStorageAction = this::performLogSave;
        if (!ensureStoragePermission()) {
            return;
        }
        pendingStorageAction = null;
        performLogSave();
    }

    private void performLogSave() {
        new Thread(() -> {
            try {
                final byte[] logBytes = LogExportHelper.buildLogBytes(this);
                final Uri uri = LogExportHelper.saveLogToDownloads(this, logBytes);
                handler.post(() -> {
                    String message = uri != null
                            ? getString(R.string.log_saved, logBytes.length)
                            : getString(R.string.log_save_failed);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                LogBuffer.e("Main", "performLogSave failed: " + e.getMessage());
                handler.post(() -> Toast.makeText(this, R.string.log_save_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void safeCall(String tag, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            LogBuffer.e("Main", tag + " failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void minimizeAfterBoot() {
        // Give the service a moment to start and bind, then move the task
        // to the background so the UI does not stay on screen after auto-start.
        handler.postDelayed(() -> {
            if (!isFinishing()) {
                moveTaskToBack(true);
                LogBuffer.i("Main", "Auto-started from boot and moved to background");
            }
        }, 1500);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogBuffer.d("Main", "onStart");
        if (!serviceBound) {
            bindTelemetryService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        LogBuffer.d("Main", "onResume");
        SensorCommandRegistry.getInstance(this).checkForUpdates();
        DashboardPresetRegistry.getInstance(this).checkForUpdates();
        if (checkSelectAll != null) {
            checkSelectAll.setOnCheckedChangeListener(null);
            checkSelectAll.setChecked(AppConfig.isHassEnabled(this));
            checkSelectAll.setOnCheckedChangeListener(sendToHaListener);
        }
        setupRules();
    }

    public static TelemetryService getTelemetryService() {
        return instance != null ? instance.telemetryService : null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogBuffer.d("Main", "onStop");
        if (serviceBound) {
            try {
                telemetryService.setCallback(null);
                unbindService(serviceConnection);
            } catch (Exception e) {
                LogBuffer.e("Main", "Unbind error: " + e.getMessage());
            }
            serviceBound = false;
            telemetryService = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogBuffer.d("Main", "onDestroy finishing=" + isFinishing());
    }

    @Override
    public void onBackPressed() {
        // Minimize instead of finishing so the foreground telemetry service keeps running.
        LogBuffer.d("Main", "onBackPressed — moving task to back");
        moveTaskToBack(true);
    }

    private void bindTelemetryService() {
        Intent intent = new Intent(this, TelemetryService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private List<CANDataItem> createSignalItems() {
        return CANDataReader.createSignalItems();
    }

    private void setupBottomNavigation() {
        navDashboard.setOnClickListener(v -> selectTab(0));
        navTelemetry.setOnClickListener(v -> selectTab(1));
        navCommands.setOnClickListener(v -> selectTab(2));
        navRules.setOnClickListener(v -> selectTab(3));
        navSettings.setOnClickListener(v -> selectTab(4));
        navAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
    }

    private void selectTab(int index) {
        int SETTINGS_INDEX = 4;
        // Leaving the settings tab: flush any pending autosave immediately so a
        // debounced doSave() cannot later overwrite changes made elsewhere
        // (e.g. the master "Send to HA" toggle on the telemetry tab).
        if (currentTab == SETTINGS_INDEX && index != SETTINGS_INDEX) {
            cancelPendingSave();
            if (editHost != null && switchEnabled != null) {
                doSave();
            }
        }
        // Entering the settings tab: drop any pending save and re-sync all
        // settings views from AppConfig to pick up outside changes.
        if (index == SETTINGS_INDEX && currentTab != SETTINGS_INDEX) {
            cancelPendingSave();
            if (editHost != null && switchEnabled != null) {
                loadConfig();
            }
        }
        for (int i = 0; i < contentViews.length; i++) {
            contentViews[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < navItems.length; i++) {
            boolean selected = i == index;
            navItems[i].setSelected(selected);
            TextView icon = (TextView) navItems[i].getChildAt(0);
            TextView label = (TextView) navItems[i].getChildAt(1);
            int color = selected ? getResources().getColor(R.color.primary)
                    : getResources().getColor(R.color.textSecondary);
            icon.setTextColor(color);
            label.setTextColor(color);
        }
        if (index == 2) {
            refreshCommandJournal();
        }
        if (index == 3) {
            setupRules();
        }
        if (index == 4) {
            updatePresetVersionLabel();
        }
        currentTab = index;
    }

    private void cancelPendingSave() {
        if (saveRunnable != null) {
            saveHandler.removeCallbacks(saveRunnable);
            saveRunnable = null;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Dashboard configuration may have changed while About screen was open.
        loadDashboardTiles();
        if (dashboardAdapter != null) {
            dashboardAdapter.setTiles(dashboardTiles);
        }
        // AttributeFilterActivity autosaves its own disabled-set; re-sync our
        // in-memory copy so a later debounced save cannot revert it.
        refreshEnabledFilterState();
    }

    // ─── Dashboard tab ───

    private void setupDashboard() {
        dashboardGrid = dashboardView.findViewById(R.id.dashboardGrid);
        fabDashboardDone = dashboardView.findViewById(R.id.fabDashboardDone);
        fabDashboardAdd = dashboardView.findViewById(R.id.fabDashboardAdd);
        dashboardEditHandler = new Handler(Looper.getMainLooper());

        loadDashboardTiles();

        dashboardAdapter = new DashboardAdapter(this);
        dashboardAdapter.setTiles(dashboardTiles);
        dashboardGrid.setAdapter(dashboardAdapter);
        dashboardAdapter.setOnTileClickListener((tile, position, view, x, y) -> {
            if (dashboardEditMode) {
                if (tile.isEmptyCell) {
                    resetDashboardEditTimeout();
                    showAddTilePicker();
                } else {
                    editDashboardTile(tile);
                }
                return;
            }
            handleDashboardTileTap(tile, view, x, y);
        });
        dashboardAdapter.setOnTileLongClickListener(tile -> {
            enterDashboardEditMode();
        });
        dashboardGrid.setOnLongClickListener(v -> {
            enterDashboardEditMode();
            return true;
        });
        dashboardAdapter.setOnTileDeleteListener((tile, position) -> {
            List<DashboardTile> tiles = dashboardAdapter.getTiles();
            tiles.remove(position);
            dashboardAdapter.setTiles(tiles);
            fillDashboardGridWithEmptyCells();
            resetDashboardEditTimeout();
        });
        if (fabDashboardDone != null) {
            fabDashboardDone.setOnClickListener(v -> exitDashboardEditMode());
        }
        if (fabDashboardAdd != null) {
            fabDashboardAdd.setOnClickListener(v -> {
                resetDashboardEditTimeout();
                showAddTilePicker();
            });
        }
    }

    private enum TileZone { LEFT, RIGHT, CENTER }

    private TileZone detectTileZone(View view, float x) {
        float width = view.getWidth();
        if (width <= 0) return TileZone.CENTER;
        float border = width * 0.25f;
        if (x < border) return TileZone.LEFT;
        if (x > width - border) return TileZone.RIGHT;
        return TileZone.CENTER;
    }

    private void enterDashboardEditMode() {
        if (dashboardEditMode) return;
        dashboardEditMode = true;
        dashboardAdapter.setEditMode(true);
        fillDashboardGridWithEmptyCells();
        if (fabDashboardDone != null) fabDashboardDone.setVisibility(View.VISIBLE);
        if (fabDashboardAdd != null) fabDashboardAdd.setVisibility(View.VISIBLE);
        resetDashboardEditTimeout();
        LogBuffer.i("Dashboard", "Entered dashboard edit mode");
        Toast.makeText(this, R.string.dashboard_edit_mode_hint, Toast.LENGTH_SHORT).show();
    }

    private void exitDashboardEditMode() {
        if (!dashboardEditMode) return;
        dashboardEditHandler.removeCallbacks(dashboardEditTimeoutRunnable);
        dashboardEditMode = false;
        dashboardAdapter.setEditMode(false);
        removeEmptyDashboardCells();
        AppConfig.saveDashboardTiles(this, dashboardAdapter.getTiles());
        if (fabDashboardDone != null) fabDashboardDone.setVisibility(View.GONE);
        if (fabDashboardAdd != null) fabDashboardAdd.setVisibility(View.GONE);
        LogBuffer.i("Dashboard", "Exited dashboard edit mode, saved " + dashboardAdapter.getTiles().size() + " tiles");
    }

    private void resetDashboardEditTimeout() {
        dashboardEditHandler.removeCallbacks(dashboardEditTimeoutRunnable);
        dashboardEditHandler.postDelayed(dashboardEditTimeoutRunnable, DASHBOARD_EDIT_TIMEOUT_MS);
    }

    /** Pause the edit-mode auto-exit timer while an edit-mode dialog is open. */
    private void pauseDashboardEditTimeout() {
        if (dashboardEditMode) {
            dashboardEditHandler.removeCallbacks(dashboardEditTimeoutRunnable);
        }
    }

    /** Restart the edit-mode auto-exit timer after an edit-mode dialog is dismissed. */
    private void resumeDashboardEditTimeout() {
        if (dashboardEditMode) {
            resetDashboardEditTimeout();
        }
    }

    private void fillDashboardGridWithEmptyCells() {
        List<DashboardTile> tiles = dashboardAdapter.getTiles();
        while (tiles.size() < DASHBOARD_GRID_CAPACITY) {
            tiles.add(DashboardTileFactory.createEmptyCellTile(this));
        }
        dashboardAdapter.setTiles(tiles);
    }

    private void removeEmptyDashboardCells() {
        List<DashboardTile> tiles = dashboardAdapter.getTiles();
        List<DashboardTile> filtered = new ArrayList<>();
        for (DashboardTile tile : tiles) {
            if (!tile.isEmptyCell) filtered.add(tile);
        }
        dashboardAdapter.setTiles(filtered);
    }

    private void showAddTilePicker() {
        Set<String> existing = new HashSet<>();
        for (DashboardTile t : dashboardAdapter.getTiles()) {
            existing.add(t.key);
            if (t.type == DashboardTile.Type.PRESET && t.presetId != null) existing.add(t.presetId);
        }
        pauseDashboardEditTimeout();
        AlertDialog picker = DashboardAddTileDialog.show(this, existing, tile -> {
            List<DashboardTile> tiles = dashboardAdapter.getTiles();
            int emptyIdx = -1;
            for (int i = 0; i < tiles.size(); i++) {
                if (tiles.get(i).isEmptyCell) { emptyIdx = i; break; }
            }
            if (emptyIdx >= 0) {
                tiles.set(emptyIdx, tile);
            } else {
                tiles.add(tile);
            }
            dashboardAdapter.setTiles(tiles);
            fillDashboardGridWithEmptyCells();
            resetDashboardEditTimeout();
        });
        picker.setOnDismissListener(d -> resumeDashboardEditTimeout());
    }

    private void editDashboardTile(DashboardTile tile) {
        resetDashboardEditTimeout();
        if (tile.type == DashboardTile.Type.COMMAND) {
            CommandRegistry.CommandEntry entry = CommandRegistry.getById(tile.key);
            if (entry != null && entry.needsValue()) {
                editCommandTileValue(tile);
                return;
            }
        } else if (tile.type == DashboardTile.Type.PRESET && tile.presetId != null) {
            DashboardPresetRegistry.DashboardPreset preset =
                    DashboardPresetRegistry.getInstance(this).getPreset(tile.presetId);
            if (preset != null && !preset.params.isEmpty()) {
                showPresetParamsDialog(preset);
                return;
            }
        }
        Toast.makeText(this, R.string.dashboard_no_tile_settings, Toast.LENGTH_SHORT).show();
    }

    private void handleDashboardTileTap(DashboardTile tile, View view, float x, float y) {
        LogBuffer.i("Dashboard", "Tile tap type=" + tile.type + " key=" + tile.key
                + (tile.presetId != null ? " preset=" + tile.presetId : "")
                + " zone=" + detectTileZone(view, x));
        DashboardPresetRegistry.DashboardPreset preset = null;
        if (tile.type == DashboardTile.Type.PRESET && tile.presetId != null) {
            preset = DashboardPresetRegistry.getInstance(this).getPreset(tile.presetId);
        }
        if (preset == null) {
            // Legacy sensor or command tile — send directly.
            if (tile.type == DashboardTile.Type.COMMAND) {
                sendDashboardCommand(tile);
            }
            return;
        }

        switch (preset.behavior) {
            case "toggle": {
                // Determine current state from primary sensor and send the opposite command.
                String state = getPresetSensorState(preset);
                boolean isOn = isPresetStateTruthy(state, preset);
                DashboardPresetRegistry.PresetStateCommands cmds = preset.commands;
                if (cmds == null) return;
                String cmdId = isOn ? cmds.offId : cmds.onId;
                String cmdValue = isOn ? cmds.offValue : cmds.onValue;
                if (cmdId.isEmpty()) return;
                pendingCommandValues.put(cmdId, cmdValue != null ? cmdValue : "");
                sendQuickCommand(cmdId);
                pendingCommandValues.remove(cmdId);
                break;
            }
            case "command": {
                // Send the single action command.
                DashboardPresetRegistry.PresetStateCommands cmds = preset.commands;
                if (cmds == null) return;
                String cmdId = cmds.actionId;
                if (cmdId.isEmpty() && !preset.actions.isEmpty()) {
                    cmdId = preset.actions.get(0).command;
                }
                if (cmdId.isEmpty()) return;
                pendingCommandValues.put(cmdId, "");
                sendQuickCommand(cmdId);
                pendingCommandValues.remove(cmdId);
                break;
            }
            case "dual_action": {
                // Alternate between left and right zone on successive taps.
                String cmdId;
                String cmdValue;
                if (tile.tapRightZone || preset.zones.size() < 2) {
                    DashboardPresetRegistry.PresetZone zone = preset.zones.get(preset.zones.size() - 1);
                    cmdId = zone.command;
                    cmdValue = zone.value;
                    tile.tapRightZone = false;
                } else {
                    DashboardPresetRegistry.PresetZone zone = preset.zones.get(0);
                    cmdId = zone.command;
                    cmdValue = zone.value;
                    tile.tapRightZone = true;
                }
                pendingCommandValues.put(cmdId, cmdValue != null ? cmdValue : "");
                sendQuickCommand(cmdId);
                pendingCommandValues.remove(cmdId);
                break;
            }
            case "dual_action_toggle": {
                if (preset.zones.size() < 2 || preset.commands == null) return;
                TileZone zone = detectTileZone(view, x);
                String cmdId;
                String cmdValue;
                switch (zone) {
                    case LEFT:
                        cmdId = preset.zones.get(0).command;
                        cmdValue = preset.zones.get(0).value;
                        break;
                    case RIGHT:
                        cmdId = preset.zones.get(preset.zones.size() - 1).command;
                        cmdValue = preset.zones.get(preset.zones.size() - 1).value;
                        break;
                    default:
                        String state = getPresetSensorState(preset);
                        boolean isOn = isPresetStateTruthy(state, preset);
                        cmdId = isOn ? preset.commands.offId : preset.commands.onId;
                        cmdValue = isOn ? preset.commands.offValue : preset.commands.onValue;
                        break;
                }
                if (cmdId == null || cmdId.isEmpty()) return;
                pendingCommandValues.put(cmdId, cmdValue != null ? cmdValue : "");
                sendQuickCommand(cmdId);
                pendingCommandValues.remove(cmdId);
                break;
            }
            case "select": {
                // Cycle to the next option.
                if (preset.options.isEmpty()) return;
                String currentValue = getPresetSensorState(preset);
                int idx = -1;
                for (int i = 0; i < preset.options.size(); i++) {
                    if (selectValueMatches(preset.options.get(i).value, currentValue)
                            || selectValueMatches(preset.options.get(i).commandValue, currentValue)) {
                        idx = i;
                        break;
                    }
                }
                if (idx < 0) {
                    // Sensor value not recognised (e.g. unlabeled numeric) — continue
                    // from the last option we sent instead of always jumping to first.
                    Integer last = lastSelectOptionIdx.get(preset.id);
                    idx = last != null ? last : -1;
                }
                int nextIdx = (idx + 1) % preset.options.size();
                lastSelectOptionIdx.put(preset.id, nextIdx);
                DashboardPresetRegistry.PresetOption next = preset.options.get(nextIdx);
                pendingCommandValues.put(next.command, next.commandValue);
                sendQuickCommand(next.command);
                pendingCommandValues.remove(next.command);
                break;
            }
            case "composite": {
                if (preset.actions != null && !preset.actions.isEmpty()) {
                    showPresetActionsDialog(preset);
                }
                break;
            }
            default:
                if (tile.type == DashboardTile.Type.COMMAND) {
                    sendDashboardCommand(tile);
                }
                break;
        }
    }

    private String getPresetSensorState(DashboardPresetRegistry.DashboardPreset preset) {
        if (preset.state == null) return "";
        String sensorKey = preset.state.truthySensor != null ? preset.state.truthySensor
                : preset.state.primarySensor;
        if (sensorKey == null) return "";
        CANDataItem item = CANDataReader.findSignalByKey(sensorKey);
        if (item == null || "---".equals(item.value)) return "";
        return SignalTranslator.translateEnumValue(sensorKey, item.value);
    }

    private boolean isPresetStateTruthy(String state, DashboardPresetRegistry.DashboardPreset preset) {
        return preset.state != null
                && DashboardLogic.isStateTruthy(state, preset.state.truthy, preset.state.truthyMode);
    }

    /** Normalized select-option comparison: case-insensitive, ignores + _ and spaces. */
    private static boolean selectValueMatches(String optionValue, String currentValue) {
        if (optionValue == null || currentValue == null) return false;
        String a = optionValue.toLowerCase(Locale.US).replace("+", "").replace("_", "").replace(" ", "");
        String b = currentValue.toLowerCase(Locale.US).replace("+", "").replace("_", "").replace(" ", "");
        return !a.isEmpty() && a.equals(b);
    }

    private void sendDashboardCommand(DashboardTile tile) {
        CommandRegistry.CommandEntry entry = CommandRegistry.getById(tile.key);
        if (entry == null) return;
        LogBuffer.i("Dashboard", "Sending dashboard command " + tile.key + " value=" + tile.commandValue);
        // Reuse existing quick-command path; parameter is baked into the tile.
        pendingCommandValues.put(tile.key, tile.commandValue);
        sendQuickCommand(tile.key);
        pendingCommandValues.remove(tile.key);
    }

    private void editCommandTileValue(DashboardTile tile) {
        CommandRegistry.CommandEntry entry = CommandRegistry.getById(tile.key);
        if (entry == null || entry.valueType == CommandRegistry.ValueType.NONE) return;
        LogBuffer.i("Dashboard", "Editing tile " + tile.key + " currentValue=" + tile.commandValue);
        showCommandValueDialog(entry, tile.commandValue, value -> {
            tile.commandValue = value;
            tile.setValue(value != null && !value.isEmpty() ? value : "—");
            AppConfig.saveDashboardTiles(this, dashboardAdapter.getTiles());
            dashboardAdapter.notifyDataSetChanged();
        });
    }

    private void showPresetParamsDialog(DashboardPresetRegistry.DashboardPreset preset) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(DashboardPresetRegistry.pick(this, preset.label, preset.labelRu));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(12), dp(16), dp(12));

        final List<EditText> inputs = new ArrayList<>();
        for (DashboardPresetRegistry.PresetParam p : preset.params) {
            TextView label = new TextView(this);
            label.setText(DashboardPresetRegistry.pick(this, p.label, p.labelRu) + (p.unit.isEmpty() ? "" : " (" + p.unit + ")"));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            container.addView(label);

            EditText input = new EditText(this);
            input.setHint(String.format(Locale.US, "%s - %s", formatNumber(p.min), formatNumber(p.max)));
            input.setInputType(InputType.TYPE_CLASS_NUMBER |
                    InputType.TYPE_NUMBER_FLAG_DECIMAL |
                    InputType.TYPE_NUMBER_FLAG_SIGNED);

            CANDataItem item = CANDataReader.findSignalByKey(p.sensor);
            String current = item != null && !"---".equals(item.value) ? item.value : "";
            if (current.isEmpty()) {
                current = formatNumber(p.min);
            }
            input.setText(current);
            inputs.add(input);
            container.addView(input);
        }

        b.setView(container);
        b.setPositiveButton(android.R.string.ok, (d, w) -> {
            for (int i = 0; i < preset.params.size() && i < inputs.size(); i++) {
                DashboardPresetRegistry.PresetParam p = preset.params.get(i);
                String v = inputs.get(i).getText().toString().trim();
                if (v.isEmpty()) continue;
                try {
                    double dval = Double.parseDouble(v.replace(',', '.'));
                    if (dval < p.min) dval = p.min;
                    if (dval > p.max) dval = p.max;
                    v = p.step == (int) p.step ? String.valueOf((int) dval) : String.valueOf(dval);
                } catch (NumberFormatException ignored) {
                }
                pendingCommandValues.put(p.command, v);
                sendQuickCommand(p.command);
                pendingCommandValues.remove(p.command);
            }
        });
        b.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = b.create();
        dialog.setOnDismissListener(d -> resumeDashboardEditTimeout());
        pauseDashboardEditTimeout();
        dialog.show();
    }

    private void showPresetActionsDialog(DashboardPresetRegistry.DashboardPreset preset) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(DashboardPresetRegistry.pick(this, preset.label, preset.labelRu));

        ListView list = new ListView(this);
        List<String> labels = new ArrayList<>();
        for (DashboardPresetRegistry.PresetAction a : preset.actions) {
            labels.add(DashboardPresetRegistry.pick(this, a.label, a.labelRu));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        list.setAdapter(adapter);

        AlertDialog dialog = b.setView(list).setNegativeButton(android.R.string.cancel, null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            DashboardPresetRegistry.PresetAction action = preset.actions.get(position);
            LogBuffer.i("Dashboard", "Preset action " + preset.id + "/" + action.id + " command=" + action.command + " value=" + action.value);
            pendingCommandValues.put(action.command, action.value != null ? action.value : "");
            sendQuickCommand(action.command);
            pendingCommandValues.remove(action.command);
            dialog.dismiss();
        });
        dialog.show();
    }

    private String formatNumber(double value) {
        return DashboardLogic.formatNumber(value);
    }

    private interface CommandValueCallback {
        void onValue(String value);
    }

    private void showCommandValueDialog(CommandRegistry.CommandEntry entry, CommandValueCallback onValueSet) {
        showCommandValueDialog(entry, null, onValueSet, null);
    }

    private void showCommandValueDialog(CommandRegistry.CommandEntry entry, String currentValue,
                                        CommandValueCallback onValueSet) {
        showCommandValueDialog(entry, currentValue, onValueSet, null);
    }

    private void showCommandValueDialog(CommandRegistry.CommandEntry entry, String currentValue,
                                        CommandValueCallback onValueSet, Runnable onCancel) {
        if (entry.valueType == CommandRegistry.ValueType.NONE) {
            onValueSet.onValue(null);
            return;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.dashboard_config_value_title);

        if (entry.valueType == CommandRegistry.ValueType.ENUM) {
            final List<String> enumKeys = new ArrayList<>(entry.enumValues.keySet());
            final String[] labels = new String[enumKeys.size()];
            for (int i = 0; i < enumKeys.size(); i++) {
                String id = enumKeys.get(i);
                int resId = enumValueResId(this, id);
                labels[i] = resId != 0 ? getString(resId) : id;
            }
            int checked = -1;
            if (currentValue != null) {
                for (int i = 0; i < enumKeys.size(); i++) {
                    if (currentValue.equals(enumKeys.get(i))) {
                        checked = i;
                        break;
                    }
                }
            }
            final int[] selected = {checked >= 0 ? checked : 0};
            b.setSingleChoiceItems(labels, checked, (d, which) -> selected[0] = which);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                if (selected[0] >= 0 && selected[0] < enumKeys.size()) {
                    onValueSet.onValue(enumKeys.get(selected[0]));
                } else {
                    onValueSet.onValue(null);
                }
            });
        } else {
            final EditText input = new EditText(this);
            input.setPadding(dp(16), dp(12), dp(16), dp(12));
            if (entry.valueHintResId != 0) input.setHint(entry.valueHintResId);
            if (currentValue != null) input.setText(currentValue);

            if (entry.valueType == CommandRegistry.ValueType.RANGE ||
                    entry.valueType == CommandRegistry.ValueType.NUMBER) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
            if (entry.valueType == CommandRegistry.ValueType.RANGE && TextUtils.isEmpty(currentValue)) {
                input.setText(String.valueOf((int) entry.minValue));
            }
            b.setView(input);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                String v = input.getText().toString().trim();
                onValueSet.onValue(v.isEmpty() ? null : v);
            });
        }

        b.setNegativeButton(android.R.string.cancel, (d, w) -> {
            if (onCancel != null) onCancel.run();
        });
        b.setOnCancelListener(d -> {
            if (onCancel != null) onCancel.run();
        });
        AlertDialog dialog = b.create();
        dialog.setOnDismissListener(d -> resumeDashboardEditTimeout());
        pauseDashboardEditTimeout();
        dialog.show();
    }

    private int dp(float px) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, px, getResources().getDisplayMetrics());
    }

    private void loadDashboardTiles() {
        dashboardTiles.clear();
        List<DashboardTile> saved = AppConfig.loadDashboardTiles(this);
        if (saved != null && !saved.isEmpty()) {
            dashboardTiles.addAll(saved);
        } else {
            dashboardTiles.addAll(DashboardTileFactory.defaultTiles(this));
        }
    }

    private void updateDashboard(List<CANDataItem> items) {
        try {
            if (dashUpdateTime != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
                dashUpdateTime.setText(sdf.format(new Date(lastRefresh)));
            }

            Map<String, CANDataItem> byKey = new LinkedHashMap<>();
            for (CANDataItem item : items) {
                if (item.key != null && !item.key.isEmpty()) {
                    byKey.put(item.key, item);
                }
            }

            DashboardPresetRegistry presetRegistry = DashboardPresetRegistry.getInstance(this);

            for (DashboardTile tile : dashboardTiles) {
                DashboardPresetRegistry.DashboardPreset preset = null;
                if (tile.type == DashboardTile.Type.PRESET && tile.presetId != null) {
                    preset = presetRegistry.getPreset(tile.presetId);
                }

                if (preset != null) {
                    updatePresetTile(tile, preset, byKey);
                } else if (tile.type == DashboardTile.Type.SENSOR) {
                    // Legacy single-sensor tile.
                    CANDataItem item = byKey.get(tile.key);
                    if (item != null && !"---".equals(item.value)) {
                        tile.setValue(SignalTranslator.translateEnumValue(tile.key, item.value),
                                item.unit != null ? item.unit : "");
                        tile.setSub("");
                        tile.setAlert(false);
                    } else {
                        tile.setValue("—", "");
                        tile.setSub("");
                        tile.setAlert(false);
                    }
                }
            }

            dashboardAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            LogBuffer.e("Main", "updateDashboard failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void updatePresetTile(DashboardTile tile, DashboardPresetRegistry.DashboardPreset preset,
                                   Map<String, CANDataItem> byKey) {
        if (preset.state == null) return;

        // Behavior-driven rendering takes precedence over the state source:
        // toggle/dual_action_toggle show on/off labels or a formatted value,
        // select shows the current mode.
        if ("toggle".equals(preset.behavior) || "dual_action_toggle".equals(preset.behavior)) {
            updateTogglePresetTile(tile, preset, byKey);
            return;
        }
        if ("select".equals(preset.behavior)) {
            updateSelectPresetTile(tile, preset, byKey);
            return;
        }

        switch (preset.state.source) {
            case "single_numeric": {
                CANDataItem item = byKey.get(preset.state.primarySensor);
                if (item != null && !"---".equals(item.value)) {
                    tile.setValue(SignalTranslator.translateValue(item.value), item.unit != null ? item.unit : "");
                    tile.setSub("");
                    tile.setAlert(false);
                } else {
                    tile.setValue("—", "");
                    tile.setSub("");
                    tile.setAlert(false);
                }
                break;
            }
            case "primary_numeric": {
                CANDataItem item = byKey.get(preset.state.primarySensor);
                if (item != null && !"---".equals(item.value)) {
                    double val = parseDoubleSafe(item.value);
                    tile.setValue(SignalTranslator.translateValue(item.value), "%");
                    boolean thresholdAlert = preset.state.thresholdOpValue > 0 && val < preset.state.thresholdOpValue;
                    tile.setAlert(thresholdAlert);
                } else {
                    tile.setValue("—", "");
                    tile.setAlert(false);
                }
                break;
            }
            case "numeric_list": {
                // Display all numeric values from the sensor list.
                StringBuilder sb = new StringBuilder();
                boolean anyValid = false;
                double minVal = Double.MAX_VALUE;
                for (String sk : preset.sensors) {
                    CANDataItem item = byKey.get(sk);
                    if (item != null && !"---".equals(item.value)) {
                        anyValid = true;
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(item.value);
                        double v = parseDoubleSafe(item.value);
                        if (v < minVal) minVal = v;
                    }
                }
                if (anyValid) {
                    tile.setValue(sb.toString(), "");
                    tile.setSub("");
                    boolean thresholdAlert = preset.state.thresholdOpValue > 0 && minVal < preset.state.thresholdOpValue;
                    tile.setAlert(thresholdAlert);
                } else {
                    tile.setValue("—", "");
                    tile.setAlert(false);
                }
                break;
            }
            case "binary_any":
            case "binary_all": {
                boolean any = "binary_any".equals(preset.state.source);
                List<String> openNames = new ArrayList<>();
                boolean triggered = false;
                for (String sk : preset.sensors) {
                    CANDataItem item = byKey.get(sk);
                    if (item == null || "---".equals(item.value)) continue;
                    String translated = SignalTranslator.translateEnumValue(sk, item.value);
                    boolean isTruthy = false;
                    if ("numeric_gt_0".equals(preset.state.truthyMode)) {
                        double v = parseDoubleSafe(item.value);
                        isTruthy = v > 0;
                    } else if ("numeric_eq_0".equals(preset.state.truthyMode)) {
                        double v = parseDoubleSafe(item.value);
                        isTruthy = v == 0;
                    } else {
                        for (String t : preset.state.truthy) {
                            if (t.equalsIgnoreCase(translated)) { isTruthy = true; break; }
                        }
                    }
                    if (isTruthy) {
                        triggered = true;
                        openNames.add(item.name);
                    } else if (!any) {
                        triggered = false;
                        break;
                    }
                }
                boolean isAlert = triggered;
                String label, sub;
                if (triggered) {
                    int openN = openNames.size();
                    int total = preset.sensors.size();
                    label = DashboardPresetRegistry.pick(this, preset.state.display.alertLabel, preset.state.display.alertLabelRu)
                            .replace("{open_n}", String.valueOf(openN))
                            .replace("{total}", String.valueOf(total))
                            .replace("{open_list}", joinStrings(openNames, ", "));
                    String alertSub = DashboardPresetRegistry.pick(this, preset.state.display.alertSub, preset.state.display.alertSubRu);
                    sub = alertSub != null
                            ? alertSub
                                    .replace("{open_n}", String.valueOf(openN))
                                    .replace("{total}", String.valueOf(total))
                                    .replace("{open_list}", joinStrings(openNames, ", "))
                            : "";
                    isAlert = preset.state.display.alertAlert;
                } else {
                    label = DashboardPresetRegistry.pick(this, preset.state.display.okLabel, preset.state.display.okLabelRu);
                    String okSub = DashboardPresetRegistry.pick(this, preset.state.display.okSub, preset.state.display.okSubRu);
                    sub = okSub != null ? okSub : "";
                    isAlert = preset.state.display.okAlert;
                }
                if (!label.isEmpty()) tile.setValue(label, "");
                else tile.setValue(triggered ? getString(R.string.dash_open) : getString(R.string.dash_all_closed), "");
                tile.setSub(sub);
                tile.setAlert(isAlert);
                break;
            }
            case "ha_status": {
                boolean enabled = AppConfig.isHassEnabled(this);
                tile.setValue(enabled ? "ON" : "OFF");
                tile.setAlert(!enabled);
                break;
            }
            default: {
                // Fallback: toggle behavior display based on primary sensor.
                if (preset.state.primarySensor != null && !preset.sensors.isEmpty()) {
                    CANDataItem item = byKey.get(preset.state.primarySensor);
                    if (item != null && !"---".equals(item.value)) {
                        String translated = SignalTranslator.translateEnumValue(preset.state.primarySensor, item.value);
                        boolean isOn = isPresetStateTruthy(translated, preset);
                        String label = isOn ? preset.state.display.onLabel : preset.state.display.offLabel;
                        String sub = isOn ? preset.state.display.onSub : preset.state.display.offSub;
                        boolean alert = isOn ? preset.state.display.onAlert : preset.state.display.offAlert;
                        if (!label.isEmpty()) tile.setValue(label, "");
                        else tile.setValue(translated, "");
                        tile.setSub(sub != null ? sub : "");
                        tile.setAlert(alert);
                    } else {
                        CANDataItem first = byKey.get(preset.sensors.get(0));
                        if (first != null && !"---".equals(first.value)) {
                            tile.setValue(SignalTranslator.translateValue(first.value), first.unit != null ? first.unit : "");
                        } else {
                            tile.setValue("—", "");
                        }
                        tile.setSub("");
                        tile.setAlert(false);
                    }
                } else {
                    // No sensors — command-only tile, show preset label.
                    tile.setValue("", "");
                    tile.setSub("");
                    tile.setAlert(false);
                }
                break;
            }
        }
    }

    /** Render toggle / dual_action_toggle presets: formatted value or on/off labels. */
    private void updateTogglePresetTile(DashboardTile tile, DashboardPresetRegistry.DashboardPreset preset,
                                        Map<String, CANDataItem> byKey) {
        DashboardPresetRegistry.PresetStateDisplay display = preset.state.display;
        String format = DashboardPresetRegistry.pick(this, display.valueFormat, display.valueFormatRu);

        if (preset.state.primarySensor == null) {
            // Stateless toggle (e.g. auto_high_beam) — nothing to display.
            tile.setValue("", "");
            tile.setSub("");
            tile.setAlert(false);
            return;
        }

        CANDataItem item = byKey.get(preset.state.primarySensor);
        if (item == null || "---".equals(item.value)) {
            tile.setValue("—", "");
            tile.setSub("");
            tile.setAlert(false);
            return;
        }

        String translated = SignalTranslator.translateEnumValue(preset.state.primarySensor, item.value);

        // Formatted-value display wins when configured (volume %, fan level, temp °C).
        if (format != null && !format.isEmpty()) {
            tile.setValue(format.replace("{value}", translated), "");
            tile.setSub("");
            tile.setAlert(false);
            return;
        }

        boolean isOn = isPresetStateTruthy(translated, preset);
        String label = isOn
                ? DashboardPresetRegistry.pick(this, display.onLabel, display.onLabelRu)
                : DashboardPresetRegistry.pick(this, display.offLabel, display.offLabelRu);
        String sub = isOn
                ? DashboardPresetRegistry.pick(this, display.onSub, display.onSubRu)
                : DashboardPresetRegistry.pick(this, display.offSub, display.offSubRu);
        boolean alert = isOn ? display.onAlert : display.offAlert;
        if (!label.isEmpty()) tile.setValue(label, "");
        else tile.setValue(translated, "");
        tile.setSub(sub != null ? substituteSensorPlaceholders(sub, byKey) : "");
        tile.setAlert(alert);
    }

    /** Render select presets: the current mode is the tile value. */
    private void updateSelectPresetTile(DashboardTile tile, DashboardPresetRegistry.DashboardPreset preset,
                                        Map<String, CANDataItem> byKey) {
        if (preset.state.primarySensor == null) {
            tile.setValue("—", "");
            tile.setSub("");
            tile.setAlert(false);
            return;
        }
        CANDataItem item = byKey.get(preset.state.primarySensor);
        if (item == null || "---".equals(item.value)) {
            tile.setValue("—", "");
            tile.setSub("");
            tile.setAlert(false);
            return;
        }
        String translated = SignalTranslator.translateEnumValue(preset.state.primarySensor, item.value);
        // Prefer the option label when the current value matches an option.
        for (DashboardPresetRegistry.PresetOption opt : preset.options) {
            if (selectValueMatches(opt.value, translated) || selectValueMatches(opt.commandValue, translated)) {
                translated = DashboardPresetRegistry.pick(this, opt.label, opt.labelRu);
                break;
            }
        }
        tile.setValue(translated, "");
        tile.setSub("");
        tile.setAlert(false);
    }

    /** Replace {sensor_key} placeholders with current translated sensor values. */
    private String substituteSensorPlaceholders(String text, Map<String, CANDataItem> byKey) {
        if (text == null || text.isEmpty()) return "";
        String out = text;
        for (Map.Entry<String, CANDataItem> e : byKey.entrySet()) {
            String placeholder = "{" + e.getKey() + "}";
            if (out.contains(placeholder)) {
                CANDataItem item = e.getValue();
                String v = item != null && !"---".equals(item.value)
                        ? SignalTranslator.translateEnumValue(e.getKey(), item.value) : "—";
                out = out.replace(placeholder, v);
            }
        }
        return out;
    }

    private void updateTile(String key, CANDataItem item, String unit, String sub) {
        DashboardTile tile = dashboardAdapter.findTile(key);
        if (tile == null) return;
        if (item != null && !"---".equals(item.value)) {
            tile.setValue(SignalTranslator.translateValue(item.value), unit);
            tile.setSub(sub);
        } else {
            tile.setValue("—", "");
            tile.setSub("");
        }
    }

    // ─── Commands tab ───

    private final Map<String, List<CommandRegistry.CommandEntry>> commandsByCategory = new LinkedHashMap<>();
    private List<CommandRegistry.CommandEntry> currentCategoryCommands = new ArrayList<>();

    private ScrollView scrollCommandJournal;

    private void setupCommands() {
        spinnerCommandCategory = findViewById(R.id.spinnerCommandCategory);
        spinnerCommand = findViewById(R.id.spinnerCommand);
        spinnerCommandEnum = findViewById(R.id.spinnerCommandEnum);
        editCommandValue = findViewById(R.id.editCommandValue);
        btnSendCommand = findViewById(R.id.btnSendCommand);
        textCommandResult = findViewById(R.id.textCommandResult);
        textCommandJournal = findViewById(R.id.textCommandJournal);
        scrollCommandJournal = findViewById(R.id.scrollCommandJournal);
        btnExpandJournal = findViewById(R.id.btnExpandJournal);

        // Group commands by localized category preserving order.
        for (CommandRegistry.CommandEntry entry : CommandRegistry.getAll()) {
            String category = entry.getCategory(this);
            commandsByCategory
                .computeIfAbsent(category, k -> new ArrayList<>())
                .add(entry);
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, new ArrayList<>(commandsByCategory.keySet()));
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCommandCategory.setAdapter(categoryAdapter);
        spinnerCommandCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String category = (String) spinnerCommandCategory.getSelectedItem();
                currentCategoryCommands = commandsByCategory.get(category);
                updateCommandSpinner();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spinnerCommand.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateValueInput();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnSendCommand.setOnClickListener(v -> sendSelectedCommand());

        btnExpandJournal.setOnClickListener(v -> {
            List<String> lines = CommandLog.getAll(this);
            StringBuilder sb = new StringBuilder();
            for (int i = lines.size() - 1; i >= 0; i--) sb.append(lines.get(i)).append('\n');
            ScrollView sv = new ScrollView(this);
            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTypeface(Typeface.MONOSPACE);
            sv.addView(tv);
            new AlertDialog.Builder(this)
                .setTitle(R.string.commands_journal_title)
                .setView(sv)
                .setPositiveButton(R.string.clear, (d, w) -> { CommandLog.clear(this); refreshCommandJournal(); })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });

        // Initialize first category.
        if (!commandsByCategory.isEmpty()) {
            currentCategoryCommands = commandsByCategory.values().iterator().next();
            updateCommandSpinner();
        }
        refreshCommandJournal();
    }

    private void sendQuickCommand(String commandId) {
        try {
            CommandRegistry.CommandEntry entry = CommandRegistry.getById(commandId);
            if (entry == null) return;
            String pendingValue = pendingCommandValues.get(commandId);
            String value = pendingValue != null ? pendingValue : "";
            CommandRegistry.CommandEntry finalEntry = entry;
            String finalValue = value;
            textCommandResult.setText(getString(R.string.commands_sending,
                finalEntry.getDisplayName(this) + (finalValue.isEmpty() ? "" : "=" + finalValue)));
            textCommandResult.setTextColor(0xFFFFC107);
            new Thread(() -> {
                try {
                    CommandExecutor.Result result = CommandExecutor.execute(
                        MainActivity.this, commandId, finalValue, CommandExecutor.Source.UI);
                    String line = finalEntry.getDisplayName(MainActivity.this)
                        + "=" + finalValue + " -> "
                        + (result.success ? getString(R.string.commands_result_ok_short)
                                          : getString(R.string.commands_result_fail))
                        + " [" + result.verificationMessage + "]"
                        + (result.error != null ? ": " + result.error : "");
                    CommandLog.append(MainActivity.this, line);
                    handler.post(() -> {
                        try {
                            textCommandResult.setText(formatCommandResult(result));
                            textCommandResult.setTextColor(result.success ? 0xFF4CAF50 : 0xFFF44336);
                            refreshCommandJournal();
                        } catch (Exception e) {
                            LogBuffer.e("Main", "Quick command result UI failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    LogBuffer.e("Main", "sendQuickCommand thread failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    handler.post(() -> {
                        textCommandResult.setText(getString(R.string.commands_result_error, e.getMessage()));
                        textCommandResult.setTextColor(0xFFF44336);
                    });
                }
            }).start();
        } catch (Exception e) {
            LogBuffer.e("Main", "sendQuickCommand failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String formatCommandResult(CommandExecutor.Result result) {
        if (!result.success) {
            return getString(R.string.commands_result_error, result.error);
        }
        StringBuilder sb = new StringBuilder(getString(R.string.commands_result_ok, result.elapsedMs));
        sb.append(" [").append(result.verificationMessage).append("]");
        if (result.actualValue != null && result.expectedValue != null) {
            sb.append(" expected=").append(result.expectedValue)
              .append(" actual=").append(result.actualValue);
        }
        return sb.toString();
    }

    private void updateCommandSpinner() {
        if (currentCategoryCommands == null) return;
        CommandEntryAdapter adapter = new CommandEntryAdapter(this, currentCategoryCommands);
        spinnerCommand.setAdapter(adapter);
        updateValueInput();
    }

    private static class CommandEntryAdapter extends android.widget.ArrayAdapter<CommandRegistry.CommandEntry> {
        CommandEntryAdapter(Context context, List<CommandRegistry.CommandEntry> entries) {
            super(context, android.R.layout.simple_spinner_item, entries);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            TextView tv = (TextView) super.getView(position, convertView, parent);
            CommandRegistry.CommandEntry entry = getItem(position);
            tv.setText(entry != null ? entry.getDisplayName(getContext()) : "");
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(14);
            return tv;
        }

        @Override
        public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
            TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
            CommandRegistry.CommandEntry entry = getItem(position);
            tv.setText(entry != null ? entry.getDisplayName(getContext()) : "");
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(14);
            tv.setPadding(16, 12, 16, 12);
            return tv;
        }
    }

    static int enumValueResId(Context ctx, String id) {
        // Map stable enum value ids to string resources such as enum_on, enum_off, etc.
        if (id == null || id.isEmpty()) return 0;
        return ctx.getResources().getIdentifier("enum_" + id, "string", ctx.getPackageName());
    }

    private void updateValueInput() {
        CommandRegistry.CommandEntry entry = (CommandRegistry.CommandEntry) spinnerCommand.getSelectedItem();
        if (entry == null) {
            editCommandValue.setVisibility(View.GONE);
            spinnerCommandEnum.setVisibility(View.GONE);
            return;
        }

        if (entry.valueType == CommandRegistry.ValueType.ENUM) {
            editCommandValue.setVisibility(View.GONE);
            spinnerCommandEnum.setVisibility(View.VISIBLE);
            final List<String> enumIds = new ArrayList<>(entry.enumValues.keySet());
            ArrayAdapter<String> enumAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, enumIds) {
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    TextView tv = (TextView) super.getView(position, convertView, parent);
                    tv.setText(enumName(enumIds.get(position)));
                    return tv;
                }
                @Override
                public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                    TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                    tv.setText(enumName(enumIds.get(position)));
                    return tv;
                }
                private String enumName(String id) {
                    int resId = enumValueResId(MainActivity.this, id);
                    return resId != 0 ? getString(resId) : id;
                }
            };
            enumAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCommandEnum.setAdapter(enumAdapter);
        } else if (entry.valueType == CommandRegistry.ValueType.NONE) {
            editCommandValue.setVisibility(View.GONE);
            spinnerCommandEnum.setVisibility(View.GONE);
        } else {
            editCommandValue.setVisibility(View.VISIBLE);
            spinnerCommandEnum.setVisibility(View.GONE);
            String hint = entry.getValueHint(this);
            if (hint == null || hint.isEmpty()) {
                hint = entry.minValue + "-" + entry.maxValue;
                if (entry.valueType == CommandRegistry.ValueType.RANGE) hint += "%";
            }
            editCommandValue.setHint(hint);
            editCommandValue.setText("");
        }
    }

    private void sendSelectedCommand() {
        try {
            CommandRegistry.CommandEntry entry = (CommandRegistry.CommandEntry) spinnerCommand.getSelectedItem();
            if (entry == null) return;

            String value = "";
            if (entry.valueType == CommandRegistry.ValueType.ENUM) {
                Object selected = spinnerCommandEnum.getSelectedItem();
                value = selected != null ? selected.toString() : "";
            } else if (entry.valueType != CommandRegistry.ValueType.NONE) {
                value = editCommandValue.getText().toString().trim();
            }

            final String commandId = entry.id;
            final String finalValue = value;
            textCommandResult.setText(getString(R.string.commands_sending,
                entry.getDisplayName(this) + (finalValue.isEmpty() ? "" : "=" + finalValue)));
            textCommandResult.setTextColor(0xFFFFC107);

            new Thread(() -> {
                try {
                    CommandExecutor.Result result = CommandExecutor.execute(
                        MainActivity.this, commandId, finalValue, CommandExecutor.Source.UI);
                    String line = entry.getDisplayName(MainActivity.this)
                        + "=" + finalValue + " -> "
                        + (result.success ? getString(R.string.commands_result_ok_short)
                                          : getString(R.string.commands_result_fail))
                        + " [" + result.verificationMessage + "]"
                        + (result.error != null ? ": " + result.error : "");
                    CommandLog.append(MainActivity.this, line);
                    handler.post(() -> {
                        try {
                            textCommandResult.setText(formatCommandResult(result));
                            textCommandResult.setTextColor(result.success ? 0xFF4CAF50 : 0xFFF44336);
                            refreshCommandJournal();
                        } catch (Exception e) {
                            LogBuffer.e("Main", "Command result UI failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    LogBuffer.e("Main", "sendSelectedCommand thread failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    handler.post(() -> {
                        textCommandResult.setText(getString(R.string.commands_result_error, e.getMessage()));
                        textCommandResult.setTextColor(0xFFF44336);
                    });
                }
            }).start();
        } catch (Exception e) {
            LogBuffer.e("Main", "sendSelectedCommand failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // Reverse order so newest entry is on top
    private void refreshCommandJournal() {
        if (textCommandJournal == null) return;
        List<String> lines = CommandLog.getAll(this);
        StringBuilder sb = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            sb.append(lines.get(i)).append('\n');
        }
        textCommandJournal.setText(sb.length() > 0 ? sb.toString() : getString(R.string.commands_journal_empty));
        if (scrollCommandJournal != null) {
            scrollCommandJournal.post(() -> scrollCommandJournal.fullScroll(ScrollView.FOCUS_UP));
        }
    }


    private static final int REQUEST_PERMISSIONS_CODE = 300;

    private void requestAllRuntimePermissions() {
        List<String> needed = new ArrayList<>();
        needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        needed.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);

        // Notification and legacy storage permissions are intentionally not
        // requested on BYD head units: they are either unsupported by the ROM
        // (showing "not supported" errors) or unnecessary on Android 10+.

        // Exclude previously denied/unsupported permissions to avoid
        // "function not supported" system messages on some OEM ROMs.
        Set<String> denied = AppConfig.getDeniedPermissions(this);
        needed.removeAll(denied);

        auditPermissions("Permission audit before request");

        List<String> toRequest = new ArrayList<>();
        for (String perm : needed) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(perm);
            }
        }

        if (!toRequest.isEmpty()) {
            LogBuffer.i("Main", "Requesting " + toRequest.size() + " permission(s): " + toRequest);
            try {
                requestPermissions(toRequest.toArray(new String[0]), REQUEST_PERMISSIONS_CODE);
            } catch (Exception e) {
                LogBuffer.e("Main", "requestPermissions failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } else {
            LogBuffer.i("Main", "All requested permissions already granted");
            requestBackgroundLocationIfNeeded();
        }
        requestIgnoreBatteryOptimizations();
    }

    private void requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (AppConfig.getDeniedPermissions(this).contains(
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            LogBuffer.i("Main", "Background location previously unsupported, skipping");
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            LogBuffer.i("Main", "Requesting background location permission");
            try {
                requestPermissions(
                        new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        REQUEST_PERMISSIONS_CODE);
            } catch (Exception e) {
                LogBuffer.e("Main", "requestBackgroundLocation failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                AppConfig.addDeniedPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        } else {
            LogBuffer.i("Main", "Background location permission already granted");
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                LogBuffer.w("Main", "requestIgnoreBatteryOptimizations: PowerManager is null");
                return;
            }
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                LogBuffer.i("Main", "Opening battery optimization settings for package=" + getPackageName());
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                LogBuffer.i("Main", "Requested ignore battery optimizations");
            } else {
                LogBuffer.i("Main", "Already ignoring battery optimizations");
            }
        } catch (Exception e) {
            LogBuffer.e("Main", "requestIgnoreBatteryOptimizations failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void auditPermissions(String tag) {
        String[] perms = {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        };
        StringBuilder sb = new StringBuilder();
        for (String perm : perms) {
            int status = checkSelfPermission(perm);
            String label;
            if (status == PackageManager.PERMISSION_GRANTED) {
                label = "granted";
            } else if (status == PackageManager.PERMISSION_DENIED) {
                label = "denied";
            } else {
                label = "unknown(" + status + ")";
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(perm.replace("android.permission.", "")).append("=").append(label);
        }
        LogBuffer.i("Main", tag + ": " + sb);
    }

    private void updateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        refreshTimeText.setText(getString(R.string.updated_prefix, sdf.format(new Date(lastRefresh))));
    }

    private void updateLocationText() {
        if (locationText == null || telemetryService == null) return;
        if (telemetryService.hasValidLocation()) {
            String text = String.format(Locale.US, "%.5f, %.5f", telemetryService.getLastLatitude(), telemetryService.getLastLongitude());
            locationText.setText(text);
            locationText.setTextColor(0xFF4CAF50);
        } else {
            locationText.setText(R.string.location_no_gps);
            locationText.setTextColor(0xFFFFC107);
        }
    }

    // ─── Signal enable filter (checkboxes on main screen) ───

    private CompoundButton.OnCheckedChangeListener sendToHaListener;

    private void loadAndApplyEnabledFilter() {
        refreshEnabledFilterState();
        setupEnabledFilterListeners();
    }

    private void refreshEnabledFilterState() {
        pendingDisabledKeys.clear();
        pendingDisabledKeys.addAll(AppConfig.getDisabledSignals(this));
        applyEnabledStateToItems(knownItems);
        updateHeaderCheckAllState();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void applyEnabledStateToItems(List<CANDataItem> items) {
        if (items == null) return;
        for (CANDataItem item : items) {
            if (item.unsupported) {
                item.enabled = false;
            } else {
                item.enabled = !pendingDisabledKeys.contains(item.key);
            }
        }
    }

    private void setupHeaderCheckAll() {
        if (headerCheckAll == null) return;
        headerCheckAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            if (isChecked) {
                pendingDisabledKeys.clear();
            } else {
                pendingDisabledKeys.clear();
                for (CANDataItem item : knownItems) {
                    if (isSignalSelectable(item)) {
                        pendingDisabledKeys.add(item.key);
                    }
                }
            }
            adapter.setAllEnabled(isChecked);
            enabledDirty = true;
            postEnabledSave();
        });
    }

    private void updateHeaderCheckAllState() {
        if (headerCheckAll == null) return;
        boolean anySelectable = false;
        boolean allChecked = true;
        for (CANDataItem item : knownItems) {
            if (!isSignalSelectable(item)) continue;
            anySelectable = true;
            if (pendingDisabledKeys.contains(item.key)) {
                allChecked = false;
                break;
            }
        }
        headerCheckAll.setOnCheckedChangeListener(null);
        headerCheckAll.setChecked(anySelectable && allChecked);
        headerCheckAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            if (isChecked) {
                pendingDisabledKeys.clear();
            } else {
                pendingDisabledKeys.clear();
                for (CANDataItem item : knownItems) {
                    if (isSignalSelectable(item)) {
                        pendingDisabledKeys.add(item.key);
                    }
                }
            }
            adapter.setAllEnabled(isChecked);
            enabledDirty = true;
            postEnabledSave();
        });
    }

    /** Signals that DiPlus reports as unsupported are visible but not selectable for HA. */
    private boolean isSignalSelectable(CANDataItem item) {
        return !CANDataReader.isUnsupportedSignal(this, item.diplusName);
    }

    private void setupColumnSorting() {
        findViewById(R.id.headerId).setOnClickListener(v -> adapter.sortBy(CANDataAdapter.SortColumn.ID));
        findViewById(R.id.headerName).setOnClickListener(v -> adapter.sortBy(CANDataAdapter.SortColumn.NAME));
        findViewById(R.id.headerValue).setOnClickListener(v -> adapter.sortBy(CANDataAdapter.SortColumn.VALUE));
        findViewById(R.id.headerUnit).setOnClickListener(v -> adapter.sortBy(CANDataAdapter.SortColumn.UNIT));
        findViewById(R.id.headerRoute).setOnClickListener(v -> adapter.sortBy(CANDataAdapter.SortColumn.ROUTE));
    }

    private void setupEnabledFilterListeners() {
        // The top checkbox is the master "Send data to HA" toggle, not a
        // select-all for individual signals.
        sendToHaListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && AppConfig.getHassHost(MainActivity.this).trim().isEmpty()) {
                    Toast.makeText(MainActivity.this, R.string.settings_enter_host, Toast.LENGTH_SHORT).show();
                }
                AppConfig.save(getApplicationContext(),
                        AppConfig.getHassHost(MainActivity.this),
                        AppConfig.getHassPort(MainActivity.this),
                        AppConfig.getHassToken(MainActivity.this),
                        AppConfig.getCarName(MainActivity.this),
                        isChecked,
                        AppConfig.isHassHttps(MainActivity.this),
                        AppConfig.isBootAutoStartEnabled(MainActivity.this),
                        AppConfig.isCarControlEnabled(MainActivity.this),
                        AppConfig.isDetailedLogEnabled(MainActivity.this));
                LogBuffer.i("Main", "Master 'Send to HA' set to " + isChecked);
            }
        };
        checkSelectAll.setOnCheckedChangeListener(null);
        checkSelectAll.setChecked(AppConfig.isHassEnabled(this));
        checkSelectAll.setOnCheckedChangeListener(sendToHaListener);

        adapter.setOnEnabledChangeListener((item, enabled) -> {
            if (enabled) {
                pendingDisabledKeys.remove(item.key);
            } else {
                pendingDisabledKeys.add(item.key);
            }
            enabledDirty = true;
            updateHeaderCheckAllState();
            postEnabledSave();
        });
    }

    private void postEnabledSave() {
        handler.removeCallbacks(saveEnabledRunnable);
        handler.postDelayed(saveEnabledRunnable, 5000);
    }

    private void saveEnabledState() {
        if (!enabledDirty) return;
        AppConfig.setDisabledSignals(this, pendingDisabledKeys);
        enabledDirty = false;
        LogBuffer.i("Main", "Saved disabled signals (count=" + pendingDisabledKeys.size() + ")");
    }

    private void showAboutIfFirstRun() {
        SharedPreferences sp = getSharedPreferences("app_first_run", Context.MODE_PRIVATE);
        if (!sp.getBoolean("about_shown", false)) {
            sp.edit().putBoolean("about_shown", true).apply();
            startActivity(new Intent(this, AboutActivity.class));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_CODE && grantResults != null) {
            StringBuilder sb = new StringBuilder(getString(R.string.permission_runtime_prefix)).append(" ");
            for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
                String result = grantResults[i] == PackageManager.PERMISSION_GRANTED
                        ? getString(R.string.permission_granted)
                        : getString(R.string.permission_denied);
                sb.append(permissions[i]).append("=").append(result).append(" ");
                LogBuffer.i("Main", "Permission " + permissions[i] + ": " + result);
            }
            Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();

            // Re-verify every requested permission — some OEM ROMs deny silently
            // or show "not supported" even when the user tapped "Allow".
            for (String perm : permissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    LogBuffer.w("Main", "Permission " + perm + " not available after request (unsupported on this device)");
                    AppConfig.addDeniedPermission(this, perm);
                }
            }

            auditPermissions("Permission audit after result");
            requestBackgroundLocationIfNeeded();
            return;
        }
    }


    // ─── Settings tab ───

    private void setupSettings() {
        editHost = settingsView.findViewById(R.id.editHost);
        editPort = settingsView.findViewById(R.id.editPort);
        editToken = settingsView.findViewById(R.id.editToken);
        editCarName = settingsView.findViewById(R.id.editCarName);
        switchEnabled = settingsView.findViewById(R.id.switchEnabled);
        switchHttps = settingsView.findViewById(R.id.switchHttps);
        switchBootAutoStart = settingsView.findViewById(R.id.switchBootAutoStart);
        switchCarControl = settingsView.findViewById(R.id.switchCarControl);
        switchDetailedLog = settingsView.findViewById(R.id.switchDetailedLog);
        switchBackgroundMode = settingsView.findViewById(R.id.switchBackgroundMode);
        switchQueueEnabled = settingsView.findViewById(R.id.switchQueueEnabled);
        editQueueMaxMb = settingsView.findViewById(R.id.editQueueMaxMb);
        editQueueMaxDays = settingsView.findViewById(R.id.editQueueMaxDays);
        editAdbHost = settingsView.findViewById(R.id.editAdbHost);
        editAdbPort = settingsView.findViewById(R.id.editAdbPort);
        editDiplusAuth = settingsView.findViewById(R.id.editDiplusAuth);
        tvTestResult = settingsView.findViewById(R.id.tvTestResult);
        tvPresetVersion = settingsView.findViewById(R.id.tvPresetVersion);
        TextView tvHttpWarning = settingsView.findViewById(R.id.tvHttpWarning);

        loadConfig();

        if (tvHttpWarning != null) {
            tvHttpWarning.setVisibility(switchHttps.isChecked() ? View.GONE : View.VISIBLE);
        }

        attachAutoSaveListeners(tvHttpWarning);

        settingsView.findViewById(R.id.btnTest).setOnClickListener(v -> testConnection());
        settingsView.findViewById(R.id.btnExportConfig).setOnClickListener(v -> exportConfig());
        settingsView.findViewById(R.id.btnImportConfig).setOnClickListener(v -> importConfig());
        settingsView.findViewById(R.id.btnOpenAutoStart).setOnClickListener(v -> openAutoStartSettings(this));
        settingsView.findViewById(R.id.btnOpenRapidMode).setOnClickListener(v -> openRapidModeSettings(this));
        settingsView.findViewById(R.id.btnApplyBackgroundMode).setOnClickListener(v -> applyBackgroundMode());
        settingsView.findViewById(R.id.btnLanguage).setOnClickListener(v -> showLanguageDialog());
        settingsView.findViewById(R.id.btnGeofences).setOnClickListener(v ->
                startActivity(new Intent(this, GeofenceListActivity.class)));
        settingsView.findViewById(R.id.btnRefreshPresets).setOnClickListener(v -> refreshPresets());
        updatePresetVersionLabel();

        settingsView.findViewById(R.id.btnTokenHelp).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.settings_token_help_title)
                .setMessage(R.string.settings_token_help_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        );
    }

    private void refreshPresets() {
        DashboardPresetRegistry registry = DashboardPresetRegistry.getInstance(this);
        registry.checkForUpdates(true, (result, version) -> {
            updatePresetVersionLabel();
            if (result == DashboardPresetRegistry.UPDATE_UPDATED) {
                Toast.makeText(this, R.string.settings_presets_updated, Toast.LENGTH_SHORT).show();
            } else if (result == DashboardPresetRegistry.UPDATE_ALREADY_LATEST) {
                Toast.makeText(this, R.string.settings_presets_latest, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.settings_presets_failed, Toast.LENGTH_SHORT).show();
            }
            // Reload presets and rebuild dashboard tiles from the refreshed registry.
            registry.load();
            loadDashboardTiles();
            if (dashboardAdapter != null) {
                dashboardAdapter.setTiles(dashboardTiles);
            }
        });
    }

    private void updatePresetVersionLabel() {
        if (tvPresetVersion == null) return;
        int version = DashboardPresetRegistry.getInstance(this).getLoadedVersion();
        tvPresetVersion.setText(getString(R.string.settings_preset_version, version));
    }

    private final TextWatcher autoSaveWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { scheduleSave(); }
    };

    private void attachAutoSaveListeners(TextView tvHttpWarning) {
        editHost.addTextChangedListener(autoSaveWatcher);
        editPort.addTextChangedListener(autoSaveWatcher);
        editToken.addTextChangedListener(autoSaveWatcher);
        editCarName.addTextChangedListener(autoSaveWatcher);
        editHost.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) warnIfHostEmpty();
        });

        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            if (buttonView == switchEnabled && isChecked) {
                warnIfHostEmpty();
            }
            if (buttonView == switchHttps && tvHttpWarning != null) {
                tvHttpWarning.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            }
            scheduleSave();
        };
        switchEnabled.setOnCheckedChangeListener(listener);
        switchHttps.setOnCheckedChangeListener(listener);
        switchBootAutoStart.setOnCheckedChangeListener(listener);
        switchCarControl.setOnCheckedChangeListener(listener);
        switchDetailedLog.setOnCheckedChangeListener(listener);
        switchBackgroundMode.setOnCheckedChangeListener(listener);
        switchQueueEnabled.setOnCheckedChangeListener(listener);
        editQueueMaxMb.addTextChangedListener(autoSaveWatcher);
        editQueueMaxDays.addTextChangedListener(autoSaveWatcher);
        editAdbHost.addTextChangedListener(autoSaveWatcher);
        editAdbPort.addTextChangedListener(autoSaveWatcher);
        editDiplusAuth.addTextChangedListener(autoSaveWatcher);
    }

    private void warnIfHostEmpty() {
        if (editHost != null && editHost.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.settings_enter_host, Toast.LENGTH_SHORT).show();
        }
    }

    private void scheduleSave() {
        if (saveRunnable != null) saveHandler.removeCallbacks(saveRunnable);
        saveRunnable = this::doSave;
        saveHandler.postDelayed(saveRunnable, 1500);
    }

    private void doSave() {
        String host = editHost.getText().toString().trim();
        String portStr = editPort.getText().toString().trim();
        int port;
        if (portStr.isEmpty()) {
            port = switchHttps.isChecked() ? 443 : 8123;
        } else {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                port = switchHttps.isChecked() ? 443 : 8123;
            }
        }
        String token = editToken.getText().toString().trim();
        String carName = editCarName.getText().toString().trim();
        if (carName.isEmpty() && CANDataReader.sVin != null && !CANDataReader.sVin.isEmpty() && !"---".equals(CANDataReader.sVin)) {
            carName = CANDataReader.sVin;
            editCarName.removeTextChangedListener(autoSaveWatcher);
            editCarName.setText(carName);
            editCarName.addTextChangedListener(autoSaveWatcher);
        }
        boolean enabled = switchEnabled.isChecked();
        boolean https = switchHttps.isChecked();
        boolean bootAutoStart = switchBootAutoStart.isChecked();
        boolean carControl = switchCarControl.isChecked();
        boolean detailedLog = switchDetailedLog.isChecked();
        boolean backgroundMode = switchBackgroundMode.isChecked();
        boolean queueEnabled = switchQueueEnabled.isChecked();
        int queueMaxMb;
        try {
            queueMaxMb = Integer.parseInt(editQueueMaxMb.getText().toString().trim());
        } catch (Exception e) {
            queueMaxMb = 100;
        }
        int queueMaxDays;
        try {
            queueMaxDays = Integer.parseInt(editQueueMaxDays.getText().toString().trim());
        } catch (Exception e) {
            queueMaxDays = 7;
        }

        AppConfig.save(this, host, port, token, carName, enabled, https, bootAutoStart, carControl, detailedLog);
        BackgroundModeManager.setEnabled(this, backgroundMode);
        AppConfig.saveQueueEnabled(this, queueEnabled);
        AppConfig.saveQueueMaxMb(this, queueMaxMb);
        AppConfig.saveQueueMaxDays(this, queueMaxDays);

        String adbHost = editAdbHost.getText().toString().trim();
        if (adbHost.isEmpty()) {
            adbHost = "127.0.0.1";
        }
        int adbPort;
        try {
            adbPort = Integer.parseInt(editAdbPort.getText().toString().trim());
        } catch (Exception e) {
            adbPort = 5555;
        }
        AppConfig.saveAdbHost(this, adbHost);
        AppConfig.saveAdbPort(this, adbPort);
        AppConfig.setDiplusAuth(this, editDiplusAuth.getText().toString());

        if (haSettingsChanged(host, port, token, enabled, https, carControl)) {
            restartTelemetryService();
        }

        savedHost = host;
        savedPort = port;
        savedToken = token;
        savedEnabled = enabled;
        savedHttps = https;
        savedCarControl = carControl;
    }

    private boolean haSettingsChanged(String host, int port, String token,
                                       boolean enabled, boolean https, boolean carControl) {
        return !host.equals(savedHost) || port != savedPort || !token.equals(savedToken)
                || enabled != savedEnabled || https != savedHttps || carControl != savedCarControl;
    }

    private void restartTelemetryService() {
        TelemetryService.stop(this);
        TelemetryService.start(this);
    }

    private void applyBackgroundMode() {
        BackgroundModeManager.applyMode(this, (success, message) -> runOnUiThread(() -> {
            if (success) {
                Toast.makeText(this, R.string.background_mode_applied, Toast.LENGTH_SHORT).show();
                LogBuffer.i("Main", "Background mode applied: " + message);
            } else {
                LogBuffer.w("Main", "Background mode failed: " + message);
                showManualAdbDialog();
            }
        }));
    }

    private void showManualAdbDialog() {
        String[] commands = BackgroundModeManager.getManualCommands(this);
        StringBuilder sb = new StringBuilder(getString(R.string.background_mode_failed))
                .append("\n\n")
                .append(getString(R.string.background_mode_manual_intro))
                .append("\n\n");
        for (String cmd : commands) {
            sb.append(cmd).append("\n");
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_background_mode)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.background_mode_copied, (d, w) -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("ADB", sb.toString()));
                    Toast.makeText(this, R.string.background_mode_copied, Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void loadConfig() {
        savedHost = AppConfig.getHassHost(this);
        savedPort = AppConfig.getHassPort(this);
        savedToken = AppConfig.getHassToken(this);
        savedEnabled = AppConfig.isHassEnabled(this);
        savedHttps = AppConfig.isHassHttps(this);
        savedCarControl = AppConfig.isCarControlEnabled(this);

        editHost.setText(savedHost);
        editPort.setText(String.valueOf(savedPort));
        editToken.setText(savedToken);
        editCarName.setText(AppConfig.getCarName(this));
        switchEnabled.setChecked(savedEnabled);
        switchHttps.setChecked(savedHttps);
        switchBootAutoStart.setChecked(AppConfig.isBootAutoStartEnabled(this));
        switchCarControl.setChecked(savedCarControl);
        switchDetailedLog.setChecked(AppConfig.isDetailedLogEnabled(this));
        switchBackgroundMode.setChecked(BackgroundModeManager.isEnabled(this));
        switchQueueEnabled.setChecked(AppConfig.isQueueEnabled(this));
        editQueueMaxMb.setText(String.valueOf(AppConfig.getQueueMaxMb(this)));
        editQueueMaxDays.setText(String.valueOf(AppConfig.getQueueMaxDays(this)));
        editAdbHost.setText(AppConfig.getAdbHost(this));
        editAdbPort.setText(String.valueOf(AppConfig.getAdbPort(this)));
        editDiplusAuth.setText(AppConfig.getDiplusAuth(this));
    }



    private String getBaseUrl() {
        String host = editHost.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(editPort.getText().toString().trim());
        } catch (Exception e) {
            port = 8123;
        }
        String scheme = switchHttps.isChecked() ? "https" : "http";
        return scheme + "://" + host + ":" + port;
    }

    private void testConnection() {
        String token = editToken.getText().toString().trim();
        String baseUrl = getBaseUrl();

        if (editHost.getText().toString().trim().isEmpty() || token.isEmpty()) {
            tvTestResult.setTextColor(0xFFFFC107);
            tvTestResult.setText(R.string.settings_test_fill_host_token);
            return;
        }

        tvTestResult.setText(R.string.settings_test_testing);
        tvTestResult.setTextColor(0xFFFFC107);

        final String finalBaseUrl = baseUrl;
        new Thread(() -> {
            try {
                URL url = new URL(finalBaseUrl + "/api/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 200) {
                        tvTestResult.setTextColor(0xFF4CAF50);
                        tvTestResult.setText(getString(R.string.settings_test_ok, code));
                        testVehicleEndpoint(finalBaseUrl, token);
                    } else {
                        tvTestResult.setTextColor(0xFFF44336);
                        tvTestResult.setText(getString(R.string.settings_test_error, "HTTP " + code));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvTestResult.setTextColor(0xFFF44336);
                    tvTestResult.setText(getString(R.string.settings_test_error, e.getMessage()));
                });
            }
        }).start();
    }

    private void testVehicleEndpoint(String baseUrl, String token) {
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/api/byd_diplus");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String testJson = "{\"car_name\":\"test\",\"vvn\":\"test\",\"batch\":[]}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(testJson.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> {
                    if (code == 200 || code == 201) {
                        tvTestResult.setTextColor(0xFF4CAF50);
                        tvTestResult.setText(getString(R.string.settings_test_api_ok, code));
                    } else if (code == 404) {
                        tvTestResult.setTextColor(0xFFFFC107);
                        tvTestResult.setText(R.string.settings_test_not_found);
                    } else {
                        tvTestResult.setTextColor(0xFFFFC107);
                        tvTestResult.setText(getString(R.string.settings_test_endpoint_http, code));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvTestResult.setTextColor(0xFFFFC107);
                    tvTestResult.setText(getString(R.string.settings_test_api_error, e.getMessage()));
                });
            }
        }).start();
    }

    private JSONObject buildConfigJson() throws Exception {
        JSONObject cfg = new JSONObject();
        cfg.put("hass_host", AppConfig.getHassHost(this));
        cfg.put("hass_port", AppConfig.getHassPort(this));
        cfg.put("hass_token", AppConfig.getHassToken(this));
        cfg.put("car_name", AppConfig.getCarName(this));
        cfg.put("hass_https", AppConfig.isHassHttps(this));
        cfg.put("hass_enabled", AppConfig.isHassEnabled(this));
        cfg.put("boot_auto_start", AppConfig.isBootAutoStartEnabled(this));
        cfg.put("car_control_enabled", AppConfig.isCarControlEnabled(this));
        cfg.put("detailed_log_enabled", AppConfig.isDetailedLogEnabled(this));
        cfg.put("queue_enabled", AppConfig.isQueueEnabled(this));
        cfg.put("queue_max_mb", AppConfig.getQueueMaxMb(this));
        cfg.put("queue_max_days", AppConfig.getQueueMaxDays(this));
        cfg.put("disabled_signals", new org.json.JSONArray(AppConfig.getDisabledSignals(this)));
        org.json.JSONArray rulesArr = new org.json.JSONArray();
        for (Rule r : RuleRegistry.load(this)) rulesArr.put(r.toJson());
        cfg.put("rules", rulesArr);
        org.json.JSONArray geoArr = new org.json.JSONArray();
        for (GeofenceZone z : AppConfig.loadGeofences(this)) geoArr.put(z.toJson());
        cfg.put("geofences", geoArr);
        return cfg;
    }

    private void applyConfigJson(JSONObject cfg) {
        String host = cfg.optString("hass_host", "").trim();
        int port = cfg.optInt("hass_port", 8123);
        String token = cfg.optString("hass_token", "").trim();
        String carName = cfg.optString("car_name", "").trim();
        boolean https = cfg.optBoolean("hass_https", false);
        boolean enabled = cfg.optBoolean("hass_enabled", false);
        boolean bootAutoStart = cfg.optBoolean("boot_auto_start", true);
        boolean carControl = cfg.optBoolean("car_control_enabled", false);
        boolean detailedLog = cfg.optBoolean("detailed_log_enabled", false);

        AppConfig.save(this, host, port, token, carName, enabled, https, bootAutoStart, carControl, detailedLog);
        AppConfig.saveQueueEnabled(this, cfg.optBoolean("queue_enabled", true));
        AppConfig.saveQueueMaxMb(this, cfg.optInt("queue_max_mb", 100));
        AppConfig.saveQueueMaxDays(this, cfg.optInt("queue_max_days", 7));

        Set<String> disabledSignals = new HashSet<>();
        org.json.JSONArray disabledArr = cfg.optJSONArray("disabled_signals");
        if (disabledArr != null) {
            for (int i = 0; i < disabledArr.length(); i++) {
                String s = disabledArr.optString(i, null);
                if (s != null && !s.isEmpty()) disabledSignals.add(s);
            }
        } else {
            // Backward compatibility: convert v1.7 enabled-signals list.
            boolean useEnabledFilter = cfg.optBoolean("use_enabled_filter", false);
            org.json.JSONArray signalsArr = cfg.optJSONArray("enabled_signals");
            if (useEnabledFilter && signalsArr != null) {
                Set<String> enabledSignals = new HashSet<>();
                for (int i = 0; i < signalsArr.length(); i++) {
                    String s = signalsArr.optString(i, null);
                    if (s != null && !s.isEmpty()) enabledSignals.add(s);
                }
                for (String[] sig : CANDataReader.SIGNAL_REGISTRY) {
                    String k = sig[2];
                    if (!enabledSignals.contains(k)) disabledSignals.add(k);
                }
            }
        }
        AppConfig.setDisabledSignals(this, disabledSignals);

        org.json.JSONArray rulesArr = cfg.optJSONArray("rules");
        if (rulesArr != null) {
            List<Rule> importedRules = new ArrayList<>();
            for (int i = 0; i < rulesArr.length(); i++) {
                org.json.JSONObject obj = rulesArr.optJSONObject(i);
                if (obj != null) importedRules.add(Rule.fromJson(obj));
            }
            RuleRegistry.save(this, importedRules);
        }

        org.json.JSONArray geoArr = cfg.optJSONArray("geofences");
        if (geoArr != null) {
            List<GeofenceZone> zones = new ArrayList<>();
            for (int i = 0; i < geoArr.length(); i++) {
                org.json.JSONObject obj = geoArr.optJSONObject(i);
                if (obj != null) zones.add(GeofenceZone.fromJson(obj));
            }
            AppConfig.saveGeofences(this, zones);
        }
    }

    private void showLanguageDialog() {
        final String[] languages = {LocaleHelper.LANG_EN, LocaleHelper.LANG_RU};
        String[] labels = {getString(R.string.settings_language_en), getString(R.string.settings_language_ru)};
        String current = LocaleHelper.getLanguage(this);
        int checked = 0;
        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                dialog.dismiss();
                LocaleHelper.setLocale(this, languages[which]);
                recreate();
            })
            .setNegativeButton(R.string.menu_cancel, null)
            .show();
    }

    private void exportConfig() {
        if (!ensureStoragePermission()) return;
        List<ConfigStorageHelper.Folder> folders = ConfigStorageHelper.getFolders(this);
        String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) labels[i] = folders.get(i).label;

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_export_to)
                .setItems(labels, (dialog, which) -> doExport(folders.get(which)))
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void doExport(ConfigStorageHelper.Folder folder) {
        new Thread(() -> {
            try {
                ConfigStorageHelper.ConfigRef ref = ConfigStorageHelper.writeConfig(this, folder, buildConfigJson());
                final String message = ref.isUri()
                        ? getString(R.string.settings_exported_download)
                        : getString(R.string.settings_exported, ref.file.getAbsolutePath());
                runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                LogBuffer.e("Main", "Export config failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.settings_export_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void importConfig() {
        if (!ensureStoragePermission()) return;
        List<ConfigStorageHelper.Folder> folders = ConfigStorageHelper.getFolders(this);
        String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) labels[i] = folders.get(i).label;

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_import_from)
                .setItems(labels, (dialog, which) -> showImportFilePicker(folders.get(which)))
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private void showImportFilePicker(ConfigStorageHelper.Folder folder) {
        new Thread(() -> {
            List<ConfigStorageHelper.ConfigRef> refs = ConfigStorageHelper.listConfigRefs(this, folder);
            if (refs.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.settings_import_failed,
                                ConfigStorageHelper.fileNamePattern() + " in " + folder.label), Toast.LENGTH_LONG).show());
                return;
            }
            final String[] names = new String[refs.size()];
            for (int i = 0; i < refs.size(); i++) names[i] = refs.get(i).name;
            final ConfigStorageHelper.ConfigRef[] refArray = refs.toArray(new ConfigStorageHelper.ConfigRef[0]);

            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.settings_select_config)
                        .setItems(names, (dialog, which) -> doImport(refArray[which]))
                        .setNegativeButton(R.string.menu_cancel, null)
                        .show();
            });
        }).start();
    }

    private void doImport(ConfigStorageHelper.ConfigRef ref) {
        new Thread(() -> {
            try {
                String text = ConfigStorageHelper.readConfigRef(this, ref);
                JSONObject cfg = new JSONObject(text);
                applyConfigJson(cfg);
                runOnUiThread(() -> {
                    loadConfig();
                    setupRules();
                    notifyRuleEngineChanged();
                    Toast.makeText(this, getString(R.string.settings_imported, ref.name), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                LogBuffer.e("Main", "Import config failed: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.settings_import_failed, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private boolean ensureStoragePermission() {
        // Storage permissions are intentionally not requested on BYD head units:
        // they trigger unsupported-permission system messages and are unnecessary
        // for log/config operations that use app-private storage or MediaStore.
        return true;
    }

    private static String joinStrings(List<String> list, String separator) {
        return DashboardLogic.joinStrings(list, separator);
    }

    private static double parseDoubleSafe(String s) {
        return DashboardLogic.parseDoubleSafe(s);
    }

    /**
     * Open the BYD system settings for application auto-start.
     *
     * <p>Verified working on BYD DiLink. The correct component is
     * {@code com.byd.appstartmanagement/com.byd.appstartmanagement.frame.AppStartManagement}
     * with action MAIN. Do not change these values unless a new firmware variant
     * is confirmed to use a different component.</p>
     */
    public static void openAutoStartSettings(Context ctx) {
        if (tryStartComponent(ctx, "com.byd.appstartmanagement", "com.byd.appstartmanagement.frame.AppStartManagement")) {
            LogBuffer.i("Main", ctx.getString(R.string.auto_start_opened));
            return;
        }

        // Generic fallbacks for other devices/ROMs.
        String[][] candidates = {
            {"com.android.settings", "com.android.settings.applications.ManageApplications"},
            {"com.android.settings", "com.android.settings.Settings$AppsDashboardActivity"},
            {"com.android.settings", "com.android.settings.Settings$AppAndNotificationDashboardActivity"},
            {"com.android.settings", "com.android.settings.applications.AppInfoDashboardFragment"},
            {"com.android.settings", "com.android.settings.applications.AppOpsDetails"},
        };

        for (String[] c : candidates) {
            if (tryStartComponent(ctx, c[0], c[1])) {
                LogBuffer.i("Main", ctx.getString(R.string.auto_start_app_manager, c[0], c[1]));
                return;
            }
        }

        // Fallback: battery optimization request.
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            LogBuffer.i("Main", ctx.getString(R.string.auto_start_battery));
            return;
        } catch (Exception e) {
            LogBuffer.d("Main", "Battery optimization intent failed: " + e.getMessage());
        }

        // Final fallback: our own app details.
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            LogBuffer.i("Main", ctx.getString(R.string.auto_start_app_details));
        } catch (Exception e) {
            LogBuffer.e("Main", "Cannot open app details: " + e.getMessage());
            Toast.makeText(ctx, ctx.getString(R.string.toast_auto_start_failed, e.getMessage()),
                Toast.LENGTH_LONG).show();
        }
    }

    private static boolean tryStartComponent(Context ctx, String pkg, String cls) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(pkg, cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            LogBuffer.d("Main", "Cannot open " + pkg + "/" + cls + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Open the BYD turbo-mode whitelist settings (background execution whitelist).
     *
     * <p>Verified working on BYD DiLink. Component:
     * {@code com.byd.rapidmode/com.byd.rapidmode.RapidModeActivity}.
     * Keep this unchanged unless a different firmware variant is confirmed.</p>
     */
    public static void openRapidModeSettings(Context ctx) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new android.content.ComponentName(
                "com.byd.rapidmode", "com.byd.rapidmode.RapidModeActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            LogBuffer.i("Main", ctx.getString(R.string.rapid_mode_launched));
        } catch (Exception e) {
            LogBuffer.e("Main", "Cannot launch rapidmode: " + e.getMessage());
            Toast.makeText(ctx, ctx.getString(R.string.rapid_mode_toast_failed, e.getMessage()),
                Toast.LENGTH_LONG).show();
        }
    }

}
