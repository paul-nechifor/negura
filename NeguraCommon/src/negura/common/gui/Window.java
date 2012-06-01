package negura.common.gui;

import negura.common.ex.NeguraRunEx;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Resource;
import org.eclipse.swt.widgets.Shell;

/**
 * An abstract class to expose some shell methods for my windows.
 *
 * @author Paul Nechifor
 */
public abstract class Window extends Resource {
    protected final Shell shell;

    protected Window(Shell shell) {
        this.shell = shell;
    }

    @Override
    public void dispose() {
        shell.dispose();
    }

    @Override
    public final Device getDevice() {
        return shell.getDisplay();
    }

    @Override
    public final boolean isDisposed() {
        return shell.isDisposed();
    }

    public void forceActive() {
        shell.forceActive();
    }
}
