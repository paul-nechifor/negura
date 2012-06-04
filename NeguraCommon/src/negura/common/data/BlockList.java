package negura.common.data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import negura.common.ex.NeguraError;
import negura.common.util.NeguraLog;

/**
 * A data type to manage the allocated block list.
 * @author Paul Nechifor
 */
public class BlockList {
    public static class Builder {
        public int lastOrderId = 0;
        public ArrayList<Integer> allocated = new ArrayList<Integer>();
        public HashSet<Integer> completed = new HashSet<Integer>();
        public HashSet<Integer> serverUnawareCompleted = new HashSet<Integer>();
        public HashSet<Integer> tempBlocks = new HashSet<Integer>();
    }

    public interface InListener {
        void addedBlocks(List<Integer> blockIds);
        void addedTempBlocks(List<Integer> blockIds);
        void removedBlocks(List<Integer> blockIds);
    }

    public interface OutListener {
        void completedBlock(int blockId);
    }

    private int lastOrderId;

    // The allocated blocks memorized in order.
    private final ArrayList<Integer> allocatedBlocks;

    // The completed blocks as a set, for fast lookup.
    private final HashSet<Integer> completedBlocks;

    // The completed blocks of which the server isn't aware have been completed.
    private final HashSet<Integer> serverUnawareComplated;

    // The temporary blocks that I have.
    private final HashSet<Integer> tempBlocks;

    // The blocks which need to be downloaded, in the allocated order.
    private final ArrayList<Integer> downloadQueue;

    // These are not saved.
    private final File downloadedBlockDir;
    private final File tempBlockDir;
    private InListener inListener = null;
    private OutListener outListener = null;

    public BlockList(Builder builder, File dataDir) {
        lastOrderId = builder.lastOrderId;
        allocatedBlocks = builder.allocated;
        completedBlocks = builder.completed;
        serverUnawareComplated = builder.serverUnawareCompleted;
        tempBlocks = builder.tempBlocks;

        downloadQueue = new ArrayList<Integer>(allocatedBlocks);
        downloadQueue.removeAll(completedBlocks);

        // Initalizing directories.
        downloadedBlockDir = new File(dataDir, "blocks");
        tempBlockDir = new File(dataDir, "temp_blocks");

        if (!downloadedBlockDir.exists() && !downloadedBlockDir.mkdirs()) {
            NeguraLog.severe("Failed to create '%d'.", downloadedBlockDir);
        }

        if (!tempBlockDir.exists() && !tempBlockDir.mkdirs()) {
            NeguraLog.severe("Failed to create '%d'.", tempBlockDir);
        }
    }

    public synchronized void setInListener(InListener inListener) {
        if (this.inListener != null)
            throw new NeguraError("Can't have more than one in listener.");

        this.inListener = inListener;

        // Since this is the start tell him all the download queue.
        if (!downloadQueue.isEmpty())
            this.inListener.addedBlocks(downloadQueue);
    }

    public synchronized void setOutListener(OutListener outListener) {
        if (this.outListener != null)
            throw new NeguraError("Can't have more than one out listener.");

        this.outListener = outListener;

        // Since this is the start tell him about all the completed blocks
        // the server isn't aware of.
        for (Integer blockId : serverUnawareComplated)
            this.outListener.completedBlock(blockId);
    }

    public synchronized void addModifications(List<Integer> modifications) {
        List<Integer> addedBlocks = new ArrayList<Integer>();
        List<Integer> removedBlocks = new ArrayList<Integer>();

        for (Integer mod : modifications) {
            if (mod > 0) {
                addedBlocks.add(mod);
            } else {
                removedBlocks.add(-mod);
            }
        }
        allocatedBlocks.removeAll(removedBlocks);
        downloadQueue.removeAll(removedBlocks);
        allocatedBlocks.addAll(addedBlocks);
        downloadQueue.addAll(addedBlocks);
        lastOrderId += modifications.size();

        if (inListener != null) {
            inListener.removedBlocks(removedBlocks);
            inListener.addedBlocks(addedBlocks);
        }
    }

    public synchronized void addToCompleted(Integer blockId) {
        downloadQueue.remove(blockId);
        completedBlocks.add(blockId);
        serverUnawareComplated.add(blockId);

        if (outListener != null) {
            outListener.completedBlock(blockId);
        }
    }

    public synchronized void addToServerAwareCompleted(Integer blockId) {
        serverUnawareComplated.remove(blockId);
    }

    public synchronized void addToTemporary(List<Integer> blockIds) {
        tempBlocks.addAll(blockIds);

        if (inListener != null) {
            inListener.addedTempBlocks(blockIds);
        }
    }

    public synchronized int getLastOrderId() {
        return lastOrderId;
    }

    public synchronized Builder toBuilder() {
        Builder ret = new Builder();

        ret.lastOrderId = lastOrderId;
        ret.allocated = allocatedBlocks;
        ret.completed = completedBlocks;
        ret.serverUnawareCompleted = serverUnawareComplated;
        ret.tempBlocks = tempBlocks;

        return ret;
    }
    
    public synchronized File getBlockFileIfExists(int blockId) {
        if (completedBlocks.contains(blockId)) {
            return getFileToSaveBlockTo(blockId);
        } else if (tempBlocks.contains(blockId)) {
            return getFileToSaveTempBlockTo(blockId);
        } else {
            return null;
        }
    }

    public final File getFileToSaveBlockTo(int blockId) {
        return new File(downloadedBlockDir, Integer.toString(blockId));
    }

    public final File getFileToSaveTempBlockTo(int blockId) {
        return new File(tempBlockDir, Integer.toString(blockId));
    }

    public synchronized boolean isTempBlocksEmpty() {
        return tempBlocks.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public synchronized Set<Integer> getTempBlocks() {
        return (HashSet<Integer>) tempBlocks.clone();
    }

    public synchronized int getAllocatedNumber() {
        return allocatedBlocks.size();
    }

    public synchronized int getFinishedNumber() {
        return completedBlocks.size();
    }

    public synchronized int getTempBlocksNumber() {
        return tempBlocks.size();
    }
}
