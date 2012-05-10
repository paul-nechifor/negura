package negura.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import negura.common.util.Comm;
import negura.common.RequestHandler;
import negura.common.data.Operation;
import negura.common.util.NeguraLog;

public class ServerRequestHandler implements RequestHandler {
    private ServerConfigManager cm;
    private DataManager dataManager;
    private Announcer announcer;

    public ServerRequestHandler(ServerConfigManager cm, DataManager dataManager,
            Announcer announcer) {
        this.cm = cm;
        this.dataManager = dataManager;
        this.announcer = announcer;

        this.dataManager.recreateTables();
    }

    public void handle(Socket socket) {
        JsonObject message = Comm.readFromSocket(socket);

        String request = message.get("request").getAsString();
        NeguraLog.info("Request '%s' from %s.", request, socket);

        // The handle_* functions needn't close the socket as it is
        // automatically closed after the function call.
        try {
            if (request.equals("server-info")) {
                handle_server_info(socket, message);
            } else if (request.equals("registration")) {
                handle_registration(socket, message);
            } else if (request.equals("allocate-operation")) {
                handle_allocate_operation(socket, message);
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
            } else if (request.equals("trigger-fs-update")) {
                handle_trigger_fs_update(socket, message);
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

    private void handle_server_info(Socket socket, JsonObject message) {
        JsonObject serverInfo = cm.getServerInfo();

        for (Entry<String, JsonElement> e : Comm.newMessage().entrySet()) {
            serverInfo.add(e.getKey(), e.getValue());
        }

        Comm.writeToSocket(socket, serverInfo);
    }

    private void handle_registration(Socket socket, JsonObject message)
            throws SQLException, IOException {
        String ipAddress = socket.getInetAddress().getHostAddress();
        int port = message.get("port").getAsInt();
        int numberOfBlocks = message.get("number-of-blocks").getAsInt();
        String publicKey = message.get("public-key").getAsString();

        if (numberOfBlocks < cm.getMinimumBlocks()) {
            registrationError(socket, "Too few blocks.");
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
        Comm.writeToSocket(socket, resp);
    }

    private void registrationError(Socket socket, String errorMessage)
            throws IOException {
        JsonObject message = Comm.newMessage();
        message.addProperty("registration", "failed");
        message.addProperty("registration-failed-reason", errorMessage);
        Comm.writeToSocket(socket, message);
        socket.close();
    }
    
    // TODO: remove operation allocation and do it directly.
    private void handle_allocate_operation(Socket socket, JsonObject message)
            throws SQLException {
        int numberOfBlocks = message.get("number-of-blocks").getAsInt();
        int firstBlock = dataManager.singleRowResult(
                "SELECT nextval('blockSeq');");
        int last = firstBlock + numberOfBlocks - 1;
        // Advancing the block sequence.
        dataManager.returnsRows("SELECT setval('blockSeq', " + last + ", true);");
        int opid = dataManager.singleRowResult("SELECT nextval('operationSeq');");

        JsonArray blocks = new JsonArray();
        for (int i = firstBlock; i <= last; i++) {
            blocks.add(new JsonPrimitive(i));
        }

        JsonObject resp = Comm.newMessage();
        resp.addProperty("opid", opid);
        resp.add("block-ids", blocks);

        Comm.writeToSocket(socket, resp);
    }

    private void handle_add_operation(Socket socket, JsonObject message)
            throws IOException {
        // For now, there is no message to send back.
        Comm.writeToSocket(socket, new JsonObject());
        socket.close();

        Operation op = Operation.fromJson(message.getAsJsonObject("op"));
        op.signature = "generated signature";
        op.date = (int) (System.currentTimeMillis() / 1000);
        int userId = message.get("uid").getAsInt();
        List<Integer> users = null;

        try {
            users = dataManager.insertOperationAndAllocate(userId, op);
        } catch (SQLException ex) {
            NeguraLog.severe(ex);
        }

        announcer.addNewBlocks(users);
        announcer.addNewOperation(op.id);
    }

    // TODO consider the case where there are no peers for any of the blocks.
    private void handle_peers_for_blocks(Socket socket, JsonObject message)
            throws SQLException {
        ArrayList<Integer> blocks = new ArrayList<Integer>();
        for (JsonElement e : message.getAsJsonArray("blocks")) {
            blocks.add(e.getAsInt());
        }

        Map<Integer, ArrayList<String>> peers =
                dataManager.peersForBlocks(blocks);

        JsonObject ret = Comm.newMessage();
        JsonArray retList = new JsonArray();
        JsonObject peersJ;
        JsonArray list;
        ArrayList<String> peerList;
        for (Integer b : blocks) {
            peersJ = new JsonObject();
            peersJ.addProperty("id", b);
            list = new JsonArray();
            peerList = peers.get(b);
            if (peerList == null) {
                // Should add this in errors.
                // TODO: got this error before.
                NeguraLog.warning("Received non existant block");
                continue;
            }
            for (String a : peerList) {
                list.add(new JsonPrimitive(a));
            }
            peersJ.add("peers", list);
            retList.add(peersJ);
        }

        ret.add("blocks", retList);

        Comm.writeToSocket(socket, ret);
    }

    private void handle_have_blocks(Socket socket, JsonObject message)
            throws SQLException, IOException {
        Comm.writeToSocket(socket, new JsonObject());
        socket.close();

        int uid = message.get("uid").getAsInt();
        LinkedList<Integer> list = new LinkedList<Integer>();
        for (JsonElement e : message.getAsJsonArray("blocks")) {
            list.add(e.getAsInt());
        }

        dataManager.insertCompleted(uid, list);
    }

    private void handle_get_block_list(Socket socket, JsonObject message)
            throws SQLException {
        int uid = message.get("uid").getAsInt();
        List<Integer> list = dataManager.getBlockList(uid);

        JsonArray blocks = new JsonArray();
        for (Integer i : list) {
            blocks.add(new JsonPrimitive(i));
        }
        JsonObject resp = Comm.newMessage();
        resp.add("blocks", blocks);
        Comm.writeToSocket(socket, resp);
    }

    private void handle_filesystem_state(Socket socket, JsonObject message)
            throws SQLException {
        int after = message.get("after").getAsInt();
        List<Operation> list = dataManager.operationsAfter(after);

        JsonObject resp = Comm.newMessage();
        JsonArray ops = new JsonArray();
        for (Operation op : list) {
            ops.add(op.toJson());
        }
        resp.add("operations", ops);
        Comm.writeToSocket(socket, resp);
    }

    private void handle_trigger_fs_update(Socket socket, JsonObject message)
            throws IOException {
        Comm.writeToSocket(socket, new JsonObject());
        socket.close();
        announcer.triggerSendNewOps();
    }
}
