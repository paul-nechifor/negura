package negura.common;

import negura.common.ex.NeguraError;

/**
 *
 * @author Paul Nechifor
 */
public abstract class Service {
    protected static final int STOPPED = 1;
    protected static final int STARTING = 2;
    protected static final int RUNNING = 3;
    protected static final int STOPPING = 4;

    protected volatile int serviceState = STOPPED;

    public final void start() {
        if (serviceState != STOPPED)
            throw new NeguraError("Can't start if it isn't stopped.");
        serviceState = STARTING;
        onStart();
    }

    protected final void started() {
        if (serviceState != STARTING)
            throw new NeguraError("You weren't starting.");
        serviceState = RUNNING;
    }

    public final void stop() {
        if (serviceState != RUNNING)
            throw new NeguraError("It isn't running.");
        serviceState = STOPPING;
        onStop();
    }

    protected final void stopped() {
        if (serviceState != STOPPING)
            throw new NeguraError("You weren't stopping.");
        serviceState = STOPPED;
    }

    protected abstract void onStart();

    protected abstract void onStop();

    public final boolean isStopped() {
        return serviceState == STOPPED;
    }

    public final boolean isStarting() {
        return serviceState == STARTING;
    }

    public final boolean isRunning() {
        return serviceState == RUNNING;
    }

    public final boolean isStopping() {
        return serviceState == STOPPING;
    }

    public final Thread startInOwnThread() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                start();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();

        return thread;
    }
}
