package com.car2hass;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/**
 * Renders the geofence list in the settings section and handles delete.
 * Extracted from MainActivity to keep that class smaller.
 */
public final class GeofenceSettingsController {

    public interface Host {
        Activity activity();
        View settingsView();
    }

    private final Host host;

    public GeofenceSettingsController(Host host) {
        this.host = host;
    }

    /** Rebuilds the geofence rows (or shows the empty hint). */
    public void render() {
        Activity a = host.activity();
        View settingsView = host.settingsView();
        LinearLayout container = settingsView.findViewById(R.id.geofenceRowsContainer);
        TextView emptyText = settingsView.findViewById(R.id.geofenceEmptyText);
        List<GeofenceZone> zones = AppConfig.loadGeofences(a);
        if (zones.isEmpty()) {
            container.removeAllViews();
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        emptyText.setVisibility(View.GONE);
        container.removeAllViews();
        for (GeofenceZone zone : zones) {
            TextView row = new TextView(a);
            row.setText(GeofenceRowText.buildRow(zone.name, zone.radius,
                    a.getString(R.string.geofence_last_visited,
                            GeofenceRowText.formatLastVisited(zone.lastVisitedAtMs,
                                    a.getString(R.string.geofence_last_visited_never),
                                    a.getString(R.string.geofence_last_visited_today),
                                    a.getString(R.string.geofence_last_visited_yesterday),
                                    Calendar.getInstance()))));
            row.setTextColor(a.getResources().getColor(R.color.textPrimary));
            row.setTextSize(14f);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundResource(R.drawable.bg_row);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(a, GeofenceEditActivity.class);
                intent.putExtra("zone_id", zone.id);
                a.startActivity(intent);
            });
            row.setOnLongClickListener(v -> {
                confirmDelete(zone);
                return true;
            });
            container.addView(row);
        }
    }

    private void confirmDelete(GeofenceZone zone) {
        Activity a = host.activity();
        new AlertDialog.Builder(a)
                .setTitle(zone.name)
                .setMessage(a.getString(R.string.geofence_delete_confirm, zone.name))
                .setPositiveButton(R.string.geofence_delete, (d, w) -> {
                    List<GeofenceZone> zones = AppConfig.loadGeofences(a);
                    for (int i = 0; i < zones.size(); i++) {
                        if (zones.get(i).id.equals(zone.id)) {
                            zones.remove(i);
                            break;
                        }
                    }
                    AppConfig.saveGeofences(a, zones);
                    render();
                })
                .setNegativeButton(R.string.menu_cancel, null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * host.activity().getResources().getDisplayMetrics().density + 0.5f);
    }
}
