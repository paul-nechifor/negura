package negura.common.data;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler that runs a task at a fixed rate and gives the option to force
 * the imediat execution of the task. After that the task will continue to be
 * executed at a fixed rate from that point in time.
 * 
 * @author Paul Nechifor
 */
public class ForceableScheduler {
    private final ScheduledExecutorService scheduler;
    private final Runnable runnable;
    private final int rateMillis;
    private final Object lock = new Object();
    private ScheduledFuture<?> task;

    public ForceableScheduler(ScheduledExecutorService scheduler,
            int rateMillis, Runnable runnable) {
        this.scheduler = scheduler;
        this.runnable = runnable;
        this.rateMillis = rateMillis;

        synchronized (lock) {
            task = scheduler.scheduleAtFixedRate(runnable, 0, rateMillis,
                    TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        synchronized (lock) {
            task.cancel(false);
        }
    }

    public void forceExecutionNow() {
        synchronized (lock) {
            task.cancel(false);
            task = scheduler.scheduleAtFixedRate(runnable, 0, rateMillis,
                    TimeUnit.MILLISECONDS);
        }
    }
}
