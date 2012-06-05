package negura.common.data;

import java.security.interfaces.RSAPublicKey;

/**
 * @author Paul Nechifor
 */
public class ServerInfo {
    public String name;
    public RSAPublicKey publicKey;
    public RSAPublicKey adminPublicKey;
    public int blockSize;
    public int minimumBlocks;
    public int maximumBlocks;
    public int checkInTime;
}
