package negura.server.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.net.Socket;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import negura.common.ex.NeguraEx;
import negura.common.util.Comm;
import negura.common.net.RequestHandler;
import negura.common.data.Operation;
import negura.common.ex.NeguraError;
import negura.common.json.Json;
import negura.common.util.NeguraLog;
import negura.server.DataManager;
import negura.server.ServerConfigManager;

public class ServerRequestHandler implements RequestHandler {
    private ServerConfigManager cm;
    private DataManager dataManager;
    private Announcer announcer;

    public ServerRequestHandler(ServerConfigManager cm, DataManager dataManager,
            Announcer announcer) {
        this.cm = cm;
        this.dataManager = dataManager;
        this.announcer = announcer;
    }

    public void handle(Socket socket) {
        JsonObject message = Comm.readMessage(socket);

        String request = message.get("request").getAsString();
        NeguraLog.info("Request '%s' from %s:%d.", request,
                socket.getInetAddress().getHostAddress(), socket.getPort());

        // The handle_* functions needn't close the socket as it is
        // automatically closed after the function call.
        try {
            if (request.equals("server-info")) {
                handle_server_info(socket, message);
            } else if (request.equals("registration")) {
                handle_registration(socket, message);
            } else if (request.equals("add-operation")) {
                handle_add_operation(socket, message);
            } else if (request.equals("peers-for-blocks")) {
                handle_peers_for_blocks(socket, message);
            } else if (request.equals("have-blocks")) {
                handle_have_blocks(socket, message);
            } else if (request.equals("get-block-list")) {
                handle_get_block_list(socket, message);
            } else if (request.equals("filesystem-state")) {
                handle_filesystem_state(socket, message);
            } else if (request.equals("hashes-for-blocks")) {
                handle_hashes_for_blocks(socket, message);
            } else if (request.equals("trigger-fs-update")) {
                handle_trigger_fs_update(socket, message);
            } else {
                String error = "Request not known: '" + request + "'.";
                Comm.terminateWithError(socket, error);
                NeguraLog.warning(error);
            }
        } catch (BatchUpdateException ex) {
            NeguraLog.severe(ex.getNextException());
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
    }

    private void handle_server_info(Socket socket, JsonObject message) {
        JsonObject serverInfo = Json.toJsonElement(cm.getServerInfo())
                .getAsJsonObject();
        Json.extend(serverInfo, Comm.newMessage());
        Comm.writeMessage(socket, serverInfo);
    }

    private void handle_registration(Socket socket, JsonObject message)
            throws SQLException, IOException {
        String ipAddress = socket.getInetAddress().getHostAddress();
        int port = message.get("port").getAsInt();
        int numberOfBlocks = message.get("number-of-blocks").getAsInt();
        String publicKey = message.get("public-key").getAsString();

        if (numberOfBlocks < cm.getServerInfo().minimumBlocks) {
            registrationError(socket, "Too few blocks.");
            return;
        }

        if (numberOfBlocks > cm.getVirtualDiskBlocks()) {
            registrationError(socket, "You can't store more blocks than there"
                    + "are in the file system.");
            return;
        }

        if (dataManager.userExists(ipAddress, port)) {
            registrationError(socket, "IP address and port has already been "
                    + "used for registration.");
            return;
        }

        int uid = dataManager.createNewUser(ipAddress, port, numberOfBlocks,
                publicKey);

        NeguraLog.info("Registered user %d with %s:%d.", uid, ipAddress, port);

        JsonObject resp = Comm.newMessage();
        resp.addProperty("registration", "accepted");
        resp.addProperty("uid", uid);
        Comm.writeMessage(socket, resp);
    }

    private void registrationError(Socket socket, String errorMessage)
            throws IOException {
        JsonObject message = Comm.newMessage();
        message.addProperty("registration", "failed");
        message.addProperty("registration-failed-reason", errorMessage);
        Comm.writeMessage(socket, message);
        socket.close();
    }

    private void handle_add_operation(Socket socket, JsonObject message)
            throws IOException, SQLException, NeguraEx {
        Operation op = Json.fromJsonObject(
                message.get("operation").getAsJsonObject(), Operation.class);
        op.signature = "generated signature";
        op.date = (int) (System.currentTimeMillis() / 1000);
        int creatorId = message.get("uid").getAsInt();

        List<Integer> allocatedUsers = new ArrayList<Integer>();
        int firstBlockId = dataManager.insertOperationAndAllocate(op, creatorId,
                allocatedUsers);

        JsonObject resp = Comm.newMessage();
        resp.addProperty("first-block-id", firstBlockId);
        Comm.writeMessage(socket, resp);
        socket.close();

        announcer.addNewAllocatedUsers(allocatedUsers);
    }

    private void handle_peers_for_blocks(Socket socket, JsonObject message)
            throws SQLException {
        ArrayList<Integer> blocks = new ArrayList<Integer>();
        for (JsonElement e : message.getAsJsonArray("blocks")) {
            blocks.add(e.getAsInt());
        }

        if (blocks.isEmpty()) {
            NeguraLog.warning("The list is empty.");
            return;
        }

        Map<Integer, ArrayList<String>> peers =
                dataManager.getPeersForBlocks(blocks);

        if (peers.isEmpty()) {
            NeguraLog.warning("No peers for entire request %s.", blocks);
        }

        JsonObject ret = Comm.newMessage();
        JsonObject peersJ = new JsonObject();
        JsonArray array;
        ArrayList<String> addresses;

        for (Integer blockId : blocks) {
            array = new JsonArray();
            addresses = peers.get(blockId);

            if (addresses == null) {
                NeguraLog.warning("No peers were found for block %d.", blockId);
                continue;
            }

            if (addresses.isEmpty()) {
                throw new NeguraError("Cannon happen.");
            }

            for (String address : addresses)
                array.add(new JsonPrimitive(address));

            peersJ.add(blockId.toString(), array);
        }

        ret.add("blocks", peersJ);

        Comm.writeMessage(socket, ret);
    }

    private void handle_have_blocks(Socket socket, JsonObject message)
            throws SQLException, IOException {
        Comm.writeMessage(socket, new JsonObject());
        socket.close();

        int uid = message.get("uid").getAsInt();
        List<Integer> list = new ArrayList<Integer>();
        for (JsonElement e : message.getAsJsonArray("blocks")) {
            list.add(e.getAsInt());
        }

        dataManager.insertCompleted(uid, list);
    }

    private void handle_get_block_list(Socket socket, JsonObject message)
            throws SQLException {
        int uid = message.get("uid").getAsInt();
        int after = message.get("after").getAsInt();
        List<Integer> list = dataManager.getBlockListAfter(uid, after);

        JsonArray blocks = new JsonArray();
        for (Integer i : list) {
            blocks.add(new JsonPrimitive(i));
        }
        
        JsonObject resp = Comm.newMessage();
        resp.add("blocks", blocks);
        Comm.writeMessage(socket, resp);
    }

    private void handle_filesystem_state(Socket socket, JsonObject message)
            throws SQLException {
        int after = message.get("after").getAsInt();
        List<Operation> list = dataManager.getOperationsAfter(after);

        JsonArray ops = new JsonArray();
        for (Operation op : list) {
            ops.add(Json.toJsonElement(op));
        }

        JsonObject resp = Comm.newMessage();
        resp.add("operations", ops);
        Comm.writeMessage(socket, resp);
    }

    private void handle_hashes_for_blocks(Socket socket, JsonObject message) {
        // ...
    }

    private void handle_trigger_fs_update(Socket socket, JsonObject message)
            throws IOException {
        Comm.writeMessage(socket, new JsonObject());
        socket.close();
        announcer.triggerSendNewOperations();
    }
}
