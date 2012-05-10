package negura.common.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import negura.common.socket.PoolSocketFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.StackObjectPool;

/**
 * Comunication utility functions.
 *
 * @author Paul Nechifor
 */
public class Comm {
    private static String protocol;
    private static String software;
    private static final Gson GSON = new Gson();
    private static ObjectPool<Socket> pool;

    public static final int BLOCK_FOUND = 1;
    public static final int BLOCK_BUSY = 2;
    public static final int BLOCK_NOT_FOUND = 3;
    // Could not read all the bytes that were requested because the block is
    // shorter.
    public static final int BLOCK_INVALID_LENGTH = 4;

    private Comm() { }

    public static void init(String protocol, String software,
            InetAddress address, int startPort, int endPort) {
        Comm.protocol = protocol;
        Comm.software = software;
        pool = new StackObjectPool<Socket>(
                new PoolSocketFactory(address, startPort, endPort));
    }

    // Returns a JSON object which already contains the required fields.
    public static JsonObject newMessage() {
        JsonObject ret = new JsonObject();
        ret.addProperty("protocol", protocol);
        ret.addProperty("software", software);
        return ret;
    }

    // Returns an empty JSON request.
    public static JsonObject newMessage(String request) {
        JsonObject ret = newMessage();
        ret.addProperty("request", request);
        return ret;
    }

    public static JsonObject getAnswer(String ipAddress, int port,
            JsonObject message) throws UnknownHostException, IOException {
        Socket socket = new Socket(ipAddress, port);
        return askForResponse(socket, message);
    }

    public static JsonObject getAnswer(InetSocketAddress address,
            JsonObject message) throws UnknownHostException, IOException {
        return getAnswer(address.getHostName(), address.getPort(), message);
    }

    // Read the JSON message from the socket, but don't close it.
    public static JsonObject readFromSocket(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            JsonParser parser = new JsonParser();
            return parser.parse(reader).getAsJsonObject();
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        return null;
    }

    // Write a JSON message to a socket and shutdown the output.
    public static void writeToSocket(Socket socket, JsonObject message) {
        try {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
            writer.write(GSON.toJson(message));
            writer.flush();
            socket.shutdownOutput();
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }
    }

    public static JsonObject askForResponse(Socket socket, JsonObject message) {
        writeToSocket(socket, message);
        JsonObject ret = readFromSocket(socket);
        closeSocket(socket);
        return ret;
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ex) {
            NeguraLog.warning(ex);
        }
    }

    public static void terminateWithError(Socket socket, String errorMessage) {
        JsonObject message = newMessage();
        message.addProperty("error", errorMessage);
        writeToSocket(socket, message);
        closeSocket(socket);
    }

    public static InputStream receiveBlockInputStream(int bid, int offset,
            InetSocketAddress address) throws IOException {
        JsonObject mesg = Comm.newMessage("up-block");
        mesg.addProperty("block-id", bid);
        if (offset > 0)
            mesg.addProperty("offset", offset);
        Socket socket = new Socket(address.getAddress(), address.getPort());
        writeToSocket(socket, mesg);

        InputStream in = socket.getInputStream();
        DataInputStream dataIn = new DataInputStream(in);
        int answerCode = dataIn.readInt();

        switch (answerCode) {
            // All is good, but ignore the length.
            case BLOCK_FOUND:
                int lenRead = dataIn.readInt();
                break;
            // All is bad.
            case BLOCK_BUSY:
            case BLOCK_NOT_FOUND:
            default:
                return null;
        }
        return in;
    }

    /**
     * Gets the block from the specified peer.
     * @param buffer        Where to store the block. Must be large enough.
     * @param offset        Where to start. Must be 0 to ignore. Note that this
     *                      offset reffers to the block, not the buffer.
     * @param length        The maximum length. Must be -1 to read all.
     * @param bid           The id of the block.
     * @param address       The address of the peer.
     * @return              The number of bytes read, or -1 if failed.
     */
    public static int receiveBlock(byte[] buffer, int offset, int length,
            int bid, InetSocketAddress address) {
        JsonObject mesg = Comm.newMessage("up-block");
        mesg.addProperty("block-id", bid);
        if (offset > 0)
            mesg.addProperty("offset", offset);
        if (length > 0)
            mesg.addProperty("length", length);

        Socket socket = null;
        try {
            try {
                socket = new Socket(address.getAddress(), address.getPort());
            } catch (Exception ex) {
                return -1;
            }
            writeToSocket(socket, mesg);

            InputStream in = socket.getInputStream();
            DataInputStream din = new DataInputStream(in);
            int answerCode = din.readInt();
            int lenRead;

            switch (answerCode) {
                case BLOCK_FOUND: // I have it.
                    lenRead = din.readInt();
                    if (length > 0 && lenRead != length)
                        throw new RuntimeException("Not same length.");
                    // Don't think you sould put offset here.
                    Util.readBytes(buffer, 0, lenRead, in);
                    break;
                case BLOCK_BUSY: // I have it, but no free connections
                case BLOCK_NOT_FOUND: // I don't have it
                default:
                    lenRead = -1;
                    break;
            }
            return lenRead;
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        } finally {
            if (socket != null)
                Comm.closeSocket(socket);
        }

        return -1;
    }

    /**
     * Converts an address from a string a socket address.
     * @param address   An address in the form '10.20.30.40:5000'.
     * @return          The socket address or null on failure.
     */
    public static InetSocketAddress stringToSocketAddress(String address) {
        String[] split = address.split(":");
        if (split.length != 2)
            return null;
        int port = -1;
        try {
            port = Integer.parseInt(split[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
        InetSocketAddress ret = new InetSocketAddress(split[0], port);
        if (ret.isUnresolved())
            return null;
        return ret;
    }
}
