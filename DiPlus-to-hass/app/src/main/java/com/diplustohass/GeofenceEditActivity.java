package com.diplustohass;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.List;
import java.util.Locale;

public class GeofenceEditActivity extends BaseLocalizedActivity {

    private EditText editName;
    private EditText editLat;
    private EditText editLng;
    private SeekBar radiusSeek;
    private TextView radiusLabel;
    private Button btnSave;
    private Button btnDelete;
    private MapView mapView;

    private GeofenceZone zone;
    private double centerLat;
    private double centerLng;
    private boolean syncingFields = false;

    private Marker centerMarker;
    private Polygon radiusCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().load(this,
                getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        setContentView(R.layout.activity_geofence_edit);
        setTitle(R.string.geofence_edit_title);

        editName = findViewById(R.id.editGeofenceName);
        editLat = findViewById(R.id.editGeofenceLat);
        editLng = findViewById(R.id.editGeofenceLng);
        radiusSeek = findViewById(R.id.radiusSeekBar);
        radiusLabel = findViewById(R.id.radiusLabel);
        btnSave = findViewById(R.id.btnGeofenceSave);
        btnDelete = findViewById(R.id.btnGeofenceDelete);
        mapView = findViewById(R.id.geofenceMap);

        setupMap();

        String zoneId = getIntent().getStringExtra("zone_id");
        List<GeofenceZone> zones = AppConfig.loadGeofences(this);
        zone = zoneId != null ? findZone(zones, zoneId) : null;

        if (zone != null) {
            editName.setText(zone.name);
            centerLat = zone.latitude;
            centerLng = zone.longitude;
            int progress = Math.max(0, (int) zone.radius - 10);
            radiusSeek.setProgress(Math.min(progress, radiusSeek.getMax()));
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            zone = new GeofenceZone();
            Location car = getCarPosition();
            if (car != null) {
                centerLat = car.getLatitude();
                centerLng = car.getLongitude();
            } else {
                centerLat = 0;
                centerLng = 0;
            }
            radiusSeek.setProgress(90);
            btnDelete.setVisibility(View.GONE);
        }

        radiusSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateRadiusOverlay();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextWatcher coordWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (syncingFields) return;
                Double lat = parseCoord(editLat.getText().toString());
                Double lng = parseCoord(editLng.getText().toString());
                if (lat != null && lng != null && Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                    centerLat = lat;
                    centerLng = lng;
                    updateMapOverlays(false);
                }
            }
        };
        editLat.addTextChangedListener(coordWatcher);
        editLng.addTextChangedListener(coordWatcher);

        findViewById(R.id.btnUseCarPosition).setOnClickListener(v -> {
            Location car = getCarPosition();
            if (car != null) {
                centerLat = car.getLatitude();
                centerLng = car.getLongitude();
                updateMapOverlays(true);
            } else {
                Toast.makeText(this, R.string.geofence_no_position, Toast.LENGTH_SHORT).show();
            }
        });

        btnSave.setOnClickListener(v -> saveZone());
        btnDelete.setOnClickListener(v -> deleteZone());

        updateRadiusOverlay();
        updateMapOverlays(true);
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        centerMarker = new Marker(mapView);
        centerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        centerMarker.setDraggable(true);
        centerMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDrag(Marker marker) {}
            @Override public void onMarkerDragEnd(Marker marker) {
                centerLat = marker.getPosition().getLatitude();
                centerLng = marker.getPosition().getLongitude();
                updateMapOverlays(false);
            }
            @Override public void onMarkerDragStart(Marker marker) {}
        });
        mapView.getOverlays().add(centerMarker);

        radiusCircle = new Polygon(mapView);
        radiusCircle.getFillPaint().setColor(0x304CAF50);
        radiusCircle.getOutlinePaint().setColor(0xFF4CAF50);
        radiusCircle.getOutlinePaint().setStrokeWidth(3f);
        mapView.getOverlays().add(radiusCircle);

        MapEventsOverlay events = new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                centerLat = p.getLatitude();
                centerLng = p.getLongitude();
                updateMapOverlays(false);
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        mapView.getOverlays().add(events);
    }

    private void updateMapOverlays(boolean recenter) {
        GeoPoint point = new GeoPoint(centerLat, centerLng);
        centerMarker.setPosition(point);
        updateRadiusOverlay();
        if (recenter) {
            boolean valid = Math.abs(centerLat) > 0.0001 || Math.abs(centerLng) > 0.0001;
            mapView.getController().setZoom(valid ? 15.0 : 3.0);
            mapView.getController().setCenter(point);
        }
        syncingFields = true;
        editLat.setText(String.format(Locale.US, "%.6f", centerLat));
        editLng.setText(String.format(Locale.US, "%.6f", centerLng));
        syncingFields = false;
        mapView.invalidate();
    }

    private void updateRadiusOverlay() {
        int r = Math.max(10, radiusSeek.getProgress() + 10);
        radiusLabel.setText(r + "m");
        if (radiusCircle != null) {
            radiusCircle.setPoints(Polygon.pointsAsCircle(new GeoPoint(centerLat, centerLng), (double) r));
        }
        if (mapView != null) mapView.invalidate();
    }

    private Double parseCoord(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private Location getCarPosition() {
        try {
            com.diplustohass.service.TelemetryService ts = MainActivity.getTelemetryService();
            if (ts != null && ts.hasValidLocation()) {
                Location loc = new Location("telemetry");
                loc.setLatitude(ts.getLastLatitude());
                loc.setLongitude(ts.getLastLongitude());
                return loc;
            }
        } catch (Exception ignored) {}
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            for (String provider : new String[]{
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location loc = lm.getLastKnownLocation(provider);
                    if (loc != null) return loc;
                } catch (SecurityException ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    private GeofenceZone findZone(List<GeofenceZone> zones, String id) {
        for (GeofenceZone z : zones) {
            if (id.equals(z.id)) return z;
        }
        return null;
    }

    private void saveZone() {
        String name = editName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.geofence_field_name, Toast.LENGTH_SHORT).show();
            return;
        }
        zone.name = name;
        zone.latitude = centerLat;
        zone.longitude = centerLng;
        zone.radius = Math.max(10, radiusSeek.getProgress() + 10);

        List<GeofenceZone> zones = AppConfig.loadGeofences(this);
        boolean found = false;
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).id.equals(zone.id)) {
                zones.set(i, zone);
                found = true;
                break;
            }
        }
        if (!found) zones.add(zone);
        AppConfig.saveGeofences(this, zones);
        Toast.makeText(this, R.string.geofence_save, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void deleteZone() {
        List<GeofenceZone> zones = AppConfig.loadGeofences(this);
        zones.removeIf(z -> z.id.equals(zone.id));
        AppConfig.saveGeofences(this, zones);
        Toast.makeText(this, R.string.geofence_delete, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
}