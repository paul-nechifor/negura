package negura.client;

import negura.client.fs.NeguraFsView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.FileHandler;
import javax.xml.bind.DatatypeConverter;
import negura.common.data.BlockIndexer;
import negura.common.data.Operation;
import negura.common.data.RsaKeyPair;
import negura.common.data.ServerInfo;
import negura.common.json.Json;
import negura.common.util.NeguraLog;
import negura.common.util.Os;

/**
 * Controls the configuration. Some properties change over time and some don't.
 * For those that change, classes that need them should always use the accessors
 * to get the current values.
 * 
 * TODO: Add a HashSet for the blocks so I can check existance faster.
 * TODO: Use Reader-Writer locks and not synchronized methods.
 * @author Paul Nechifor
 */
public class ClientConfigManager {
    private static final String BLOCK_EXTENSION = ".blk";

    public static class Builder {
        private transient File configFile;

        private InetSocketAddress serverAddress;
        private int storedBlocks;
        private File blockDir;
        private int servicePort;
        private int ftpPort;
        private String threadPoolOptions;
        private RsaKeyPair keyPair;
        private int userId;
        private ServerInfo serverInfo;
        private File logFile;
        
        // These cannot be initialized by the builder.
        private BlockIndexer blockIndex = new BlockIndexer();
        private final ArrayList<Integer> blockList = new ArrayList<Integer>();
        private List<Operation> operations = new ArrayList<Operation>();

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

        public Builder blockDir(File blockDir) {
            this.blockDir = blockDir;
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

        public ClientConfigManager build() {
            if (logFile == null)
                logFile = new File(configFile.getParent(), "log.txt");
            return new ClientConfigManager(this);
        }
    }

    private Builder builder;

    private MessageDigest blockHash;
    private PeerCache peerCache;
    private BlockCache blockCache;
    private FileHandler fileLogHandler;
    private NeguraFsView fsView;
    private final ArrayList<Integer> downloadQueue = new ArrayList<Integer>();

    public ClientConfigManager(Builder builder) {
        this.builder = builder;
        initCommon();
    }

    public ClientConfigManager(File configFile) throws IOException {
        this.builder = Json.fromFile(configFile, Builder.class);
        this.builder.configFile = configFile;
        initCommon();
    }

    public void save() throws IOException {
        Json.toFile(builder.configFile, builder);
    }

    private void initCommon() {
        loadFileHandler();

        try {
            blockHash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            NeguraLog.severe(ex);
        }

        peerCache = new PeerCache(this);
        blockCache = new BlockCache(this);
        fsView = new NeguraFsView(this, builder.operations);

        // The download queue will be made up of those blocks which are in the
        // block list but haven't been downloaded.
        for (Integer id : builder.blockList) {
            if (!builder.blockIndex.idToHash.containsKey(id)) {
                downloadQueue.add(id);
            }
        }

        blockCache.load();
    }

    private void loadFileHandler() {
        try {
            fileLogHandler = new FileHandler(
                    builder.logFile.getAbsolutePath(), true);
            fileLogHandler.setFormatter(NeguraLog.FORMATTER);
            NeguraLog.addHandler(fileLogHandler);
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }
    }

    public synchronized String saveBlock(int id, byte[] block, int size) {
        File blockFile = null;
        try {
            blockFile = Os.randomFile(builder.blockDir, null, BLOCK_EXTENSION);
        } catch (IOException ex) {
            NeguraLog.severe(ex, "Failed to create block file.");
        }
        String code = blockFile.getName();
        code = code.substring(0, code.length() - BLOCK_EXTENSION.length());

        // Getting the hash for this block.
        blockHash.update(block, 0, size);
        String hash = DatatypeConverter.printHexBinary(
                blockHash.digest());

        try {
            FileOutputStream out = new FileOutputStream(blockFile);
            out.write(block, 0, size);
            out.close();
        } catch (IOException ex) {
            NeguraLog.severe(ex, "Couldn't write config file.");
        }

        builder.blockIndex.hashToId.put(hash, id);
        builder.blockIndex.idToHash.put(id, hash);
        builder.blockIndex.idToStoreCode.put(id, code);
        builder.blockIndex.storeCodeToId.put(code, id);

        downloadQueue.remove(new Integer(id));

        return hash;
    }

