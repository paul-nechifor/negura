package negura.common;

/**
 * A Service is a sequence of code that can be run in a new thread (or in the
 * current thread) until a stop is requested. All the methods are synchronized.
 * @author Paul Nechifor
 */
public abstract class Service implements Runnable {
    /**
     * When this is volatile variable is set to false, the service should try
     * to stop as soon as possible.
     */
    protected volatile boolean continueRunning = false;

    /**
     * Starts the service int a new thread and returns.
     */
    public synchronized void startInNewThread() {
        if (continueRunning)
            throw new RuntimeException("The service is already started.");

        continueRunning = true;

        new Thread(this).start();
    }

    /**
     * Starts the service in this thread and returns when all the processing
     * ends. This might be because another thread requested it to stop.
     */
    public synchronized void start() {
        if (continueRunning)
            throw new RuntimeException("The service is already started.");

        continueRunning = true;

        run();
    }

    /**
     * Tells the service to stop as soon as possible.
     */
    public synchronized void requestStop() {
        if (!continueRunning)
            throw new RuntimeException("The service wasn't started.");

        continueRunning = false;
    }
}
