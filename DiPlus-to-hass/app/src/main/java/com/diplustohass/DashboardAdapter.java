package com.diplustohass;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class DashboardAdapter extends BaseAdapter {

    private static final int VIEW_TYPE_STANDARD = 0;
    private static final int VIEW_TYPE_ZONES = 1;

    private final Context context;
    private final List<DashboardTile> tiles;
    private OnTileClickListener clickListener;
    private OnTileLongClickListener longClickListener;
    private OnTileDeleteListener deleteListener;
    private OnTileAddListener addListener;
    private boolean editMode = false;

    public interface OnTileClickListener {
        void onTileClick(DashboardTile tile, int position, View view, float x, float y);
    }

    public interface OnTileLongClickListener {
        void onTileLongClick(DashboardTile tile);
    }

    public interface OnTileDeleteListener {
        void onTileDelete(DashboardTile tile, int position);
    }

    public interface OnTileAddListener {
        void onTileAdd(int position);
    }

    public DashboardAdapter(Context context) {
        this.context = context;
        this.tiles = new ArrayList<>();
    }

    public void setTiles(List<DashboardTile> tiles) {
        this.tiles.clear();
        if (tiles != null) this.tiles.addAll(tiles);
        notifyDataSetChanged();
    }

    public List<DashboardTile> getTiles() {
        return new ArrayList<>(tiles);
    }

    public DashboardTile findTile(String key) {
        for (DashboardTile tile : tiles) {
            if (tile.key.equals(key)) return tile;
        }
        return null;
    }

    public void setOnTileClickListener(OnTileClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTileLongClickListener(OnTileLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnTileDeleteListener(OnTileDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setOnTileAddListener(OnTileAddListener listener) {
        this.addListener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return editMode;
    }

    @Override public int getCount() { return tiles.size(); }
    @Override public Object getItem(int position) { return tiles.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public int getViewTypeCount() { return 2; }

    @Override public int getItemViewType(int position) {
        return hasZones(tiles.get(position)) ? VIEW_TYPE_ZONES : VIEW_TYPE_STANDARD;
    }

    /** True when the tile is a preset with tap zones (e.g. volume/fan/temperature −/+). */
    private boolean hasZones(DashboardTile tile) {
        if (tile.type != DashboardTile.Type.PRESET || tile.presetId == null) return false;
        try {
            DashboardPresetRegistry.DashboardPreset preset =
                    DashboardPresetRegistry.getInstance(context).getPreset(tile.presetId);
            return preset != null && preset.zones != null && !preset.zones.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh;
        if (convertView == null) {
            int layoutRes = getItemViewType(position) == VIEW_TYPE_ZONES
                    ? R.layout.dashboard_tile_zones : R.layout.dashboard_tile;
            convertView = LayoutInflater.from(context).inflate(layoutRes, parent, false);
            vh = new ViewHolder();
            vh.icon = convertView.findViewById(R.id.tileIcon);
            vh.iconImage = convertView.findViewById(R.id.tileIconImage);
            vh.label = convertView.findViewById(R.id.tileLabel);
            vh.value = convertView.findViewById(R.id.tileValue);
            vh.unit = convertView.findViewById(R.id.tileUnit);
            vh.sub = convertView.findViewById(R.id.tileSub);
            vh.deleteBadge = convertView.findViewById(R.id.tileDelete);
            vh.zoneLeft = convertView.findViewById(R.id.tileZoneLeft);
            vh.zoneRight = convertView.findViewById(R.id.tileZoneRight);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        DashboardTile tile = tiles.get(position);
        // Prefer a PNG icon (filesDir override -> bundled asset); fall back to emoji.
        Bitmap iconBitmap = tile.iconName != null && !tile.iconName.isEmpty()
                ? PresetIconResolver.resolve(context, tile.iconName) : null;
        if (iconBitmap != null && vh.iconImage != null) {
            vh.iconImage.setImageBitmap(iconBitmap);
            vh.iconImage.setVisibility(View.VISIBLE);
            vh.icon.setVisibility(View.GONE);
        } else {
            if (vh.iconImage != null) vh.iconImage.setVisibility(View.GONE);
            vh.icon.setVisibility(View.VISIBLE);
        }
        vh.icon.setText(tile.icon);
        vh.label.setText(tile.label);
        vh.value.setText(tile.value);
        vh.unit.setText(tile.unit);
        vh.sub.setText(tile.sub);
        vh.sub.setVisibility(tile.sub.isEmpty() ? View.GONE : View.VISIBLE);

        int valueColor = tile.alert ? context.getResources().getColor(R.color.accentRed)
                : context.getResources().getColor(R.color.textPrimary);
        vh.value.setTextColor(valueColor);

        if (vh.deleteBadge != null) {
            vh.deleteBadge.setVisibility(editMode && !tile.isEmptyCell ? View.VISIBLE : View.GONE);
        }

        boolean showZones = !editMode && hasZones(tile);
        if (vh.zoneLeft != null) vh.zoneLeft.setVisibility(showZones ? View.VISIBLE : View.GONE);
        if (vh.zoneRight != null) vh.zoneRight.setVisibility(showZones ? View.VISIBLE : View.GONE);

        convertView.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                vh.lastTouchX = event.getX();
                vh.lastTouchY = event.getY();
                // Prevent GridView from intercepting the gesture so that clicks
                // and long-clicks on tiles are handled by the item itself.
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (action == MotionEvent.ACTION_UP) {
                vh.lastTouchX = event.getX();
                vh.lastTouchY = event.getY();
            }
            return false;
        });
        convertView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTileClick(tile, position, v, vh.lastTouchX, vh.lastTouchY);
            }
        });
        convertView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onTileLongClick(tile);
            return true;
        });

        if (vh.deleteBadge != null) {
            vh.deleteBadge.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onTileDelete(tile, position);
            });
        }

        return convertView;
    }

    static class ViewHolder {
        TextView icon, label, value, unit, sub, deleteBadge, zoneLeft, zoneRight;
        ImageView iconImage;
        float lastTouchX, lastTouchY;
    }
}
