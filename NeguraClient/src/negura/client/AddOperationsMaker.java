package negura.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.DatatypeConverter;
import negura.common.data.Block;
import negura.common.data.BlockList;
import negura.common.data.Operation;
import negura.common.ex.NeguraError;
import negura.common.ex.NeguraEx;
import negura.common.util.NeguraLog;
import negura.common.util.Os;
import negura.common.util.Sha;

/**
 * Creates the add operations from the given file or directory.
 * @author Paul Nechifor
 */
public class AddOperationsMaker {
    private static class FileData {
        public File file;
        public String storePath;
        public long size;

        public FileData(File file, String storePath, long size) {
            this.file = file;
            this.storePath = storePath;
            this.size = size;
        }
    }

    public interface Listener {
        /**
         * Signals an update
         * @param complete          From 0 to 1, where 1 is completed.
         */
        void update(double complete);
    }

    private final ClientConfigManager cm;
    private final File fileOrDir;
    private final String initialStorePath;
    private final Listener listener;
    private final BlockList blockList;
    private final int blockSize;
    private final byte[] buffer;
    private final MessageDigest fileHash;

    private final List<FileData> allTheFiles = new ArrayList<FileData>();
    private final ArrayList<Operation> operations = new ArrayList<Operation>();
    private final ArrayList<ArrayList<File>> blockFiles
            = new ArrayList<ArrayList<File>>();

    private File dirForTempBlocks;

    private long totalSize = 0;
    private long currentProcessed = 0;

    public AddOperationsMaker(ClientConfigManager cm, File fileOrDir,
            String initialStorePath, Listener listener) {
        this.cm = cm;
        this.fileOrDir = fileOrDir.getAbsoluteFile();

        if (initialStorePath.endsWith("/")) {
            this.initialStorePath = initialStorePath
                    .substring(0, initialStorePath.length() - 1);
        } else {
            this.initialStorePath = initialStorePath;
        }

        this.listener = listener;
        this.blockList = cm.getBlockList();
        this.blockSize = cm.getBlockSize();
        this.buffer = new byte[this.blockSize];
        
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            NeguraLog.severe(ex);
        }
        this.fileHash = md;
    }

    /**
     * Starts the creation of the operations.
     * @throws IOException
     */
    public void createOperations() throws IOException {
        // The blocks will be saved here by block hash and then moved to the
        // temp block dir.
        dirForTempBlocks = Os.createRandomFile(cm.getDataDir(),
                Os.FileType.DIR, null, null);

        // Getting all the file data recursivelly.
        loadAllFiles();

        for (FileData fd : allTheFiles) {
            createOperation(fd);
        }
    }

    public ArrayList<Operation> getOperations() {
        return operations;
    }

    /**
     * Readies these operations to be served.
     * @param firstBlocks   A list of the first block for every operation.
     * @throws NeguraEx     On failure to delete files.
     */
    public void confirmOperations(List<Integer> firstBlocks) throws NeguraEx {
        if (firstBlocks.size() != operations.size()) {
            throw new NeguraError("The sizes do not fit.");
        }

        int len = operations.size();
        int blockId;

        List<Integer> tempBlockIds = new ArrayList<Integer>();

        for (int i = 0; i < len; i++) {
            blockId = firstBlocks.get(i);

            for (File blockFile : blockFiles.get(i)) {
                move(blockFile, blockList.getFileToSaveTempBlockTo(blockId));
                tempBlockIds.add(blockId);

                blockId++;
            }
        }

        blockList.addToTemporary(tempBlockIds);

        delete(dirForTempBlocks);
    }

    /**
     * Eliminates what was created by this failed operation.
     * @throws NeguraEx     On failure to delete files
     */
    public void cancelOperations() throws NeguraEx {
        for (ArrayList<File> files : blockFiles) {
            for (File file : files) {
                delete(file);
            }
        }

        delete(dirForTempBlocks);
    }

    private void loadAllFiles() {
        if (fileOrDir.isFile()) {
            addFile(fileOrDir, initialStorePath);
        } else {
            addDir(fileOrDir, initialStorePath);
        }
    }

    private void addDir(File dir, String storePath) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory())
                addDir(f, storePath + "/" + f.getName());
            else
                addFile(f, storePath + "/" + f.getName());
        }
    }

    private void addFile(File file, String storePath) {
        FileData fd = new FileData(file, storePath, file.length());
        allTheFiles.add(fd);

        totalSize += fd.size;
    }

    private void createOperation(FileData fd) throws IOException {
        double totalSizeDouble = totalSize;
        int nrBlocks = (int) Math.ceil((double) fd.size / blockSize);
        Block[] blocks = new Block[nrBlocks];

        ArrayList<File> theseBlocks = new ArrayList<File>();
        FileInputStream in = new FileInputStream(fd.file);
        int blockReadSize;
        File blockFile;

        for (int i = 0; i < blocks.length; i++) {
            // Reading the block.
            blockReadSize = in.read(buffer, 0, blockSize);

            // Getting the block hash.
            String hash = Sha.get256(buffer, 0, blockReadSize);

            // Saving the block.
            blockFile = new File(dirForTempBlocks, hash);
            theseBlocks.add(blockFile);
            FileOutputStream out = new FileOutputStream(blockFile);
            out.write(buffer, 0, blockReadSize);
            out.close();
            
            // Update listener.
            currentProcessed += blockReadSize;
            listener.update(currentProcessed / totalSizeDouble);

            // Update file hash.
            fileHash.update(buffer, 0, blockReadSize);

            blocks[i] = new Block(0, hash);
        }

        in.close();

        blockFiles.add(theseBlocks);

        Operation op = new Operation();
        op.type = "add";
        op.path = fd.storePath;
        op.size = fd.size;
        op.blocks = blocks;
        op.hash = DatatypeConverter.printHexBinary(fileHash.digest());
        op.signature = "my signature. this will be changed by the server";

        fileHash.reset();

        operations.add(op);
    }

    private static void delete(File file) throws NeguraEx {
        if (!file.delete()) {
            throw new NeguraEx("Failed to delete '%s'.",
                    file.getAbsoluteFile());
        }
    }

    private static void move(File from, File to) throws NeguraEx {
        if (!from.renameTo(to)) {
            throw new NeguraEx("Failed to move '%s' to '%s'.",
                    from.getAbsoluteFile(), to.getAbsoluteFile());
        }
    }
}
