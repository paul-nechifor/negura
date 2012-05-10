package negura.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedList;

/**
 *
 * @author Paul Nechifor
 */
public class Block {
    public int id;
    public String hash;

    public Block() {
    }

    public Block(int id, String hash) {
        this.id = id;
        this.hash = hash;
    }

    public static JsonArray blocksToJsonArray(Block[] blocks) {
        JsonArray ret = new JsonArray();
        JsonObject o;
        for (Block b : blocks) {
            o = new JsonObject();
            o.addProperty("id", b.id);
            o.addProperty("hash", b.hash);
            ret.add(o);
        }
        return ret;
    }

    public static Block[] jsonArrayToBlocks(JsonArray json) {
        LinkedList<Block> ret = new LinkedList<Block>();
        Block b;
        for (JsonElement e : json) {
            JsonObject o = e.getAsJsonObject();
            ret.add(new Block(o.get("id").getAsInt(),
                    o.get("hash").getAsString()));
        }
        return ret.toArray(new Block[0]);
    }
}
