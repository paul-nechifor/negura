package negura.client.gui;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import negura.client.ClientConfigManager;
import negura.client.I18n;
import negura.common.gui.Swt;
import negura.common.gui.Window;
import negura.common.util.NeguraLog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * @author Paul Nechifor
 */
public class LogWindow extends Window {
    private final ClientConfigManager cm; // Shut up NetBeans. It is used.
    private final WindowLogHandler windowLogHandler;
    private final Font smallFont;

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

    public LogWindow(Display display, CommonResources resources,
            ClientConfigManager cm) {
        super(new Shell(display));
        this.cm = cm;

        shell.setText(I18n.get("log"));
        shell.setSize(455, 368);
        shell.setLayout(new FillLayout());
        shell.setImage(resources.getImage("application"));
        smallFont = Swt.getMonospacedFont(display, 8);
        Swt.connectDisposal(shell, smallFont);

        StyledText styledText = new StyledText(shell, SWT.BORDER |
                SWT.V_SCROLL | SWT.MULTI | SWT.WRAP);
        styledText.setEditable(false);
        styledText.setFont(smallFont);
        
        shell.addShellListener(new ShellListener() {
            public void shellClosed(ShellEvent se) {
                // It might not have been added yet.
                if (windowLogHandler != null)
                    NeguraLog.removeHandler(windowLogHandler);
                dispose();
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
}
