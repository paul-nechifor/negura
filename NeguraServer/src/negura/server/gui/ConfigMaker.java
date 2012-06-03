package negura.server.gui;

import java.io.File;
import java.io.IOException;
import negura.common.data.RsaKeyPair;
import negura.common.data.ThreadPoolOptions;
import negura.common.gui.KeyGenerationWindow;
import negura.common.gui.Swt;
import negura.common.util.MsgBox;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

/**
 *
 * @author Paul Nechifor
 */
public class ConfigMaker {
    private static final int SMALLEST_BLOCK = 64 * 1024;
    private static final int BLOCK_OPTIONS = 7;
    private static final String[] optionsStr = new String[BLOCK_OPTIONS];
    private static final int[] optionsInt = new int[BLOCK_OPTIONS];

    private Display display;
    private final Shell shell;
    private final StackLayout stackLayout;
    private final Font titleFont;
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
    private KeyGenerationWindow keyGenerationWindow = null;
    private RsaKeyPair[] keyPair = new RsaKeyPair[2];
    private final Button doneB;
    private final Builder builder;

    static {
        // Initializing the block size options.
        for (int i = 0; i < BLOCK_OPTIONS; i++) {
            optionsInt[i] = (int) Math.pow(2, i) * SMALLEST_BLOCK;
            optionsStr[i] = Util.bytesWithUnit(optionsInt[i], 0);
        }
    }

