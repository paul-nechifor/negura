package negura.common.gui;

import negura.common.ex.NeguraRunEx;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Resource;
import org.eclipse.swt.widgets.Shell;

/**
 *
 * @author Paul Nechifor
 */
public abstract class Window extends Resource {
    private boolean disposed = false;
    protected final Shell shell;

    protected Window(Shell shell) {
        this.shell = shell;
    }

    @Override
    public void dispose() {
        if (disposed) {
            throw new NeguraRunEx("Already disposed.");
        }

        shell.dispose();

        disposed = true;
    }

    @Override
    public final Device getDevice() {
        return shell.getDisplay();
    }

    @Override
    public final boolean isDisposed() {
        return disposed;
    }

    public void forceActive() {
        shell.forceActive();
    }
}
