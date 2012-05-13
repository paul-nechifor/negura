package negura.client.gui;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import negura.client.ClientConfigManager;
import negura.client.ClientConfigManager.Builder;
import negura.client.Main;
import negura.client.I18n;
import negura.common.data.RsaKeyPair;
import negura.common.data.ServerInfo;
import negura.common.gui.Swt;
import negura.common.json.Json;
import negura.common.util.Comm;
import negura.common.util.MsgBox;
import negura.common.util.NeguraLog;
import negura.common.util.Rsa;
import negura.common.util.Util;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

/**
 * Manages the registration progess.
 *
 * @author Paul Nechifor
 */
public class Registration {
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
    private ServerInfo serverInfo;
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

        Swt.getMonospaceFont(display, 12);

        // Page one positioning.
        page1 = new Composite(shell, SWT.NONE);
        page1.setLayout(new MigLayout("insets 10","[grow]"));
        Label newConnectionL = Swt.n(Label.class, page1, SWT.LEFT,
                "wrap 30px");
        Label addressL = Swt.n(Label.class, page1, SWT.LEFT, "wrap");
        addressT = Swt.n(Text.class, page1, SWT.BORDER,
                "wrap push, w 200!");
        Button continueB = Swt.n(Button.class, page1, SWT.PUSH,
                "wrap, align right");

        // Page one options.
        newConnectionL.setText(I18n.get("newConnection"));
        Swt.changeControlFontSize(newConnectionL, display, 16);
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
        Label settingsL = Swt.n(Label.class, page2, SWT.LEFT,
                "span, align left, wrap 30px");
        Label serverNameL = Swt.n(Label.class, page2, SWT.LEFT, null);
        serverNameValL = Swt.n(Label.class, page2, SWT.LEFT, "w max, wrap");
        Label blockSizeL = Swt.n(Label.class, page2, SWT.LEFT, null);
        blockSizeValL = Swt.n(Label.class, page2, SWT.LEFT, "w max, wrap");
        Label minBlocksL = Swt.n(Label.class, page2, SWT.LEFT, null);
        minBlocksValL = Swt.n(Label.class, page2, SWT.LEFT,
                "w max, wrap 30px");
        Label blocksToStoreL = Swt.n(Label.class, page2, SWT.LEFT, null);
        blocksToStoreT = Swt.n(Text.class, page2, SWT.BORDER, "w max");
        blocksToStoreS = Swt.n(Slider.class, page2, SWT.HORIZONTAL,
                "w max, wrap");
        Label spaceToBeUsedL = Swt.n(Label.class, page2, SWT.LEFT, null);
        spaceToBeUsedValL = Swt.n(Label.class, page2, SWT.LEFT,
                "w max, wrap");
        Label ftpPortL = Swt.n(Label.class, page2, SWT.LEFT, null);
        ftpPortT = Swt.n(Text.class, page2, SWT.BORDER, "w max, wrap push");
        doneB = Swt.n(Button.class, page2, SWT.PUSH,
                "span, align right");

