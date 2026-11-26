package hu.onda;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Háttérben, rendszeres időközönként önállóan frissít, az app megnyitása nélkül is. */
public class UpdateJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters p) {
        new Thread(() -> {
            boolean retry = false;
            try { Updater.updateNow(this); } catch (Exception e) { retry = true; }
            jobFinished(p, retry);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return true; }
}
