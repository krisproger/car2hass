package com.car2hass.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.car2hass.LogBuffer;

public class KeepAliveJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        LogBuffer.i("KeepAliveJob", "Job fired");
        TelemetryService.start(this);
        jobFinished(params, false);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogBuffer.i("KeepAliveJob", "Job stopped");
        return true;
    }
}
