package negura.common.net;

import java.net.Socket;

/**
 * An interface that specifies how a request is processed.
 * @author Paul Nechifor
 */
public interface RequestHandler {
    /**
     * The method which is called to handle a request from a socket.
     * @param socket    The socket containing the message to be handled.
     */
    public void handle(Socket socket);
}
