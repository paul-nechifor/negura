package negura.common.gui;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import negura.common.data.RsaKeyPair;
import negura.common.json.Json;
import negura.common.util.MsgBox;
import negura.common.util.Os;
import negura.common.util.Rsa;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A window for generating RSA key pairs.
 * @author Paul Nechifor
 */
public class KeyGenerationWindow extends Window {
    private static final int[] KEY_SIZE_I = new int[]{1024, 2048};
    private static final String[] KEY_SIZE_S = new String[KEY_SIZE_I.length];

    private final Text publicKeyT;
    private final Text privateKeyT;

    static {
        // Initializing the key size options.
        for (int i = 0; i < KEY_SIZE_I.length; i++)
            KEY_SIZE_S[i] = Integer.toString(KEY_SIZE_I[i]);
    }

    public KeyGenerationWindow(Display display) {
        super(new Shell(display));

        Font monospacedFont = Swt.getMonospacedFont(display, 9);
        Swt.connectDisposal(shell, monospacedFont);

        shell.setText("Key pair generator");
        shell.setSize(700, 550);
        Swt.centerShell(shell);
        shell.setLayout(new MigLayout("insets 10", "[right][100!][][grow]",
                "[grow][grow]"));

        Swt.newLabel(shell, "aligny top", "Public key:");
        publicKeyT = Swt.newMulti(shell, "span, h 20%, w max, wrap", null);

        Swt.newLabel(shell, "aligny top", "Private key:");
        privateKeyT = Swt.newMulti(shell,
                "span, h 60%, w max, wrap 30px", null);

        Swt.newLabel(shell, null, "Key size:");
        final Combo keySizeC = Swt.newCombo(shell, "w max", KEY_SIZE_S, 0);
        Button generateB = Swt.newButton(shell, null, "Generate");
        Button saveB = Swt.newButton(shell, null, "Save");

        // Options
        publicKeyT.setFont(monospacedFont);
        privateKeyT.setFont(monospacedFont);
        shell.setDefaultButton(generateB);
        generateB.setFocus();

        generateB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                KeyPair pair = Rsa.generateKeyPair(
                        KEY_SIZE_I[keySizeC.getSelectionIndex()]);
                publicKeyT.setText(Rsa.toString(pair.getPublic(), 80));
                privateKeyT.setText(Rsa.toString(pair.getPrivate(), 80));
            }
        });
        saveB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSaveB();
            }
        });

        Swt.centerShell(shell);
        shell.open();
    }

    /**
     * Opens this window without any previous Display and returns when the
     * window is closed.
     */
    public static void openIndependently() {
        Display display = new Display();
        KeyGenerationWindow window = new KeyGenerationWindow(display);
        Swt.loopUntilClosed(display, window.shell);
    }

    private void onSaveB() {
        if (publicKeyT.getText().trim().isEmpty() ||
                privateKeyT.getText().trim().isEmpty()) {
            MsgBox.warning(shell, "Please paste keys or press Generate to " +
                   "generate new key pair.");
            return;
        }

        RSAPublicKey publicKey =
                Rsa.publicKeyFromString(publicKeyT.getText());
        RSAPrivateKey privateKey =
                Rsa.privateKeyFromString(privateKeyT.getText());

        if (publicKey == null) {
            MsgBox.warning(shell, "Public key is invalid.");
            return;
        }

        if (privateKey == null) {
            MsgBox.warning(shell, "Private key is invalid.");
            return;
        }

        // Hide the window now, but reshow it if something failes.
        shell.setVisible(false);

        FileDialog fd = new FileDialog(shell, SWT.SAVE);
        fd.setText("Save key pair");
        fd.setFilterExtensions(new String[]{"*.keypair"});
        fd.setFilterPath(Os.getUserHome().getAbsolutePath());
        String selected = fd.open();

        if (selected == null) {
            shell.setVisible(true);
            return;
        }

        File selectedFile = new File(selected);

        PasswordWindow pw = new PasswordWindow(shell, true, false,
                "Enter password to save key pair with.");
        String password = pw.open();
        boolean remember = pw.getRemember() != null ? pw.getRemember() : false;

        if (password == null) {
            shell.setVisible(true);
            return;
        }

        RsaKeyPair rsaKeyPair = RsaKeyPair.createNewPair(publicKey, privateKey,
                password, 4000);

        if (remember) {
            rsaKeyPair.transformToStored(password,
                    rsaKeyPair.getRepetitions() / 4);
        }
        
        try {
            Json.toFile(selectedFile, rsaKeyPair.toJsonObject());
        } catch (IOException ex) {
            MsgBox.error(shell, String.format("Error saving to '%s': %s",
                    selectedFile.getAbsoluteFile(), ex.getMessage()));
            shell.setVisible(true);
            return;
        }

        // Close the window on success.
        shell.dispose();
    }

    /**
     * Loads a key pair from a file by opening a file dialog and a password
     * dialog if needed.
     * @param shell         The parent window.
     * @return              An object array containing a File and a RsaKeyPair
     *                      or null on failure.
     */
    public static Object[] loadKeyPair(Shell shell) {
        FileDialog fd = new FileDialog(shell, SWT.OPEN);
        fd.setText("Load key pair");
        fd.setFilterExtensions(new String[]{"*.keypair"});
        fd.setFilterPath(Os.getUserHome().getAbsolutePath());
        String selected = fd.open();

        if (selected == null)
            return null;

        File selectedFile = new File(selected);
        RsaKeyPair rsaKeyPair = null;
        try {
            rsaKeyPair = Json.fromFile(selectedFile, RsaKeyPair.class);
        } catch (IOException ex) {
            MsgBox.error(shell, "Error loading file: " + ex.getMessage());
            return null;
        }

        // If it isn't in stored form, I have to ask for the password.
        if (!rsaKeyPair.isPrivateKeyDecrypted()) {
            if (!tryToDecrypt(rsaKeyPair, shell, 5))
                return null;
        }

        // The password was correct so set the key pair and path.
        return new Object[] {selectedFile, rsaKeyPair};
    }

    public static boolean tryToDecrypt(RsaKeyPair rsaKeyPair, Shell shell,
            int tries) {
        String message = "Enter password to decrypt key pair.";
        Boolean remember = false;
        for (int i = 0; i < tries; i++) {
            PasswordWindow pw = new PasswordWindow(shell, false, remember,
                    message);
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
                message = "Password was incorrect. Try again or exit.";
            }
        }

        return false;
    }
}
