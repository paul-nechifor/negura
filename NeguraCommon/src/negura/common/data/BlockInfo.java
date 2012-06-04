package negura.common.data;

/**
 * Block statistics class.
 * @author Paul Nechifor
 */
public class BlockInfo {
    public int bid;
    public int allocated;
    public int completed;
    public int oid;

    public BlockInfo() { }

    public BlockInfo(int bid, int allocated, int completed, int opid) {
        this.bid = bid;
        this.allocated = allocated;
        this.completed = completed;
        this.oid = opid;
    }
}
