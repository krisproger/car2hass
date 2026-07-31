package com.diplustohass.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.diplustohass.LogBuffer;

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
