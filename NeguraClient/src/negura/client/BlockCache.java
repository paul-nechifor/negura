package negura.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;

/**
 *
 * @author Paul Nechifor
 */
public class BlockCache {
    class BlockData {

        byte[] data;
        long lastUsed;

        public BlockData(byte[] data, int length) {
            if (data.length == length) {
                this.data = data;
            } else {
                this.data = new byte[length];
                System.arraycopy(data, 0, this.data, 0, length);
            }
            lastUsed = System.currentTimeMillis();
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }
    }
    private ClientConfigManager cm;
    private HashMap<Integer, BlockData> blocks
            = new HashMap<Integer, BlockData>();
    private byte[] buffer;

    public BlockCache(ClientConfigManager cm) {
        this.cm = cm;
    }

    // TODO: change how this function is called.
    public void load() {
        this.buffer = new byte[cm.getServerInfoBlockSize()];
    }

    public InputStream getBlockInputStream(int blockId) throws IOException {
        BlockData bd = null;
        List<InetSocketAddress> peers = cm.getPeerCache()
                .getPeersForBlock(blockId);
        if (peers == null) {
            throw new IOException("No peers to download block from.");
        }
        for (InetSocketAddress address : peers) {
            NeguraLog.info("Address: " + address);
            int read = Comm.receiveBlock(buffer, 0, -1, blockId, address);
            System.out.println("Read " + read + "  from block  " + blockId);
            if (read > 0) {
                // Got the block.
                bd = new BlockData(buffer, read);
                break;
            }
        }

        if (bd == null) {
            throw new IOException("Couldn't get the block.");
        }

        return bd.getInputStream();
    }
}