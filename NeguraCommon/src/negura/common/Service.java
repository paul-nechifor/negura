package negura.common;

/**
 *
 * @author Paul Nechifor
 */
public abstract class Service implements Runnable
{
    protected boolean running = false;

    /**
     * Starts the service and returns, but doesn't do anything if it isn't
     * started.
     */
    public void startInNewThread()
    {
        if (running)
            return;

        running = true;

        new Thread(this).start();
    }

    public void start()
    {
        if (running)
            return;

        running = true;

        run();
    }

    public void tryToSleep(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException ex) {}
    }
    
    /**
     * Tells the service to stop as soon as possible.
     */
    public void stop()
    {
        running = false;
    }
}
