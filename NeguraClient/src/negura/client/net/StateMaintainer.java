package negura.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import negura.client.ClientConfigManager;
import negura.common.util.Comm;
import negura.common.Service;
import negura.common.data.BlockList;
import negura.common.data.Operation;
import negura.common.ex.NeguraError;
import negura.common.json.Json;
import negura.common.util.NeguraLog;
import negura.common.util.Util;

/**
 * @author Paul Nechifor
 */
public class StateMaintainer extends Service {
    private final ClientConfigManager cm;
    private final byte[] buffer;

    private final HashSet<Integer> completed = new HashSet<Integer>();

    private long lastSent = System.currentTimeMillis();
    private int sendAt = 60 * 1000;
    private int updateBlockListEvery = 5 * 60 * 1000; // Every 5 minutes.
    // At startup, trigger block list and file system update.
    private int updateFileSystemEvery = 5 * 60 * 1000;
    private long lastUpdatedBlockList = 0;
    private long lastUpdatedFileSystem = 0;

    public StateMaintainer(ClientConfigManager cm) {
        this.cm = cm;
        this.buffer = new byte[cm.getBlockSize()];
        this.sendAt = 60 * 1000;
    }

    public void run() {
        List<Integer> downloadQueue;
        BlockList blockList = cm.getBlockList();
        Map<Integer, ArrayList<String>> peers;
        long now;

        while (continueRunning) {
            try {
                Thread.sleep(1061);
            } catch (InterruptedException ex) { }
            now = System.currentTimeMillis();

            if (now - lastUpdatedBlockList > updateBlockListEvery) {
                try {
                    tryToUpdateBlockList();
                } catch (UnknownHostException ex) {
                    NeguraLog.warning(ex);
                } catch (IOException ex) {
                    NeguraLog.warning(ex);
                }
                lastUpdatedBlockList = System.currentTimeMillis();
            }

            if (now - lastUpdatedFileSystem > updateFileSystemEvery) {
                try {
                    tryToUpdateFileSystem();
                } catch (UnknownHostException ex) {
                    NeguraLog.warning(ex);
                } catch (IOException ex) {
                    NeguraLog.warning(ex);
                }
                lastUpdatedFileSystem = System.currentTimeMillis();
            }

            if (now - lastSent > sendAt) {
                sendCompletedBlocks();
                lastSent = System.currentTimeMillis();
            }

            if (blockList.isDownloadQueueEmpty())
                continue;

            downloadQueue = blockList.getDownloadQueue();

            // If the blocks are in my temp blocks, extract those that are.
            if (!blockList.isTempBlocksEmpty()) {
                copyTempBlocks(downloadQueue, blockList.getTempBlocks());
            }

            cm.getPeerCache().preemptivelyCache(downloadQueue);

            for (int id : downloadQueue)
                tryToDownload(id, cm.getPeerCache().getPeersForBlock(id));

            // If I got to this point and the queue is now empty, it means that
            // I've just now finished downloading all the blocks that were
            // allocated to me so I'd best flush the completed blocks and not
            // wait.
            if (cm.getBlockList().isDownloadQueueEmpty())
                sendCompletedBlocks();
        }
    }
    
    public void triggerBlockListUpdate() {
        lastUpdatedBlockList = 0;
    }

    public void triggerFileSystemUpdate() {
        lastUpdatedFileSystem = 0;
    }

    private void tryToUpdateBlockList() throws IOException {
        JsonObject mesg = Comm.newMessage("get-block-list");
        mesg.addProperty("uid", cm.getUserId());
        mesg.addProperty("after", cm.getBlockList().getLastOrderId());
        JsonObject resp = Comm.readMessage(cm.getServerAddress(), mesg);

        List<Integer> blocks = new ArrayList<Integer>();
        for (JsonElement e : resp.getAsJsonArray("blocks")) {
            blocks.add(e.getAsInt());
        }

        cm.getBlockList().addModifications(blocks);
    }

    private void tryToUpdateFileSystem() throws UnknownHostException,
            IOException {
        JsonObject mesg = Comm.newMessage("filesystem-state");
        mesg.addProperty("after", cm.getFsView().getLastOperationId());
        JsonObject resp = Comm.readMessage(cm.getServerAddress(), mesg);

        List<Operation> ops = new ArrayList<Operation>();
        for (JsonElement e : resp.getAsJsonArray("operations")) {
            ops.add(Json.fromJsonObject(e.getAsJsonObject(), Operation.class));
        }

        cm.getFsView().addOperations(ops);
    }

    private void tryToDownload(int bid, List<InetSocketAddress> peers) {
        if (peers == null) {
            NeguraLog.warning("No peers to download block %d from.", bid);
            return;
        }

        for (InetSocketAddress address : peers) {
            int read = Comm.readBlock(buffer, 0, -1, bid, address);
            if (read <= 0) // Couldn't get block.
                continue;

            if (!saveBlock(bid, buffer, read))
                continue;

            if (completed.contains(bid)) {
                throw new NeguraError("Already have.");
            }

            completed.add(bid);
            cm.getBlockList().haveDownloadedBlock(bid);

            // The block was downloaded so I can break out.
            break;
        }
    }

    private boolean saveBlock(int bid, byte[] block, int size) {
        File blockFile = cm.getBlockList().getFileToSaveBlockTo(bid);

        // TODO: Check the hash for this block.

        try {
            FileOutputStream out = new FileOutputStream(blockFile);
            out.write(block, 0, size);
            out.close();
            return true;
        } catch (IOException ex) {
            NeguraLog.warning(ex, "Couldn't write block %d.", bid);
            return false;
        }
    }

    /**
     * Copies the temporary blocks as if they were downloaded.
     * @param downloadQueue     The blocks which I might have on the system.
     * @param tempBlocks        The blocks which I have on the system.
     */
    private void copyTempBlocks(List<Integer> downloadQueue,
            Set<Integer> tempBlocks) {
        BlockList blockList = cm.getBlockList();
        File fin, fout;
        FileInputStream fis;
        FileOutputStream fos;
        byte[] copyBuffer = new byte[8 * 1024];

        ArrayList<Integer> toBeDownloaded = new ArrayList<Integer>();
        for (Integer blockId : downloadQueue) {
            if (tempBlocks.contains(blockId))
                toBeDownloaded.add(blockId);
        }

        for (int blockId : toBeDownloaded) {
            
            try {
                fin = blockList.getFileToSaveTempBlockTo(blockId);
                fout = blockList.getFileToSaveBlockTo(blockId);
                fis = new FileInputStream(fin);
                fos = new FileOutputStream(fout);
                Util.copyStream(fis, fos, copyBuffer);

                if (completed.contains(blockId)) {
                    throw new NeguraError("Already have.");
                }

                completed.add(blockId);
                cm.getBlockList().haveDownloadedBlock(blockId);
            } catch (IOException ex) {
                NeguraLog.warning(ex, "Error copying block.");
            }
        }

        // Removing the completed ones.
        downloadQueue.removeAll(completed);
    }
    private void sendCompletedBlocks() {
        if (completed.isEmpty())
            return;

        JsonObject mesg = Comm.newMessage("have-blocks");
        mesg.addProperty("uid", cm.getUserId());
        JsonArray list = new JsonArray();
        for (int id : completed)
            list.add(new JsonPrimitive(id));
        mesg.add("blocks", list);

        try {
            Comm.readMessage(cm.getServerAddress(), mesg);
            NeguraLog.info("Sent downloaded blocks: " + completed);
            completed.clear();
        } catch (Exception ex) {
            NeguraLog.warning(ex, "Couldn't communicate with the server.");
        }
    }


}