        // Page two options.
        settingsL.setText(I18n.get("settings"));
        Swt.changeControlFontSize(settingsL, display, 16);
        serverNameL.setText(I18n.get("serverName"));
        blockSizeL.setText(I18n.get("blockSize"));
        minBlocksL.setText(I18n.get("minimumBlocks"));
        blocksToStoreL.setText(I18n.get("blocksToStore"));
        blocksToStoreT.addVerifyListener(Swt.INTEGER_VERIFIER);
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
            @Override
            public void handleEvent(Event e) {
                int value = blocksToStoreS.getSelection();
                updateUsedBlocks(value, true);
            }
        });
        spaceToBeUsedL.setText(I18n.get("usedSpace"));
        ftpPortL.setText(I18n.get("ftpPort"));
        ftpPortT.addVerifyListener(Swt.INTEGER_VERIFIER);
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
        Swt.centerShell(shell);
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
            MsgBox.warning(shell, I18n.format("invalidAddressForm",
                    "10.20.30.40:5000"));
            return;
        }

        JsonObject serverInfoRequest = Comm.newMessage("server-info");

        try {
            JsonObject o = Comm.readMessage(serverAddress, serverInfoRequest);
            serverInfo = Json.fromJsonObject(o, ServerInfo.class);
        } catch (Exception ex) {
            MsgBox.warning(shell, I18n.get("errorContactingServer") + "\n\n" +
                    Util.getStackTrace(ex));
            return;
        }

        minBlocks = serverInfo.minimumBlocks;
        int defaultBlocks = minBlocks * 2;

        serverNameValL.setText(serverInfo.name);
        blockSizeValL.setText((serverInfo.blockSize / 1024) + " KiB");
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
            MsgBox.warning(shell, I18n.get("invalidNumberOfBlocks"));
            return;
        }

        try {
            ftpPort = Integer.parseInt(ftpPortT.getText());
        } catch (NumberFormatException ex) { }
        if (ftpPort < 1 || ftpPort >= (256 * 256)) {
            MsgBox.warning(shell, I18n.get("invalidFtpPort"));
            return;
        }

        // Creating the directories if they need to be created.
        File blockDir = new File(new File(Util.getUserDataDir(), 
                Main.SHORT_NAME), "blocks");
        File configFileDir = new File(Util.getUserConfigDir(),
                Main.SHORT_NAME);
        if (!blockDir.exists() && !blockDir.mkdirs()) {
            MsgBox.error(shell, I18n.format("failedBlockDir",
                    blockDir.getAbsoluteFile()));
            shell.dispose();
            return;
        }
        if (!configFileDir.exists() && !configFileDir.mkdirs()) {
            MsgBox.error(shell, I18n.format("failedConfigDir",
                    configFileDir.getAbsoluteFile()));
            shell.dispose();
            return;
        }
        File configFile = new File(configFileDir, "config.json");

        RsaKeyPair rsaKeyPair = new RsaKeyPair();
        KeyPair keyPair = Rsa.generateKeyPair(1024);
        if (keyPair == null) {
            MsgBox.error(shell, I18n.get("failedRsaKeyPair"));
            shell.dispose();
            return;
        }
        // TODO: encrypt the private key.
        rsaKeyPair.publicKey = (RSAPublicKey) keyPair.getPublic();
        rsaKeyPair.encryptedPrivateKey = "To encrypt later.";

        JsonObject regMsg = Comm.newMessage("registration");
        regMsg.addProperty("public-key", Rsa.toString(keyPair.getPublic()));
        regMsg.addProperty("number-of-blocks", numberOfBlocks);
        regMsg.addProperty("port", servicePort);

        JsonObject regResp = null;
        try {
            regResp = Comm.readMessage(serverAddress, regMsg);
        } catch (Exception ex) {
            MsgBox.warning(shell, I18n.get("errorContactingServer") + "\n\n" +
                    Util.getStackTrace(ex));
            return;
        }

        if (!regResp.get("registration").getAsString().equals("accepted")) {
            String reason = regResp.get("registration-failed-reason")
                    .getAsString();
            MsgBox.warning(shell, I18n.get("failedRegistration") + "\n\n" +
                    reason);
            return;
        }
        
        int uid = regResp.get("uid").getAsInt();

        ClientConfigManager cm = new Builder(configFile)
                .serverAddress(serverAddress)
                .storedBlocks(serverInfo.minimumBlocks).serverInfo(serverInfo)
                .blockDir(blockDir).userId(uid).servicePort(servicePort)
                .ftpPort(ftpPort).keyPair(rsaKeyPair).threadPoolOptions(
                "core-pool-size=0;maximum-pool-size=15;keep-alive-time=30")
                .build();

        try {
            cm.save();
        } catch (IOException ex) {
            MsgBox.error(shell, "Failed to save the config file.");
            shell.dispose();
            return;
        }

        shell.setVisible(false);
        MsgBox.info(shell, I18n.get("successRegistration"));
        registeredSuccessfully = true;
        shell.dispose();
    }

    /*
    private void message(String message) {
        message(message, SWT.ICON_WARNING);
    }

    private void message(String message, int type) {
        MessageBox messageBox = new MessageBox(shell, type | SWT.OK);
        messageBox.setText("");
        messageBox.setMessage(message);
        int buttonID = messageBox.open();
    }
     *
     */

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



    // Returns the location of the configuration file or null on failure.
    public static File testRegister(int code, String serverIp, int serverPort) {
        String strCode = new String(new char[] {(char)('a' + code)});
        int port = 20000 + code;

        File dir = new File(new File(System.getProperty("user.home"), "util"),
                strCode);
        String path = dir.getAbsolutePath();
        if (dir.exists()) {
            if (!Util.removeDirectory(dir)) {
                NeguraLog.severe("Failed to delete dir: %s", dir);
            }
        }
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
        ServerInfo serverInfo = null;
        InetSocketAddress serverAddress = new InetSocketAddress(serverIp,
                serverPort);

        try {
            JsonObject o = Comm.readMessage(serverAddress, serverInfoRequest);
            serverInfo = Json.fromJsonObject(o, ServerInfo.class);
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        RsaKeyPair rsaKeyPair = new RsaKeyPair();
        KeyPair keyPair = Rsa.generateKeyPair(1024);
        // TODO: encrypt the private key.
        rsaKeyPair.publicKey = (RSAPublicKey) keyPair.getPublic();
        rsaKeyPair.encryptedPrivateKey = "To encrypt later.";

        int storedBlocks = serverInfo.minimumBlocks;
        if (code == 0)
            storedBlocks = 700;

        Builder builder = new Builder(configFile).serverAddress(serverAddress)
                .serverInfo(serverInfo).blockDir(blockDir).servicePort(port)
                .storedBlocks(storedBlocks).threadPoolOptions(
                "core-pool-size=0;maximum-pool-size=15;keep-alive-time=30")
                .ftpPort(2220 + code).keyPair(rsaKeyPair);

        JsonObject regMsg = Comm.newMessage("registration");
        regMsg.addProperty("public-key", Rsa.toString(rsaKeyPair.publicKey));
        regMsg.addProperty("number-of-blocks", storedBlocks);
        regMsg.addProperty("port", port);

        JsonObject regMsgResp = null;

        try {
            regMsgResp = Comm.readMessage(serverIp, serverPort, regMsg);
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        if (!regMsgResp.get("registration").getAsString().equals("accepted")) {
            NeguraLog.severe("Registration failed: " +
                    regMsgResp.get("registration-failed-reason").getAsString());
        }

        builder.userId(regMsgResp.get("uid").getAsInt());
        try {
            builder.build().save();
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }

        return configFile;
    }
}
