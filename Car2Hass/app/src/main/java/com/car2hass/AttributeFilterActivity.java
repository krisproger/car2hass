package com.car2hass;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AttributeFilterActivity extends BaseLocalizedActivity {

    private List<SignalEntry> entries;
    private SignalAdapter adapter;
    private CheckBox checkSelectAll;
    private Set<String> disabledKeys;

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attribute_filter);

        checkSelectAll = findViewById(R.id.checkSelectAll);
        ListView listView = findViewById(R.id.listViewSignals);
        TextView tvCount = findViewById(R.id.tvFilterCount);

        disabledKeys = new HashSet<>(AppConfig.getDisabledSignals(this));

        entries = new ArrayList<>();
        for (String[] sig : CANDataReader.SIGNAL_REGISTRY) {
            String key = sig[2];
            String name = sig[1];
            String diplusName = sig[0];
            boolean unsupported = CANDataReader.isUnsupportedSignal(this, diplusName);
            boolean checked = !unsupported && !disabledKeys.contains(key);
            if (unsupported) {
                disabledKeys.add(key);
            }
            entries.add(new SignalEntry(key, name, checked, unsupported));
        }
        // Persist the implicit disabled state for currently unsupported signals.
        AppConfig.setDisabledSignals(this, disabledKeys);

        // Group by signal category (headers), preserving the taxonomy order.
        java.util.Map<String, String> categoryOf = new java.util.HashMap<>();
        com.car2hass.vehicle.RegistryStore reg = null;
        try { reg = com.car2hass.vehicle.RegistryStore.load(this); } catch (Exception ignored) {}
        for (SignalEntry e : entries) {
            try { categoryOf.put(e.key, reg != null ? reg.category(e.key) : "other"); }
            catch (Exception ex) { categoryOf.put(e.key, "other"); }
        }
        List<String> order = java.util.Arrays.asList(
                "drive", "battery", "climate", "access", "lights", "tyres",
                "safety", "sentry", "media", "location", "device", "phone", "other");
        List<SignalEntry> grouped = new ArrayList<>();
        for (String cat : order) {
            boolean header = false;
            for (SignalEntry e : entries) {
                if (!cat.equals(categoryOf.get(e.key))) continue;
                if (!header) { grouped.add(SignalEntry.header(cat)); header = true; }
                grouped.add(e);
            }
        }
        entries = grouped;

        adapter = new SignalAdapter();
        listView.setAdapter(adapter);

        updateSelectAllCheckbox();
        updateCountText(tvCount);

        adapter.setOnCheckedChangeListener(() -> {
            updateSelectAllCheckbox();
            updateCountText(tvCount);
        });

        findViewById(R.id.headerName).setOnClickListener(v -> {
            Collections.sort(entries, (a, b) -> a.name.compareToIgnoreCase(b.name));
            adapter.notifyDataSetChanged();
        });
        findViewById(R.id.headerKey).setOnClickListener(v -> {
            Collections.sort(entries, (a, b) -> a.key.compareToIgnoreCase(b.key));
            adapter.notifyDataSetChanged();
        });

        findViewById(R.id.btnSaveFilter).setOnClickListener(v -> saveFilter());
    }

    private void updateSelectAllCheckbox() {
        boolean allChecked = true;
        boolean anyChecked = false;
        boolean hasSelectable = false;
        for (SignalEntry e : entries) {
            if (e.unsupported || e.isHeader) continue;
            hasSelectable = true;
            if (e.checked) anyChecked = true;
            else allChecked = false;
        }
        if (!hasSelectable) {
            allChecked = false;
            anyChecked = false;
        }
        checkSelectAll.setOnCheckedChangeListener(null);
        if (allChecked) {
            checkSelectAll.setChecked(true);
            checkSelectAll.setText(R.string.filter_deselect_all);
        } else {
            checkSelectAll.setChecked(anyChecked);
            checkSelectAll.setText(R.string.filter_select_all);
        }
        checkSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            checkSelectAll.setOnCheckedChangeListener(null);
            for (SignalEntry e : entries) {
                if (e.unsupported || e.isHeader) continue;
                e.checked = isChecked;
                if (isChecked) {
                    disabledKeys.remove(e.key);
                } else {
                    disabledKeys.add(e.key);
                }
            }
            adapter.notifyDataSetChanged();
            updateCountText(findViewById(R.id.tvFilterCount));
            updateSelectAllCheckbox();
            scheduleDisabledSave();
        });
    }

    private void updateCountText(TextView tv) {
        if (tv == null) return;
        int checked = 0;
        int total = 0;
        for (SignalEntry e : entries) {
            if (e.isHeader) continue;
            total++;
            if (e.checked) checked++;
        }
        tv.setText(getString(R.string.filter_count, checked, total));
    }

    private void scheduleDisabledSave() {
        if (saveRunnable != null) saveHandler.removeCallbacks(saveRunnable);
        saveRunnable = () -> AppConfig.setDisabledSignals(this, disabledKeys);
        saveHandler.postDelayed(saveRunnable, 500);
    }

    private void saveFilter() {
        if (saveRunnable != null) saveHandler.removeCallbacks(saveRunnable);
        AppConfig.setDisabledSignals(this, disabledKeys);
        finish();
    }

    private String categoryLabel(String key) {
        int res = getResources().getIdentifier(
                "sensor_category_" + key, "string", getPackageName());
        return res != 0 ? getString(res) : key;
    }

    private static class SignalEntry {
        final String key;
        final String name;
        boolean checked;
        final boolean unsupported;
        final boolean isHeader;
        final String groupKey;
        SignalEntry(String key, String name, boolean checked, boolean unsupported) {
            this(key, name, checked, unsupported, false, null);
        }
        SignalEntry(String key, String name, boolean checked, boolean unsupported,
                    boolean isHeader, String groupKey) {
            this.key = key;
            this.name = name;
            this.checked = checked;
            this.unsupported = unsupported;
            this.isHeader = isHeader;
            this.groupKey = groupKey;
        }
        static SignalEntry header(String groupKey) {
            return new SignalEntry("", "", false, false, true, groupKey);
        }
    }

    private class SignalAdapter extends BaseAdapter {
        private Runnable onCheckedChange;

        void setOnCheckedChangeListener(Runnable listener) {
            this.onCheckedChange = listener;
        }

        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = LayoutInflater.from(AttributeFilterActivity.this)
                        .inflate(R.layout.attribute_filter_row, parent, false);
                vh = new ViewHolder();
                vh.checkBox = convertView.findViewById(R.id.rowCheck);
                vh.nameText = convertView.findViewById(R.id.rowName);
                vh.keyText = convertView.findViewById(R.id.rowKey);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }

            SignalEntry entry = entries.get(position);
            if (entry.isHeader) {
                vh.checkBox.setVisibility(View.GONE);
                vh.nameText.setText(categoryLabel(entry.groupKey));
                vh.keyText.setText("");
                vh.nameText.setEnabled(true);
                vh.keyText.setEnabled(true);
                convertView.setOnClickListener(null);
                convertView.setBackgroundColor(0xFF2A2A2A);
                return convertView;
            }
            vh.checkBox.setVisibility(View.VISIBLE);
            vh.nameText.setText(entry.name);
            vh.keyText.setText(entry.key);

            // Remove listener before setting to avoid recursion
            vh.checkBox.setOnCheckedChangeListener(null);
            vh.checkBox.setChecked(entry.checked);
            vh.checkBox.setEnabled(!entry.unsupported);
            vh.nameText.setEnabled(!entry.unsupported);
            vh.keyText.setEnabled(!entry.unsupported);
            if (entry.unsupported) {
                vh.nameText.setText(entry.name + " (unsupported)");
            }
            vh.checkBox.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
                if (entry.unsupported) return;
                entry.checked = isChecked;
                if (isChecked) disabledKeys.remove(entry.key);
                else disabledKeys.add(entry.key);
                if (onCheckedChange != null) onCheckedChange.run();
                scheduleDisabledSave();
            });

            convertView.setOnClickListener(v -> {
                if (!entry.unsupported) vh.checkBox.toggle();
            });

            if (position % 2 == 0) {
                convertView.setBackgroundColor(0xFF1E1E1E);
            } else {
                convertView.setBackgroundColor(0xFF252525);
            }

            return convertView;
        }
    }

    static class ViewHolder {
        CheckBox checkBox;
        TextView nameText;
        TextView keyText;
    }
}
