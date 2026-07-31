package com.diplustohass;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class GeofenceListActivity extends BaseLocalizedActivity {

    private ListView listView;
    private TextView emptyText;
    private List<GeofenceZone> zones;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geofence_list);
        setTitle(R.string.geofence_list_title);

        listView = findViewById(R.id.geofenceList);
        emptyText = findViewById(R.id.geofenceEmptyText);

        findViewById(R.id.fabAddGeofence).setOnClickListener(v -> {
            startActivity(new Intent(this, GeofenceEditActivity.class));
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            GeofenceZone zone = zones.get(position);
            Intent intent = new Intent(this, GeofenceEditActivity.class);
            intent.putExtra("zone_id", zone.id);
            startActivity(intent);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            GeofenceZone zone = zones.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(zone.name)
                    .setMessage(getString(R.string.geofence_delete_confirm, zone.name))
                    .setPositiveButton(R.string.geofence_delete, (d, w) -> {
                        zones.remove(position);
                        AppConfig.saveGeofences(this, zones);
                        refreshList();
                    })
                    .setNegativeButton(R.string.menu_cancel, null)
                    .show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        zones = AppConfig.loadGeofences(this);
        if (zones.isEmpty()) {
            listView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            String[] labels = new String[zones.size()];
            for (int i = 0; i < zones.size(); i++) {
                GeofenceZone z = zones.get(i);
                labels[i] = z.name + " (" + (int) z.radius + "m) — "
                        + getString(R.string.geofence_last_visited, formatLastVisited(z.lastVisitedAtMs));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, labels);
            listView.setAdapter(adapter);
        }
    }

    /**
     * Format the last-visited timestamp relative to today, e.g. "today 08:12",
     * "yesterday 23:40", or an absolute "dd.MM HH:mm" for older visits.
     * Returns the "never" string when the zone has no recorded visit.
     */
    private String formatLastVisited(long ms) {
        if (ms <= 0) {
            return getString(R.string.geofence_last_visited_never);
        }
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(ms);
        java.text.SimpleDateFormat timeFmt =
                new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        String time = timeFmt.format(then.getTime());

        java.util.Calendar day = java.util.Calendar.getInstance();
        if (isSameDay(day, then)) {
            return getString(R.string.geofence_last_visited_today, time);
        }
        day.add(java.util.Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(day, then)) {
            return getString(R.string.geofence_last_visited_yesterday, time);
        }
        return new java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
                .format(then.getTime());
    }

    private static boolean isSameDay(java.util.Calendar a, java.util.Calendar b) {
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }
}
