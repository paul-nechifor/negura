/*
 * Folosesc:
 *  - Apache FtpServer pe partea de client pentru a arăta spre exterior ca un
 *    sistem de fisiere ce poate fi montat.
 *  - PostgreSQL pe artea de server pentru a gestiona toate datele legate de
 *    utilizatori, blocuri și fișiere.
 *  - Connection pool pentru baza de date (commons-dbcp, commons-pool).
 *  - JSON cu pachetul Gson pentru comunicarea între diferitele părți pentru că
 *    e ușor de modificat.
 *  - Thread pool pe partea de client și server pentru răspunderea la cererile
 *    primite din socket-uri.
 *  - SWT pentru a avea o interfată grafică nativă cu tot cu partea din tray.
 *  - MigLayout pentru SWT pentru a descrie interfata mai concis.
 */

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
import negura.client.net.StateMaintainer;
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
import negura.common.data.Block;
import negura.common.data.BlockList;
import negura.common.data.Operation;
import negura.common.json.Json;
import negura.common.util.Comm;
import negura.common.net.RequestServer;
import negura.common.util.NeguraLog;
import negura.common.util.Os;
import negura.common.util.Util;

public class Negura {
    private final ClientConfigManager cm;
    private final RequestServer requestServer;
    private final StateMaintainer stateMaintainer;
    private final NeguraFtpServer ftpServer;

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

        ClientRequestHandler handler = new ClientRequestHandler(this, cm);
        requestServer = new RequestServer(cm.getServicePort(),
                cm.getThreadPoolOptions(), handler);
        stateMaintainer = new StateMaintainer(cm);
        ftpServer = new NeguraFtpServer(cm);
    }




    //////////////////////////////////////////////////////////////////////////

    public void start() {
        // Starting the tray GUI in it's own thread.
        final Negura that = this;
        new Thread(new Runnable() {
            public void run() {
                trayGui = new TrayGui(that, cm);
                trayGui.start();
            }
        }).start();

        requestServer.startInNewThread();
        stateMaintainer.startInNewThread();



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

    public void shutdown(boolean complain) {
        try {
            requestServer.requestStop();
            trayGui.requestStop();
            cm.save();
            if (ftpServer != null)
                ftpServer.requestStop();
        } catch (Exception ex) {
            if (complain)
                NeguraLog.severe(ex);
        }
        // TODO: see why not all the threads are stopping. Is it the FTP server?
        System.exit(0);
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
                addDir(f, storePath + "/" + f.getName());
            else
                addFile(f, storePath + "/" + f.getName());
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
        MessageDigest blockHash = MessageDigest.getInstance("SHA-256");
        byte[] block = new byte[blockSize];
        int blockReadSize;

        for (int i = 0; i < blocks.length; i++) {
            // Reading the block.
            blockReadSize = in.read(block, 0, blockSize);

            // Getting the block hash.
            blockHash.update(block, 0, blockReadSize);
            String hash = DatatypeConverter.printHexBinary(blockHash.digest());

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

            blockList.haveTempBlock(blockId);
            blockId++;
        }

        // Removing the temp-temp dir.
        if (!dirForTempBlocks.delete()) {
            NeguraLog.warning("Failed to delete '%s'.",
                    dirForTempBlocks.getAbsoluteFile());
        }
    }
}
