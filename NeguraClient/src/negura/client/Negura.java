/*
 * TODO: I should support both IPv4 and IPv6 addresses.
 *
 * TODO: A user shouldn't be able to choose to store more blocks than there are
 * in the file system.
 *
 * TODO: When Negura starts I should perform a check to see if all the blocks
 * exist etc.
 *
 * TODO: Put a handler for the exceptions in different threads.
 *
 * TODO: User joins in DataManager.
 */

package negura.client;

import com.google.gson.JsonElement;
import java.security.NoSuchAlgorithmException;
import negura.client.net.ClientRequestHandler;
import negura.client.fs.NeguraFile;
import negura.client.fs.NeguraFileInputStream;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import negura.client.ftp.NeguraFtpServer;
import negura.client.gui.TrayGui;
import negura.client.net.BlockMaintainer;
import negura.client.net.StateMaintainer;
import negura.common.data.Operation;
import negura.common.data.RsaKeyPair;
import negura.common.data.TrafficLogger;
import negura.common.ex.NeguraEx;
import negura.common.gui.KeyPairUnlocker;
import negura.common.gui.PanicBox;
import negura.common.json.Json;
import negura.common.util.Comm;
import negura.common.net.RequestServer;
import negura.common.util.NeguraLog;
import negura.common.util.NeguraLog.SevereHandler;
import negura.common.util.Util;

public class Negura {
    private final ClientConfigManager cm;
    private final RequestServer requestServer;
    private final StateMaintainer stateMaintainer;
    private final BlockMaintainer blockMaintainer;
    private final NeguraFtpServer ftpServer;
    private final TrafficLogger trafficLogger;

    private final SevereHandler severeHandler = new SevereHandler() {
        public void terminateFor(String message, Throwable throwable) {
            // Try to close but ignore errors.
            try {
                shutdown();
            } catch (Exception ex) { }

            PanicBox.show(message, throwable);
        }
    };

    // This is special. It has to be started in it's own thread.
    private TrayGui trayGui;

    public Negura(File configFile) throws NeguraEx {
        NeguraLog.setSevereHandler(severeHandler);

        // Loading the configuration manager.
        ClientConfigManager manager = null;
        try {
            manager = new ClientConfigManager(configFile, this);
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }

        // If the private key is locked, build a GUI and prompt the user for
        // the password and quit on failure.
        RsaKeyPair rsaKeyPair = manager.getKeyPair();
        if (!rsaKeyPair.isPrivateKeyDecrypted()) {
            KeyPairUnlocker unlocker = new KeyPairUnlocker(rsaKeyPair, 5,
                    "Enter password to decrypt key pair.", 
                    "The passwords were incorrect. The application will now " +
                    "close.");
            
            if (!unlocker.openAndTryToUnlock()) {
                throw new NeguraEx("Failed to unlock private key.");
            }
        }

        cm = manager;
        trafficLogger = new TrafficLogger(cm.getTrafficAggregator(), 0.5, 120);
        ClientRequestHandler handler = new ClientRequestHandler(cm);
        requestServer = new RequestServer(cm.getServicePort(),
                cm.getThreadPoolOptions(), handler);
        ftpServer = new NeguraFtpServer(cm);
        stateMaintainer = new StateMaintainer(cm);
        blockMaintainer = new BlockMaintainer(cm);
        cm.getBlockList().setOutListener(stateMaintainer);
        cm.getBlockList().setInListener(blockMaintainer);
    }

    public void start() {
        // Starting the tray GUI in it's own thread.
        final Negura that = this;
        new Thread(new Runnable() {
            public void run() {
                trayGui = new TrayGui(that, cm);
                trayGui.start();
            }
        }).start();

        requestServer.startInOwnThread();
        stateMaintainer.start();
        blockMaintainer.start();
    }

    public void shutdown() {
        try {
            cm.save();
        } catch (IOException ex) {
            NeguraLog.severe(ex, "Failed to save the configuration.");
        }

        trayGui.stop();
        blockMaintainer.stop();
        stateMaintainer.stop();
        requestServer.stop();
        trafficLogger.shutdown();
        if (ftpServer.isRunning())
            ftpServer.stop();

        NeguraLog.flushAll();
    }

    public final NeguraFtpServer getFtpServer() {
        return ftpServer;
    }

    public final StateMaintainer getStateMaintainer() {
        return stateMaintainer;
    }

    public final RequestServer getRequestServer() {
        return requestServer;
    }

    public final TrafficLogger getTrafficLogger() {
        return trafficLogger;
    }

    public List<Integer> uploadOperations(ArrayList<Operation> operations)
            throws IOException {
        JsonObject mesg = Comm.newMessage("add-operation");
        mesg.addProperty("uid", cm.getUserId());
        mesg.add("operations", Json.toJsonElement(operations));

        JsonObject resp = Comm.readMessage(cm.getServerAddress(), mesg);

        List<Integer> firstBlockIds = new ArrayList<Integer>();

        for (JsonElement e : resp.getAsJsonArray("first-block-ids")) {
            firstBlockIds.add(e.getAsInt());
        }

        return firstBlockIds;
    }
}
