package negura.common.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.security.interfaces.RSAPublicKey;
import negura.common.util.Rsa;

/**
 *
 * @author Paul Nechifor
 */
public class RSAPublicKeyTypeConverter implements JsonSerializer<RSAPublicKey>,
        JsonDeserializer<RSAPublicKey> {
    @Override
    public JsonElement serialize(RSAPublicKey t, Type type,
            JsonSerializationContext jsc) {
        return new JsonPrimitive(Rsa.toString(t));
    }

    @Override
    public RSAPublicKey deserialize(JsonElement je, Type type,
            JsonDeserializationContext jdc) throws JsonParseException {
        return Rsa.publicKeyFromString(je.getAsString());
    }
}
