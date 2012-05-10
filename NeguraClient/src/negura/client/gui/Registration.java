package negura.client.gui;

import com.google.gson.JsonObject;
import java.io.File;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import negura.client.ClientConfigManager;
import negura.client.Main;
import negura.client.I18n;
import negura.common.util.Comm;
import negura.common.util.NeguraLog;
import negura.common.util.RSA;
import negura.common.util.Util;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

/**
 * Manages the registration progess.
 *
 * @author Paul Nechifor
 */
public class Registration {
    private static final VerifyListener INTEGER_VERIFIER =
            new VerifyListener() {
        @Override
        public void verifyText(final VerifyEvent event) {
            switch (event.keyCode) {
                case 0:                // To allow setText()
                case SWT.BS:           // Backspace
                case SWT.DEL:          // Delete
                case SWT.HOME:         // Home
                case SWT.END:          // End
                case SWT.ARROW_LEFT:   // Left arrow
                case SWT.ARROW_RIGHT:  // Right arrow
                    return;
            }
            if (!Character.isDigit(event.character))
                event.doit = false;  // Disallow the action.
        }
    };

    private Display display;
    private final Shell shell;
    private final StackLayout stackLayout;
    private final Composite page1;
    private final Composite page2;
    private final Text addressT;
    private final Label serverNameValL;
    private final Label blockSizeValL;
    private final Label minBlocksValL;
    private final Text blocksToStoreT;
    private final Slider blocksToStoreS;
    private final Label spaceToBeUsedValL;
    private final Text ftpPortT;
    private final Button doneB;
    private InetSocketAddress serverAddress;
    private JsonObject serverInfo;
    private int blockSize = -1;
    private int minBlocks;
    private boolean registeredSuccessfully = false;

