package negura.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import negura.common.util.NeguraLog;

/**
 *
 * @author Paul Nechifor
 */
public class RequestServer extends Service {

    private int port;
    private RequestHandler handler;
    private ThreadPoolExecutor exec;
    private ServerSocket serverSocket;

    public RequestServer(int port, String threadPoolOptions,
            RequestHandler handler) {
        this.port = port;
        this.handler = handler;
        this.exec = fromOptions(threadPoolOptions);
        if (this.exec == null) {
            NeguraLog.severe("Error while creating thread pool with options: "
                    + "'%s'.", threadPoolOptions);
        }
    }

    /**
     * Returns a ThreadPoolExecutor from the options.
     * @param options
     * @return
     */
    public static ThreadPoolExecutor fromOptions(String options) {
        int corePoolSize = -1;
        int maximumPoolSize = -1;
        int keepAliveTime = -1;

        for (String option : options.replaceAll("\\s+", "").split(";")) {
            String[] split = option.split("=");
            if (split.length != 2)
                return null;
            int value = -1;
            try {
                value = Integer.parseInt(split[1]);
            } catch (NumberFormatException ex) { }

            if (split[0].equals("core-pool-size"))
                corePoolSize = value;
            else if (split[0].equals("maximum-pool-size"))
                maximumPoolSize = value;
            else if (split[0].equals("keep-alive-time"))
                keepAliveTime = value;
            else
                return null;
        }

        try {
            BlockingQueue<Runnable> q =
                    new ArrayBlockingQueue<Runnable>(maximumPoolSize);
            ThreadPoolExecutor ret = new ThreadPoolExecutor(corePoolSize,
                    maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, q);
            return ret;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port), 5);
        } catch (IOException ex) {
            NeguraLog.severe(ex, "Could not open the server.");
        }

        Socket toClose = null;

        while (running) {
            try {
                final Socket socket = serverSocket.accept();
                toClose = socket;
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        handler.handle(socket);
                    }
                });
            } catch (RejectedExecutionException ex) {
                try { toClose.close(); } catch (Exception ex2) { }
                NeguraLog.warning(ex, "I had to reject request.");
            } catch (IOException ex) {
                // This test is necesary because the socket will be forcefully
                // closed when the program closes.
                if (running)
                    NeguraLog.severe(ex, "Accept failed");
            }
        }
        
        // This thread is running out. Wait 5 seconds and if the thread pool
        // hasn't shut down orderlly, force it to shut down.

        exec.shutdown();
        for (int i = 0; i < 1000; i++) {
            if (exec.isTerminated())
                break;
            tryToSleep(5);
        }
        if (!exec.isTerminated())
            exec.shutdownNow();
    }

    @Override
    public void stop() {
        super.stop();
        try {
            serverSocket.close();
        } catch (Exception ex) {}

        // The shutdown of the thread pool is called after the end of this loop
        // and not by the thread who calls this method.
    }
}
