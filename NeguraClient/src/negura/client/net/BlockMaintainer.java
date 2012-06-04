package negura.client.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import negura.client.ClientConfigManager;
import negura.common.Service;
import negura.common.data.BlockList;
import negura.common.data.TrafficAggregator;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;
import negura.common.util.Util;

/**
 * Maintains the stat of the blocks by downloading, copying and deleting them.
 * @author Paul Nechifor
 */
public class BlockMaintainer extends Service implements BlockList.InListener {
    private final ClientConfigManager cm;
    private final StateMaintainer sm;
    private final BlockList blockList;
    private final TrafficAggregator trafficAggregator;
    private final PeerCache peerCache;
    private final byte[] buffer;
    private final LinkedList<Integer> queue = new LinkedList<Integer>();
    private final Object schedulerLock = new Object();
    private ScheduledExecutorService scheduler = null;

    public BlockMaintainer(ClientConfigManager cm) {
        this.cm = cm;
        this.sm = cm.getNegura().getStateMaintainer();
        this.blockList = cm.getBlockList();
        this.trafficAggregator = cm.getTrafficAggregator();
        this.buffer = new byte[cm.getBlockSize()];
        this.peerCache = cm.getPeerCache();
    }

    @Override
    protected void onStart() {
        restartScheduler();
        started();
    }

    @Override
    protected void onStop() {
        stopScheduler();
        stopped();
    }

    @Override
    public void addedBlocks(List<Integer> blockIds) {
        synchronized (queue) {
            queue.addAll(blockIds);
        }

        // Load peers for these blocks early.
        peerCache.preemptivelyCache(blockIds);

        // If some of the blocks that were allocated to me are in my temp blocks
        // copy them instead of downloading them.
        if (!blockList.isTempBlocksEmpty()) {
            copyFromTempBlocksIfPresent();
        }

        // If the scheduler was off, restart it.
        restartScheduler();
    }

    @Override
    public void removedBlocks(List<Integer> blockIds) {
        synchronized (queue) {
            queue.removeAll(blockIds);
        }

        // Now remove them from the disk.
    }

    private void restartScheduler() {
        synchronized (schedulerLock) {
            // If it is already started, return.
            if (scheduler != null)
                return;

            scheduler = Executors.newSingleThreadScheduledExecutor();

            Runnable runnable = new Runnable() {
                public void run() {
                    downloadOneBlock();
                }
            };

            int delayMillis = 20;

            // The space between the end and the start of a call is fixed.
            scheduler.scheduleWithFixedDelay(runnable, delayMillis, delayMillis,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void stopScheduler() {
        synchronized (schedulerLock) {
            // If it is already stopped, return.
            if (scheduler == null)
                return;

            scheduler.shutdown();
            scheduler = null;
        }
    }

    private void downloadOneBlock() {
        Integer blockId;
        
        synchronized (queue) {
            blockId = queue.pollFirst();

            // If the queue is empty stop the scheduler and flush the completed
            // blocks to the server.
            if (blockId == null) {
                stopScheduler();
                sm.triggerSendDownloadedBlocks();
                return;
            }
        }

        // Try to download the block from someone.
        if (tryToDownload(blockId, peerCache.getPeersForBlock(blockId))) {
            // Succedeed in downloading the block.
            blockList.addToCompleted(blockId);
        } else {
            // Failed so add it to the back of the queue for later.
            synchronized (queue) {
                queue.addLast(blockId);
            }
        }

    }

    private boolean tryToDownload(int blockId, List<InetSocketAddress> peers) {
        if (peers == null || peers.isEmpty()) {
            NeguraLog.warning("No peers to download block %d from.", blockId);
            return false;
        }

        for (InetSocketAddress address : peers) {
            int read = Comm.readBlock(buffer, 0, -1, blockId, address);
            if (read <= 0) // Couldn't get block.
                continue;

            trafficAggregator.addSessionDown(read);

            if (saveBlock(blockId, buffer, read)) {
                return true;
            } else {
                continue;
            }
        }

        return false;
    }

    private boolean saveBlock(int blockId, byte[] blockBytes, int size) {
        File blockFile = cm.getBlockList().getFileToSaveBlockTo(blockId);

        // TODO: Check the hash for this block.

        try {
            FileOutputStream out = new FileOutputStream(blockFile);
            out.write(blockBytes, 0, size);
            out.close();
            return true;
        } catch (IOException ex) {
            NeguraLog.warning(ex, "Couldn't write block %d.", blockId);
            return false;
        }
    }

    private void copyFromTempBlocksIfPresent() {
        Set<Integer> tempBlocks = blockList.getTempBlocks();

        // Find out which of the allocated ones are in my temp blocks and
        // remove them from the queue.
        ArrayList<Integer> canCopy = new ArrayList<Integer>();
        synchronized (queue) {
            Iterator<Integer> iterator = queue.iterator();
            Integer blockId;
            while (iterator.hasNext()) {
                blockId = iterator.next();
                if (tempBlocks.contains(blockId)) {
                    iterator.remove(); // Remove from queue.
                    canCopy.add(blockId);
                }
            }
        }

        File fin, fout;
        FileInputStream fis;
        FileOutputStream fos;
        byte[] copyBuffer = new byte[256 * 1024];

        for (Integer blockId : canCopy) {
            try {
                fin = blockList.getFileToSaveTempBlockTo(blockId);
                fout = blockList.getFileToSaveBlockTo(blockId);
                fis = new FileInputStream(fin);
                fos = new FileOutputStream(fout);
                Util.copyStream(fis, fos, copyBuffer);

                // Completed the copy so add it to the completed blocks.
                blockList.addToCompleted(blockId);
            } catch (IOException ex) {
                // Failed so add it to the back of the queue.
                synchronized (queue) {
                    queue.addLast(blockId);
                }
                NeguraLog.warning(ex, "Error copying temp block %d.", blockId);
            }
        }
    }
}
