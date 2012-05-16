package negura.common.util;

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
import negura.common.json.Json;

/**
 * Comunication utility functions.
 *
 * @author Paul Nechifor
 */
public class Comm {
    private static String protocol;
    private static String software;
    private static final JsonParser PARSER = new JsonParser();

    public static InetAddress ADDRESS;

    public static final int BLOCK_FOUND = 1;
    public static final int BLOCK_BUSY = 2;
    public static final int BLOCK_NOT_FOUND = 3;
    // Could not read all the bytes that were requested because the block is
    // shorter.
    public static final int BLOCK_INVALID_LENGTH = 4;

    private Comm() { }

    public static void init(String protocol, String software) {
        Comm.protocol = protocol;
        Comm.software = software;
        try {
            ADDRESS = Os.getFirstNetworkAddress();
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }
    }

    /**
     * Returns a JSON object with the required fields.
     * @return              The JSON message.
     */
    public static JsonObject newMessage() {
        JsonObject ret = new JsonObject();
        ret.addProperty("protocol", protocol);
        ret.addProperty("software", software);
        return ret;
    }

    /**
     * Returns an empty JSON request.
     * @param request       The type of the request.
     * @return              The JSON message.
     */
    public static JsonObject newMessage(String request) {
        JsonObject ret = newMessage();
        ret.addProperty("request", request);
        return ret;
    }

    public static JsonObject readMessage(Socket socket, JsonObject message) {
        writeMessage(socket, message);
        JsonObject ret = readMessage(socket);
        closeSocket(socket);
        return ret;
    }

    public static JsonObject readMessage(InetSocketAddress address,
            JsonObject message) throws IOException  {
        JsonObject ret = null;
        Socket socket = new Socket();
        try {
            socket.bind(new InetSocketAddress(ADDRESS, 0));
            socket.connect(address);
            ret = readMessage(socket, message);
        } finally {
            Comm.closeSocket(socket);
        }
        return ret;
    }

    public static JsonObject readMessage(String ipAddress, int port,
            JsonObject message) throws IOException {
        return readMessage(new InetSocketAddress(ipAddress, port), message);
    }

    /**
     * Reads a JSON message from the socket without closing it.
     * @param socket    Where to read from.
     * @return          The JSON message which was read.
     */
    public static JsonObject readMessage(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            return PARSER.parse(reader).getAsJsonObject();
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        return null;
    }

    // Write a JSON message to a socket and shutdown the output.
    public static void writeMessage(Socket socket, JsonObject message) {
        try {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
            writer.write(Json.toString(message));
            writer.flush();
            socket.shutdownOutput();
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }
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
        writeMessage(socket, message);
        closeSocket(socket);
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

    // TODO: e foarte aiurea totul pe-aici. trebuie revizuit.
    public static int readBlock(byte[] buffer, int offset, int length,
            int bid, InetSocketAddress address) {
        JsonObject mesg = Comm.newMessage("up-block");
        mesg.addProperty("block-id", bid);
        if (offset > 0)
            mesg.addProperty("offset", offset);
        if (length > 0)
            mesg.addProperty("length", length);

        Socket socket = new Socket();
        try {
            try {
                socket.bind(new InetSocketAddress(ADDRESS, 0));
                socket.connect(address);
            } catch (Exception ex) {
                return -1;
            }
            writeMessage(socket, mesg);

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
