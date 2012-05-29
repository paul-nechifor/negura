package negura.client.ftp;

import java.awt.Desktop;
import java.net.URI;
import negura.client.ClientConfigManager;
import negura.common.Service;
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
 *
 * @author Paul Nechifor
 */
public class NeguraFtpServer extends Service {
    private FtpServer ftpServer;
    private ClientConfigManager cm;
    // I save the value so that if it changes while the server is started the
    // window will still open with the correct port.
    private int openedOnPort = -1;

    public NeguraFtpServer(ClientConfigManager cm) {
        this.cm = cm;
    }

    public void run() {
        openedOnPort = cm.getFtpPort();
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
        factory.setPort(openedOnPort);
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
    public void requestStop() {
        super.requestStop();
        if (ftpServer != null && !ftpServer.isStopped())
            ftpServer.stop();
    }
    /**
     * Tries to open a browser window to the FTP server location.
     * @return true on success.
     */
    public boolean openBrowserWindow() {
        if (ftpServer == null || ftpServer.isStopped()
                || ftpServer.isSuspended() || !Desktop.isDesktopSupported())
            return false;
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE))
            return false;

        try {
            desktop.browse(new URI("ftp://127.0.0.1:" + openedOnPort));
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    public int getOpenedOnPort() {
        return openedOnPort;
    }
}
