package com.car2hass;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Log export/share flow: the export dialog, the "send to server" comment prompt
 * and the file-share path. Extracted from MainActivity to keep that class smaller.
 * The storage-permission save path stays in MainActivity (it needs the permission
 * result callback) and is invoked through {@link Host#saveLog()}.
 */
public final class LogExportController {

    // Binder transaction limit is ~1 MB; keep EXTRA_TEXT well below it.
    private static final int MAX_SHARE_TEXT_LENGTH = 500_000;

    public interface Host {
        Activity activity();
        void post(Runnable r);
        LogSendController logSend();
        /** Runs the "save to Downloads" flow (handles the storage permission). */
        void saveLog();
        void toast(int res);
        String str(int res);
        /** Consumes and returns the pending research report path (or null). */
        String takeResearchPath();
    }

    private final Host host;

    public LogExportController(Host host) {
        this.host = host;
    }

    /** The 3-way export dialog: share / save / send to server. */
    public void showDialog() {
        final Activity a = host.activity();
        new AlertDialog.Builder(a)
            .setTitle(R.string.log_export_title)
            .setItems(new String[]{
                    a.getString(R.string.log_export_share),
                    a.getString(R.string.log_export_save),
                    a.getString(R.string.log_export_server)}, (d, which) -> {
                if (which == 0) exportShare();
                else if (which == 1) host.saveLog();
                else promptServerUpload();
            })
            .show();
    }

    /** Asks for an optional car/conditions note, then uploads the full log. */
    private void promptServerUpload() {
        final Activity a = host.activity();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView hint = new TextView(a);
        hint.setText(R.string.log_server_hint);
        hint.setTextSize(13);
        hint.setTextColor(attrColor(R.attr.carTextTertiary));
        box.addView(hint);
        EditText edit = new EditText(a);
        edit.setHint(R.string.log_server_hint);
        edit.setSingleLine(false);
        edit.setMinLines(3);
        edit.setMaxLines(5);
        // Auto-fill with the vehicle identifier so the developer can see who the
        // log came from; the user can edit or keep it.
        edit.setText(buildIdentifier(a));
        box.addView(edit);

        TextView autofill = new TextView(a);
        autofill.setText(R.string.log_server_hint_autofill);
        autofill.setTextSize(12);
        autofill.setTextColor(attrColor(R.attr.carTextTertiary));
        autofill.setPadding(0, dp(4), 0, 0);
        box.addView(autofill);

        new AlertDialog.Builder(a)
            .setTitle(R.string.log_export_server)
            .setView(box)
            .setPositiveButton(android.R.string.ok, (d, w) ->
                    host.logSend().upload(edit.getText().toString().trim()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Vehicle/device identifier auto-filled into the log comment for analysis. */
    public static String buildIdentifier(Context ctx) {
        StringBuilder sb = new StringBuilder("Car2Hass " + AppInfo.getVersionString(ctx));
        if (CANDataReader.sVin != null && !CANDataReader.sVin.isEmpty()
                && !"---".equals(CANDataReader.sVin)) {
            sb.append(" · VVIN ").append(CANDataReader.sVin);
        }
        if (CANDataReader.sFirmware != null && !CANDataReader.sFirmware.isEmpty()
                && !"---".equals(CANDataReader.sFirmware)) {
            sb.append(" · FW ").append(CANDataReader.sFirmware);
        }
        String carName = AppConfig.getCarName(ctx);
        if (carName != null && !carName.isEmpty()) {
            sb.append(" · ").append(carName);
        }
        String profile = AppConfig.getSelectedProfile(ctx);
        if (profile != null && !profile.isEmpty()) {
            sb.append(" · ").append(profile);
        }
        return sb.toString();
    }

    private void exportShare() {
        final Activity a = host.activity();
        new Thread(() -> {
            try {
                final byte[] logBytes = LogExportHelper.buildLogBytes(a, host.takeResearchPath());
                host.post(() -> shareLog(a, logBytes));
            } catch (Exception e) {
                LogBuffer.e("Main", "exportLogShare failed: " + e.getMessage());
            }
        }).start();
    }

    private void shareLog(Activity a, byte[] logBytes) {
        // Preferred path: share the log file saved to Downloads so the full
        // log never travels through a binder transaction.
        Uri uri = null;
        try {
            uri = LogExportHelper.saveLogToDownloads(a, logBytes);
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
                share.setClipData(ClipData.newUri(a.getContentResolver(), "log", uri));
                a.startActivity(Intent.createChooser(share, a.getString(R.string.log_export_share)));
                return;
            } catch (Exception e) {
                LogBuffer.w("Main", "shareLog: stream share failed, falling back to text: " + e.getMessage());
            }
        }
        // Fallback (API 24-28 or MediaStore failure): share as truncated text.
        // Decode only the tail bytes so a multi-MB log is never materialised as
        // one giant String (OOM risk on head units).
        int start = Math.max(0, logBytes.length - MAX_SHARE_TEXT_LENGTH);
        String tail = new String(logBytes, start, logBytes.length - start,
                java.nio.charset.StandardCharsets.UTF_8);
        String text = start > 0 ? a.getString(R.string.log_share_truncated) + tail : tail;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        a.startActivity(Intent.createChooser(share, a.getString(R.string.log_export_share)));
    }

    private int dp(int v) {
        return (int) (v * host.activity().getResources().getDisplayMetrics().density + 0.5f);
    }

    private int attrColor(int attrRes) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (host.activity().getTheme().resolveAttribute(attrRes, tv, true)) return tv.data;
        return 0xFFFFFFFF;
    }
}
