package negura.common.data;

/**
 * @author Paul Nechifor
 */
public class Block {
    public int bid;
    public String hash;

    public Block() {
    }

    public Block(int bid, String hash) {
        this.bid = bid;
        this.hash = hash;
    }
}
