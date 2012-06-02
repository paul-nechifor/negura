package negura.client.net;

import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import negura.client.ClientConfigManager;
import negura.client.Negura;
import negura.common.data.TrafficAggregator;
import negura.common.json.Json;
import negura.common.util.Comm;
import negura.common.net.RequestHandler;
import negura.common.util.NeguraLog;
import negura.common.util.Util;

/**
 *
 * @author Paul Nechifor
 */
public class ClientRequestHandler implements RequestHandler {
    private final ClientConfigManager cm;
    private final Negura negura;
    private final TrafficAggregator trafficAggregator;

    public ClientRequestHandler(Negura negura, ClientConfigManager cm) {
        this.negura = negura;
        this.cm = cm;
        this.trafficAggregator = cm.getTrafficAggregator();
    }

    public void handle(Socket socket) {
        JsonObject message = Comm.readMessage(socket);

        long start = System.nanoTime();
        String request = message.get("request").getAsString();
        

        // The handle_* functions needn't close the socket as it is
        // automatically closed after the function call.
        try {
            if (request.equals("block-announce")) {
                handle_block_announce(socket, message);
            } else if (request.equals("up-block")) {
                handle_up_block(socket, message);
            } else if (request.equals("filesystem-update")) {
                handle_filesystem_update(socket, message);
            } else {
                String error = "Request not known: '" + request + "'.";
                Comm.terminateWithError(socket, error);
                NeguraLog.warning(error);
            }
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        if (!socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ex) {
                NeguraLog.warning(ex);
            }
        }

        if (!request.equals("up-block")) {
            NeguraLog.info("Request '%s' from %s:%d. %d", request,
                    socket.getInetAddress().getHostAddress(), socket.getPort(),
                    System.nanoTime() - start);
        }
    }

    private void handle_up_block(Socket socket, JsonObject message)
            throws IOException {
        int blockId = message.get("block-id").getAsInt();
        File blockLocation = cm.getBlockList().getBlockFileIfExists(blockId);

        OutputStream os = socket.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        DataOutputStream dos = new DataOutputStream(bos);

        int offset = Json.getDefault(message, "offset", 0);
        int length = Json.getDefault(message, "length", -1);

        if (blockLocation == null) {
            dos.writeInt(Comm.BLOCK_NOT_FOUND);
            NeguraLog.warning("Requested block %d not found.", blockId);
            dos.flush();
            trafficAggregator.addSessionUp(4);
            return;
        }

        int realLength = (int) blockLocation.length() - offset;
        if (length > realLength) {
            dos.writeInt(Comm.BLOCK_INVALID_LENGTH);
            NeguraLog.warning("Invalid length %d > %d.", length, realLength);
            dos.flush();
            trafficAggregator.addSessionUp(4);
            return;
        }

        // If length is -1 then all was requested.
        if (length == -1) {
            length = realLength;
        }

        InputStream in = new FileInputStream(blockLocation);
        if (offset > 0) {
            in.skip(offset);
        }

        // TODO: Reuse the buffer.
        byte[] buffer = new byte[cm.getBlockSize()];
        int read = Util.readBytes(buffer, 0, length, in);

        try {
            dos.writeInt(Comm.BLOCK_FOUND);
            dos.writeInt(read);
            dos.flush();
            bos.write(buffer, 0, read);
            trafficAggregator.addSessionUp(4 + 4 + read);
        } catch (IOException ex) {
            NeguraLog.warning(ex);
        }
    }

    private void handle_block_announce(Socket socket, JsonObject message)
            throws UnknownHostException, IOException {
        Comm.writeMessage(socket, new JsonObject());
        socket.close();
        negura.getStateMaintainer().triggerBlockListUpdate();
    }

    private void handle_filesystem_update(Socket socket, JsonObject message)
            throws IOException {
        Comm.writeMessage(socket, new JsonObject());
        socket.close();
        negura.getStateMaintainer().triggerFileSystemUpdate();
    }
}
