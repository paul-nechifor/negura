package negura.client;

import negura.client.fs.NeguraFsView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import negura.common.util.Comm;
import negura.common.Service;
import negura.common.data.Operation;
import negura.common.util.NeguraLog;

/**
 * Manages the download of the blocks which have been allocated to this user by
 * the server. All the methods are private and are running within a single
 * thread so no synchronization is needed.
 * @author Paul Nechifor
 */
public class StateMaintainer extends Service {
    private ClientConfigManager cm;
    private byte[] buffer;
    private ArrayList<Integer> completed = new ArrayList<Integer>();
    private PeerCache peerCache;
    private long lastSent = System.currentTimeMillis();
    private int sendAt = 60 * 1000;
    private int updateBlockListEvery = 5 * 60 * 1000; // Every 5 minutes.
    // At startup, trigger block list and file system update.
    private int updateFileSystemEvery = 5 * 60 * 1000;
    private long lastUpdatedBlockList = 0;
    private long lastUpdatedFileSystem = 0;

    public StateMaintainer(ClientConfigManager cm) {
        this.cm = cm;
        this.buffer = new byte[cm.getServerInfoBlockSize()];
        this.sendAt = 60 * 1000;
        this.peerCache = cm.getPeerCache();
    }

    public void run() {
        List<Integer> downloadQueue;
        Map<Integer, ArrayList<String>> peers;
        long now;

        while (running) {
            tryToSleep(1061);
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

            if (cm.isDownloadQueueEmpty())
                continue;

            downloadQueue = cm.getDownloadQueue();
            peerCache.preemptivelyCache(downloadQueue);

            for (int id : downloadQueue)
                tryToDownload(id, peerCache.getPeersForBlock(id));

            // If I got to this point and the queue is now empty, it means that
            // I've just now finished downloading all the blocks that were
            // allocated to me so I'd best flush the completed blocks and not
            // wait.
            if (cm.isDownloadQueueEmpty())
                sendCompletedBlocks();
        }
    }
    
    public void triggerBlockListUpdate() {
        lastUpdatedBlockList = 0;
    }

    public void triggerFileSystemUpdate() {
        lastUpdatedFileSystem = 0;
    }

    private void tryToUpdateBlockList() throws UnknownHostException,
            IOException {
        // TODO: specify from where the new blocks should be listed.
        JsonObject mesg = Comm.newMessage("get-block-list");
        mesg.addProperty("uid", cm.getUserId());
        JsonObject resp = Comm.readMessage(cm.getServerSocketAddress(), mesg);

        List<Integer> blocks = new ArrayList<Integer>();
        for (JsonElement e : resp.getAsJsonArray("blocks")) {
            blocks.add(e.getAsInt());
        }

        cm.pushBlocks(blocks, true);
    }

    private void tryToUpdateFileSystem() throws UnknownHostException,
            IOException {
        NeguraFsView fsView = cm.getFsView();
        
        JsonObject mesg = Comm.newMessage("filesystem-state");
        mesg.addProperty("after", fsView.getLastOperationId());
        JsonObject resp = Comm.readMessage(cm.getServerSocketAddress(), mesg);

        List<Operation> ops = new ArrayList<Operation>();
        for (JsonElement e : resp.getAsJsonArray("operations"))
            ops.add(Operation.fromJson(e.getAsJsonObject()));

        fsView.addOperations(ops);
    }

    private void tryToDownload(int id, List<InetSocketAddress> peers) {
        if (peers == null) {
            NeguraLog.warning("No peers to download block %d from.", id);
            return;
        }

        for (InetSocketAddress address : peers) {
            int read = Comm.readBlock(buffer, 0, -1, id, address);
            if (read <= 0) // Couldn't get block.
                continue;

            cm.saveBlock(id, buffer, read);
            completed.add(id);
            break;
        }
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
            Comm.readMessage(cm.getServerSocketAddress(), mesg);
            NeguraLog.info("Sent downloaded blocks: " + completed);
            completed.clear();
        } catch (Exception ex) {
            NeguraLog.warning(ex, "Couldn't communicate with the server.");
        }
    }
}
