package negura.client.ftp;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import negura.client.ClientConfigManager;
import negura.common.OnOffService;
import negura.common.util.NeguraLog;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.listener.ListenerFactory;

/**
 * TODO: REWRITE THIS
 * @author Paul Nechifor
 */
public class NeguraFtpServer extends OnOffService {
    private final ClientConfigManager cm;
    private FtpServer ftpServer;

    public NeguraFtpServer(ClientConfigManager cm) {
        this.cm = cm;
    }

    @Override
    protected void turnedOn() {
        FtpServerFactory serverFactory = new FtpServerFactory();

        ConnectionConfigFactory config = new ConnectionConfigFactory();
        config.setAnonymousLoginEnabled(true);
        config.setMaxLogins(10);
        config.setMaxAnonymousLogins(10);
        serverFactory.setConnectionConfig(config.createConnectionConfig());
        // TODO: Get concurrentUsers as a ClientConfigManager value.
        serverFactory.setUserManager(new AnonymousUserManager(10));

        // Replacing the default listener with a custom port.
        ListenerFactory factory = new ListenerFactory();
        factory.setPort(cm.getFtpPort());
        serverFactory.addListener("default", factory.createListener());

        // Setting the filesystem view.
        serverFactory.setFileSystem(new FileSystemFactory() {
            public FileSystemView createFileSystemView(User user)
                    throws FtpException {
                return new NeguraFtpFsView(cm.getFsView());
            }
        });

        // Starting the server.
        ftpServer = serverFactory.createServer();
        try {
            ftpServer.start();
        } catch (FtpException ex) {
            NeguraLog.severe(ex);
        }

        openBrowserWindow();
    }

    @Override
    protected void turnedOff() {
        ftpServer.stop();
    }
    /**
     * Tries to open a browser window to the FTP server location.
     * @return true on success.
     */
    public boolean openBrowserWindow() {
        if (ftpServer.isStopped() || ftpServer.isSuspended() ||
                !Desktop.isDesktopSupported()) {
            return false;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return false;
        }

        try {
            desktop.browse(new URI("ftp://127.0.0.1:" + cm.getFtpPort()));
        } catch (URISyntaxException ex) {
            NeguraLog.warning(ex);
            return false;
        } catch (IOException ex) {
            NeguraLog.warning(ex);
            return false;
        }

        return true;
    }
}
