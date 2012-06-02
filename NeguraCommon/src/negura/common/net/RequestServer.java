package negura.common.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import negura.common.Service;
import negura.common.data.ThreadPoolOptions;
import negura.common.util.NeguraLog;

/**
 * @author Paul Nechifor
 */
public class RequestServer extends Service {
    private final int port;
    private final ThreadPoolOptions options;
    private final RequestHandler handler;
    private ThreadPoolExecutor exec;
    private ServerSocket serverSocket;

    public RequestServer(int port, ThreadPoolOptions options,
            RequestHandler handler) {
        this.port = port;
        this.options = options;
        this.handler = handler;
    }

    @Override
    protected void onStart() {
        BlockingQueue<Runnable> q = new ArrayBlockingQueue<Runnable>(
                options.getMaximumPoolSize());
        exec = new ThreadPoolExecutor(
                options.getCorePoolSize(),
                options.getMaximumPoolSize(),
                options.getKeepAliveTimeMillis(),
                TimeUnit.MILLISECONDS, q);

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port), 5);
        } catch (IOException ex) {
            NeguraLog.severe(ex, "Could not open the server.");
        }

        // Telling the superclass to change the serviceState.
        started();

        Socket toClose = null;

        while (serviceState == RUNNING) {
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
                if (serviceState == RUNNING)
                    NeguraLog.severe(ex, "Accept failed");
            }
        }

        exec.shutdown();

        stopped();
    }

    @Override
    protected void onStop() {
        // Closing the socket. This means it will exit the accept if it was
        // blocked on it. Note that stopped() is called after exiting that loop
        // in that thread, not here.
        try {
            serverSocket.close();
        } catch (Exception ex) {
            NeguraLog.warning(ex);
        }
    }

    public int getThreadsActive() {
        return exec.getActiveCount();
    }
}
