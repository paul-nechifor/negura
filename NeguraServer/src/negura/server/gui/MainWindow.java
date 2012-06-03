package negura.server.gui;

import negura.common.gui.Swt;
import negura.common.gui.Window;
import negura.server.ServerConfigManager;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;

/**
 * The main window of the server GUI.
 * @author Paul Nechifor
 */
public class MainWindow extends Window {
    private final ServerConfigManager cm;
    private final Display display;
    private final Runnable runAfter;

    public MainWindow(ServerConfigManager cm, Runnable runAfter) {
        super(new Shell(new Display()));

        this.cm = cm;
        this.runAfter = runAfter;
        this.display = (Display) getDevice();

        shell.setText("Negura Server");

        shell.setLayout(Swt.singletonLayout(5, 5));

        String[] tabNames = {"Output", "Blocks", "Files", "Settings"};

        TabFolder tabFolder = Swt.newTabForder(shell, null, tabNames);

        new OutputTab(tabFolder.getItem(0));
        new BlocksTab(tabFolder.getItem(1));
        
        shell.setSize(797, 599);

        Swt.centerShell(shell);
        shell.open();
    }

    public final void loopUntilClosed() {
        Thread t = new Thread(runAfter);
        t.start();

        Swt.loopUntilClosed(display, shell);
    }
}
