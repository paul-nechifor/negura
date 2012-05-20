package negura.common;

/**
 * A Service is a sequence of code that can be run in a new thread (or in the
 * current thread) until a stop is requested. All the methods are synchronized.
 * @author Paul Nechifor
 */
public abstract class Service implements Runnable {
    private boolean continueRunning = false;

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

    /**
     * Returns true if the thread will continue to run; if this is false it
     * doesn't mean that it has stopped as it might not have had time.
     * @return      If the thread will continue to run.
     */
    public synchronized boolean getContinueRunning() {
        return continueRunning;
    }
}
