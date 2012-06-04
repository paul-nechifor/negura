package negura.client.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import negura.client.ClientConfigManager;
import negura.common.data.TrafficAggregator;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;

/**
 *
 * @author Paul Nechifor
 */
public class BlockCache {
    private static class BlockData {
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
    
    private final ClientConfigManager cm;
    private final TrafficAggregator trafficAggregator;
    private final byte[] buffer;

    public BlockCache(ClientConfigManager cm, TrafficAggregator ta,
            int blockSize) {
        this.cm = cm;
        this.trafficAggregator = ta;
        this.buffer = new byte[blockSize];
    }

    public InputStream getBlockInputStream(int blockId) throws IOException {
        BlockData bd = null;
        List<InetSocketAddress> peers = cm.getPeerCache()
                .getPeersForBlock(blockId);
        if (peers == null) {
            throw new IOException("No peers to download block from.");
        }
        for (InetSocketAddress address : peers) {
            int read = Comm.readBlock(buffer, 0, -1, blockId, address);
            NeguraLog.info("Read %d from block %d.", read, blockId);
            if (read > 0) {
                trafficAggregator.addSessionDown(read);
                // Got the block.
                bd = new BlockData(buffer, read);
                break;
            }
        }

        if (bd == null) {
            throw new IOException("Couldn't get block.");
        }

        return bd.getInputStream();
    }
}