package com.diplustohass;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CANDataAdapter extends BaseAdapter {

    public enum SortColumn { ID, NAME, VALUE, UNIT, ROUTE }

    private final Context context;
    private final List<CANDataItem> items;
    private OnEnabledChangeListener enabledChangeListener;
    private SortColumn sortColumn = SortColumn.ID;
    private boolean ascending = true;

    public interface OnEnabledChangeListener {
        void onEnabledChanged(CANDataItem item, boolean enabled);
    }

    public CANDataAdapter(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
    }

    public void setOnEnabledChangeListener(OnEnabledChangeListener listener) {
        this.enabledChangeListener = listener;
    }

    public void setData(List<CANDataItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        applySort();
        notifyDataSetChanged();
    }

    public List<CANDataItem> getItems() {
        return new ArrayList<>(items);
    }

    public void setAllEnabled(boolean enabled) {
        for (CANDataItem item : items) {
            if (item.unsupported) continue;
            item.enabled = enabled;
        }
        notifyDataSetChanged();
    }

    public void sortBy(SortColumn column) {
        if (sortColumn == column) {
            ascending = !ascending;
        } else {
            sortColumn = column;
            ascending = true;
        }
        applySort();
        notifyDataSetChanged();
    }

    public SortColumn getSortColumn() {
        return sortColumn;
    }

    public boolean isAscending() {
        return ascending;
    }

    private void applySort() {
        Comparator<CANDataItem> comparator;
        switch (sortColumn) {
            case NAME:
                comparator = Comparator.comparing(a -> a.name != null ? a.name : "");
                break;
            case VALUE:
                comparator = (a, b) -> compareValues(a.value, b.value);
                break;
            case UNIT:
                comparator = Comparator.comparing(a -> a.unit != null ? a.unit : "");
                break;
            case ROUTE:
                comparator = Comparator.comparing(a -> a.rawData != null ? a.rawData : "");
                break;
            case ID:
            default:
                comparator = Comparator.comparing(a -> a.key != null && !a.key.isEmpty() ? a.key : a.diplusName);
                break;
        }
        if (!ascending) {
            comparator = comparator.reversed();
        }
        try {
            Collections.sort(items, comparator);
        } catch (Exception e) {
            // Ignore sort errors (e.g. null values in comparator)
        }
    }

    private static int compareValues(String a, String b) {
        // Try numeric comparison first; fall back to string compare.
        Double da = parseNumeric(a);
        Double db = parseNumeric(b);
        if (da != null && db != null) {
            return Double.compare(da, db);
        }
        if (da != null) return -1;
        if (db != null) return 1;
        String sa = a != null ? a : "";
        String sb = b != null ? b : "";
        return sa.compareTo(sb);
    }

    private static Double parseNumeric(String value) {
        if (value == null || value.isEmpty() || "---".equals(value)) return null;
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return items.get(i).canId; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    com.diplustohass.R.layout.can_data_row, parent, false);
            vh = new ViewHolder();
            vh.checkBox = convertView.findViewById(com.diplustohass.R.id.rowCheck);
            vh.idText = convertView.findViewById(com.diplustohass.R.id.rowId);
            vh.nameText = convertView.findViewById(com.diplustohass.R.id.rowName);
            vh.valueText = convertView.findViewById(com.diplustohass.R.id.rowValue);
            vh.unitText = convertView.findViewById(com.diplustohass.R.id.rowUnit);
            vh.routeText = convertView.findViewById(com.diplustohass.R.id.rowRoute);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        CANDataItem item = items.get(position);
        vh.idText.setText(item.key != null && !item.key.isEmpty() ? item.key : item.diplusName);
        vh.nameText.setText(item.name);
        vh.valueText.setText(SignalTranslator.translateValue(item.value));
        vh.unitText.setText(item.unit);
        vh.routeText.setText(item.rawData != null && !item.rawData.isEmpty() ? item.rawData : "");

        boolean fresh = (System.currentTimeMillis() - item.lastUpdate) < 5000;
        vh.valueText.setTextColor(fresh ? 0xFF4CAF50 : 0xFFB0B0B0);

        // Unsupported signals are shown for information but cannot be enabled for HA.
        if (item.unsupported) {
            vh.checkBox.setVisibility(View.GONE);
            vh.nameText.setTextColor(0xFF808080);
            vh.valueText.setTextColor(0xFF808080);
        } else {
            vh.checkBox.setVisibility(View.VISIBLE);
            vh.nameText.setTextColor(0xFFFFFFFF);
            // Keep the value color computed above.
        }

        // Bind checkbox without triggering the listener
        vh.checkBox.setOnCheckedChangeListener(null);
        vh.checkBox.setChecked(item.enabled);
        vh.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (item.unsupported) return;
                item.enabled = isChecked;
                if (enabledChangeListener != null) {
                    enabledChangeListener.onEnabledChanged(item, isChecked);
                }
            }
        });

        if (position % 2 == 0) {
            convertView.setBackgroundColor(0xFF1E1E1E);
        } else {
            convertView.setBackgroundColor(0xFF252525);
        }

        return convertView;
    }

    static class ViewHolder {
        CheckBox checkBox;
        TextView idText, nameText, valueText, unitText, routeText;
    }
}
