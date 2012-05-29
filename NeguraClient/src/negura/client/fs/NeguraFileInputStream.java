package negura.client.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import negura.client.ClientConfigManager;
import negura.common.util.Util;
import negura.common.data.Operation;
import negura.common.ex.NeguraError;

/**
 *
 * @author Paul Nechifor
 */
public class NeguraFileInputStream extends InputStream {
    private final ClientConfigManager cm;

    private int blockSize;
    private final int[] blockIds;
    private final long size;
    private long position;
    private int blockIndex = 0;
    private InputStream currBlock;

    public NeguraFileInputStream(NeguraFile file, long offset)
            throws IOException {
        cm = file.fsView.getConfigManager();
        blockSize = cm.getBlockSize();
        size = file.size;

        if (offset < 0 || offset >= size)
            throw new IOException("Invalid offset.");

        position = offset;
        blockIndex = (int) (position / blockSize);

        Operation o = file.fsView.getOperation(file.operationId);

        // Getting the block IDs.
        blockIds = new int[o.lastbid - o.firstbid + 1];
        for (int i = 0; i < blockIds.length; i++)
            blockIds[i] = o.firstbid + i;

        // Tell the peer cache to ready the peer lists for the blocks.
        cm.getPeerCache().preemptivelyCache(blockIds);

        // Load the first block.
        makeBlockAvailable(blockIds[blockIndex], (int)position % blockSize);
    }

    /**
     * This method is supposed to return the number of bytes that can be read
     * without blocking, so I'll return the number of bytes left in the current
     * block or the length of the next block if I'm right at the end of the
     * block.
     * @return How much you can read without blocking.
     * @throws IOException
     */
    @Override
    public int available() throws IOException {
        if (position >= size)
            return -1;

        // If it's the last block, return what's left.
        if (blockIndex == blockIds.length - 1)
            return (int) (size - position);

        int leftInBlock = blockSize - (int)(position % blockSize);
        if (leftInBlock == 0)
            throw new AssertionError("Cannot happen.");

        return leftInBlock;
    }

    @Override
    public void close() throws IOException {
        if (currBlock != null)
            currBlock.close();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (position >= size)
            return -1;

        boolean isLastBlock = blockIndex == blockIds.length - 1;
        int leftInBlock;

        if (isLastBlock)
            leftInBlock = (int) (size - position);
        else
            leftInBlock = blockSize - (int)(position % blockSize);

        if (leftInBlock == 0)
            throw new NeguraError("Cannot happen.");

        int readFromBlock = (length < leftInBlock) ? length : leftInBlock;
        Util.readBytes(buffer, offset, readFromBlock, currBlock);

        position += readFromBlock;
        int newBlock = (int) (position / blockSize);
        if (newBlock != blockIndex) {
            blockIndex = newBlock;
            makeBlockAvailable(blockIds[blockIndex], 0);
        }

        return readFromBlock;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        throw new UnsupportedOperationException("Use another read.");
    }

    @Override
    public long skip(long n) throws IOException {
        if (n < 0)
            throw new NeguraError("Negative skip: %d.", n);

        position += n;
        if (position >= size) {
            throw new IOException(String.format("Can't skip to %d which is " +
                    "beyond size %d.", position, size));
        }

        int newBlock = (int) position / blockSize;
        if (newBlock != blockIndex) {
            blockIndex = newBlock;
            makeBlockAvailable(blockIds[blockIndex], (int)position % blockSize);
        } else {
            currBlock.skip(n);
        }

        return n;
    }

    // Tries to open the InputStream to the specified block either by finding it
    // in the saved blocks or by getting it by the wire.
    private void makeBlockAvailable(int blockId, int offset)
            throws IOException {
        // Closing the last block.
        if (currBlock != null)
            currBlock.close();

        File blockFile = cm.getBlockList().getBlockFileIfExists(blockId);
        if (blockFile != null) {
            // Block was found locally.
            currBlock = new FileInputStream(blockFile);
        } else {
            // Getting the block through the cache.
            currBlock = cm.getBlockCache().getBlockInputStream(blockId);
        }
        
        if (offset > 0)
            currBlock.skip(offset);
    }
}
