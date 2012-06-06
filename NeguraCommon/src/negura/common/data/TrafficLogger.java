package negura.common.data;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    public static class Builder {
        public long previusActiveTimeDown = 0;
        public long previusActiveTimeUp = 0;
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
    private final long intervalNanos;
    private final int ringLength;
    private int currentRingIndex = 0;

    private long lastDownload = 0;
    private long lastUpload = 0;

    private AtomicLong sessionActiveTimeDown;
    private AtomicLong sessionActiveTimeUp;
    private final long previusActiveTimeDown;
    private final long previusActiveTimeUp;

    /**
     * Creates a logger of the generated traffic.
     * @param ta        The aggregator to pool.
     * @param builder   The builder to set the previous state.
     * @param length    The length of the log.
     * @param interval  The interval in seconds at which to pool.
     */
    public TrafficLogger(TrafficAggregator ta, Builder builder, double interval,
            int length) {
        this.trafficAggregator = ta;
        this.previusActiveTimeDown = builder.previusActiveTimeDown;
        this.previusActiveTimeUp = builder.previusActiveTimeUp;
        this.sessionActiveTimeDown = new AtomicLong(0);
        this.sessionActiveTimeUp = new AtomicLong(0);
        this.interval = interval;
        this.ringLength = length;
        this.recordRing = getFilledArray(this.ringLength);
        this.intervalNanos = (long) (interval * 1000000000);
        this.scheduler.scheduleAtFixedRate(callUpdateRing,
                intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public final long getSessionActiveTimeDown() {
        return sessionActiveTimeDown.get();
    }
    
    public final long getSessionActiveTimeUp() {
        return sessionActiveTimeUp.get();
    }
    
    public final long getPreviousActiveTimeDown() {
        return previusActiveTimeDown;
    }

    public final long getPreviousActiveTimeUp() {
        return previusActiveTimeUp;
    }

    public Builder getBuilder() {
        Builder ret = new Builder();

        ret.previusActiveTimeDown = previusActiveTimeDown +
                sessionActiveTimeDown.get();
        ret.previusActiveTimeUp = previusActiveTimeUp +
                sessionActiveTimeUp.get();

        return ret;
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

    /**
     * Get an appripriate array in which to copy elements from this log.
     * @return      An array of records.
     */
    public Record[] getFilledArray() {
        return getFilledArray(ringLength);
    }

    private Record[] getFilledArray(int length) {
        Record[] ret = new Record[length];

        for (int i = 0; i < length; i++) {
            ret[i] = new Record(0, 0);
        }

        return ret;
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

        if (nextRecord.download > 0)
            sessionActiveTimeDown.addAndGet(intervalNanos);
        if (nextRecord.upload > 0)
            sessionActiveTimeUp.addAndGet(intervalNanos);
    }
}
