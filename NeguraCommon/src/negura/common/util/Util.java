package negura.common.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Static utility methods used throughout the project.
 * @author Paul Nechifor
 */
public class Util {
    /** All the units that can exist with a long. */
    private static final String[] UNITS = new String[]{"B", "KiB", "MiB", "GiB",
            "TiB", "PiB", "EiB"};

    private Util() { }

    /**
     * Returns the long as a string with it's binary prefix unit.
     */
    public static String bytesWithUnit(long bytes) {
        for (int i = 0; i < UNITS.length; i++) {
            if (bytes < 1024)
                return bytes + " " + UNITS[i];
            bytes /= 1024;
        }
        return bytes + " " + UNITS[UNITS.length - 1];
    }

    public static JsonObject readJsonFromFile(String path)
            throws FileNotFoundException, IOException {
        FileReader fis = new FileReader(path);
        BufferedReader in = new BufferedReader(fis);
        JsonParser parser = new JsonParser();
        JsonObject config = parser.parse(in).getAsJsonObject();
        in.close();
        return config;
    }
    
    /**
     * Reads the whole input stream as a string encoded with UTF-8.
     * @param is        The input stream.
     * @return          The string read from the stream.
     * @throws IOException
     */
    public static String readStreamAsString(InputStream is) throws IOException {
        char[] buffer = new char[8 * 1024];
        StringBuilder builder = new StringBuilder();
        Reader in = new InputStreamReader(is, "UTF-8");
        int read;

        try {
            do {
                read = in.read(buffer, 0, buffer.length);
                if (read > 0)
                    builder.append(buffer, 0, read);
            } while (read >= 0);
        } finally {
            in.close();
        }
        return builder.toString();
    }

    // TODO: This hould return a file and the file should have been created
    // empty before it returns.
    public static String randomFileName(File dir, String pre, String post) {
        int len = 8;
        while (true) {
            char[] c = new char[len];
            for (int i = 0; i < len; i++)
                c[i] = (char) ('a' + Math.random() * 26);

            String code = new String(c);
            String name = "";
            if (pre != null)
                name += pre;

            name += code;
            if (post != null)
                name += post;

            File newFile = new File(dir, name);
            if (!newFile.exists())
                return code;
        }
    }

    /**
     * Reads <code>n</code> bytes from a <code>InputStream</code> to a buffer.
     * @param buffer   Where to store the bytes.
     * @param bufferOffset
     * @param n        How many to read.
     * @param in       From where to read.
     * @return         Number of bytes read.
     * @throws IOException
     */
    public static int readBytes(byte[] buffer, int bufferOffset, int n,
            InputStream in) throws IOException {
        int offset = 0;
        int read;

        while (true) {
            read = in.read(buffer, bufferOffset + offset, n - offset);
            if (read <= 0 || offset == n)
                break;
            offset += read;
        }

        return offset;
    }
    /**
     * Returns the user configuration directory which is specific to the
     * platform.
     * @return    The configuration directory.
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
     * Returns the data directory which is specific to the platform.
     * @return
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
     * Returns what would be normally printed by an exception as a string.
     * @param aThrowable
     * @return
     */
    public static String getStackTrace(Throwable aThrowable) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        return result.toString();
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
     * @return              Success or failure.
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
}
