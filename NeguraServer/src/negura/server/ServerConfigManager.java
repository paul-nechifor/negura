package negura.server;

import com.google.gson.JsonObject;
import java.io.FileNotFoundException;
import java.io.IOException;
import negura.common.util.NeguraLog;
import negura.common.util.Util;

/**
 * Manages the server configuration.
 * @author Paul Nechifor
 */
public class ServerConfigManager {
    private JsonObject config;

    public ServerConfigManager(String configPath) {
        try {
            config = Util.readJsonFromFile(configPath);
        } catch (FileNotFoundException ex) {
            NeguraLog.severe(ex);
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }
    }

    public String getDatabaseUrl() {
        return config.get("database-url").getAsString();
    }

    public String getDatabaseUser() {
        return config.get("database-user").getAsString();
    }

    public String getDatabasePassword() {
        return config.get("database-password").getAsString();
    }

    public int getPort() {
        return config.get("port").getAsInt();
    }

    public int getThreadPoolSize() {
        return config.get("thread-pool-size").getAsInt();
    }

    public int getMinimumBlocks() {
        return config.get("minimum-blocks").getAsInt();
    }

    public JsonObject getServerInfo() {
        JsonObject ret = new JsonObject();
        ret.add("name", config.get("name"));
        ret.add("public-key", config.get("public-key"));
        ret.add("admin-public-key", config.get("admin-public-key"));
        ret.add("block-size", config.get("block-size"));
        ret.add("minimum-blocks", config.get("minimum-blocks"));
        ret.add("check-in-time", config.get("check-in-time"));
        return ret;
    }
}
