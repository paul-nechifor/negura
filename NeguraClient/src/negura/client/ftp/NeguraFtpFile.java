package negura.client.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import negura.client.fs.NeguraFile;
import negura.client.fs.NeguraFileInputStream;
import org.apache.ftpserver.ftplet.FtpFile;

/**
 *
 * @author Paul Nechifor
 */
public class NeguraFtpFile implements FtpFile {
    private NeguraFile file;

    public NeguraFtpFile(NeguraFile file) {
        this.file = file;
    }

    public String getAbsolutePath() {
        return file.location + "/" + file.name;
    }

    public String getName() {
        return file.name;
    }

    public boolean isHidden() {
        return false;
    }

    public boolean isDirectory() {
        return file.type == NeguraFile.DIR;
    }

    public boolean isFile() {
        return file.type == NeguraFile.FILE;
    }

    // TODO. Make sure this is correct, that is, a file which doesn't exist
    // can't be created.
    public boolean doesExist() {
        return true;
    }

    public boolean isReadable() {
        return true;
    }

    public boolean isWritable() {
        return false;
    }

    public boolean isRemovable() {
        return false;
    }

    public String getOwnerName() {
        return "owner";
    }

    public String getGroupName() {
        return "group";
    }

    public int getLinkCount() {
        return 1;
    }

    public long getLastModified() {
        return file.date;
    }

    public boolean setLastModified(long l) {
        return false;
    }

    public long getSize() {
        return file.size;
    }

    public boolean mkdir() {
        return false;
    }

    public boolean delete() {
        return false;
    }

    public boolean move(FtpFile ff) {
        return false;
    }

    /**
     * List files. If not a directory, null will be returned.
     * @return
     */
    public List<FtpFile> listFiles() {
        if (!isDirectory())
            return null;

        if (file.subfiles == null || file.subfiles.isEmpty())
            return new LinkedList<FtpFile>();

        LinkedList<FtpFile> list = new LinkedList<FtpFile>();
        for (NeguraFile f : file.listFilesInOrder())
            list.add(new NeguraFtpFile(f));

        return Collections.unmodifiableList(list);
    }

    public OutputStream createOutputStream(long offset) throws IOException {
        throw new IOException("Cannot open for writing.");
    }

    public InputStream createInputStream(long offset) throws IOException {
        if (isDirectory())
            throw new IOException("Cannot open a directory for reading.");
        NeguraFileInputStream in = new NeguraFileInputStream(file, offset);
        return in;
    }
}
