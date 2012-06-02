package negura.common.data;

import negura.common.json.Json;

/**
 * Options for creating a ThreadPoolExecutor.
 * @author Paul Nechifor
 */
public class ThreadPoolOptions {
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final int keepAliveTimeMillis;

    public static ThreadPoolOptions getDefault() {
        return new ThreadPoolOptions(0, 10, 30000);
    }

    public static ThreadPoolOptions fromString(String jsonString) {
        return Json.fromString(jsonString, ThreadPoolOptions.class);
    }

    public ThreadPoolOptions(int core, int max, int keep) {
        this.corePoolSize = core;
        this.maximumPoolSize = max;
        this.keepAliveTimeMillis = keep;
    }

    @Override
    public final String toString() {
        return Json.toString(this);
    }

    public final int getCorePoolSize() {
        return corePoolSize;
    }

    public final int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public final int getKeepAliveTimeMillis() {
        return keepAliveTimeMillis;
    }
}
