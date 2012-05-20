package negura.server.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import negura.common.util.Comm;
import negura.common.Service;
import negura.common.util.NeguraLog;
import negura.server.DataManager;

/**
 * Announces the uses of the blocks they must download and of the filesystem
 * update.
 * @author Paul Nechifor
 */
public class Announcer extends Service {
    private DataManager dataManager;
    private Thread thisThread;
    private final LinkedList<Integer> queue = new LinkedList<Integer>();
    final private LinkedList<Integer> opsQueue = new LinkedList<Integer>();
    private long lastSentNewOps = System.currentTimeMillis();

    public Announcer(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void run() {
        thisThread = Thread.currentThread();
        long now;
        while (getContinueRunning()) {
            announceNewBlocks();
            
            now = System.currentTimeMillis();
            if (now - lastSentNewOps > 5 * 60 * 1000) {
                announceNewOperation();
                lastSentNewOps = System.currentTimeMillis();
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) { }
        }
    }

    @Override
    public void requestStop() {
        super.requestStop();
        thisThread.interrupt();
    }

    public void addNewBlocks(List<Integer> userIds) {
        synchronized (queue) {
            queue.addAll(userIds);
        }
    }

    public void addNewOperation(int opId) {
        synchronized (opsQueue) {
            opsQueue.add(opId);
        }
    }

    public void triggerSendNewOps() {
        lastSentNewOps = 0;
    }

    // TODO: the fact that I access the DB everytime to get the address is wrong
    private void announceNewBlocks() {
        // At each iteration in this outer loop I get one entry out. I do it
        // like this so that I don't lock the queue during the whole while loop.

        Integer userId;
        JsonObject message = Comm.newMessage("block-announce");
        while (!queue.isEmpty()) {
            synchronized (queue) {
                userId = queue.pop();
            }

            InetSocketAddress address = dataManager.userAddress(userId);
            try {
                Comm.readMessage(address, message);
            } catch (Exception ex) {
                NeguraLog.warning(ex);
            }

            // TODO:
            // The queue might get too big (unable to process as fast as they
            // are arriving in which care I should probably just trim it.
        }
    }

    private void announceNewOperation() {
        synchronized (opsQueue) {
            if (opsQueue.isEmpty())
                return;
        }

        List<InetSocketAddress> recentUsers = null;
        try {
            recentUsers = dataManager.getRecentUserAddresses();
        } catch (SQLException ex) {
            NeguraLog.severe(ex);
        }
        // Prepare the universal message.
        JsonObject send = Comm.newMessage("filesystem-update");
        JsonArray array = new JsonArray();
        synchronized (opsQueue) {
            if (opsQueue.isEmpty())
                return;
            for (Integer i : opsQueue)
                array.add(new JsonPrimitive(i));
            opsQueue.clear();
        }
        send.add("ids", array);

        for (InetSocketAddress a : recentUsers) {
            try {
                Comm.readMessage(a, send);
            } catch (Exception ex) {
                NeguraLog.warning(ex);
            }
        }
    }
}