    public synchronized void pushOperations(List<Operation> operations) {
        fsView.addOperations(operations);
    }

    public synchronized void pushBlocks(List<Integer> newBlocks,
            boolean addForDownload) {
        // Eliminate the blocks that already are in the list.
        Iterator<Integer> iter = newBlocks.iterator();
        while (iter.hasNext()) {
            if (builder.blockList.contains(iter.next().intValue())) {
                iter.remove();
            }
        }

        if (newBlocks.isEmpty()) {
            return;
        }

        // If the number of new blocks exceeds the number of stored blocks
        // remove from the left.
        if (newBlocks.size() > builder.storedBlocks) {
            int throwOut = newBlocks.size() - builder.storedBlocks;
            for (int i = 0; i < throwOut; i++) {
                newBlocks.remove(0); // TODO: use another data type.
            }
        }

        int pushOut;
        int maxToLeave = builder.storedBlocks - newBlocks.size();
        if (builder.blockList.size() > maxToLeave) {
            pushOut = builder.blockList.size() - maxToLeave;
        } else {
            pushOut = 0;
        }

        for (int i = 0; i < pushOut; i++) {
            deleteBlock(builder.blockList.remove(0)); // TODO: use another data type.
        }
        for (Integer i : newBlocks) {
            builder.blockList.add(i);
        }

        if (addForDownload) {
            downloadQueue.addAll(newBlocks);
        }
    }

    /**
     * Gets file location for the saved block.
     * @param id   The id of the block.
     * @return     The file location, or <code>null</code> if the blocks doesn't
     *             exist.
     */
    public synchronized File fileForBlockId(int id) {
        if (builder.blockIndex.idToStoreCode.containsKey(id)) {
            return new File(builder.blockDir,
                    builder.blockIndex.idToStoreCode.get(id) + BLOCK_EXTENSION);
        }
        return null;
    }

    /**
     * Removes all traces of the block, except from the blockList.
     */
    private synchronized void deleteBlock(int id) {
        if (builder.blockIndex.idToHash.containsKey(id)) {
            String hash = builder.blockIndex.idToHash.get(id);
            String storeCode = builder.blockIndex.idToStoreCode.get(id);
            File block = fileForBlockId(id);
            if (block.delete() == false) {
                throw new RuntimeException("Could not delete file " + block);
            }
            builder.blockIndex.hashToId.remove(hash);
            builder.blockIndex.idToHash.remove(id);
            builder.blockIndex.idToStoreCode.remove(id);
            builder.blockIndex.storeCodeToId.remove(storeCode);
        }
    }

    public synchronized boolean isDownloadQueueEmpty() {
        return downloadQueue.isEmpty();
    }

    /**
     * The idea is that you get the state of the download queue as a whole and
     * try to download them; you might not be able to get them all and then you
     * should call this method again because new ones might have been added in
     * the mean time.
     * @return 
     */
    @SuppressWarnings("unchecked")
    public synchronized List<Integer> getDownloadQueue() {
        return (List<Integer>) downloadQueue.clone();
    }

    public synchronized InetSocketAddress getServerSocketAddress() {
        return builder.serverAddress;
    }

    public synchronized int getServerInfoBlockSize() {
        return builder.serverInfo.blockSize;
    }

    public synchronized int getStoredBlocks() {
        return builder.storedBlocks;
    }

    public synchronized File getBlockDir() {
        return builder.blockDir;
    }

    public synchronized int getPort() {
        return builder.servicePort;
    }

    public synchronized int getFtpPort() {
        return builder.ftpPort;
    }

    public synchronized int getUserId() {
        return builder.userId;
    }

    public synchronized String getThreadPoolOptions() {
        return builder.threadPoolOptions;
    }

    public synchronized File getLogFile() {
        return builder.logFile;
    }

    /**
     * Gets the filesystem view (doesn't change).
     * @return
     */
    public NeguraFsView getFsView() {
        return fsView;
    }

    /**
     * Gets the peer cache (doesn't change).
     * @return
     */
    public PeerCache getPeerCache() {
        return peerCache;
    }

    /**
     * Gets the block cache (doesn't change).
     * @return
     */
    public BlockCache getBlockCache() {
        return blockCache;
    }
}
