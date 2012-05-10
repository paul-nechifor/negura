package negura.common.data;

import java.util.HashMap;

/**
 *
 * @author Paul Nechifor
 */
public class BlockIndexer {
    public HashMap<String, Integer> hashToId = new HashMap<String, Integer>();
    public HashMap<Integer, String> idToHash = new HashMap<Integer, String>();
    public HashMap<Integer, String> idToStoreCode
            = new HashMap<Integer, String>();
    public HashMap<String, Integer> storeCodeToId
            = new HashMap<String, Integer>();
}
