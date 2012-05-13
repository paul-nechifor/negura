package negura.common.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 *
 * @author Paul Nechifor
 */
public class MsgBox {
    private MsgBox() { }

    public static int message(Shell shell, String text, String message,
            int type) {
        MessageBox messageBox = new MessageBox(shell);
        if (text != null)
            messageBox.setText(text);
        if (message != null)
            messageBox.setMessage(message);
        return messageBox.open();
    }

    public static int info(Shell shell, String message) {
        return message(shell, null, message, SWT.ICON_INFORMATION | SWT.OK);
    }

    public static int error(Shell shell, String message) {
        return message(shell, null, message, SWT.ICON_ERROR | SWT.OK);
    }
}
