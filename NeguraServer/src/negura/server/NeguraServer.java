package negura.server;

import java.sql.SQLException;
import negura.server.net.ServerRequestHandler;
import negura.server.net.Announcer;
import java.io.File;
import java.io.IOException;
import negura.common.data.RsaKeyPair;
import negura.common.ex.NeguraEx;
import negura.common.gui.KeyPairUnlocker;
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

    public NeguraServer(File configFile, boolean cli) throws NeguraEx {
        ServerConfigManager manager = null;
        try {
            manager = new ServerConfigManager(configFile, this);
        } catch (NeguraEx ex) {
            NeguraLog.severe(ex, "Error loading config file '%s': %s",
                    configFile.getAbsolutePath(), ex.getMessage());
        }

        // If the private key is locked, build a GUI and prompt the user for
        // the password and quit on failure.
        RsaKeyPair rsaKeyPair = manager.getServerKeyPair();
        if (!rsaKeyPair.isPrivateKeyDecrypted()) {
            KeyPairUnlocker unlocker = new KeyPairUnlocker(rsaKeyPair, 5,
                    "Enter password to decrypt server private key.",
                    "The passwords were incorrect. The server will close.");

            if (!unlocker.openAndTryToUnlock()) {
                throw new NeguraEx("Failed to unlock private key.");
            }
        }

        this.cli = cli;
        cm = manager;
        dataManager = new DataManager(cm);
        announcer = new Announcer(cm);
        requestHandler = new ServerRequestHandler(cm);
        requestServer = new RequestServer(cm.getPort(),
                cm.getThreadPoolOptions(), requestHandler);
    }
    
    public final DataManager getDataManager() {
        return dataManager;
    }

    public final Announcer getAnnouncer() {
        return announcer;
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
        // TODO: Reenable this.
//        try {
//            cm.save();
//        } catch (IOException ex) {
//            NeguraLog.severe(ex, "Failed to save the configuration.");
//        }

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
