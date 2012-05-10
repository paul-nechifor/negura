package negura.server;

import negura.common.util.Comm;

public class Main {
    private Main() { }

    public static void main(String[] args) {
        //Comm.init("1.0", "NeguraServer 0.1");

        NeguraServer server = new NeguraServer(args[0]);
        server.run();
    }
}
