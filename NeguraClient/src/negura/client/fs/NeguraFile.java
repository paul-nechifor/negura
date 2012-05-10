package negura.client.fs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Paul Nechifor
 */
public class NeguraFile {
    public static final int FILE = 1;
    public static final int DIR = 2;
    
    /**
     * Compares files on name alone; useful for sorting files in a listing.
     */
    public static final Comparator<NeguraFile> NAME_COMPARATOR
            = new Comparator<NeguraFile>() {
        public int compare(NeguraFile a, NeguraFile b) {
            return a.name.compareTo(b.name);
        }
    };

    public NeguraFsView fsView;
    public int operationId;
    public String location;
    public String name;
    public int type;
    public long date;
    public long size;
    public HashMap<String, NeguraFile> subfiles;

    /**
     * Static factory constructor for a directory type.
     */
    public static NeguraFile newDir(NeguraFsView fsView, String location,
            String name, int date) {
        NeguraFile ret = new NeguraFile();
        ret.fsView = fsView;
        ret.operationId = 0; // No ID.
        ret.location = location;
        ret.name = name;
        ret.type = DIR;
        ret.date = date;
        ret.size = 4096;
        ret.subfiles = new HashMap<String, NeguraFile>();
        return ret;
    }

    /**
     * Static factory constructor for a file type.
     */
    public static NeguraFile newFile(NeguraFsView fsView, String location,
            String name, int date, long size, int operationId) {
        NeguraFile ret = new NeguraFile();
        ret.fsView = fsView;
        ret.operationId = operationId;
        ret.location = location;
        ret.name = name;
        ret.type = FILE;
        ret.date = date;
        ret.size = size;
        ret.subfiles = null;
        return ret;
    }

    /**
     * Returns a list of files in this directory (if it is a directory) in a
     * sorted order.
     * @return     The sorted list of files.
     */
    public List<NeguraFile> listFilesInOrder() {
        if (type != DIR)
            throw new RuntimeException("Can't list a non directory.");
        if (subfiles == null)
            throw new AssertionError("No subfiles.");

        List<NeguraFile> ret = new ArrayList<NeguraFile>(subfiles.values());
        Collections.sort(ret, NAME_COMPARATOR);
        return Collections.unmodifiableList(ret);
    }
}
