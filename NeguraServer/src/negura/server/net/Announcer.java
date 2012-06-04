package negura.server.net;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import negura.common.Service;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;
import negura.common.util.Util;
import negura.server.DataManager;
import negura.server.ServerConfigManager;

/**
 * Announces the uses of the blocks they must download and of the filesystem
 * update.
 * @author Paul Nechifor
 */
public class Announcer extends Service {
    private final Runnable callAnnounceNewBlocks = new Runnable() {
        @Override
        public void run() {
            announceNewBlocks();
        }
    };
    private final Runnable callAnnounceNewOperations = new Runnable() {
        @Override
        public void run() {
            announceNewOperations();
        }
    };

    private final DataManager dataManager;
    private final Object allocatedUsersLock = new Object();
    private HashSet<Integer> allocatedUsers = new HashSet<Integer>();
    private ScheduledExecutorService scheduler;

    public Announcer(ServerConfigManager cm) {
        this.dataManager = cm.getNeguraServer().getDataManager();
    }

    @Override
    public void onStart() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(callAnnounceNewBlocks,
                5, 5, TimeUnit.MINUTES);

        started();
    }

    @Override
    public void onStop() {
        scheduler.shutdown();

        stopped();
    }

    /**
     * Add users to which blocks have been allocated and need to be announced of
     * this.
     * @param userIds       The list of user ids.
     */
    public void addNewAllocatedUsers(List<Integer> userIds) {
        synchronized (allocatedUsersLock) {
            allocatedUsers.addAll(userIds);
        }
    }

    /**
     * Called to signal that the operations should be announced.
     */
    public void triggerSendNewOperations() {
        // Thread safe.
        scheduler.schedule(callAnnounceNewOperations, 0, TimeUnit.SECONDS);
    }

    private void announceNewBlocks() {
        // Cloning it so I can process it without hogging the lock.
        HashSet<Integer> userIds;
        synchronized (allocatedUsersLock) {
            if (allocatedUsers.isEmpty())
                return;
            userIds = allocatedUsers;
            allocatedUsers = new HashSet<Integer>();
        }

        List<String> addresses = null;
        try {
            addresses = dataManager.getUserAddresses(userIds);
        } catch (SQLException ex) {
            NeguraLog.severe(ex);
        }

        if (addresses.size() != userIds.size()) {
            NeguraLog.severe("How come not all were found?");
        }

        JsonObject mesg = Comm.newMessage("block-announce");

        InetSocketAddress socketAddress;
        for (String address : addresses) {
            socketAddress = Util.stringToSocketAddress(address);
            try {
                Comm.readMessage(socketAddress, mesg);
            } catch (IOException ex) {
                NeguraLog.warning(ex);
            }
        }
    }

    private void announceNewOperations() {
        List<InetSocketAddress> recentUsers = null;
        try {
            recentUsers = dataManager.getRecentUserAddresses();
        } catch (SQLException ex) {
            NeguraLog.severe(ex);
        }

        JsonObject mesg = Comm.newMessage("filesystem-update");

        for (InetSocketAddress a : recentUsers) {
            try {
                Comm.readMessage(a, mesg);
            } catch (Exception ex) {
                NeguraLog.warning(ex);
            }
        }

        announceNewBlocks();
    }
}
