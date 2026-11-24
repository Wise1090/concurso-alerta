package com.concursos.alerta;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Verificação periódica em segundo plano (a cada ~30 min, sobrevive ao reinício do aparelho). */
public class CheckJob extends JobService {
    private static final int JOB_ID = 4242;

    public static void agendar(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        JobInfo ji = new JobInfo.Builder(JOB_ID, new ComponentName(c, CheckJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30 * 60 * 1000L)
                .setPersisted(true)
                .build();
        js.schedule(ji);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            boolean refazer = false;
            try {
                Verificador.rodar(getApplicationContext());
            } catch (Exception e) {
                refazer = true;
            }
            jobFinished(params, refazer);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
