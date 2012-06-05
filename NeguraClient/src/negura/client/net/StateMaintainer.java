package negura.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import negura.client.ClientConfigManager;
import negura.client.fs.NeguraFsView;
import negura.common.Service;
import negura.common.data.BlockList;
import negura.common.data.ForceableScheduler;
import negura.common.data.Operation;
import negura.common.json.Json;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;

/**
 * Maintains the state of the system depeding on what the server wants.
 * @author Paul Nechifor
 */
public class StateMaintainer extends Service implements BlockList.OutListener {
    private final ClientConfigManager cm;
    private final BlockList blockList;
    private final NeguraFsView fsView;
    private final ArrayList<Integer> completed = new ArrayList<Integer>();

    private ScheduledExecutorService scheduler;

    private ForceableScheduler fileSystemUpdateTask;
    private ForceableScheduler blockListUpdateTask;
    private ForceableScheduler sendDownloadedBlocksTask;

    public StateMaintainer(ClientConfigManager cm) {
        this.cm = cm;
        this.blockList = cm.getBlockList();
        this.fsView = cm.getFsView();
    }

    @Override
    protected void onStart() {
        int rateMillis = 5 * 60 * 1000;

        scheduler = Executors.newSingleThreadScheduledExecutor();

        fileSystemUpdateTask = new ForceableScheduler(scheduler, rateMillis,
                new Runnable() {
            @Override
            public void run() {
                fileSystemUpdate();
            }
        });
        blockListUpdateTask = new ForceableScheduler(scheduler, rateMillis,
                new Runnable() {
            @Override
            public void run() {
                blockListUpdate();
            }
        });
        sendDownloadedBlocksTask = new ForceableScheduler(scheduler, rateMillis,
                new Runnable() {
            @Override
            public void run() {
                sendCompletedBlocks();
            }
        });

        started();
    }

    @Override
    protected void onStop() {
        fileSystemUpdateTask.stop();
        blockListUpdateTask.stop();
        sendDownloadedBlocksTask.stop();
        scheduler.shutdown();
        
        stopped();
    }

    public void triggerFileSystemUpdate() {
        fileSystemUpdateTask.forceExecutionNow();
    }

    public void triggerBlockListUpdate() {
        blockListUpdateTask.forceExecutionNow();
    }

    public void triggerSendDownloadedBlocks() {
        sendDownloadedBlocksTask.forceExecutionNow();
    }

    private void fileSystemUpdate() {
        JsonObject mesg = Comm.newMessage("filesystem-state");
        mesg.addProperty("after", fsView.getLastOperationId());
        JsonObject resp = null;

        try {
            resp = Comm.readMessage(cm.getServerAddress(), mesg);
        } catch (IOException ex) {
            NeguraLog.warning(ex, "Server error.");
            return;
        }

        List<Operation> ops = new ArrayList<Operation>();
        for (JsonElement e : resp.getAsJsonArray("operations")) {
            ops.add(Json.fromJsonObject(e.getAsJsonObject(), Operation.class));
        }
        
        if (!ops.isEmpty()) {
            fsView.addOperations(ops);
        }
    }

    private void blockListUpdate() {
        JsonObject mesg = Comm.newMessage("get-block-list");
        mesg.addProperty("uid", cm.getUserId());
        mesg.addProperty("after", blockList.getLastOrderId());
        JsonObject resp = null;

        try {
            resp = Comm.readMessage(cm.getServerAddress(), mesg);
        } catch (IOException ex) {
            NeguraLog.warning(ex, "Server error.");
            return;
        }

        List<Integer> blocks = new ArrayList<Integer>();
        for (JsonElement e : resp.getAsJsonArray("blocks")) {
            blocks.add(e.getAsInt());
        }

        if (!blocks.isEmpty()) {
            blockList.addModifications(blocks);
        }
    }

    @SuppressWarnings("unchecked")
    private void sendCompletedBlocks() {
        // Take the blocks out so that I don't hog the lock.
        ArrayList<Integer> toSend;
        synchronized (completed) {
            if (completed.isEmpty())
                return;
            
            // TODO: Maybe change this to eliminate clone.
            toSend = (ArrayList<Integer>) completed.clone();
            completed.clear();
        }

        JsonObject mesg = Comm.newMessage("have-blocks");
        mesg.addProperty("uid", cm.getUserId());
        mesg.add("blocks", Json.toJsonElement(toSend));

        try {
            Comm.readMessage(cm.getServerAddress(), mesg);
            NeguraLog.info("Sent completed blocks: " + toSend);

            for (Integer blockId : toSend)
                blockList.addToServerAwareCompleted(blockId);
        } catch (IOException ex) {
            NeguraLog.warning(ex, "Server error.");

            // On failure add them back.
            synchronized (completed) {
                completed.addAll(toSend);
            }
        }
    }

    @Override
    public void completedBlock(int blockId) {
        synchronized (completed) {
            completed.add(blockId);
        }
    }
}
