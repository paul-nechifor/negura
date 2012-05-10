package negura.common.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import negura.common.data.BlockIndexer;

/**
 *
 * @author Paul Nechifor
 */
public class BlockIndexerTypeConverter implements JsonSerializer<BlockIndexer>,
        JsonDeserializer<BlockIndexer> {
    @Override
    public JsonElement serialize(BlockIndexer t, Type type,
            JsonSerializationContext jsc) {
        JsonArray ret = new JsonArray();
        for (Integer id : t.idToHash.keySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("i", id);
            o.addProperty("h", t.idToHash.get(id));
            o.addProperty("c", t.idToStoreCode.get(id));
            ret.add(o);
        }
        return ret;
    }

    @Override
    public BlockIndexer deserialize(JsonElement je, Type type,
            JsonDeserializationContext jdc) throws JsonParseException {
        BlockIndexer bi = new BlockIndexer();
        for (JsonElement e : je.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            Integer id = Integer.parseInt(o.get("i").getAsString());
            String hash = o.get("h").getAsString();
            String storeCode = o.get("c").getAsString();

            bi.hashToId.put(hash, id);
            bi.idToHash.put(id, hash);
            bi.idToStoreCode.put(id, storeCode);
            bi.storeCodeToId.put(storeCode, id);
        }
        return bi;
    }
}