    public Registration() {
        display = new Display();
        shell = new Shell(display);
        shell.setText("Negura");
        shell.setSize(550, 400);
        stackLayout = new StackLayout();
        shell.setLayout(stackLayout);

        // Page one positioning.
        page1 = new Composite(shell, SWT.NONE);
        page1.setLayout(new MigLayout("insets 10","[grow]"));
        Label newConnectionLb = newComp(Label.class, page1, SWT.LEFT,
                "wrap 30px");
        Label addressL = newComp(Label.class, page1, SWT.LEFT, "wrap");
        addressT = newComp(Text.class, page1, SWT.BORDER,
                "wrap push, w 200!");
        Button continueB = newComp(Button.class, page1, SWT.PUSH,
                "wrap, align right");

        // Page one options.
        newConnectionLb.setText(I18n.get("newConnection"));
        changeLabelFontSize(newConnectionLb, 16);
        addressL.setText(I18n.get("serverAddress"));
        continueB.setText(I18n.get("continue"));
        continueB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                stepTwo();
            }
        });
        addressT.setText("127.0.0.1:5000"); // TODO delete this.

        // Page two positioning.
        page2 = new Composite(shell, SWT.NONE);
        page2.setLayout(new MigLayout("insets 10","[right][150!][max]"));
        Label settingsL = newComp(Label.class, page2, SWT.LEFT,
                "span, align left, wrap 30px");
        Label serverNameL = newComp(Label.class, page2, SWT.LEFT, null);
        serverNameValL = newComp(Label.class, page2, SWT.LEFT, "w max, wrap");
        Label blockSizeL = newComp(Label.class, page2, SWT.LEFT, null);
        blockSizeValL = newComp(Label.class, page2, SWT.LEFT, "w max, wrap");
        Label minBlocksL = newComp(Label.class, page2, SWT.LEFT, null);
        minBlocksValL = newComp(Label.class, page2, SWT.LEFT,
                "w max, wrap 30px");
        Label blocksToStoreL = newComp(Label.class, page2, SWT.LEFT, null);
        blocksToStoreT = newComp(Text.class, page2, SWT.BORDER, "w max");
        blocksToStoreS = newComp(Slider.class, page2, SWT.HORIZONTAL,
                "w max, wrap");
        Label spaceToBeUsedL = newComp(Label.class, page2, SWT.LEFT, null);
        spaceToBeUsedValL = newComp(Label.class, page2, SWT.LEFT,
                "w max, wrap");
        Label ftpPortL = newComp(Label.class, page2, SWT.LEFT, null);
        ftpPortT = newComp(Text.class, page2, SWT.BORDER, "w max, wrap push");
        doneB = newComp(Button.class, page2, SWT.PUSH,
                "span, align right");

        // Page two options.
        settingsL.setText(I18n.get("settings"));
        changeLabelFontSize(settingsL, 16);
        serverNameL.setText(I18n.get("serverName"));
        blockSizeL.setText(I18n.get("blockSize"));
        minBlocksL.setText(I18n.get("minimumBlocks"));
        blocksToStoreL.setText(I18n.get("blocksToStore"));
        blocksToStoreT.addVerifyListener(INTEGER_VERIFIER);
        blocksToStoreT.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent me) {
                int n = 0;
                try {
                    n = Integer.parseInt(blocksToStoreT.getText());
                } catch (NumberFormatException ex) { }
                updateUsedBlocks(n, false);
            }
        });
        blocksToStoreS.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event e) {
                int value = blocksToStoreS.getSelection();
                updateUsedBlocks(value, true);
            }
        });
        spaceToBeUsedL.setText(I18n.get("usedSpace"));
        ftpPortL.setText(I18n.get("ftpPort"));
        ftpPortT.addVerifyListener(INTEGER_VERIFIER);
        ftpPortT.setText("43210");
        doneB.setText(I18n.get("done"));
        doneB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                stepThree();
            }
        });

        stackLayout.topControl = page1;
        shell.layout();
        shell.setDefaultButton(continueB);
    }

    public void loopUntilClosed() {
        centerShell(shell);
        shell.open();
        while (!shell.isDisposed())
            if (!display.readAndDispatch())
                display.sleep();
        display.dispose();
    }

    public boolean isRegisteredSuccessfully() {
        return registeredSuccessfully;
    }

    private void stepTwo() {
        serverAddress = Comm.stringToSocketAddress(addressT.getText().trim());
        if (serverAddress == null) {
            message(I18n.format("invalidAddressForm", "10.20.30.40:5000"));
            return;
        }

        JsonObject serverInfoRequest = Comm.newMessage("server-info");

        try {
            serverInfo = Comm.getMessage(serverAddress, serverInfoRequest);
        } catch (Exception ex) {
            message(I18n.get("errorContactingServer") + "\n\n" +
                    Util.getStackTrace(ex));
            return;
        }
        
        String serverName = serverInfo.get("name").getAsString();
        blockSize = serverInfo.get("block-size").getAsInt();
        minBlocks = serverInfo.get("minimum-blocks").getAsInt();
        int defaultBlocks = minBlocks * 2;

        serverNameValL.setText(serverName);
        blockSizeValL.setText((blockSize / 1024) + " KiB");
        minBlocksValL.setText(Integer.toString(minBlocks));
        blocksToStoreT.setText(Integer.toString(defaultBlocks));
        blocksToStoreS.setMinimum(minBlocks);
        blocksToStoreS.setMaximum(minBlocks * 10);
        blocksToStoreS.setIncrement(8);
        updateUsedBlocks(defaultBlocks, false);

        stackLayout.topControl = page2;
        shell.setDefaultButton(doneB);
        shell.layout();
    }

    private void stepThree() {
        // Verifing that all the supplied data is valid.
        int numberOfBlocks = -1;
        int servicePort = 51423;
        int ftpPort = -1;

        try {
            numberOfBlocks = Integer.parseInt(blocksToStoreT.getText());
        } catch (NumberFormatException ex) { }
        if (numberOfBlocks < minBlocks) {
            message(I18n.get("invalidNumberOfBlocks"));
            return;
        }

        try {
            ftpPort = Integer.parseInt(ftpPortT.getText());
        } catch (NumberFormatException ex) { }
        if (ftpPort < 1 || ftpPort >= (256 * 256)) {
            message(I18n.get("invalidFtpPort"));
            return;
        }

        // Creating the directories if they need to be created.
        File blockDir = new File(new File(Util.getUserDataDir(), Main.SHORT_NAME),
                "blocks");
        File configFileDir = new File(Util.getUserConfigDir(),
                Main.SHORT_NAME);
        if (!blockDir.exists() && !blockDir.mkdirs()) {
            message(I18n.format("failedBlockDir", blockDir.getAbsoluteFile()));
            System.exit(1);
        }
        if (!configFileDir.exists() && !configFileDir.mkdirs()) {
            message(I18n.format("failedConfigDir",
                    configFileDir.getAbsoluteFile()));
            System.exit(1);
        }
        File configFile = new File(configFileDir, "config.json");

        KeyPair keyPair = RSA.generateKeyPair(1024);
        if (keyPair == null) {
            message(I18n.get("failedRsaKeyPair"));
            System.exit(1);
        }

        JsonObject regMsg = Comm.newMessage("registration");
        regMsg.addProperty("public-key", RSA.toString(keyPair.getPublic()));
        regMsg.addProperty("number-of-blocks", numberOfBlocks);
        regMsg.addProperty("port", servicePort);

        JsonObject regResp = null;
        try {
            regResp = Comm.getMessage(serverAddress, regMsg);
        } catch (Exception ex) {
            message(I18n.get("errorContactingServer") + "\n\n" +
                    Util.getStackTrace(ex));
            return;
        }

        if (!regResp.get("registration").getAsString().equals("accepted")) {
            String reason = regResp.get("registration-failed-reason")
                    .getAsString();
            message(I18n.get("failedRegistration") + "\n\n" + reason);
            return;
        }
        
        int uid = regResp.get("uid").getAsInt();

        ClientConfigManager cm = ClientConfigManager.createWith(configFile,
                serverAddress.getHostName(), serverAddress.getPort(),
                serverInfo, blockDir.getAbsolutePath(), uid);
        cm.setPort(servicePort);
        cm.setFtpPort(ftpPort);
        cm.setKeyPair(keyPair);
        cm.save();

        message(I18n.get("successRegistration"), SWT.ICON_INFORMATION);
        registeredSuccessfully = true;
        shell.dispose();
    }

    private void message(String message) {
        message(message, SWT.ICON_WARNING);
    }

    private void message(String message, int type) {
        MessageBox messageBox = new MessageBox(shell, type | SWT.OK);
        messageBox.setText("");
        messageBox.setMessage(message);
        int buttonID = messageBox.open();
    }

    private void updateUsedBlocks(int n, boolean fromSlider) {
        if (fromSlider)
            blocksToStoreT.setText(Integer.toString(n));
        else {
            if (n > blocksToStoreS.getMaximum())
                blocksToStoreS.setSelection(blocksToStoreS.getMaximum());
            else
                blocksToStoreS.setSelection(n);
        }
        int inMiB = (blockSize * n) / (1024 * 1024);
        spaceToBeUsedValL.setText(inMiB + " MiB");
    }

    private <T extends Control> T newComp(Class<T> type, Composite c, int i,
            String layoutData) {
        try {
            T t = type.getDeclaredConstructor(Composite.class, int.class).newInstance(c, i);
            if (layoutData != null)
                t.setLayoutData(layoutData);
            return t;
        } catch (Exception ex) { throw new AssertionError(); }
    }

    private void centerShell(Shell shell) {
        Rectangle bds = shell.getDisplay().getBounds();
        Point p = shell.getSize();
        int nLeft = (bds.width - p.x) / 2;
        int nTop = (bds.height - p.y) / 2;
        shell.setBounds(nLeft, nTop, p.x, p.y);
    }

    private void changeLabelFontSize(Label label, int size) {
        FontData[] fontData = label.getFont().getFontData();
        for (int i = 0; i < fontData.length; ++i)
            fontData[i].setHeight(size);
        final Font newFont = new Font(display, fontData);
        label.setFont(newFont);

        // Since you created the font, you must dispose it
        label.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                newFont.dispose();
            }
        });
    }

    // Returns the location of the configuration file or null on failure.
    public static File testRegister(int code, String serverIp, int serverPort) {
        String strCode = new String(new char[] {(char)('a' + code)});
        int port = 20000 + code;

        File dir = new File(new File(System.getProperty("user.home"), "util"),
                strCode);
        String path = dir.getAbsolutePath();
        // Creating the directory.
        if (!dir.mkdir()) {
            NeguraLog.severe("Couldn't create dir '%s'.", path);
        }

        // Creating the config file path.
        File configFile = new File(dir, "config.json");

        // Creating the block dir.
        File blockDir = new File(dir, "blocks");
        String blockDirPath = blockDir.getAbsolutePath();
        if (!blockDir.mkdir()) {
            NeguraLog.severe("Couldn't create block dir '%s'.", blockDirPath);
        }

        JsonObject serverInfoRequest = Comm.newMessage("server-info");
        JsonObject serverInfo = null;

        try {
            serverInfo = Comm.getMessage(serverIp, serverPort,
                    serverInfoRequest);
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        KeyPair keyPair = RSA.generateKeyPair(1024);

        ClientConfigManager cm = ClientConfigManager.createWith(configFile,
                serverIp, serverPort, serverInfo, blockDirPath, -1);
        cm.setPort(port);
        cm.setFtpPort(2220 + code);
        cm.setKeyPair(keyPair);
        if (port == 20000)
            cm.setStoredBlocks(700);


        JsonObject regMsg = Comm.newMessage("registration");
        regMsg.addProperty("public-key", RSA.toString(cm.getPublicKey()));
        regMsg.addProperty("number-of-blocks", cm.getStoredBlocks());
        regMsg.addProperty("port", cm.getPort());

        JsonObject regMsgResp = null;

        try {
            regMsgResp = Comm.getMessage(serverIp, serverPort, regMsg);
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        if (!regMsgResp.get("registration").getAsString().equals("accepted")) {
            NeguraLog.severe("Registration failed: " +
                    regMsgResp.get("registration-failed-reason").getAsString());
            System.exit(1);
        }

        cm.setUserId(regMsgResp.get("uid").getAsInt());
        cm.save();

        return configFile;
    }
}
