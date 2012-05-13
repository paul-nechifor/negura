package negura.common.json;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import negura.common.data.BlockIndexer;

/**
 *
 * @author Paul Nechifor
 */
public class Json {
    private static final Gson gson;
    private static final JsonParser PARSER = new JsonParser();

    private Json() { }

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(File.class, new FileTypeConverter());
        builder.registerTypeAdapter(RSAPublicKey.class,
                new RSAPublicKeyTypeConverter());
        builder.registerTypeAdapter(RSAPrivateKey.class,
                new RSAPrivateKeyTypeConverter());
        builder.registerTypeAdapter(BlockIndexer.class,
                new BlockIndexerTypeConverter());
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES);
        gson = builder.create();
    }

    public static String toString(Object object) {
        return gson.toJson(object);
    }

    public static String toString(JsonElement jsonElement) {
        return gson.toJson(jsonElement);
    }

    public static void toFile(File file, Object object) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(gson.toJson(object));
        writer.close();
    }

    public static <E> E fromReader(Reader reader, Class<E> type) {
        return gson.fromJson(reader, type);
    }

    public static <E> E fromFile(File file, Class<E> type)
            throws FileNotFoundException, IOException {
        FileReader reader = new FileReader(file);
        E ret = gson.fromJson(reader, type);
        reader.close();
        return ret;
    }

    public static <E> E fromJsonObject(JsonObject object, Class<E> type) {
        return gson.fromJson(object, type);
    }
}
