package negura.common.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.security.interfaces.RSAPrivateKey;
import negura.common.util.Rsa;

/**
 * TODO: This class should be deleted in the future.
 * @author Paul Nechifor
 */
public class RSAPrivateKeyTypeConverter implements JsonSerializer<RSAPrivateKey>,
        JsonDeserializer<RSAPrivateKey> {
    @Override
    public JsonElement serialize(RSAPrivateKey t, Type type,
            JsonSerializationContext jsc) {
        return new JsonPrimitive(Rsa.toString(t));
    }

    @Override
    public RSAPrivateKey deserialize(JsonElement je, Type type,
            JsonDeserializationContext jdc) throws JsonParseException {
        return Rsa.privateKeyFromString(je.getAsString());
    }
}