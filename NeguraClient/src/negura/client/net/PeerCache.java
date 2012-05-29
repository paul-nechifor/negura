package negura.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
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
    private final HashMap<Integer, PeerSet> blocks
            = new HashMap<Integer, PeerSet>();

    private static class PeerSet {
        HashSet<InetSocketAddress> set = new HashSet<InetSocketAddress>();
        long lastUpdate;
    }

    public PeerCache(ClientConfigManager cm) {
        this.cm = cm;
        this.myPeerAddress = new InetSocketAddress(Comm.ADDRESS,
                cm.getServicePort());
    }

    public synchronized void preemptivelyCache(Collection<Integer> blockIds) {
        if (blockIds.isEmpty())
            return;

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

        Integer blockId;
        PeerSet peerSet;
        InetSocketAddress address;

        for (Entry<String, JsonElement> pair
                : resp.getAsJsonObject("blocks").entrySet()) {
            blockId = Integer.parseInt(pair.getKey());

            peerSet = blocks.get(blockId);

            if (peerSet == null) {
                peerSet = new PeerSet();
                blocks.put(blockId, peerSet);
            }

            // Update the time.
            peerSet.lastUpdate = System.nanoTime();

            for (JsonElement f : pair.getValue().getAsJsonArray()) {
                address = Util.stringToSocketAddress(f.getAsString());

                // Don't add myself.
                if (!address.equals(myPeerAddress)) {
                    peerSet.set.add(address);
                }
            }
        }
    }

    /**
     * Get the peer addresses in a random order for a given block.
     * @param blockId
     * @return
     */
    public synchronized List<InetSocketAddress> getPeersForBlock(int blockId) {
        // If the block isn't in the list, try to get it.
        if (!blocks.containsKey(blockId)) {
            preemptivelyCache(Arrays.asList(blockId));
        }

        if (!blocks.containsKey(blockId)) {
            NeguraLog.warning("Couldn't get peers for block %d.", blockId);
            return null;
        }

        List<InetSocketAddress> peers = new ArrayList<InetSocketAddress>(
                blocks.get(blockId).set);
        Collections.shuffle(peers);

        // TODO: if the entry is too old, update the list for the block along
        // with some of the oldest ones, not just by itself.

        return peers;

        // TODO: If the cache is too big, remove the oldest entries.
        // TODO: Maybe. If the cache is too small, add random blocks. I might
        // need them in the future, or my peers might need them.
    }
}
