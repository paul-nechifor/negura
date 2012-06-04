package negura.client.gui;

import java.io.File;
import negura.client.ClientConfigManager;
import negura.client.I18n;
import negura.client.Negura;
import negura.common.Service;
import negura.common.data.RsaKeyPair;
import negura.common.gui.KeyGenerationWindow;
import negura.common.util.MsgBox;
import negura.common.util.Os;
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
    private final Display display;
    private final Shell shell;
    private final Negura negura;
    private final ClientConfigManager cm;
    private final TrayItem trayItem;
    private final CommonResources resources;

    private LogWindow logWindow = null;
    private Statistics statistics = null;

    public TrayGui(Negura negura, ClientConfigManager cm) {
        this.negura = negura;
        this.cm = cm;
        this.display = new Display();
        this.resources = new CommonResources(display);
        this.shell = new Shell(display);
        this.trayItem = new TrayItem(display.getSystemTray(), SWT.NONE);

        load();
    }

    protected void onStart() {
        started();

        while (!shell.isDisposed() && serviceState == RUNNING) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        shell.dispose();
        resources.dispose();
        display.dispose();
    }

    protected void onStop() {
        // This will mean that the loop will terminate and the other things
        // will be disposed.
        shell.dispose();
        stopped();
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
        shell.setImage(resources.getImage("tray"));
        trayItem.setImage(resources.getImage("tray"));
        trayItem.setToolTipText(I18n.format("servingOn", cm.getServicePort()));

        final Menu menu = new Menu(shell, SWT.POP_UP);
        final MenuItem startFtpMi = n(menu, SWT.CHECK, I18n.get("startFtp"), null);
        startFtpMi.setText(I18n.get("startFtp"));
        MenuItem refreshMi = n(menu, SWT.PUSH, I18n.get("refreshFileSystem"));
        MenuItem viewLogMi = n(menu, SWT.PUSH, I18n.get("openLog"));
        MenuItem statisticsMi = n(menu, SWT.PUSH, "Statistics");
        n(menu, SWT.SEPARATOR, null);
        MenuItem exitMi = n(menu, SWT.PUSH, I18n.get("exit"),
                resources.getImage("exit"));
        n(menu, SWT.SEPARATOR, null);
        MenuItem selfDestructMi = n(menu, SWT.PUSH, I18n.get("selfDestruct"),
                resources.getImage("exit"));
        
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
                    negura.getFtpServer().start();
                    tip(I18n.get("applicationFtp"), I18n.format("startedOn",
                            cm.getFtpPort()));
                } else {
                    negura.getFtpServer().stop();
                    tip(I18n.get("applicationFtp"), I18n.get("stoppedFtp"));
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
        statisticsMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                openStatisticsWindow();
            }
        });
        exitMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                negura.shutdown();
            }
        });
        selfDestructMi.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                negura.shutdown();
                Os.removeDirectory(Os.getUserConfigDir(
                        I18n.get("applicationShortName")));
                Os.removeDirectory(Os.getUserDataDir(
                        I18n.get("applicationShortName")));
            }
        });
    }

    private synchronized void openLogWindow() {
        if (logWindow == null || logWindow.isDisposed()) {
            logWindow = new LogWindow(display, resources, cm);
        } else {
            logWindow.forceActive();
        }
    }

    private synchronized void openStatisticsWindow() {
        if (statistics == null || statistics.isDisposed()) {
            statistics = new Statistics(display, resources, cm);
        } else {
            statistics.forceActive();
        }
    }

    private void tip(String text, String message) {
        ToolTip tip = new ToolTip(shell,
                SWT.BALLOON | SWT.ICON_INFORMATION);
        if (text != null)
            tip.setText(text);
        if (message != null)
            tip.setMessage(message);
        tip.setAutoHide(true);
        trayItem.setToolTip(tip);
        tip.setVisible(true);
    }
}
