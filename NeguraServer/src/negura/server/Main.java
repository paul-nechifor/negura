package negura.server;

import java.io.File;
import negura.common.ex.NeguraEx;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;
import negura.common.util.Os;
import negura.server.gui.ConfigMaker;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * Handles the command line arguments and launches then application.
 * @author Paul Nechifor
 */
public class Main {
    private Main() { }

    public static void main(String[] args) {
        Comm.init("1.0", "NeguraServer 0.1");


        Option help = new Option("help", "Shows you the help.");
        Option cli = new Option("cli", "Use command line interface instead of "
                + "the GUI.");
        Option config = OptionBuilder.withArgName("file")
                .hasArg()
                .withDescription("Use <file> instead of the configuration file "
                + "found in the user's configuration directory.")
                .create("config");

        Options options = new Options();
        options.addOption(help);
        options.addOption(cli);
        options.addOption(config);

        CommandLineParser parser = new PosixParser();
        CommandLine line = null;
        
        try {
            line = parser.parse(options, args);
        } catch(ParseException ex) {
            System.err.println(ex.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(" ", options);
            System.exit(1);
        }

        if (line.hasOption("help") || line.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(" ", options);
            System.exit(0);
        }

        boolean useCli = false;
        File configFile;

        if (line.hasOption("cli"))
            useCli = true;

        if (line.hasOption("config")) {
            configFile = new File(line.getOptionValue("config"));
        } else {
            configFile = Os.getUserConfigDir("neguraserver", "server.json");
        }

        // If the configuration file doesn't exist, create it.
        if (!configFile.exists()) {
            ConfigMaker m = new ConfigMaker(configFile);
            m.loopUntilClosed();
        }

        // The config file wasn't created, so exit.
        if (!configFile.exists()) {
            System.exit(0);
        }

        try {
            NeguraServer server = new NeguraServer(configFile, useCli);
            server.run();
        } catch (NeguraEx ex) {
            NeguraLog.warning(ex.getMessage());
        }
    }
}
