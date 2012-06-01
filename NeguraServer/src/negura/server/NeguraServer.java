package negura.server;

import java.sql.SQLException;
import negura.server.net.ServerRequestHandler;
import negura.server.net.Announcer;
import java.io.File;
import java.io.IOException;
import negura.common.net.RequestServer;
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
            NeguraLog.severe(ex, "Couldn't read config file at '%s'.",
                    configFile.getAbsolutePath());
        }

        dataManager = new DataManager(cm);
        announcer = new Announcer(dataManager);
        requestHandler = new ServerRequestHandler(cm, dataManager, announcer);
        requestServer = new RequestServer(cm.getPort(),
                cm.getThreadPoolOptions(), requestHandler);
    }

    public void run() {
        // If this is the first run the database must be initalized.
        if (cm.getFirstRun()) {
            try {
                dataManager.createTables();
                dataManager.createOriginalBlocks(cm.getVirtualDiskBlocks());
                dataManager.initializeSettings(cm.getVirtualDiskBlocks());
                cm.setFirstRunOff();
            } catch (SQLException ex) {
                NeguraLog.severe(ex);
            }
        }

        announcer.turnOn();
        requestServer.startInNewThread();

        try {
            System.in.read();
        } catch (IOException ex) { }
        NeguraLog.info("Exiting...");
        shutdown();
    }

    public void shutdown() {
        requestServer.requestStop();
        announcer.turnOff();
        dataManager.shutdown();
    }
}
