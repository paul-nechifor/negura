package negura.server;

import java.io.File;
import java.io.IOException;
import negura.common.data.RsaKeyPair;
import negura.common.data.ServerInfo;
import negura.common.data.ThreadPoolOptions;
import negura.common.json.Json;

/**
 * Manages the server configuration.
 *
 * @author Paul Nechifor
 */
public class ServerConfigManager {
    public static class Builder {
        public transient File configFile;

        public ServerInfo serverInfo;
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
            this.configFile = configFile;
            this.serverInfo = new ServerInfo();
        }
    }

    private Builder builder;

    public ServerConfigManager(Builder builder) {
        this.builder = builder;
    }

    public ServerConfigManager(File configFile) throws IOException {
        this.builder = Json.fromFile(configFile, Builder.class);
        this.builder.configFile = configFile;
    }

    public void save() throws IOException {
        Json.toFile(builder.configFile, builder);
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

    public int getPort() {
        return builder.port;
    }

    public ServerInfo getServerInfo() {
        return builder.serverInfo;
    }

    public ThreadPoolOptions getThreadPoolOptions() {
        return builder.threadPoolOptions;
    }

    public boolean getFirstRun() {
        return builder.firstRun;
    }

    public void setFirstRunOff() {
        builder.firstRun = false;
    }

    public int getVirtualDiskBlocks() {
        return builder.virtualDiskBlocks;
    }
}
