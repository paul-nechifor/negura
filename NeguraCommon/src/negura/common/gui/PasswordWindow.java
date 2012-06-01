package negura.common.gui;

import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A modal dialog for requesting password with optional confirmation.
 *
 * @author Paul Nechifor
 */
public class PasswordWindow {

    private final Display display;
    private final Shell shell;
    private final boolean confirm;
    private final Boolean remember;
    private final Label messageL;
    private final Text passwordT;
    private Text confirmT;
    private Button rememberCb;

    private String password = null;
    private Boolean rememberSelection = null;

    /**
     * Initializes the dialog window.
     * @param parent        The parrent to lock while showing this.
     * @param confirm       If there should be a confirmation text box.
     * @param remember      null means there is no checkbox, true and false mean
     *                      that there is one and will be set to that value.
     * @param message       The message for the request.
     */
    public PasswordWindow(Shell parent, boolean confirm, Boolean remember,
            String message) {
        this.display = parent.getDisplay();
        this.shell = new Shell(display, SWT.APPLICATION_MODAL);
        this.confirm = confirm;
        this.remember = remember;

        shell.setLayout(new MigLayout("insets 10",
                "[right][fill][][200::]"));
        shell.setText("Password input");

        messageL = Swt.newLabel(shell, "skip 1, span, wrap", message);

        Swt.newLabel(shell, null, "Password:");
        passwordT = Swt.newPassword(shell, "span, wrap", null);

        if (confirm) {
            Swt.newLabel(shell, null, "Confirm:");
            confirmT = Swt.newPassword(shell, "span, wrap", null);
        }

        if (remember != null) {
            rememberCb = Swt.newCheckBox(shell, "skip 1, span, align left",
                    "Remember password.");
            rememberCb.setSelection(remember);
        }

        Button doneB = Swt.newButton(shell, "skip 1", "Done");
        Button cancelB = Swt.newButton(shell, null, "Cancel");

        shell.setDefaultButton(doneB);

        shell.pack();
        Swt.centerShell(shell);
        
        doneB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                verify();
            }
        });

        cancelB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                shell.dispose();
            }
        });
    }

    public final String open() {
        shell.open();

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        return password;
    }

    public final Boolean getRemember() {
        return rememberSelection;
    }

    private void verify() {
        String textPassword = passwordT.getText();

        if (confirm && !textPassword.equals(confirmT.getText())) {
            reset("Passwords do not match.");
            return;
        }

        if (textPassword.length() <= 1) {
            reset("Password is insecure.");
            return;
        }

        if (remember != null)
            rememberSelection = rememberCb.getSelection();

        password = textPassword;

        shell.dispose();
    }

    private void reset(String message) {
        messageL.setText(message);
        passwordT.setText("");
        if (confirm)
            confirmT.setText("");
        passwordT.setFocus();
    }
}
