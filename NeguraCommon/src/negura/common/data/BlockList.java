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
            for (Integer bid : t.downloadedBlocks)
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
                ret.downloadedBlocks.add(e.getAsInt());
            
            ret.downloadQueue.addAll(ret.allocatedBlocks);
            ret.downloadQueue.removeAll(ret.downloadedBlocks);

            for (JsonElement e : obj.getAsJsonArray("temp-blocks"))
                ret.tempBlocks.add(e.getAsInt());

            return ret;
        }
    }

    private int lastOrderId = 0;

    // The allocated blocks memorized in order.
    private ArrayList<Integer> allocatedBlocks = new ArrayList<Integer>();

    // The blocks which need to be downloaded, in the allocated order.
    private ArrayList<Integer> downloadQueue = new ArrayList<Integer>();

    // The downloaded blocks as a set, for fast lookup.
    private HashSet<Integer> downloadedBlocks = new HashSet<Integer>();

    private HashSet<Integer> tempBlocks = new HashSet<Integer>();

    // These are not saved.
    private transient File downloadedBlockDir;
    private transient File tempBlockDir;


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

    public synchronized int getLastOrderId() {
        return lastOrderId;
    }
    
    public synchronized File getBlockFileIfExists(int blockId) {
        if (downloadedBlocks.contains(blockId)) {
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

    public synchronized boolean isDownloadQueueEmpty() {
        return downloadQueue.isEmpty();
    }

    /**
     * The idea is that you get the state of the download queue as a whole and
     * try to download them; you might not be able to get them all and then you
     * should call this method again because new ones might have been added in
     * the mean time.
     * @return
     */
    @SuppressWarnings("unchecked")
    public synchronized List<Integer> getDownloadQueue() {
        return (ArrayList<Integer>) downloadQueue.clone();
    }

    public synchronized boolean isTempBlocksEmpty() {
        return tempBlocks.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public synchronized Set<Integer> getTempBlocks() {
        return (HashSet<Integer>) tempBlocks.clone();
    }

    public synchronized void addModifications(List<Integer> modifications) {
        for (Integer mod : modifications) {
            if (mod > 0) {
                allocatedBlocks.add(mod);
                downloadQueue.add(mod);
                lastOrderId++;
            } else {
                allocatedBlocks.remove(-mod);
                downloadQueue.remove(-mod);
            }
        }
    }

    public synchronized void haveDownloadedBlock(int blockId) {
        downloadQueue.remove(new Integer(blockId)); // Damn boxing.
        downloadedBlocks.add(blockId);
    }

    public synchronized void haveTempBlock(int blockId) {
        tempBlocks.add(blockId);
    }
}
