package negura.server;

import java.io.File;
import negura.common.util.Comm;
import negura.server.gui.ConfigMaker;

public class Main {
    private Main() { }

    public static void main(String[] args) {
        Comm.init("1.0", "NeguraServer 0.1");

        if (args.length == 0) {
            ConfigMaker m = new ConfigMaker();
            m.loopUntilClosed();
        } else {
            NeguraServer server = new NeguraServer(new File(args[0]));
            server.run();
        }
    }
}
