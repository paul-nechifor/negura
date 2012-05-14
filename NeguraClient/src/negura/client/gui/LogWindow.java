package negura.client.gui;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import negura.client.ClientConfigManager;
import negura.common.gui.Swt;
import negura.common.util.NeguraLog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 *
 * @author Paul Nechifor
 */
public class LogWindow {
    private Shell shell;
    private ClientConfigManager cm; // Shut up Netbeans. It is actually used.
    private WindowLogHandler windowLogHandler;

    private static class WindowLogHandler extends Handler {
        private StyledText styledText;
        public WindowLogHandler(StyledText styledText) {
            super();
            this.styledText = styledText;
        }

        @Override
        public void publish(final LogRecord record) {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    if (!styledText.isDisposed()) {
                        styledText.append(NeguraLog.FORMATTER.format(record));
                        styledText.setCaretOffset(styledText.getCharCount());
                    }
                }
            });
        }

        @Override
        public void flush() { }
        @Override
        public void close() throws SecurityException { }
    }

    public LogWindow(Display display, ClientConfigManager cm) {
        this.cm = cm;

        shell = new Shell(display);
        shell.setText("Log");
        shell.setSize(455, 368);
        shell.setLayout(new FillLayout());
        StyledText styledText = new StyledText(shell, SWT.BORDER |
                SWT.V_SCROLL | SWT.MULTI | SWT.WRAP);
        styledText.setEditable(false);
        Swt.changeControlFontSize(styledText, display, 8);
        
        shell.addShellListener(new ShellListener() {
            public void shellClosed(ShellEvent se) {
                // It might not have been added yet.
                if (windowLogHandler != null)
                    NeguraLog.removeHandler(windowLogHandler);
                shell.dispose();
                shell = null;
            }

            public void shellActivated(ShellEvent se) { }
            public void shellDeactivated(ShellEvent se) { }
            public void shellIconified(ShellEvent se) { }
            public void shellDeiconified(ShellEvent se) { }
        });

        shell.open();

        // Flush the file handler so that I can load everything.
        NeguraLog.flushAll();
        try {
            FileReader in = new FileReader(cm.getLogFile());
            char[] buffer = new char[8 * 1024];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer))
                styledText.append(new String(buffer, 0, read));
        } catch (FileNotFoundException ex) {
            NeguraLog.warning(ex, "Log file couldn't be found");
        } catch (IOException ex) {
            NeguraLog.warning(ex, "Error reading log file.");
        }

        windowLogHandler = new WindowLogHandler(styledText);
        NeguraLog.addHandler(windowLogHandler);
    }

    public void forceActive() {
        shell.forceActive();
    }

    public boolean isClosed() {
        return shell == null;
    }
}
