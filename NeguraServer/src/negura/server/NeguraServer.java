package negura.server;

import java.io.File;
import java.io.IOException;
import negura.common.RequestServer;
import negura.common.util.NeguraLog;

public class NeguraServer {
    private ServerConfigManager cm;
    private DataManager dataManager;
    private ServerRequestHandler requestHandler;
    private RequestServer requestServer;
    private Announcer announcer;

    public NeguraServer(File configFile) {
        try {
            cm = new ServerConfigManager(configFile);
        } catch (IOException ex) {
            NeguraLog.severe(ex, "Couldn't read config file.");
        }
        dataManager = new DataManager(cm);

        announcer = new Announcer(dataManager);
        requestHandler = new ServerRequestHandler(cm, dataManager, announcer);
        requestServer = new RequestServer(cm.getPort(), 10, requestHandler);
    }

    public void run() {
        announcer.startInNewThread();
        requestServer.startInNewThread();

        try {
            System.in.read();
        } catch (IOException ex) { }
        NeguraLog.info("Exiting...");
        shutdown();
    }

    public void shutdown() {
        requestServer.stop();
        announcer.stop();
        dataManager.shutdown();
        System.out.println("4");System.out.flush();
    }
}
