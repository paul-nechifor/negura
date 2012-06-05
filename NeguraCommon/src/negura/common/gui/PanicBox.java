package negura.common.gui;

import negura.common.util.Util;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Constructs a display and shell only for showing the error message and
 * exception.
 * @author Paul Nechifor
 */
// TODO: It doesn't resize to smaller sizes properly.
public class PanicBox {
    private PanicBox() { }

    public static void show(String message, Throwable throwable) {
        Display display = new Display();
        final Shell shell = new Shell(display);

        shell.setText("Panic");
        shell.setLayout(new MigLayout("insets 10",
                "[:450:, grow, fill][:100:, fill]", "[:600:, grow, fill][]"));

        Font smallFont = Swt.getMonospacedFont(display, 8);
        Swt.connectDisposal(shell, smallFont);

        StyledText styledText = new StyledText(shell, SWT.BORDER |
                SWT.V_SCROLL | SWT.MULTI | SWT.WRAP);
        styledText.setEditable(false);
        styledText.setFont(smallFont);
        styledText.setLayoutData("span, wrap");

        Button exitB = Swt.newButton(shell, "skip 1", "Exit");

        styledText.setText("The application had to close.\nMessage: " +
                message + "\n\n" + Util.getStackTrace(throwable));

        exitB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                shell.dispose();
            }
        });

        shell.pack();
        Swt.centerShell(shell);
        shell.open();

        Swt.loopUntilClosed(display, shell);
    }
}
