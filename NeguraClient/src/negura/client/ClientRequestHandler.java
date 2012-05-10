package negura.client;

import negura.client.fs.NeguraFsView;
import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import negura.common.util.Comm;
import negura.common.RequestHandler;
import negura.common.util.NeguraLog;
import negura.common.util.Util;

/**
 *
 * @author Paul Nechifor
 */
public class ClientRequestHandler implements RequestHandler {
    private Negura negura;
    private ClientConfigManager cm;
    private NeguraFsView fsView;

    public ClientRequestHandler(Negura negura, ClientConfigManager cm) {
        this.negura = negura;
        this.cm = cm;
        this.fsView = cm.getFsView();
    }

    public void handle(Socket socket) {
        JsonObject message = Comm.readFromSocket(socket);

        String request = message.get("request").getAsString();
        NeguraLog.info("Request '%s' from %s.", request, socket);

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
            try { socket.close(); }
            catch (IOException ex) { }
        }
    }

    private void handle_block_announce(Socket socket, JsonObject message)
            throws UnknownHostException, IOException {
        Comm.writeToSocket(socket, new JsonObject());
        socket.close();
        negura.getStateMaintainer().triggerBlockListUpdate();
    }

    private void handle_up_block(Socket socket, JsonObject message)
            throws IOException {
        File blockLocation = cm.fileForBlockId(
                message.get("block-id").getAsInt());
        BufferedOutputStream out = new BufferedOutputStream(
                socket.getOutputStream());
        DataOutputStream dataOut = new DataOutputStream(out);
        int offset = 0;
        int length = -1;

        if (message.get("offset") != null) {
            offset = message.get("offset").getAsInt();
        }
        if (message.get("length") != null) {
            length = message.get("length").getAsInt();
        }

        if (blockLocation == null) {
            dataOut.writeInt(Comm.BLOCK_NOT_FOUND);
            NeguraLog.warning("Block not found.");
            dataOut.flush();
            return;
        }

        int realLength = (int) blockLocation.length() - offset;
        if (length > realLength) {
            dataOut.writeInt(Comm.BLOCK_INVALID_LENGTH);
            NeguraLog.warning("Block invalid length.");
            dataOut.flush();
            return;
        }

        if (length == -1) {
            length = realLength;
        }

        InputStream in = new FileInputStream(blockLocation);
        if (offset > 0) {
            in.skip(offset);
        }
        byte[] buffer = new byte[cm.getServerInfoBlockSize()];
        int read = Util.readBytes(buffer, 0, length, in);

        try {
            dataOut.writeInt(Comm.BLOCK_FOUND);
            dataOut.writeInt(read);
            dataOut.flush();
            out.write(buffer, 0, read);
        } catch (IOException ex) {
            NeguraLog.warning(ex);
        }
    }

    private void handle_filesystem_update(Socket socket, JsonObject message)
            throws IOException {
        Comm.writeToSocket(socket, new JsonObject());
        socket.close();
        negura.getStateMaintainer().triggerFileSystemUpdate();
    }
}
