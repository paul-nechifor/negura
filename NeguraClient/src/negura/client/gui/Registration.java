package negura.client.gui;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import negura.client.ClientConfigManager;
import negura.client.ClientConfigManager.Builder;
import negura.client.I18n;
import negura.common.data.RsaKeyPair;
import negura.common.data.ServerInfo;
import negura.common.gui.Swt;
import negura.common.json.Json;
import negura.common.util.Comm;
import negura.common.util.MsgBox;
import negura.common.util.NeguraLog;
import negura.common.util.Os;
import negura.common.util.Rsa;
import negura.common.util.Util;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

/**
 * Manages the registration progess.
 *
 * @author Paul Nechifor
 */
public class Registration {
    private Display display;
    private final Shell shell;
    private final StackLayout stackLayout;
    private final Font titleFont;
    private final Composite p1;
    private final Composite p2;
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
    private int minBlocks;
    private boolean registeredSuccessfully = false;

    public Registration() {
        display = new Display();
        shell = new Shell(display);
        shell.setText(I18n.get("applicationName"));
        shell.setSize(550, 400);
        stackLayout = new StackLayout();
        shell.setLayout(stackLayout);
        Image icon = new Image(display, getClass().getResourceAsStream(
                "/res/icons/application_32.png"));
        Swt.connectDisposal(shell, icon);
        shell.setImage(icon);

        Swt.getMonospacedFont(display, 12);

        // Page one positioning.
        p1 = new Composite(shell, SWT.NONE);
        p1.setLayout(new MigLayout("insets 10","[grow]"));

        Label newConnectionL = Swt.newLabel(p1, "wrap 30px",
                I18n.get("newConnection"));

        Swt.newLabel(p1, "wrap", I18n.get("serverAddress"));
        addressT = Swt.newText(p1, "wrap push, w 200!", "127.0.0.1:5000");

        Button continueB = Swt.newButton(p1, "wrap, align right",
                I18n.get("continue"));

        // Page one options.
        titleFont = Swt.getFontWithDifferentHeight(display,
                newConnectionL.getFont(), 16);
        Swt.connectDisposal(shell, titleFont);
        newConnectionL.setFont(titleFont);

        continueB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                stepTwo();
            }
        });

        // Page two positioning.
        p2 = new Composite(shell, SWT.NONE);
        p2.setLayout(new MigLayout("insets 10","[right][150!][max]"));

        Label settingsL = Swt.newLabel(p2, "span, align left, wrap 30px",
                I18n.get("settings"));

        Swt.newLabel(p2, null, I18n.get("serverName"));
        serverNameValL = Swt.newLabel(p2, "w max, wrap", null);

        Swt.newLabel(p2, null, I18n.get("blockSize"));
        blockSizeValL = Swt.newLabel(p2, "w max, wrap", null);

        Swt.newLabel(p2, null, I18n.get("minimumBlocks"));
        minBlocksValL = Swt.newLabel(p2, "w max, wrap 30px", null);

        Swt.newLabel(p2, null, I18n.get("blocksToStore"));

        blocksToStoreT = Swt.newText(p2, "w max", null);
        blocksToStoreS = Swt.newHSlider(p2, "w max, wrap", -1, -1, -1);

        Swt.newLabel(p2, null, I18n.get("usedSpace"));
        spaceToBeUsedValL = Swt.newLabel(p2, "w max, wrap", null);

        Swt.newLabel(p2, null, I18n.get("ftpPort"));
        ftpPortT = Swt.newText(p2, "w max, wrap push", "43210");

        doneB = Swt.newButton(p2, "span, align right", I18n.get("done"));

        // Page two options.
        settingsL.setFont(titleFont);
        blocksToStoreT.addVerifyListener(Swt.INTEGER_VERIFIER);
        Swt.connectTo(Swt.TEXT_FROM_SLIDER, blocksToStoreT, blocksToStoreS);
        Swt.connectTo(Swt.SLIDER_FROM_TEXT, blocksToStoreS, blocksToStoreT);
        Swt.Mod mod = new Swt.Mod() {
            public void modify(Widget to, Widget... from) {
                Label label = (Label) to;
                Text text = (Text) from[0];
                long numberOfBlocks = Util.parseLongOrZero(text.getText());
                label.setText(Util.bytesWithUnit(numberOfBlocks *
                        serverInfo.blockSize, 2));
            }
        };
        Swt.connectTo(mod, spaceToBeUsedValL, blocksToStoreT);
        
        ftpPortT.addVerifyListener(Swt.INTEGER_VERIFIER);
        doneB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                stepThree();
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

        blocksToStoreS.setMaximum(minBlocks * 10);
        blocksToStoreS.setMinimum(minBlocks);
        blocksToStoreS.setIncrement(8);
        serverNameValL.setText(serverInfo.name);
        blockSizeValL.setText(Util.bytesWithUnit(
                serverInfo.blockSize, 0));
        minBlocksValL.setText(Integer.toString(minBlocks));
        blocksToStoreT.setText(Integer.toString(defaultBlocks));

        stackLayout.topControl = p2;
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
        File dataDir = new File(Os.getUserDataDir(),
                I18n.get("applicationShortName"));
        File configFileDir = new File(Os.getUserConfigDir(),
                I18n.get("applicationShortName"));
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            MsgBox.error(shell, I18n.format("failedDataDir",
                    dataDir.getAbsoluteFile()));
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

        Builder builder = new Builder(configFile)
                .serverAddress(serverAddress)
                .storedBlocks(serverInfo.minimumBlocks).serverInfo(serverInfo)
                .dataDir(dataDir).userId(uid).servicePort(servicePort)
                .ftpPort(ftpPort).keyPair(rsaKeyPair).threadPoolOptions(
                "core-pool-size=0;maximum-pool-size=15;keep-alive-time=30");

        ClientConfigManager cm = null;
        try {
            cm = builder.build();
        } catch (IOException ex) {
            MsgBox.error(shell, "Failed to build configuration.");
            shell.dispose();
            return;
        }

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

    // Returns the location of the configuration file or null on failure.
    public static File testRegister(int code, String serverIp, int serverPort) {
        String strCode = new String(new char[] {(char)('a' + code)});
        int port = 20000 + code;

        File dir = new File(new File(System.getProperty("user.home"), "util"),
                strCode);
        String path = dir.getAbsolutePath();
        if (dir.exists()) {
            if (!Os.removeDirectory(dir)) {
                NeguraLog.severe("Failed to delete dir: %s", dir);
            }
        }
        // Creating the directory.
        if (!dir.mkdir()) {
            NeguraLog.severe("Couldn't create dir '%s'.", path);
        }

        // Creating the config file path.
        File configFile = new File(dir, "config.json");

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

        Builder builder = new Builder(configFile).serverAddress(serverAddress)
                .serverInfo(serverInfo).dataDir(dir).servicePort(port)
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
