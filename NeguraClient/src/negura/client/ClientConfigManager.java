package negura.client;

import negura.client.fs.NeguraFsView;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;
import negura.common.util.Util;
import negura.common.data.Operation;
import negura.common.util.RSA;

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

    private static final Logger LOGGER = Logger.getLogger("NeguraServer");
    private static final String BLOCK_EXTENSION = ".blk";
    private String serverIp;
    private int serverPort;
    private int storedBlocks;
    private String blockDir;
    private int port;
    private int ftpPort;
    private int threadPoolSize;
    private RSAPublicKey publicKey;
    // This will have to be put in an encrypted key file in the future.
    private RSAPrivateKey privateKey;
    private int userId;
    private JsonObject serverInfo;
    private NeguraFsView fsView;
    private final ArrayList<Integer> blockList = new ArrayList<Integer>();
    // These are saved in a single array in the config.
    private HashMap<String, Integer> hashToId = new HashMap<String, Integer>();
    private HashMap<Integer, String> idToHash = new HashMap<Integer, String>();
    private HashMap<Integer, String> idToStoreCode
            = new HashMap<Integer, String>();
    private HashMap<String, Integer> storeCodeToId
            = new HashMap<String, Integer>();
    private ArrayList<Integer> downloadQueue = new ArrayList<Integer>();
    // These are not saved.
    private File configPath;
    private MessageDigest blockHash;
    private final PeerCache peerCache;
    private final BlockCache blockCache;

    private ClientConfigManager(File configPath) {
        this.configPath = configPath;

        try {
            blockHash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            System.exit(1);
        }

        peerCache = new PeerCache(this);
        blockCache = new BlockCache(this);
    }

    public static ClientConfigManager load(File configPath)
            throws FileNotFoundException, IOException {
        ClientConfigManager cm = new ClientConfigManager(configPath);

        JsonObject config = Util.readJsonFromFile(configPath.getAbsolutePath());

        cm.serverIp = config.get("server-ip").getAsString();
        cm.serverPort = config.get("server-port").getAsInt();
        cm.storedBlocks = config.get("stored-blocks").getAsInt();
        cm.blockDir = config.get("block-dir").getAsString();
        cm.port = config.get("port").getAsInt();
        cm.ftpPort = config.get("ftp-port").getAsInt();
        cm.threadPoolSize = config.get("thread-pool-size").getAsInt();
        cm.privateKey = RSA.privateKeyFromString(config.get("private-key")
                .getAsString());
        cm.publicKey = RSA.publicKeyFromString(config.get("public-key")
                .getAsString());
        cm.userId = config.get("user-id").getAsInt();

        cm.serverInfo = config.getAsJsonObject("server-info");

        List<Operation> operations = new ArrayList<Operation>();
        for (JsonElement e : config.getAsJsonArray("filesystem")) {
            operations.add(Operation.fromJson(e.getAsJsonObject()));
        }
        cm.fsView = new NeguraFsView(cm, operations);

        for (JsonElement e : config.getAsJsonArray("block-list")) {
            cm.blockList.add(e.getAsInt());
        }

        for (JsonElement e : config.getAsJsonArray("downloaded-blocks")) {
            JsonObject o = e.getAsJsonObject();
            Integer id = Integer.parseInt(o.get("i").getAsString());
            String hash = o.get("h").getAsString();
            String storeCode = o.get("c").getAsString();

            cm.hashToId.put(hash, id);
            cm.idToHash.put(id, hash);
            cm.idToStoreCode.put(id, storeCode);
            cm.storeCodeToId.put(storeCode, id);
        }

        // The download queue will be made up of those blocks which are in the
        // block list but haven't been downloaded.
        for (Integer id : cm.blockList) {
            if (!cm.idToHash.containsKey(id)) {
                cm.downloadQueue.add(id);
            }
        }

        cm.blockCache.load();

        return cm;
    }

    public static ClientConfigManager createWith(File configPath,
            String serverIp, int serverPort, JsonObject serverInfo,
            String blockDir, int userId) {
        ClientConfigManager cm = new ClientConfigManager(configPath);

        cm.serverIp = serverIp;
        cm.serverPort = serverPort;
        cm.storedBlocks = serverInfo.get("minimum-blocks").getAsInt();
        cm.blockDir = blockDir;
        cm.port = -1;
        cm.ftpPort = -1;
        cm.threadPoolSize = 3;
        cm.publicKey = null;
        cm.privateKey = null;
        cm.userId = userId;

        cm.serverInfo = serverInfo;
        cm.fsView = new NeguraFsView(cm);
        cm.blockCache.load();

        return cm;
    }

    public synchronized void save() {
        JsonArray blockListSaved = new JsonArray();
        for (Integer i : blockList) {
            blockListSaved.add(new JsonPrimitive(i));
        }

        JsonArray downloadedBlocks = new JsonArray();
        for (Integer id : idToHash.keySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("i", id);
            o.addProperty("h", idToHash.get(id));
            o.addProperty("c", idToStoreCode.get(id));
            downloadedBlocks.add(o);
        }

        JsonArray filesystem = new JsonArray();
        for (Operation o : fsView.getOperations()) {
            filesystem.add(o.toJson());
        }

        JsonObject config = new JsonObject();
        config.addProperty("server-ip", serverIp);
        config.addProperty("server-port", serverPort);
        config.addProperty("stored-blocks", storedBlocks);
        config.addProperty("block-dir", blockDir);
        config.addProperty("port", port);
        config.addProperty("ftp-port", ftpPort);
        config.addProperty("thread-pool-size", getThreadPoolSize());
        config.addProperty("private-key", RSA.toString(privateKey));
        config.addProperty("public-key", RSA.toString(publicKey));
        config.addProperty("user-id", getUserId());

        config.add("server-info", serverInfo);
        config.add("filesystem", filesystem);

        config.add("block-list", blockListSaved);
        config.add("downloaded-blocks", downloadedBlocks);

        try {
            FileWriter w = new FileWriter(configPath);
            w.write(new Gson().toJson(config));
            w.close();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Couldn't write config file.", ex);
        }
    }

    public synchronized String saveBlock(int id, byte[] block, int size) {
        File bDir = new File(blockDir);
        String storeCode = Util.randomFileName(bDir, null, BLOCK_EXTENSION);
        File blockFile = new File(bDir, storeCode + BLOCK_EXTENSION);

        // Getting the hash for this block.
        blockHash.update(block, 0, size);
        String hash = DatatypeConverter.printHexBinary(
                blockHash.digest());

        try {
            FileOutputStream out = new FileOutputStream(blockFile);
            out.write(block, 0, size);
            out.close();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Unable to save block.", ex);
        }

        hashToId.put(hash, id);
        idToHash.put(id, hash);
        idToStoreCode.put(id, storeCode);
        storeCodeToId.put(storeCode, id);

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
            if (blockList.contains(iter.next().intValue())) {
                iter.remove();
            }
        }

        if (newBlocks.isEmpty()) {
            return;
        }

        // If the number of new blocks exceeds the number of stored blocks
        // remove from the left.
        if (newBlocks.size() > storedBlocks) {
            int throwOut = newBlocks.size() - storedBlocks;
            for (int i = 0; i < throwOut; i++) {
                newBlocks.remove(0); // TODO: use another data type.
            }
        }

        int pushOut;
        int maxToLeave = storedBlocks - newBlocks.size();
        if (blockList.size() > maxToLeave) {
            pushOut = blockList.size() - maxToLeave;
        } else {
            pushOut = 0;
        }

        for (int i = 0; i < pushOut; i++) {
            deleteBlock(blockList.remove(0)); // TODO: use another data type.
        }
        for (Integer i : newBlocks) {
            blockList.add(i);
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
        if (idToStoreCode.containsKey(id)) {
            return new File(blockDir, idToStoreCode.get(id) + BLOCK_EXTENSION);
        }
        return null;
    }

    /**
     * Removes all traces of the block, except from the blockList.
     */
    private synchronized void deleteBlock(int id) {
        if (idToHash.containsKey(id)) {
            String hash = idToHash.get(id);
            String storeCode = idToStoreCode.get(id);
            File block = fileForBlockId(id);
            if (block.delete() == false) {
                throw new RuntimeException("Could not delete file " + block);
            }
            hashToId.remove(hash);
            idToHash.remove(id);
            idToStoreCode.remove(id);
            storeCodeToId.remove(storeCode);
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

    public synchronized String getServerIp() {
        return serverIp;
    }

    public synchronized void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public synchronized int getServerPort() {
        return serverPort;
    }

    public synchronized void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public synchronized InetSocketAddress getServerSocketAddress() {
        return new InetSocketAddress(serverIp, serverPort);
    }

    public synchronized int getStoredBlocks() {
        return storedBlocks;
    }

    public synchronized void setStoredBlocks(int storedBlocks) {
        this.storedBlocks = storedBlocks;
    }

    public synchronized String getBlockDir() {
        return blockDir;
    }

    public synchronized void setBlockDir(String blockDir) {
        this.blockDir = blockDir;
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized void setPort(int port) {
        this.port = port;
    }

    public synchronized int getFtpPort() {
        return ftpPort;
    }

    public synchronized void setFtpPort(int ftpPort) {
        this.ftpPort = ftpPort;
    }

    public synchronized void setKeyPair(KeyPair keyPair) {
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    public synchronized RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public synchronized RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public synchronized int getUserId() {
        return userId;
    }

    public synchronized void setUserId(int userId) {
        this.userId = userId;
    }

    public synchronized int getThreadPoolSize() {
        return threadPoolSize;
    }

    public synchronized int getServerInfoBlockSize() {
        return serverInfo.get("block-size").getAsInt();
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
