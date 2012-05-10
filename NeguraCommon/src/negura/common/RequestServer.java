package negura.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import negura.common.util.NeguraLog;

/**
 *
 * @author Paul Nechifor
 */
public class RequestServer extends Service {

    private int port;
    private RequestHandler handler;
    private ExecutorService exec;
    private ServerSocket serverSocket;

    public RequestServer(int port, int threadPoolSize, RequestHandler handler) {
        this.port = port;
        this.handler = handler;
        this.exec = Executors.newFixedThreadPool(threadPoolSize);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port), 5);
        } catch (IOException ex) {
            NeguraLog.severe("Could not open the server.", ex);
        }

        while (running) {
            try {
                final Socket socket = serverSocket.accept();
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        handler.handle(socket);
                    }
                });
            } catch (IOException ex) {
                // This test is necesary because the socket will be forcefully
                // closed when the program closes.
                if (running)
                    NeguraLog.severe("Accept failed", ex);
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
