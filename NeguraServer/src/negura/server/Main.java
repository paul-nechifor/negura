package negura.server;

import java.net.InetAddress;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;
import negura.common.util.Util;

public class Main {
    private Main() { }

    public static void main(String[] args) {
        InetAddress localAddress = null;
        try {
            localAddress = Util.getFirstNetworkAddress();
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }
        Comm.init("1.0", "NeguraServer 0.1", localAddress, 50000, 60000);

        NeguraServer server = new NeguraServer(args[0]);
        server.run();
    }
}
