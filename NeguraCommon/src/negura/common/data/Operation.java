package negura.common.data;

public class Operation {
    public int oid;
    public String path;
    public String newPath;   // Might be null.
    public String signature;
    public int date;
    public long size;        // Might be null, that is, -1.
    public String hash;      // Might be null.
    public String type;
    public Block[] blocks;   // Might be null.
    public int firstbid;
    public int lastbid;
}
