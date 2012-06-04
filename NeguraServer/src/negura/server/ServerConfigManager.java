package negura.server;

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import negura.common.data.RsaKeyPair;
import negura.common.data.ServerInfo;
import negura.common.data.ThreadPoolOptions;
import negura.common.ex.NeguraEx;
import negura.common.json.Json;

/**
 * Manages the server configuration.
 *
 * @author Paul Nechifor
 */
public class ServerConfigManager {
    public static class Builder {
        public transient File configFile;

        public ServerInfo serverInfo = new ServerInfo();
        public int virtualDiskBlocks;
        public int port;
        public ThreadPoolOptions threadPoolOptions;
        public String databaseUrl;
        public String databaseUser;
        public String databasePassword; // TODO: Change this.
        public RsaKeyPair serverKeyPair;
        public RsaKeyPair adminKeyPair;
        public boolean firstRun;

        public Builder(File configFile) {
            if (configFile == null) {
                throw new IllegalArgumentException("The file can't be null");
            }

            this.configFile = configFile;
        }
    }

    private final Builder builder;
    private final NeguraServer neguraServer;

    public ServerConfigManager(Builder builder, NeguraServer neguraServer) {
        this.builder = builder;
        this.neguraServer = neguraServer;
    }

    public ServerConfigManager(File configFile, NeguraServer neguraServer)
            throws NeguraEx {
        this.neguraServer = neguraServer;

        try {
            this.builder = Json.fromFile(configFile, Builder.class);
        } catch (JsonSyntaxException ex) {
            throw new NeguraEx("Invalid config file: " + ex.getMessage());
        } catch (IOException ex) {
            throw new NeguraEx("Error reading config file: " + ex.getMessage());
        }

        this.builder.configFile = configFile;
    }

    public void save() throws IOException {
        Json.toFile(builder.configFile, builder);
    }

    public final NeguraServer getNeguraServer() {
        return neguraServer;
    }

    public String getDatabaseUrl() {
        return builder.databaseUrl;
    }

    public String getDatabaseUser() {
        return builder.databaseUser;
    }

    public String getDatabasePassword() {
        return builder.databasePassword;
    }

    public RsaKeyPair getServerKeyPair() {
        return builder.serverKeyPair;
    }

    public int getPort() {
        return builder.port;
    }

    public ServerInfo getServerInfo() {
        return builder.serverInfo;
    }

    public ThreadPoolOptions getThreadPoolOptions() {
        return builder.threadPoolOptions;
    }

    public synchronized boolean getFirstRun() {
        return builder.firstRun;
    }

    public synchronized void setFirstRunOff() {
        builder.firstRun = false;
    }

    public int getVirtualDiskBlocks() {
        return builder.virtualDiskBlocks;
    }
}
