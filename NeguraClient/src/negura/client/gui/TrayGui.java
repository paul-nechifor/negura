package negura.client.gui;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import negura.client.ClientConfigManager;
import negura.client.I18n;
import negura.client.Main;
import negura.client.Negura;
import negura.client.ftp.NeguraFtpServer;
import negura.common.Service;
import negura.common.util.Util;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.TrayItem;

/**
 *
 * @author Paul Nechifor
 */
public class TrayGui extends Service {
    private static final String[] IMAGES = new String[] {
        "trayicon", "/res/icons/trayicon_24.png",
        "exit", "/res/icons/exit_16.png"
    };

    private final Shell mainShell;
    private final Display display;
    private final Map<String, Image> icons = new HashMap<String, Image>();
    private final TrayItem trayItem;
    private final Negura negura;
    private final ClientConfigManager cm;

    private LogWindow logWindow = null;

    public TrayGui(Negura negura, ClientConfigManager cm) {
        this.negura = negura;
        this.cm = cm;
        display = new Display();
        mainShell = new Shell(display);
        trayItem = new TrayItem(display.getSystemTray(), SWT.NONE);

        Class<?> c = getClass();
        for (int i = 0; i < IMAGES.length; i += 2) {
            Image img = new Image(display, c.getResourceAsStream(IMAGES[i+1]));
            icons.put(IMAGES[i], img);
        }
        load();

        //openLogWindow();
    }

    private MenuItem n(Menu menu, int type, String name, Image icon) {
        MenuItem ret = new MenuItem(menu, type);
        if (name != null)
            ret.setText(name);
        if (icon != null)
            ret.setImage(icon);
        return ret;
    }

    private MenuItem n(Menu menu, int type, String name) {
        return n(menu, type, name, null);
    }

    private void load() {
        mainShell.setImage(icons.get("trayicon"));
        trayItem.setImage(icons.get("trayicon"));
        trayItem.setToolTipText(I18n.format("servingOn", cm.getPort()));

        final Menu menu = new Menu(mainShell, SWT.POP_UP);
        final MenuItem startFtpMi = n(menu, SWT.CHECK, I18n.get("startFtp"), null);
        startFtpMi.setText(I18n.get("startFtp"));
        MenuItem refreshMi = n(menu, SWT.PUSH, I18n.get("refreshFileSystem"));
        MenuItem viewLogMi = n(menu, SWT.PUSH, "Open log");
        n(menu, SWT.SEPARATOR, null);
        MenuItem exitMi = n(menu, SWT.PUSH, I18n.get("exit"),
                icons.get("exit"));
        n(menu, SWT.SEPARATOR, null);
        MenuItem selfDestructMi = n(menu, SWT.PUSH, I18n.get("selfDestruct"),
                icons.get("exit"));
        
        menu.setDefaultItem(startFtpMi);

        // Listeners
        trayItem.addListener(SWT.MenuDetect, new Listener() {
            public void handleEvent(Event event) {
                menu.setVisible(true);
            }
        });
        startFtpMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                if (startFtpMi.getSelection()) {
                    NeguraFtpServer ftpServer =  negura.getFtpServer();
                    ftpServer.start();
                    tip("Negura FTP",
                            "Started on " + ftpServer.getOpenedOnPort());
                } else {
                    negura.getFtpServer().stop();
                    tip("Negura FTP", "The FTP server has been stoped.");
                }
            }
        });
        refreshMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                negura.getStateMaintainer().triggerFileSystemUpdate();
            }
        });
        viewLogMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                openLogWindow();
            }
        });
        exitMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                negura.shutdown();
            }
        });
        selfDestructMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                Util.removeDirectory(new File(Util.getUserConfigDir(),
                        Main.SHORT_NAME));
                Util.removeDirectory(new File(Util.getUserDataDir(),
                        Main.SHORT_NAME));
                negura.shutdown();
            }
        });
    }

    private void openLogWindow() {
        if (logWindow == null || logWindow.isClosed())
            logWindow = new LogWindow(display, cm);
        else
            logWindow.forceActive();
    }

    private void tip(String text, String message) {
        ToolTip tip = new ToolTip(mainShell,
                SWT.BALLOON | SWT.ICON_INFORMATION);
        if (text != null)
            tip.setText(text);
        if (message != null)
            tip.setMessage(message);
        tip.setAutoHide(true);
        trayItem.setToolTip(tip);
        tip.setVisible(true);
    }

    public void run() {
        while (!mainShell.isDisposed() && running) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        for (Image image : icons.values())
            image.dispose();
        mainShell.dispose();
        display.dispose();
    }
}
