package negura.server;

import java.sql.SQLException;
import negura.server.net.ServerRequestHandler;
import negura.server.net.Announcer;
import java.io.File;
import java.io.IOException;
import negura.common.ex.NeguraEx;
import negura.common.net.RequestServer;
import negura.common.util.NeguraLog;
import negura.server.gui.MainWindow;

public class NeguraServer {
    private final boolean cli;
    private final ServerConfigManager cm;
    private final DataManager dataManager;
    private final ServerRequestHandler requestHandler;
    private final RequestServer requestServer;
    private final Announcer announcer;

    public NeguraServer(File configFile, boolean cli) {
        ServerConfigManager manager = null;
        try {
            manager = new ServerConfigManager(configFile);
        } catch (NeguraEx ex) {
            NeguraLog.severe(ex, "Error loading config file '%s': %s",
                    configFile.getAbsolutePath(), ex.getMessage());
        }

        this.cli = cli;
        cm = manager;
        dataManager = new DataManager(cm);
        announcer = new Announcer(dataManager);
        requestHandler = new ServerRequestHandler(cm, dataManager, announcer);
        requestServer = new RequestServer(cm.getPort(),
                cm.getThreadPoolOptions(), requestHandler);
    }

    public void run() {
        Runnable callRunAfterUiInit = new Runnable() {
            public void run() {
                runAfterUiInit();
            }
        };

        if (cli) {
            runAfterUiInit();
            try {
                System.in.read();
            } catch (IOException ex) { }
        } else {
            MainWindow mainWindow = new MainWindow(cm, callRunAfterUiInit);
            mainWindow.loopUntilClosed();
        }
        
        shutdown();
    }

    public void shutdown() {
        try {
            cm.save();
        } catch (IOException ex) {
            NeguraLog.severe(ex, "Failed to save the configuration.");
        }

        requestServer.stop();
        announcer.stop();
        dataManager.shutdown();

        NeguraLog.flushAll();
    }

    private void runAfterUiInit() {
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

        announcer.start();
        requestServer.startInOwnThread();

        NeguraLog.info("Started listening on %d.", cm.getPort());
    }
}
