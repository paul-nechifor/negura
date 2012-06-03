package negura.common.util;

import negura.common.ex.NeguraError;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;

import static negura.common.util.Util.Multiplier.MULTIP;

/**
 * General purpose static utility methods.
 * @author Paul Nechifor
 */
public class Util {
    /** All the units that can exist with a long. */
    private static final String[] UNITS = new String[]{"B", "KiB", "MiB", "GiB",
            "TiB", "PiB", "EiB"};
    private static final long MINUTE = 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;

    public static class Multiplier {
        public static final long B = 1;
        public static final long KiB = B * 1024;
        public static final long MiB = KiB * 1024;
        public static final long GiB = MiB * 1024;
        public static final long TiB = GiB * 1024;
        public static final long PiB = TiB * 1024;
        public static final long EiB = PiB * 1024;
        public static final long[] MULTIP = new long[] {B, KiB, MiB, GiB, TiB,
            PiB, EiB};

        public final double size;
        public final long unit;

        public Multiplier(double size, long unit) {
            this.size = size;
            this.unit = unit;
        }
    }

    private Util() { }

    /**
     * Returns the long as a string with it's binary prefix unit.
     * @param bytes         The number to be formatted.
     * @param precision     The number of decimals.
     * @return              The string representation.
     */
    public static String bytesWithUnit(long bytes, int precision) {
        double ret = bytes;
        String format = "%." + precision + "f %s";
        for (int i = 0; i < UNITS.length; i++) {
            if (ret < 1024.0)
                return String.format(format, ret, UNITS[i]);
            ret /= 1024.0;
        }
        return String.format(format, ret, UNITS[UNITS.length - 1]);
    }

    public static Multiplier bytesWithoutUnit(long bytes) {
        double ret = bytes;
        for (int i = 0; i < MULTIP.length; i++) {
            if (ret < 1024.0)
                return new Multiplier(ret, MULTIP[i]);
            ret /= 1024.0;
        }
        return new Multiplier(ret, MULTIP[MULTIP.length - 1]);
    }

    public static String timeInterval(long seconds) {
        long copy = seconds;

        long secs = copy % 60;
        copy /= 60;
        long mins = copy % 60;
        copy /= 60;
        long hours = copy % 24;
        long days = copy / 24;

        String ret = String.format("%02d:%02d:%02d", hours, mins, secs);

        if (days > 0) {
            return String.format("%dd %s", days, ret);
        } else {
            return ret;
        }
    }

    /**
     * Tries to parse a string as long.
     * @param string    The string which represents a long.
     * @return          The long value or zero on error.
     */
    public static long parseLongOrZero(String string) {
        try {
            return Long.parseLong(string);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Tries to parse a string as int.
     * @param string    The string which represents an int.
     * @return          The int value or zero on error.
     */
    public static int parseIntOrZero(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException ex) {
            return 0;
        }
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

    // TODO: update this to IPv6
    public static InetSocketAddress stringToSocketAddress(String address) {
        String[] split = address.split(":");
        int port = Integer.parseInt(split[1]);
        return new InetSocketAddress(split[0], port);
    }

    public static int[] toArray(Collection<Integer> collection) {
        int[] ret = new int[collection.size()];
        int k = 0;

        for (int item : collection) {
            ret[k] = item;
            k++;
        }
        
        return ret;
    }

    public static ArrayList<Integer> toArrayList(int[] array) {
        ArrayList<Integer> ret = new ArrayList<Integer>();

        for (int item : array) {
            ret.add(item);
        }
        
        return ret;
    }

    public static void copyStream(InputStream in, OutputStream out,
            byte[] buffer) throws IOException {
        int read;

        while ((read = in.read(buffer)) >= 0)
            out.write(buffer, 0, read);

        in.close();
        out.close();
    }

    public static String join(String[] strings, String delimiter) {
        if (strings == null || strings.length == 0)
            throw new NeguraError("Can't join 0 strings.");

        StringBuilder builder = new StringBuilder(strings[0]);

        for (int i = 1; i < strings.length; i++)
            builder.append(delimiter).append(strings[i]);

        return builder.toString();
    }
}
