package negura.common.gui;

import negura.common.data.RsaKeyPair;
import negura.common.ex.NeguraError;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * A class which will prompt the user with a PasswordWindow to unlock a
 * RsaKeyPair.
 * @author Paul Nechifor
 */
public class KeyPairUnlocker {
    private final RsaKeyPair rsaKeyPair;
    private final int tries;
    private final String message;
    private final String failureMessage;
    private final Display display;
    private final Shell shell;

    public KeyPairUnlocker(RsaKeyPair rsaKeyPair, int tries, String message,
            String failureMessage) {
        this.rsaKeyPair = rsaKeyPair;
        this.tries = tries;
        this.message = message;
        this.failureMessage = failureMessage;
        this.display = new Display();
        this.shell = new Shell(display);

        if (rsaKeyPair.isPrivateKeyDecrypted()) {
            throw new NeguraError("Already unlocked.");
        }
    }

    public boolean openAndTryToUnlock() {
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                decryptPrivateKey();
            }
        });

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        shell.dispose();
        display.dispose();

        return rsaKeyPair.isPrivateKeyDecrypted();
    }

    public void decryptPrivateKey() {
        if (!tryToDecrypt(rsaKeyPair, shell, tries, message)) {
            MsgBox.error(shell, failureMessage);
        }

        shell.dispose();
    }

    public static boolean tryToDecrypt(RsaKeyPair rsaKeyPair, Shell shell,
            int tries, String message) {
        String newMessage = message;
        Boolean remember = false;

        for (int i = 0; i < tries; i++) {
            PasswordWindow pw = new PasswordWindow(shell, false, remember,
                    newMessage);
            String password = pw.open();
            remember = pw.getRemember();

            // User canceled so return not succeded.
            if (password == null)
                return false;

            if (rsaKeyPair.decryptPrivateKey(password)) {
                // Password is correct.
                // Unlock the private key if the user wants.
                if (remember != null && remember) {
                    rsaKeyPair.transformToStored(password,
                            rsaKeyPair.getRepetitions() / 4);
                }
                // The password was correct.
                return true;
            } else {
                // The password was incorrect so try another time.
                newMessage = "Password was incorrect. Try again or exit.";
            }
        }

        return false;
    }
}
