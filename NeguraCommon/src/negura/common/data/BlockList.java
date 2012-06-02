package negura.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.io.File;
import java.lang.reflect.Type;
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
    public static class TypeConverter implements JsonSerializer<BlockList>,
            JsonDeserializer<BlockList> {
        @Override
        public JsonElement serialize(BlockList t, Type type,
                JsonSerializationContext jsc) {
            JsonObject ret = new JsonObject();
            ret.addProperty("last-order-id", t.lastOrderId);

            JsonArray allocatedBlocks = new JsonArray();
            for (Integer bid : t.allocatedBlocks)
                allocatedBlocks.add(new JsonPrimitive(bid));
            ret.add("allocated", allocatedBlocks);

            JsonArray downloadedBlocks = new JsonArray();
            for (Integer bid : t.completedBlocks)
                downloadedBlocks.add(new JsonPrimitive(bid));
            ret.add("downloaded", downloadedBlocks);

            JsonArray tempBlocks = new JsonArray();
            for (Integer bid : t.tempBlocks)
                tempBlocks.add(new JsonPrimitive(bid));
            ret.add("temp-blocks", tempBlocks);

            return ret;
        }

        @Override
        public BlockList deserialize(JsonElement je, Type type,
                JsonDeserializationContext jdc) throws JsonParseException {
            BlockList ret = new BlockList();
            JsonObject obj = je.getAsJsonObject();

            ret.lastOrderId = obj.get("last-order-id").getAsInt();

            for (JsonElement e : obj.getAsJsonArray("allocated"))
                ret.allocatedBlocks.add(e.getAsInt());
            
            for (JsonElement e : obj.getAsJsonArray("downloaded"))
                ret.completedBlocks.add(e.getAsInt());
            
            ret.downloadQueue.addAll(ret.allocatedBlocks);
            ret.downloadQueue.removeAll(ret.completedBlocks);

            for (JsonElement e : obj.getAsJsonArray("temp-blocks"))
                ret.tempBlocks.add(e.getAsInt());

            return ret;
        }
    }

    public interface InListener {
        void addedBlocks(List<Integer> blockIds);
        void removedBlocks(List<Integer> blockIds);
    }

    public interface OutListener {
        void completedBlock(int blockId);
    }

    private int lastOrderId = 0;

    // The allocated blocks memorized in order.
    private ArrayList<Integer> allocatedBlocks = new ArrayList<Integer>();

    // The blocks which need to be downloaded, in the allocated order.
    private ArrayList<Integer> downloadQueue = new ArrayList<Integer>();

    // The completed blocks as a set, for fast lookup.
    private HashSet<Integer> completedBlocks = new HashSet<Integer>();

    // The completed blocks of which the server isn't aware have been completed.
    private HashSet<Integer> serverUnawareComplated = new HashSet<Integer>();

    // The temporary blocks that I have.
    private HashSet<Integer> tempBlocks = new HashSet<Integer>();

    // These are not saved.
    private transient File downloadedBlockDir;
    private transient File tempBlockDir;
    private transient InListener inListener = null;
    private transient OutListener outListener = null;

    public BlockList() {
    }

    public synchronized void setDataDir(File dataDir) {
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

    public synchronized void addToTemporary(int blockId) {
        tempBlocks.add(blockId);
    }

    public synchronized int getLastOrderId() {
        return lastOrderId;
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

    public synchronized File getFileToSaveBlockTo(int blockId) {
        return new File(downloadedBlockDir, Integer.toString(blockId));
    }

    public synchronized File getFileToSaveTempBlockTo(int blockId) {
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
