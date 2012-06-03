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

import java.security.NoSuchAlgorithmException;
import negura.client.net.ClientRequestHandler;
import negura.client.fs.NeguraFile;
import negura.client.fs.NeguraFileInputStream;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;
import negura.client.ftp.NeguraFtpServer;
import negura.client.gui.TrayGui;
import negura.client.net.BlockMaintainer;
import negura.client.net.StateMaintainer;
import negura.common.data.Block;
import negura.common.data.BlockList;
import negura.common.data.Operation;
import negura.common.data.TrafficLogger;
import negura.common.json.Json;
import negura.common.util.Comm;
import negura.common.net.RequestServer;
import negura.common.util.NeguraLog;
import negura.common.util.Os;
import negura.common.util.Sha;
import negura.common.util.Util;

public class Negura {
    private final ClientConfigManager cm;
    private final RequestServer requestServer;
    private final StateMaintainer stateMaintainer;
    private final BlockMaintainer blockMaintainer;
    private final NeguraFtpServer ftpServer;
    private final TrafficLogger trafficLogger;

    // This is special. It has to be started in it's own thread.
    private TrayGui trayGui;

    public Negura(File configFile) {
        // Loading the configuration manager.
        ClientConfigManager manager = null;
        try {
            manager = new ClientConfigManager(configFile, this);
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }
        cm = manager;

        trafficLogger = new TrafficLogger(cm.getTrafficAggregator(), 0.5, 120);
        ClientRequestHandler handler = new ClientRequestHandler(this, cm);
        requestServer = new RequestServer(cm.getServicePort(),
                cm.getThreadPoolOptions(), handler);
        ftpServer = new NeguraFtpServer(cm);
        stateMaintainer = new StateMaintainer(cm);
        blockMaintainer = new BlockMaintainer(cm, stateMaintainer);
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

        if (cm.getServicePort() == 20000) {
            try {
                Thread.sleep(10000);
                addDir(new File("/home/p/tmp/negura"), "");
                JsonObject mesg = Comm.newMessage("trigger-fs-update");
                Comm.readMessage(cm.getServerAddress(), mesg);
            } catch (Exception ex) {
                NeguraLog.severe(ex);
            }
        }
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

    public void writeFile(String filePath, File output) throws IOException {
        NeguraFile f = cm.getFsView().getFile(filePath);
        if (f == null)
            throw new IOException("No such file.");
        FileOutputStream out = new FileOutputStream(output);
        NeguraFileInputStream in = new NeguraFileInputStream(f, 0);

        Util.copyStream(in, out, new byte[8192 * 8]);
    }

    public void addDir(File dir, String storePath)
            throws NoSuchAlgorithmException, IOException {
        for (File f : dir.listFiles()) {
            if (f.isDirectory())
                addDir(f, storePath + File.separator + f.getName());
            else
                addFile(f, storePath + File.separator + f.getName());
        }
    }

    public void addFile(File file, String storePath)
            throws NoSuchAlgorithmException, IOException {
        NeguraLog.info("Adding file '%s' as '%s'.", file.getAbsoluteFile(),
                storePath);

        // The blocks will be saved here by block hash and then moved to the
        // temp block dir.
        File dirForTempBlocks = Os.createRandomFile(cm.getDataDir(),
                Os.FileType.DIR, null, null);

        BlockList blockList = cm.getBlockList();
        int blockSize = cm.getBlockSize();
        long fileSize = file.length();
        int nrBlocks = (int) Math.ceil((double) fileSize / blockSize);
        Block[] blocks = new Block[nrBlocks];
        File[] blockFiles = new File[nrBlocks];

        FileInputStream in = new FileInputStream(file);
        MessageDigest fileHash = MessageDigest.getInstance("SHA-256");
        byte[] block = new byte[blockSize];
        int blockReadSize;

        for (int i = 0; i < blocks.length; i++) {
            // Reading the block.
            blockReadSize = in.read(block, 0, blockSize);

            // Getting the block hash.
            String hash = Sha.get256(block, 0, blockReadSize);

            // Saving the file.
            blockFiles[i] = new File(dirForTempBlocks, hash);
            FileOutputStream out = new FileOutputStream(blockFiles[i]);
            out.write(block, 0, blockReadSize);
            out.close();

            // Update file hash.
            fileHash.update(block, 0, blockReadSize);

            blocks[i] = new Block(0, hash);
        }

        in.close();

        Operation op = new Operation();
        op.type = "add";
        op.path = storePath;
        op.size = fileSize;
        op.blocks = blocks;
        op.hash = DatatypeConverter.printHexBinary(fileHash.digest());
        op.signature = "my signature. this will be changed by the server";

        JsonObject mesg = Comm.newMessage("add-operation");
        mesg.addProperty("uid", cm.getUserId());
        mesg.add("operation", Json.toJsonElement(op));
        JsonObject resp = Comm.readMessage(cm.getServerAddress(), mesg);
        int firstBlockId = resp.get("first-block-id").getAsInt();

        // Moving the blocks to the temp dir and registering their presence.
        int blockId = firstBlockId;
        File moveTo;
        boolean moved;
        for (int i = 0; i < blocks.length; i++) {
            moveTo = blockList.getFileToSaveTempBlockTo(blockId);
            moved = blockFiles[i].renameTo(moveTo);
            
            if (!moved) {
                NeguraLog.severe("Failed to move '%s' to '%s'.", blockFiles[i],
                        moveTo);
            }

            blockList.addToTemporary(blockId);
            blockId++;
        }

        // Removing the temp-temp dir.
        if (!dirForTempBlocks.delete()) {
            NeguraLog.warning("Failed to delete '%s'.",
                    dirForTempBlocks.getAbsoluteFile());
        }
    }
}
