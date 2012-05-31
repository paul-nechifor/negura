package negura.common.data;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import negura.common.data.TrafficLogger.Record;

/**
 *
 * @author Paul Nechifor
 */
public class TrafficLogger {
    public static class Record {
        // No need for setters and getters since the whole object is copied.
        public long download;
        public long upload;

        public Record(long download, long upload) {
            this.download = download;
            this.upload = upload;
        }
    }
    
    private final TrafficAggregator trafficAggregator;
    private final Record[] recordRing;
    private final ScheduledExecutorService scheduler
            = Executors.newScheduledThreadPool(1);
    private final Runnable callUpdateRing = new Runnable() {
        @Override
        public void run() {
            updateRing();
        }
    };

    private final double interval;
    private final int ringLength;
    private int currentRingIndex = 0;

    private long lastDownload = 0;
    private long lastUpload = 0;

    /**
     * Creates a logger of the traffic consumed.
     * @param ta        The aggregator to pool.
     * @param length    The length of the log.
     * @param interval  The interval in seconds at which to pool.
     */
    public TrafficLogger(TrafficAggregator ta, double interval, int length) {
        this.trafficAggregator = ta;
        this.interval = interval;
        this.ringLength = length;
        this.recordRing = getFilledArray(this.ringLength);
        long nanos = (long) (interval * 1000000000);
        scheduler.scheduleAtFixedRate(callUpdateRing,
                nanos, nanos, TimeUnit.NANOSECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Copies the elements in order.
     * @param filledArray       The filled array where to copy them to.
     */
    public synchronized void copyToArray(Record[] filledArray) {
        if (filledArray.length != ringLength) {
            throw new IllegalArgumentException("Different sizes: " +
                    filledArray.length + ", " + ringLength + ".");
        }

        int loopElement = currentRingIndex;
        int i = -1;
        Record a, b;

        do {
            loopElement = (loopElement + 1) % ringLength;
            i++;

            a = filledArray[i];
            b = recordRing[loopElement];

            a.download = b.download;
            a.upload = b.upload;
        } while (loopElement != currentRingIndex);
    }

    private Record[] getFilledArray(int length) {
        Record[] ret = new Record[length];

        for (int i = 0; i < length; i++) {
            ret[i] = new Record(0, 0);
        }

        return ret;
    }

    /**
     * Get an appripriate array in which to copy elements from this log.
     * @return      An array of records.
     */
    public Record[] getFilledArray() {
        return getFilledArray(ringLength);
    }

    private synchronized void updateRing() {
        long nowDownload = trafficAggregator.getSessionDown();
        long nowUpload = trafficAggregator.getSessionUp();

        currentRingIndex = (currentRingIndex + 1) % ringLength;

        Record nextRecord = recordRing[currentRingIndex];
        nextRecord.download = (long)((nowDownload - lastDownload) / interval);
        nextRecord.upload = (long)((nowUpload - lastUpload) / interval);

        lastDownload = nowDownload;
        lastUpload = nowUpload;
    }
}
