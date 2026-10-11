package com.car2hass;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A button that turns into an inline progress bar while a task runs. Used for
 * long operations (vehicle research, update download) so progress is always
 * visible without a modal dialog or a notification permission.
 */
public class ProgressActionButton extends FrameLayout {

    private Button button;
    private View busy;
    private ProgressBar progress;
    private TextView label;

    public ProgressActionButton(Context c) { this(c, null, 0); }

    public ProgressActionButton(Context c, AttributeSet a) { this(c, a, 0); }

    public ProgressActionButton(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        LayoutInflater.from(c).inflate(R.layout.view_progress_action_button, this, true);
        button = findViewById(R.id.pabButton);
        busy = findViewById(R.id.pabBusy);
        progress = findViewById(R.id.pabProgress);
        label = findViewById(R.id.pabLabel);
    }

    /** Shows the clickable button with the given label. */
    public void setIdle(int textRes, View.OnClickListener listener) {
        button.setVisibility(VISIBLE);
        button.setEnabled(true);
        button.setText(textRes);
        button.setOnClickListener(listener);
        busy.setVisibility(GONE);
    }

    /** Same as {@link #setIdle} but for the alternate action (view report / install). */
    public void setAlt(int textRes, View.OnClickListener listener) {
        setIdle(textRes, listener);
    }

    /** Replaces the button with a progress bar (determinate when max > 0). */
    public void startBusy(int textRes, int max) {
        button.setVisibility(GONE);
        button.setOnClickListener(null);
        busy.setVisibility(VISIBLE);
        label.setText(textRes);
        if (max > 0) {
            progress.setIndeterminate(false);
            progress.setMax(max);
            progress.setProgress(0);
        } else {
            progress.setIndeterminate(true);
        }
    }

    public void setProgress(int done, int total) {
        if (total > 0) {
            progress.setIndeterminate(false);
            progress.setMax(total);
            progress.setProgress(done);
        }
    }

    public void setBusyLabel(CharSequence text) { label.setText(text); }

    public void setBusyLabel(int textRes) { label.setText(textRes); }

    public boolean isBusy() { return busy.getVisibility() == VISIBLE; }
}
