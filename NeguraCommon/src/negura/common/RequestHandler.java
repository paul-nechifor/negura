package negura.common;

import java.net.Socket;

/**
 *
 * @author Paul Nechifor
 */
public interface RequestHandler
{
    public abstract void handle(Socket socket);
}
