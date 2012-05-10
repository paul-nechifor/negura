package negura.common.data;

import com.google.gson.JsonObject;

public class Operation implements Comparable<Operation> {
    public int id;
    public String path;
    public String newPath;   // Might be null.
    public String signature;
    public int date;
    public long size;        // Might be null, that is, -1.
    public String hash;      // Might be null.
    public String type;
    public Block[] blocks;   // Might be null.

    public static Operation fromJson(JsonObject json) {
        Operation o = new Operation();
        o.id = json.get("id").getAsInt();
        o.path = json.get("path").getAsString();
        if (json.get("signature") != null)
            o.signature = json.get("signature").getAsString();
        o.date = json.get("date").getAsInt();
        o.type = json.get("type").getAsString();

        o.newPath = null;
        o.size = -1;
        o.hash = null;
        o.blocks = null;
        if (o.type.equals("add")) {
            o.size = json.get("size").getAsLong();
            o.hash = json.get("hash").getAsString();
            o.blocks = Block.jsonArrayToBlocks(json.getAsJsonArray("blocks"));
        } else if (o.type.equals("move")) {
            o.newPath = json.get("new-path").getAsString();
        } else if (o.type.equals("delete")) {
        } else {
            throw new AssertionError();
        }

        return o;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("path", path);
        if (signature != null)
            json.addProperty("signature", signature);
        json.addProperty("date", date);
        json.addProperty("type", type);

        if (type.equals("add")) {
            json.addProperty("size", size);
            json.addProperty("hash", hash);
            json.add("blocks", Block.blocksToJsonArray(blocks));
        } else if (type.equals("move")) {
            json.addProperty("new-path", newPath);
        } else if (type.equals("delete")) {
        } else {
            throw new AssertionError();
        }

        return json;
    }

    @Override
    public int compareTo(Operation o) {
        return id - o.id;
    }

    public int[] getBlockIds() {
        int[] ret = new int[blocks.length];
        for (int i = 0; i < ret.length; i++)
            ret[i] = blocks[i].id;
        return ret;
    }
}
