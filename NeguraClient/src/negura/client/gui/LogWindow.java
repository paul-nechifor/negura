package negura.client.gui;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import negura.client.ClientConfigManager;
import negura.client.I18n;
import negura.common.gui.Swt;
import negura.common.gui.Window;
import negura.common.gui.StyledTextLogHandler;
import negura.common.util.NeguraLog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * @author Paul Nechifor
 */
public class LogWindow extends Window {
    private final ClientConfigManager cm; // Shut up NetBeans. It is used.
    private final StyledTextLogHandler styledTextLogHandler;
    private final Font smallFont;

    public LogWindow(Display display, CommonResources resources,
            ClientConfigManager cm) {
        super(new Shell(display));
        this.cm = cm;

        shell.setText(I18n.get("log"));
        shell.setSize(455, 368);
        shell.setLayout(new FillLayout());
        shell.setImage(resources.getImage("application"));

        smallFont = Swt.getMonospacedFont(display, 8);

        StyledText styledText = new StyledText(shell, SWT.BORDER |
                SWT.V_SCROLL | SWT.MULTI | SWT.WRAP);
        styledText.setEditable(false);
        styledText.setFont(smallFont);
        
        shell.addShellListener(new ShellAdapter() {
            @Override
            public void shellClosed(ShellEvent se) {
                dispose();
            }
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

        styledTextLogHandler = new StyledTextLogHandler(styledText);
        NeguraLog.addHandler(styledTextLogHandler);
    }

    @Override
    public void dispose() {
        super.dispose();

        smallFont.dispose();

        // It might not have been added yet.
        if (styledTextLogHandler != null) {
            NeguraLog.removeHandler(styledTextLogHandler);
            styledTextLogHandler.close();
        }
    }
}
