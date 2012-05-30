package negura.client;

import negura.client.net.BlockCache;
import negura.client.net.PeerCache;
import negura.client.fs.NeguraFsView;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import negura.common.data.BlockList;
import negura.common.data.Operation;
import negura.common.data.RsaKeyPair;
import negura.common.data.ServerInfo;
import negura.common.data.TrafficAggregator;
import negura.common.json.Json;
import negura.common.util.NeguraLog;

/**
 * Controls the configuration. Some properties change over time and some don't.
 * For those that change, classes that need them should always use the accessors
 * to get the current values.
 * 
 * @author Paul Nechifor
 */
public class ClientConfigManager {
    public static class Builder {
        private transient File configFile;

        private InetSocketAddress serverAddress;
        private int storedBlocks;
        private File dataDir;
        private int servicePort;
        private int ftpPort;
        private String threadPoolOptions;
        private RsaKeyPair keyPair;
        private int userId;
        private ServerInfo serverInfo;
        private File logFile;
        
        // These cannot be initialized by the builder.
        private final BlockList blockList = new BlockList();
        private final List<Operation> operations = new ArrayList<Operation>();
        private final TrafficAggregator trafficAggregator
                = new TrafficAggregator();

        public Builder(File configFile) {
            this.configFile = configFile;
        }

        public Builder serverAddress(InetSocketAddress serverAddress) {
            this.serverAddress = serverAddress;
            return this;
        }

        public Builder storedBlocks(int storedBlocks) {
            this.storedBlocks = storedBlocks;
            return this;
        }

        public Builder dataDir(File dataDir) {
            this.dataDir = dataDir;
            return this;
        }

        public Builder servicePort(int servicePort) {
            this.servicePort = servicePort;
            return this;
        }

        public Builder ftpPort(int ftpPort) {
            this.ftpPort = ftpPort;
            return this;
        }

        public Builder threadPoolOptions(String threadPoolOptions) {
            this.threadPoolOptions = threadPoolOptions;
            return this;
        }

        public Builder keyPair(RsaKeyPair keyPair) {
            this.keyPair = keyPair;
            return this;
        }

        public Builder userId(int userId) {
            this.userId = userId;
            return this;
        }

        public Builder serverInfo(ServerInfo serverInfo) {
            this.serverInfo = serverInfo;
            return this;
        }

        public Builder logFile(File logFile) {
            this.logFile = logFile;
            return this;
        }

        public ClientConfigManager build() throws IOException {
            if (logFile == null)
                logFile = new File(configFile.getParent(), "log.txt");
            return new ClientConfigManager(this, null);
        }
    }

    // Immutable fields.
    private final InetSocketAddress serverAddress;
    private final int storedBlocks;
    private final File dataDir;
    private final int servicePort;
    private final int ftpPort;
    private final String threadPoolOptions;
    private final int userId;
    private final int blockSize;
    private final File logFile;

    // Final objects.
    private final Builder builder;
    private final PeerCache peerCache;
    private final BlockCache blockCache;
    private final BlockList blockList;
    private final NeguraFsView fsView;
    private final Negura negura;
    private final TrafficAggregator trafficAggregator;

    public ClientConfigManager(Builder builder, Negura negura)
            throws IOException {
        // Loading the file log handler as early as possible to log errors.
        FileHandler handler = new FileHandler(
                builder.logFile.getAbsolutePath(), true);
        handler.setFormatter(NeguraLog.FORMATTER);
        NeguraLog.addHandler(handler);

        // Initializing immutable fields.
        this.serverAddress = builder.serverAddress;
        this.storedBlocks = builder.storedBlocks;
        this.dataDir = builder.dataDir;
        this.servicePort = builder.servicePort;
        this.ftpPort = builder.ftpPort;
        this.threadPoolOptions = builder.threadPoolOptions;
        this.userId = builder.userId;
        this.blockSize = builder.serverInfo.blockSize;
        this.logFile = builder.logFile;

        // Initializing final objects.
        this.builder = builder;
        this.peerCache = new PeerCache(this);
        this.blockCache = new BlockCache(this);
        this.blockList = builder.blockList;
        this.fsView = new NeguraFsView(this, builder.operations);
        this.negura = negura;
        this.trafficAggregator = builder.trafficAggregator;

        // Special initialization operations.
        this.blockCache.load();
        this.builder.blockList.setDataDir(builder.dataDir);
    }

    public ClientConfigManager(File configFile, Negura negura)
            throws IOException {
        this(Json.fromFile(configFile, Builder.class), negura);
        this.builder.configFile = configFile;
    }

    public void save() throws IOException {
        Json.toFile(builder.configFile, builder);
    }

    public final PeerCache getPeerCache() {
        return peerCache;
    }

    public final BlockCache getBlockCache() {
        return blockCache;
    }

    public final BlockList getBlockList() {
        return blockList;
    }

    public final NeguraFsView getFsView() {
        return fsView;
    }

    public final Negura getNegura() {
        return negura;
    }

    public final TrafficAggregator getTrafficAggregator() {
        return trafficAggregator;
    }

    public final InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public final int getStoredBlocks() {
        return storedBlocks;
    }

    public final File getDataDir() {
        return dataDir;
    }

    public final int getServicePort() {
        return servicePort;
    }

    public final int getFtpPort() {
        return ftpPort;
    }

    public final String getThreadPoolOptions() {
        return threadPoolOptions;
    }

    public final int getUserId() {
        return userId;
    }

    public final int getBlockSize() {
        return blockSize;
    }

    public final File getLogFile() {
        return logFile;
    }
}
