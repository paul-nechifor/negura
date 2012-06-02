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
import negura.common.data.ThreadPoolOptions;
import negura.common.data.TrafficAggregator;
import negura.common.json.Json;
import negura.common.util.NeguraLog;

/**
 * Controls the configuration.
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
        private ThreadPoolOptions threadPoolOptions;
        private RsaKeyPair rsaKeyPair;
        private int userId;
        private ServerInfo serverInfo;
        private File logFile;
        
        // These cannot be initialized by the builder, but are stored for JSON
        // serialization.
        private final BlockList blockList = new BlockList();
        private final List<Operation> operations = new ArrayList<Operation>();
        private final TrafficAggregator trafficAggregator
                = new TrafficAggregator();

        public Builder(File configFile) {
            this.configFile = configFile;
        }

        public final void setServerAddress(InetSocketAddress serverAddress) {
            this.serverAddress = serverAddress;
        }

        public final void setStoredBlocks(int storedBlocks) {
            this.storedBlocks = storedBlocks;
        }

        public final void setDataDir(File dataDir) {
            this.dataDir = dataDir;
        }

        public final void setServicePort(int servicePort) {
            this.servicePort = servicePort;
        }

        public final void setFtpPort(int ftpPort) {
            this.ftpPort = ftpPort;
        }

        public final void setThreadPoolOptions(ThreadPoolOptions options) {
            this.threadPoolOptions = options;
        }

        public final void setRsaKeyPair(RsaKeyPair rsaKeyPair) {
            this.rsaKeyPair = rsaKeyPair;
        }

        public final void setUserId(int userId) {
            this.userId = userId;
        }

        public final void setServerInfo(ServerInfo serverInfo) {
            this.serverInfo = serverInfo;
        }

        public final void setLogFile(File logFile) {
            this.logFile = logFile;
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
    private final ThreadPoolOptions threadPoolOptions;
    private final RsaKeyPair keyPair;
    private final int userId;
    private final int blockSize;
    private final File logFile;

    // Final objects.
    private final Builder builder;
    private final TrafficAggregator trafficAggregator;
    private final PeerCache peerCache;
    private final BlockCache blockCache;
    private final BlockList blockList;
    private final NeguraFsView fsView;
    private final Negura negura;
    
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
        this.keyPair = builder.rsaKeyPair;
        this.userId = builder.userId;
        this.blockSize = builder.serverInfo.blockSize;
        this.logFile = builder.logFile;

        // Initializing final objects.
        this.trafficAggregator = builder.trafficAggregator;
        this.builder = builder;
        this.peerCache = new PeerCache(this);
        this.blockCache = new BlockCache(this, trafficAggregator, blockSize);
        this.blockList = builder.blockList;
        this.fsView = new NeguraFsView(this, builder.operations);
        this.negura = negura;

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

    public final ThreadPoolOptions getThreadPoolOptions() {
        return threadPoolOptions;
    }

    public final RsaKeyPair getKeyPair() {
        return keyPair;
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
