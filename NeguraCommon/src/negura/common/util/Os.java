package negura.common.util;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Operating system specific utility methods.
 * @author Paul Nechifor
 */
public class Os {
    public static enum FileType {FILE, DIR}

    private Os() { }

    /**
     * Creates and returns a new empty file or empty directory.
     * @param dir       The directory in which the file should be stored.
     * @param type      Should it be file or a directory?
     * @param prefix    A prefix for the file name. Can be null.
     * @param suffix    A suffix for the file. Can be null. If a suffix isn't
     *                  supplied the file name will have no extension.
     * @return          The created empty file or empty directory or null on
     *                  failure.
     * @throws IOException on failure to create the file.
     */
    public static File createRandomFile(File dir, FileType type, String prefix,
            String suffix) throws IOException {
        int len = 8;

        File newFile = null;
        do {
            char[] c = new char[len];
            for (int i = 0; i < len; i++)
                c[i] = (char) ('A' + Math.random() * 26);

            String name = "";
            if (prefix != null)
                name += prefix;
            name += new String(c);
            if (suffix != null)
                name += suffix;

            newFile = new File(dir, name);
        } while (newFile.exists());

        switch (type) {
            case FILE:
                if (newFile.createNewFile())
                    return newFile;
                break;
            case DIR:
                if (newFile.mkdirs())
                    return newFile;
                break;
            default:
                throw new AssertionError();
        }

        // Failed to create the file or directory.
        return null;
    }

    public static File getUserHome() {
        return new File(System.getProperty("user.home"));
    }
    
    /**
     * Returns the user configuration directory which is specific to the
     * platform.
     * @return              The configuration directory.
     */
    public static File getUserConfigDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String location;
        if (os.indexOf("windows") >= 0) {
            // The OS is Windows.
            location = System.getenv("APPDATA");
            if (location != null && !location.isEmpty())
                return new File(location);
            location = System.getProperty("user.home");
            if (location != null)
                return new File(location);
        } else {
            // The OS is probably UNIX based.
            location = System.getenv("XDG_CONFIG_HOME");
            if (location != null && !location.isEmpty())
                return new File(location);
            location = System.getProperty("user.home") + File.separator
                    + ".config";
            File ret = new File(location);
            if (ret.exists())
                return ret;
            if (ret.mkdirs())
                return ret;
        }
        throw new AssertionError("Couldn't get user config dir.");
    }

    /**
     * Returns the file/dir specified by the path which is relative to the
     * user's configuration directory.
     * @param path          The path names.
     * @return              The File corresponding to it.
     */
    public static File getUserConfigDir(String... path) {
        return join(getUserConfigDir(), path);
    }

    /**
     * Returns the data directory which is specific to the platform.
     * @return              The data directory.
     */
    public static File getUserDataDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String location;
        if (os.indexOf("windows") >= 0) {
            // The OS is Windows.
            location = System.getenv("APPDATA");
            if (location != null && !location.isEmpty())
                return new File(location);
            location = System.getProperty("user.home");
            if (location != null)
                return new File(location);
        } else {
            // The OS is probably UNIX based.
            location = System.getenv("XDG_DATA_HOME");
            if (location != null && !location.isEmpty())
                return new File(location);
            location = System.getProperty("user.home") + File.separator
                    + ".local" + File.separator + "share";
            File ret = new File(location);
            if (ret.exists())
                return ret;
            if (ret.mkdirs())
                return ret;
        }
        throw new AssertionError("Couldn't get user data dir.");
    }

    /**
     * Returns the file/dir specified by the path which is relative to the
     * user's data directory.
     * @param path          The path names.
     * @return              The File corresponding to it.
     */
    public static File getUserDataDir(String... path) {
        return join(getUserDataDir(), path);
    }

    /**
     * Returns all the addresses of all the network interfaces with the
     * exception of the loopback interface. Inet4Addresses are returned before
     * all Inet6Addresses.
     * @return      The list of all network addresses.
     * @throws SocketException
     */
    public static List<InetAddress> getAllNetworkAddresses()
            throws SocketException {
        List<NetworkInterface> interfaces =
                Collections.list(NetworkInterface.getNetworkInterfaces());
        LinkedList<InetAddress> ret = new LinkedList<InetAddress>();
        for (NetworkInterface ni : interfaces) {
            if (ni.isLoopback())
                continue;
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address)
                    ret.addFirst(a);
                else
                    ret.addLast(a);
            }
        }

        return ret;
    }

    /**
     * Gets the first network address with the exception of the loopback, but
     * if there are none, it returns the local host. Inet4Addresses are returned
     * before all Inet6Addresses.
     * @return      A network address.
     * @throws SocketException
     * @throws UnknownHostException 
     */
    public static InetAddress getFirstNetworkAddress()
            throws SocketException, UnknownHostException {
        List<InetAddress> a = getAllNetworkAddresses();
        if (a.isEmpty())
            return InetAddress.getLocalHost();
        return a.get(0);
    }

    /**
     * Remove a directory and all of it's contents.
     * @param directory     The directory to be removed.
     * @return              True on success, false otherwise.
     */
    public static boolean removeDirectory(File directory) {
        if (directory == null || !directory.exists() ||
                !directory.isDirectory()) {
            return false;
        }

        String[] list = directory.list();
        boolean success = true;

        // Some JVMs return null for File.list() when the directory is empty.
        if (list != null) {
            for (int i = 0; i < list.length; i++) {
                File entry = new File(directory, list[i]);
                if (entry.isDirectory()) {
                    if (!removeDirectory(entry))
                        success = false;
                }
                else {
                    if (!entry.delete())
                        success = false;
                }
            }
        }

        if (!directory.delete())
            success = false;

        return success;
    }

    private static File join(File f, String[] strings) {
        String ret = f.getAbsolutePath() + File.separator
                + Util.join(strings, File.separator);
        return new File(ret);
    }
}
