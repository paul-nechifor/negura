package negura.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import negura.client.ClientConfigManager;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;
import negura.common.util.Util;

/**
 * Caches the information about which peers have certain blocks.
 * @author Paul Nechifor
 */
public class PeerCache {
    private final ClientConfigManager cm;
    private final InetSocketAddress myPeerAddress;
    private final HashMap<Integer, PeerList> blocks
            = new HashMap<Integer, PeerList>();

    public PeerCache(ClientConfigManager cm) {
        this.cm = cm;
        this.myPeerAddress = new InetSocketAddress(Comm.ADDRESS,
                cm.getServicePort());
    }

    public synchronized void preemptivelyCache(int[] blockIds) {
        JsonObject mesg = Comm.newMessage("peers-for-blocks");
        JsonArray list = new JsonArray();
        for (int i : blockIds)
            list.add(new JsonPrimitive(i));

        mesg.add("blocks", list);
        JsonObject resp = null;
        try {
            resp = Comm.readMessage(cm.getServerAddress(), mesg);
        } catch (Exception ex) {
            NeguraLog.warning(ex, "Server isn't responding.");
            return;
        }

        JsonObject o;
        PeerList peerList;
        int block;
        InetSocketAddress address;

        for (JsonElement e : resp.getAsJsonArray("blocks")) {
            o = e.getAsJsonObject();
            block = o.get("id").getAsInt();

            if (blocks.containsKey(block)) {
                peerList = blocks.get(block);
            } else {
                peerList = new PeerList();
                blocks.put(block, peerList);
            }
            // Update the time.
            peerList.lastUpdate = System.currentTimeMillis();

            for (JsonElement f : o.getAsJsonArray("peers")) {
                address = Util.stringToSocketAddress(f.getAsString());
                // Don't add myself.
                if (!address.equals(myPeerAddress))
                    peerList.list.add(address);
            }
        }
    }

    /**
     * Get the peer addresses in a random order for a given block.
     * @param id
     * @return
     */
    public synchronized List<InetSocketAddress> getPeersForBlock(int id) {
        // If the block isn't in the list, try to get it.
        if (!blocks.containsKey(id)) {
            preemptivelyCache(new int[]{id});
        }

        if (!blocks.containsKey(id)) {
            NeguraLog.warning("Couldn't get peers for block %d.", id);
            return null;
        }

        List<InetSocketAddress> peers = new ArrayList<InetSocketAddress>(
                blocks.get(id).list);
        Collections.shuffle(peers);

        // TODO: if the entry is too old, update the list for the block along
        // with some of the oldest ones, not just by itself.

        return peers;

        // TODO: If the cache is too big, remove the oldest entries.
        // TODO: Maybe. If the cache is too small, add random blocks. I might
        // need them in the future, or my peers might need them.
    }
}

class PeerList {
    HashSet<InetSocketAddress> list = new HashSet<InetSocketAddress>();
    long lastUpdate;
}
