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

    public static int info(Shell shell, String message) {
        MessageBox messageBox = new MessageBox(shell, SWT.ICON_INFORMATION |
                SWT.OK);
        messageBox.setText("");
        messageBox.setMessage(message);
        return messageBox.open();
    }
}
