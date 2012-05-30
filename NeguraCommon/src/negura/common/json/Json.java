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
import java.util.Map.Entry;
import negura.common.data.BlockList;

/**
 *
 * @author Paul Nechifor
 */
public class Json {
    private static final Gson GSON;
    private static final JsonParser PARSER = new JsonParser();

    private Json() { }

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(File.class, new FileTypeConverter());
        builder.registerTypeAdapter(RSAPublicKey.class,
                new RSAPublicKeyTypeConverter());
        builder.registerTypeAdapter(RSAPrivateKey.class,
                new RSAPrivateKeyTypeConverter());
        builder.registerTypeAdapter(BlockList.class,
                new BlockList.TypeConverter());
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES);
        GSON = builder.create();
    }

    public static String toString(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Returns the JsonElement corresponding to an object.
     * @param object    The object whose fields will be included.
     * @return          The JsonElement.
     */
    public static JsonElement toJsonElement(Object object) {
        return PARSER.parse(toString(object));
    }

    public static String toString(JsonElement jsonElement) {
        return GSON.toJson(jsonElement);
    }

    public static void toFile(File file, Object object) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(GSON.toJson(object));
        writer.close();
    }

    public static <E> E fromReader(Reader reader, Class<E> type) {
        return GSON.fromJson(reader, type);
    }

    public static <E> E fromFile(File file, Class<E> type)
            throws FileNotFoundException, IOException {
        FileReader reader = new FileReader(file);
        E ret = GSON.fromJson(reader, type);
        reader.close();
        return ret;
    }

    public static <E> E fromJsonObject(JsonObject object, Class<E> type) {
        return GSON.fromJson(object, type);
    }

    /**
     * Extend a JsonObject by adding all the fields of another to it.
     * @param toBeExtended      The one which will be extended.
     * @param object            The one which contains the fields with which to
     *                          extend.
     */
    public static void extend(JsonObject toBeExtended, JsonObject object) {
        for (Entry<String, JsonElement> e : object.entrySet()) {
            toBeExtended.add(e.getKey(), e.getValue());
        }
    }

    public static int getDefault(JsonObject o, String field, int defValue) {
        JsonElement e = o.get(field);

        if (e == null) {
            return defValue;
        }

        return e.getAsInt();
    }
}