    public ConfigMaker(File configFile) {
        builder = new Builder(configFile);

        display = new Display();
        shell = new Shell(display);
        shell.setText("Negura Server");
        shell.setSize(760, 620);
        stackLayout = new StackLayout();
        shell.setLayout(stackLayout);

        // Page one positioning. ///////////////////////////////////////////////
        p1 = new Composite(shell, SWT.NONE);
        p1.setLayout(new MigLayout("insets 10",
                "[right][200!][max][100::, fill]"));
        Label newConfigL = Swt.newLabel(p1, "span, align left, wrap 30px",
                "New configuration");

        Swt.newLabel(p1, null, "Server name:");
        serverNameT = Swt.newText(p1, "w max, wrap", "Negura Server Name");

        Swt.newLabel(p1, null, "Block size:");
        blockSizeC = Swt.newCombo(p1, "w max, wrap 25px", optionsStr, 2);

        Swt.newLabel(p1, null, "Virtual disk blocks:");
        diskBlocksT = Swt.newText(p1, "w max", null);
        Scale diskBlocksS = Swt.newHScale(p1, "span, w max, wrap",
                128, 1024, 64);

        Swt.newLabel(p1, null, "Virtual disk space:");
        Label diskSpaceL = Swt.newLabel(p1, "w max, wrap 25px", null);

        Swt.newLabel(p1, null, "Minimum user blocks:");
        minBlocksT = Swt.newText(p1, "w max", null);
        Scale minBlocksS = Swt.newHScale(p1, "span, w max, wrap",
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
                new ThreadPoolOptions(2, 20, 30000).toString());

        Swt.newLabel(p1, null, "Database URL:");
        databaseUrlT = Swt.newText(p1, "span, w max",
                "jdbc:postgresql://127.0.0.1:5432/neguradb");

        Swt.newLabel(p1, null, "Database user:");
        databaseUserT = Swt.newText(p1, "w max, wrap", "p");

        Swt.newLabel(p1, null, "Database password:");
        databasePasswordT = Swt.newPassword(p1, "w max, wrap push",
                "password");

        Button continueB = Swt.newButton(p1, "skip 3", "Continue");

        // Page one options. ///////////////////////////////////////////////////
        titleFont = Swt.getFontWithDifferentHeight(display,
                newConfigL.getFont(), 16);
        Swt.connectDisposal(shell, titleFont);
        newConfigL.setFont(titleFont);

        Swt.Mod tripleConnector = new Swt.Mod() {
            public void modify(Widget to, Widget... from) {
                Label label = (Label) to;
                Combo combo = (Combo) from[0];
                Text text = (Text) from[1];
                int blockSize = optionsInt[combo.getSelectionIndex()];
                long numberOfBlocks = Util.parseLongOrZero(text.getText());
                label.setText(Util.bytesWithUnit(numberOfBlocks * blockSize,2));
            }
        };

        diskBlocksT.addVerifyListener(Swt.INTEGER_VERIFIER);
        Swt.connectTo(Swt.TEXT_FROM_SCALE, diskBlocksT, diskBlocksS);
        Swt.connectTo(Swt.SCALE_FROM_TEXT, diskBlocksS, diskBlocksT);
        Swt.connectTo(tripleConnector, diskSpaceL, blockSizeC, diskBlocksT);
        diskBlocksT.setText("768");
        
        minBlocksT.addVerifyListener(Swt.INTEGER_VERIFIER);
        Swt.connectTo(Swt.TEXT_FROM_SCALE, minBlocksT, minBlocksS);
        Swt.connectTo(Swt.SCALE_FROM_TEXT, minBlocksS, minBlocksT);
        Swt.connectTo(tripleConnector, minSpaceL, blockSizeC, minBlocksT);
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
        p2.setLayout(new MigLayout("insets 10","[right][grow][100::, fill]"));
        Label newConfig2L = Swt.newLabel(p2, "span, align left, wrap 30px",
                "New configuration");

        Swt.newLabel(p2, null, "Server key pair:");
        final Text key1T = Swt.newText(p2, "w max", null);
        Button load1B = Swt.newButton(p2, "wrap", "Load");

        Swt.newLabel(p2, null, "Admin key pair:");
        final Text key2T = Swt.newText(p2, "w max", null);
        Button load2B = Swt.newButton(p2, "wrap push", "Load");

        Button genB = Swt.newButton(p2, "align left", "Key pair generator");

        doneB = Swt.newButton(p2, "skip 1", "Done");

        // Page two options. ///////////////////////////////////////////////////
        newConfig2L.setFont(titleFont);
        key1T.setEditable(false);
        key2T.setEditable(false);

        load1B.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                loadKeyPair(key1T, 0);
            }
        });
        load2B.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                loadKeyPair(key2T, 1);
            }
        });
        genB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openKeyGeneratorWindow();
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

        Swt.centerShell(shell);
        shell.open();
    }

    public void loopUntilClosed() {
        Swt.loopUntilClosed(display, shell);
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
        builder.threadPoolOptions = ThreadPoolOptions.fromString(
                threadPoolT.getText());
        builder.databaseUrl = databaseUrlT.getText();
        builder.databaseUser = databaseUserT.getText();
        builder.databasePassword = databasePasswordT.getText();
        builder.firstRun = true;

        if (builder.threadPoolOptions == null) {
            MsgBox.warning(shell, "The thread pool options are invalid.");
            return;
        }

        stackLayout.topControl = p2;
        shell.setDefaultButton(doneB);
        shell.layout();
    }

    private void loadKeyPair(Text text, int index) {
        Object[] ret = KeyGenerationWindow.loadKeyPair(shell);
        if (ret == null)
            return;

        text.setText(((File) ret[0]).getAbsolutePath());
        keyPair[index] = (RsaKeyPair) ret[1];
    }

    private void openKeyGeneratorWindow() {
        if (keyGenerationWindow == null || keyGenerationWindow.isDisposed()) {
            keyGenerationWindow = new KeyGenerationWindow(display);
        } else {
            keyGenerationWindow.forceActive();
        }
    }

    private void step3() {
        if (keyPair[0] == null) {
            MsgBox.warning(shell, "You have to load a server key pair.");
            return;
        }

        if (keyPair[1] == null) {
            MsgBox.warning(shell, "You have to load an admin key pair.");
            return;
        }

        builder.serverKeyPair = keyPair[0];
        builder.adminKeyPair = keyPair[1];

        File parent = builder.configFile.getParentFile();
        if (!parent.exists() && parent.mkdirs()) {
            shell.setVisible(false);
            MsgBox.error(shell, "Failed to create directory: " +
                    parent.getAbsolutePath());
            shell.dispose();
            return;
        }

        ServerConfigManager cm = new ServerConfigManager(builder);
        try {
            cm.save();
        } catch (IOException ex) {
            shell.setVisible(false);
            MsgBox.error(shell, "Failed to save to file: " + ex.getMessage());
            shell.dispose();
            return;
        }

        shell.setVisible(false);
        MsgBox.info(shell, "Configuration was written successfuly.");

        shell.dispose();
    }
}