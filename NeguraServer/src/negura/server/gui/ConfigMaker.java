package negura.server.gui;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import negura.common.RequestServer;
import negura.common.gui.Swt;
import negura.common.gui.Swt.ManyToOne;
import negura.common.util.MsgBox;
import negura.common.util.Rsa;
import negura.common.util.Util;
import negura.server.ServerConfigManager;
import negura.server.ServerConfigManager.Builder;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

/**
 *
 * @author Paul Nechifor
 */
public class ConfigMaker {
    private static final int SMALLEST_BLOCK = 64 * 1024;
    private static final int BLOCK_OPTIONS = 7;
    private static final String[] optionsStr = new String[BLOCK_OPTIONS];
    private static final int[] optionsInt = new int[BLOCK_OPTIONS];
    private static final int[] keySizeInt = new int[]{1024, 2048};
    private static final String[] keySizeStr = new String[keySizeInt.length];

    private Display display;
    private final Shell shell;
    private final StackLayout stackLayout;
    private final Font monospacedFont;
    private final Composite p1;
    private final Composite p2;
    private final Text serverNameT;
    private final Combo blockSizeC;
    private final Text diskBlocksT;
    private final Text minBlocksT;
    private final Text portT;
    private final Text checkInTimeT;
    private final Text threadPoolT;
    private final Text databaseUrlT;
    private final Text databaseUserT;
    private final Text databasePasswordT;
    private final Button doneB;
    private final Text publicKey1T;
    private final Text privateKey1T;
    private final Text publicKey2T;
    private final Text privateKey2T;
    private final Builder builder;

    static {
        // Initializing the block size options.
        for (int i = 0; i < BLOCK_OPTIONS; i++) {
            optionsInt[i] = (int) Math.pow(2, i) * SMALLEST_BLOCK;
            optionsStr[i] = String.format("%d KiB", optionsInt[i] / 1024);
        }

        // Initializing the key size options.
        for (int i = 0; i < keySizeInt.length; i++)
            keySizeStr[i] = Integer.toString(keySizeInt[i]);
    }

