package negura.common.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import negura.common.util.NeguraLog;
import org.apache.commons.pool.BasePoolableObjectFactory;

/**
 *
 * @author Paul Nechifor
 */
public class PoolSocketFactory extends BasePoolableObjectFactory<Socket> {
    private InetAddress address;
    private int startPort;
    private int endPort;

    public PoolSocketFactory(InetAddress address, int startPort, int endPort) {
        this.address = address;
        this.startPort = startPort;
        this.endPort = endPort;
    }

    @Override
    public Socket makeObject() throws Exception {
        Socket socket = new Socket();

        for (int port = startPort; port <= startPort; port++) {
            try {
                socket.bind(new InetSocketAddress(address, port));
                return socket;
            } catch (IOException ex) { }
        }

        NeguraLog.severe("Failed to find bind to address %s on port range "
                + "%d-%d.", address, startPort, endPort);

        throw new Exception("Couldn't find an open port.");
    }
    @Override
    public void passivateObject(Socket socket) {
        try {
            if (!socket.isClosed())
                socket.close();
        } catch (IOException ex) {
            NeguraLog.warning(ex);
        }
    }
}
