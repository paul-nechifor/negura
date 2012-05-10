package negura.common.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.io.File;
import java.lang.reflect.Type;

/**
 *
 * @author Paul Nechifor
 */
public class FileTypeConverter implements JsonSerializer<File>,
        JsonDeserializer<File> {
    @Override
    public JsonElement serialize(File t, Type type,
            JsonSerializationContext jsc) {
        return new JsonPrimitive(t.getAbsolutePath());
    }

    @Override
    public File deserialize(JsonElement je, Type type,
            JsonDeserializationContext jdc) throws JsonParseException {
        return new File(je.getAsString());
    }
}
