package negura.client;

import java.io.File;
import negura.client.gui.Registration;
import negura.common.util.Comm;
import negura.common.util.Os;
import org.apache.ftpserver.ftplet.FtpException;

//Icon from http://www.komodomedia.com/.

/**
 * Main class which manages the command line arguments.
 * @author Paul Nechifor
 */
public class Main {
    private Main() { }

    public static void main(String[] args) throws FtpException {
        Comm.init("1.0", "Negura 0.1");

        File configFile = null;

        // If there are no arguments, start with the default configuration file
        // or start the registration.
        if (args.length == 0) {
            configFile = new File(new File(Os.getUserConfigDir(),
                    I18n.get("applicationShortName")), "config.json");
            if (!configFile.exists()) {
                Registration r = new Registration();
                r.loopUntilClosed();
                if (!r.isRegisteredSuccessfully())
                    return;
            }
        } else {
            if (args[0].equals("autoreg") && args.length == 4) {
                configFile = Registration.testRegister(
                        Integer.parseInt(args[1]), args[2],
                        Integer.parseInt(args[3]));
            } else {
                System.err.println("Invalid parameters.");
                System.exit(1);
            }
        }

        Negura negura = new Negura(configFile);
        negura.start();
    }
}
