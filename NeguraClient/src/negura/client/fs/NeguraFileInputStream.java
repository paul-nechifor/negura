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

/**
 *
 * @author Paul Nechifor
 */
public class NeguraFileInputStream extends InputStream {
    private ClientConfigManager cm;
    private int blockSize;
    private int[] blockIds;
    private long size;
    private long position;
    private int blockIndex = 0;
    private InputStream currBlock;

    public NeguraFileInputStream(NeguraFile file, long offset)
            throws IOException {
        cm = file.fsView.getConfigManager();
        Operation o = file.fsView.getOperation(file.operationId);

        blockIds = o.getBlockIds();
        List<Integer> blockList = new ArrayList<Integer>(blockIds.length);
        for (int i : blockIds)
            blockList.add(i);
        // Tell the peer cache to ready the peer lists for the blocks.
        cm.getPeerCache().preemptivelyCache(blockList);
        blockSize = cm.getServerInfoBlockSize();
        size = file.size;
        
        if (offset < 0 || offset >= size)
            throw new IOException("Invalid offset.");

        position = offset;
        blockIndex = (int) (position / blockSize);
        int blockOffset = (int) (position % blockSize);

        makeBlockAvailable(blockIds[blockIndex], blockOffset);
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
            throw new AssertionError("Cannot happen.");

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
        System.out.println("Used this.");
        System.exit(1);

        if (n < 0)
            throw new AssertionError("Can't do negative skip.");
        position += n;
        if (position >= size)
            throw new IOException("Was asked to skip over file size.");

        int newBlock = (int) (position / blockSize);
        if (newBlock != blockIndex) {
            blockIndex = newBlock;
            makeBlockAvailable(blockIds[blockIndex],
                    (int) (position % blockSize));
        } else {
            currBlock.skip(n);
        }

        return n;
    }

    // Trys to open the InputStream to the specified block either by finding it
    // in the saved blocks or by getting it by the wire.
    private void makeBlockAvailable(int blockId, int offset)
            throws IOException {
        if (currBlock != null)
            currBlock.close();

        File f = cm.fileForBlockId(blockId);
        if (f != null) {
            // Block was found locally.
            currBlock = new FileInputStream(f);
            if (offset > 0)
                currBlock.skip(offset);
            return;
        }

        currBlock = cm.getBlockCache().getBlockInputStream(blockId);
        if (offset > 0)
            currBlock.skip(offset);
        if (currBlock == null)
            throw new IOException("Couldn't find any peer with this block.");
    }
}