    public ConfigMaker() {
        builder = new Builder(new File("server.json"));

        display = new Display();
        shell = new Shell(display);
        shell.setText("Negura Server");
        shell.setSize(760, 620);
        stackLayout = new StackLayout();
        shell.setLayout(stackLayout);

        monospacedFont = Swt.getMonospaceFont(display, 9);
        Swt.connectDispose(shell, monospacedFont);

        // Page one positioning. ///////////////////////////////////////////////
        p1 = new Composite(shell, SWT.NONE);
        p1.setLayout(new MigLayout("insets 10","[right][200!][max]"));
        Label newConfigL = Swt.newLabel(p1, "span, align left, wrap 30px",
                "New configuration");

        Swt.newLabel(p1, null, "Server name:");
        serverNameT = Swt.newText(p1, "w max, wrap", "Negura Server Name");

        Swt.newLabel(p1, null, "Block size:");
        blockSizeC = Swt.newCombo(p1, "w max, wrap 25px", optionsStr, 2);

        Swt.newLabel(p1, null, "Virtual disk blocks:");
        diskBlocksT = Swt.newText(p1, "w max", null);
        Slider diskBlocksS = Swt.newSlider(p1, "w max, wrap",
                128, 1024, 64);

        Swt.newLabel(p1, null, "Virtual disk space:");
        Label diskSpaceL = Swt.newLabel(p1, "w max, wrap 25px", null);

        Swt.newLabel(p1, null, "Minimum user blocks:");
        minBlocksT = Swt.newText(p1, "w max", null);
        Slider minBlocksS = Swt.newSlider(p1, "w max, wrap",
                128, 1024, 64);

        Swt.newLabel(p1, null, "Minimum user space:");
        Label minSpaceL = Swt.newLabel(p1, "w max, wrap 25px", null);

        Swt.newLabel(p1, null, "Port:");
        portT = Swt.newText(p1, "w max, wrap", "5000");

        Swt.newLabel(p1, null, "Check-in time:");
        checkInTimeT = Swt.newText(p1, "w max", "300");
        Swt.newLabel(p1, "wrap", "seconds");

        Swt.newLabel(p1, null, "Thread pool options:");
        threadPoolT = Swt.newText(p1, "span, w max, wrap 25px",
                "core-pool-size=2;maximum-pool-size=20;keep-alive-time=30");

        Swt.newLabel(p1, null, "Database URL:");
        databaseUrlT = Swt.newText(p1, "span, w max",
                "jdbc:postgresql://127.0.0.1:5432/neguradb");

        Swt.newLabel(p1, null, "Database user:");
        databaseUserT = Swt.newText(p1, "w max, wrap", "p");

        Swt.newLabel(p1, null, "Database password:");
        databasePasswordT = Swt.newPassword(p1, "w max, wrap push",
                "password");

        Button continueB = Swt.newButton(p1, "span, align right", "Continue");

        // Page one options. ///////////////////////////////////////////////////
        Swt.changeControlFontSize(newConfigL, display, 16);

        diskBlocksT.addVerifyListener(Swt.INTEGER_VERIFIER);
        Swt.connect(diskBlocksT, diskBlocksS);
        Swt.tripleConnect(diskSpaceL, blockSizeC, diskBlocksT, new ManyToOne() {
            public void connect(Label one, Control[] many) {
                long bSize = optionsInt[((Combo)many[0]).getSelectionIndex()];
                int blockNo = Integer.parseInt(((Text)many[1]).getText());
                one.setText(Util.bytesWithUnit(bSize * blockNo));
            }
        });
        diskBlocksT.setText("768");
        
        minBlocksT.addVerifyListener(Swt.INTEGER_VERIFIER);
        Swt.connect(minBlocksT, minBlocksS);
        Swt.tripleConnect(minSpaceL, blockSizeC, minBlocksT, new ManyToOne() {
            public void connect(Label one, Control[] many) {
                long bSize = optionsInt[((Combo)many[0]).getSelectionIndex()];
                int blockNo = Integer.parseInt(((Text)many[1]).getText());
                one.setText(Util.bytesWithUnit(bSize * blockNo));
            }
        });
        minBlocksT.setText("350");

        portT.addVerifyListener(Swt.INTEGER_VERIFIER);
        checkInTimeT.addVerifyListener(Swt.INTEGER_VERIFIER);

        continueB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                step2();
            }
        });

        // Page two positioning. ///////////////////////////////////////////////
        p2 = new Composite(shell, SWT.NONE);
        p2.setLayout(new MigLayout("insets 10","[right][100px!][max]"));
        Label newConfig2L = Swt.newLabel(p2, "span, align left, wrap 30px",
                "New configuration");

        Swt.newLabel(p2, "aligny top", "Public key:");
        publicKey1T = Swt.newMulti(p2, "span, h 60px!, w max, wrap", null);

        Swt.newLabel(p2, "aligny top", "Private key:");
        privateKey1T = Swt.newMulti(p2, "span, h 90px!, w max, wrap", null);

        Swt.newLabel(p2, null, "Key size:");
        final Combo key1SizeC = Swt.newCombo(p2, "w max", keySizeStr, 0);
        Button generate1B = Swt.newButton(p2, "wrap 25px", "Generate");

        Swt.newLabel(p2, "aligny top", "Admin public key:");
        publicKey2T = Swt.newMulti(p2, "span, h 60px!, w max, wrap", null);

        Swt.newLabel(p2, "aligny top", "Admin private key:");
        privateKey2T = Swt.newMulti(p2, "span, h 90px!, w max, wrap", null);

        Swt.newLabel(p2, null, "Key size:");
        final Combo key2SizeC = Swt.newCombo(p2, "w max", keySizeStr, 0);
        Button generate2B = Swt.newButton(p2, "wrap push", "Generate");

        doneB = Swt.newButton(p2, "span, align right", "Done");

        // Page two options. ///////////////////////////////////////////////////
        Swt.changeControlFontSize(newConfig2L, display, 16);
        publicKey1T.setFont(monospacedFont);
        privateKey1T.setFont(monospacedFont);
        publicKey2T.setFont(monospacedFont);
        privateKey2T.setFont(monospacedFont);


        generate1B.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                KeyPair pair = Rsa.generateKeyPair(
                        keySizeInt[key1SizeC.getSelectionIndex()]);
                publicKey1T.setText(Rsa.toString(pair.getPublic(), 70));
                privateKey1T.setText(Rsa.toString(pair.getPrivate(), 70));
            }
        });

        generate2B.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                KeyPair pair = Rsa.generateKeyPair(
                        keySizeInt[key2SizeC.getSelectionIndex()]);
                publicKey2T.setText(Rsa.toString(pair.getPublic(), 70));
                privateKey2T.setText(Rsa.toString(pair.getPrivate(), 70));
            }
        });

        doneB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                step3();
            }
        });

        stackLayout.topControl = p1;
        shell.layout();
        shell.setDefaultButton(continueB);
    }

    public void loopUntilClosed() {
        Swt.centerShell(shell);
        shell.open();
        while (!shell.isDisposed())
            if (!display.readAndDispatch())
                display.sleep();
        display.dispose();
    }

    private void step2() {
        builder.serverInfo.name = serverNameT.getText();
        builder.serverInfo.blockSize =
                optionsInt[blockSizeC.getSelectionIndex()];
        builder.virtualDiskBlocks = Integer.parseInt(diskBlocksT.getText());
        builder.serverInfo.minimumBlocks =
                Integer.parseInt(minBlocksT.getText());
        builder.port = Integer.parseInt(portT.getText());
        builder.serverInfo.checkInTime =
                Integer.parseInt(checkInTimeT.getText());
        builder.threadPoolOptions = threadPoolT.getText();
        builder.databaseUrl = databaseUrlT.getText();
        builder.databaseUser = databaseUserT.getText();
        builder.databasePassword = databasePasswordT.getText();

        if (RequestServer.fromOptions(builder.threadPoolOptions) == null) {
            MsgBox.warning(shell, "The thread pool options are invalid.");
            return;
        }

        stackLayout.topControl = p2;
        shell.setDefaultButton(doneB);
        shell.layout();
    }

    private void step3() {
        builder.serverInfo.publicKey =
                Rsa.publicKeyFromString(publicKey1T.getText());
        builder.serverInfo.adminPublicKey =
                Rsa.publicKeyFromString(publicKey2T.getText());
        RSAPrivateKey privateKey1 =
                Rsa.privateKeyFromString(privateKey1T.getText());
        RSAPrivateKey privateKey2 =
                Rsa.privateKeyFromString(privateKey2T.getText());

        if (builder.serverInfo.publicKey == null) {
            MsgBox.warning(shell, "Public key is invalid.");
            return;
        }

        if (builder.serverInfo.adminPublicKey == null) {
            MsgBox.warning(shell, "Admin public key is invalid.");
            return;
        }

        if (privateKey1 == null) {
            MsgBox.warning(shell, "Private key is invalid.");
            return;
        }

        if(privateKey2 == null) {
            MsgBox.warning(shell, "Admin private key is invalid.");
            return;
        }

        builder.privateKey = privateKey1;
        builder.adminPrivateKey = privateKey2;

        ServerConfigManager cm = new ServerConfigManager(builder);
        try {
            cm.save();
        } catch (IOException ex) {
            MsgBox.error(shell, "Failed to save file to " + builder.configFile);
            shell.dispose();
            return;
        }

        shell.setVisible(false);
        MsgBox.info(shell, "Configuration was written successfuly.");

        shell.dispose();
    }
}
