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

package negura.client;

import negura.client.fs.NeguraFile;
import negura.client.fs.NeguraFileInputStream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import javax.xml.bind.DatatypeConverter;
import negura.client.ftp.NeguraFtpServer;
import negura.client.gui.TrayGui;
import negura.common.util.Comm;
import negura.common.RequestServer;
import negura.common.util.NeguraLog;

public class Negura {
    private ClientConfigManager cm;
    private InetSocketAddress serverAddress;
    private ClientRequestHandler requestHandler;
    private RequestServer requestServer;
    private StateMaintainer stateMaintainer;
    private TrayGui trayGui;
    private NeguraFtpServer ftpServer;

    public Negura(File configFile) {
        try {
            cm = new ClientConfigManager(configFile);
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }

        serverAddress = cm.getServerSocketAddress();

        requestHandler = new ClientRequestHandler(this, cm);
        requestServer = new RequestServer(cm.getPort(), cm.getThreadPoolSize(),
                requestHandler);
        stateMaintainer = new StateMaintainer(cm);
        ftpServer = new NeguraFtpServer(cm);
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

        startCheck();

        requestServer.startInNewThread();
        stateMaintainer.startInNewThread();

        try {
            Thread.sleep(9999);
        } catch (InterruptedException ex) { }

        if (cm.getPort() == 20000) {
            addDir(new File("/home/p/tmp/negura"), "");

            try {
                Thread.sleep(6000);
            } catch (InterruptedException ex) { }

            JsonObject mesg = Comm.newMessage("trigger-fs-update");
            Comm.readMessage(serverAddress, mesg);
        }

//        if (cm.getPort() == 20000) {
//            try {
//                Thread.sleep(3000);
//            } catch (InterruptedException ex) { }
//            try {
//                writeFile("/muzică/Yui - Again.mp3", new File("/home/p/o.mp3"));
//            } catch (Exception ex) {
//                ex.printStackTrace();
//                System.exit(1);
//            }
//        }
    }

    public void shutdown() {
        requestServer.stop();
        trayGui.stop();
        try {
            cm.save();
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }
        if (ftpServer != null)
            ftpServer.stop();
        // TODO: see why not all the threads are stopping. Is it the FTP server?
        System.exit(0);
    }

    // Checks to see if everythins is in order and tries to repair if possible.
    public void startCheck() {
        File blockDir = cm.getBlockDir();
        if (!blockDir.exists())
            blockDir.mkdir();

        // TODO: check if all the blocks are present.
    }

    public NeguraFtpServer getFtpServer() {
        return ftpServer;
    }

    public StateMaintainer getStateMaintainer() {
        return stateMaintainer;
    }

    public void writeFile(String filePath, File output) throws IOException {
        NeguraFile f = cm.getFsView().getFile(filePath);
        if (f == null)
            throw new IOException("No such file.");
        FileOutputStream out = new FileOutputStream(output);
        NeguraFileInputStream in = new NeguraFileInputStream(f, 0);

        byte[] buf = new byte[8192 * 8];
        int read;
        while ((read = in.read(buf)) >= 0)
            out.write(buf, 0, read);

        in.close();
        out.close();
    }

    public void addDir(File dir, String storePath) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory())
                addDir(f, storePath + "/" + f.getName());
            else
                addFile(f, storePath + "/" + f.getName());
        }
    }

    public void addFile(File file, String storePath) {
        NeguraLog.info("Adding file '%s' as '%s'.", file.getAbsoluteFile(),
                storePath);
        
        try {
            int blockSize = cm.getServerInfoBlockSize();
            long fileSize = file.length();
            int nrBlocks = (int) Math.ceil((double) fileSize / blockSize);

            JsonObject req = Comm.newMessage("allocate-operation");
            req.addProperty("number-of-blocks", nrBlocks);

            JsonObject respIds = Comm.readMessage(serverAddress, req);

            req = Comm.newMessage("add-operation");
            req.addProperty("uid", cm.getUserId());
            JsonObject op = new JsonObject();
            req.add("op", op);
            op.addProperty("type", "add");
            op.add("id", respIds.get("opid"));
            op.addProperty("date", new Date().getTime() / 1000);
            op.addProperty("path", storePath);
            op.addProperty("size", fileSize);
            req.add("op", op);
            JsonArray blocks = new JsonArray();
            op.add("blocks", blocks);

            FileInputStream in = new FileInputStream(file);
            MessageDigest fileHash = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[blockSize];
            int blockReadSize;

            ArrayList<Integer> newBlocks = new ArrayList<Integer>();

            for (JsonElement idElem : respIds.getAsJsonArray("block-ids")) {
                int id = idElem.getAsInt();

                // Reading the block.
                blockReadSize = in.read(block, 0, blockSize);

                // Writing block to the block file.
                String hash = cm.saveBlock(id, block, blockReadSize);

                // Update file hash.
                fileHash.update(block, 0, blockReadSize);

                // Add this block to the list of blocks.
                JsonObject b = new JsonObject();
                b.addProperty("id", id);
                b.addProperty("hash", hash);
                blocks.add(b);

                newBlocks.add(id);
            }

            in.close();

            op.addProperty("hash",
                    DatatypeConverter.printHexBinary(fileHash.digest()));

            Comm.readMessage(serverAddress, req);

            cm.pushBlocks(newBlocks, false);
        } catch (Exception ex) {
            NeguraLog.severe(ex, "Error adding file.");
        }
    }
}
