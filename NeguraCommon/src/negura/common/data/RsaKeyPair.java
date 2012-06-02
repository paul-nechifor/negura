package negura.common.data;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import javax.xml.bind.DatatypeConverter;
import negura.common.ex.NeguraError;
import negura.common.ex.NeguraRunEx;
import negura.common.util.Aes;
import negura.common.util.NeguraLog;
import negura.common.util.Rsa;
import negura.common.util.Sha;

/**
 * @author Paul Nechifor
 */
public class RsaKeyPair {
    public static class TypeConverter implements JsonSerializer<RsaKeyPair>,
            JsonDeserializer<RsaKeyPair> {
        @Override
        public JsonElement serialize(RsaKeyPair t, Type type,
                JsonSerializationContext jsc) {
            return t.toJsonObject();
        }

        @Override
        public RsaKeyPair deserialize(JsonElement je, Type type,
                JsonDeserializationContext jdc) throws JsonParseException {
            return fromJsonObject(je.getAsJsonObject());
        }
    }

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    private String encryptedPrivateKey;
    private int repetitions;
    private boolean stored;
    private String storedHashPass;
    private int storedRepetitions;

    private RsaKeyPair() { }

    public static RsaKeyPair createNewPair(RSAPublicKey publicKey,
            RSAPrivateKey privateKey, String password, int repetitions) {
        RsaKeyPair ret = new RsaKeyPair();

        ret.publicKey = publicKey;
        ret.privateKey = privateKey;
        ret.repetitions = repetitions;

        String encryptedWith = Sha.get256(password, repetitions);
        ret.encryptedPrivateKey = encryptPrivateKey(privateKey, encryptedWith);
        ret.stored = false;

        return ret;
    }

    public static RsaKeyPair fromJsonObject(JsonObject object) {
        RsaKeyPair ret = new RsaKeyPair();

        ret.publicKey = Rsa.publicKeyFromString(
                object.get("public").getAsString());
        ret.encryptedPrivateKey = object.get("encrypted-private").getAsString();
        ret.repetitions = object.get("repetitions").getAsInt();
        ret.stored = object.get("stored").getAsBoolean();

        if (ret.stored) {
            ret.storedHashPass = object.get("stored-hash-pass").getAsString();
            ret.storedRepetitions = object.get("stored-reps").getAsInt();
            String encryptedWith = Sha.get256(
                    DatatypeConverter.parseHexBinary(ret.storedHashPass),
                    ret.repetitions - ret.storedRepetitions);
            ret.privateKey = decryptPrivateKey(ret.encryptedPrivateKey,
                    encryptedWith);

            if (ret.privateKey == null)
                throw new NeguraRunEx("Password was modified.");
        }

        return ret;
    }

    public final synchronized JsonObject toJsonObject() {
        JsonObject ret = new JsonObject();
        ret.addProperty("public", Rsa.toString(publicKey));
        ret.addProperty("encrypted-private", encryptedPrivateKey);
        ret.addProperty("repetitions", repetitions);
        ret.addProperty("stored", stored);

        if (stored) {
            ret.addProperty("stored-hash-pass", storedHashPass);
            ret.addProperty("stored-reps", storedRepetitions);
        }

        return ret;
    }

    /**
     * Make the pair to decrypt de private key automatically.
     * @param password      The correct password.
     * @param storeReps     How many hashes on the stored version.
     */
    public final synchronized void transformToStored(String password,
            int storeReps) {
        if (!isPrivateKeyDecrypted())
            throw new NeguraError("Key wasn't decrypted first.");
        
        if (stored)
            throw new NeguraError("Already stored.");

        if (storeReps >= repetitions)
            throw new NeguraError("Can't do this.");

        if (storeReps >= repetitions / 2)
            throw new NeguraError("Unsafe: %d, %d.", storeReps, repetitions);

        String with = Sha.get256(password, repetitions);
        RSAPrivateKey key = decryptPrivateKey(encryptedPrivateKey, with);
        if (key == null)
            throw new NeguraError("The password is incorrect.");

        stored = true;
        storedRepetitions = storeReps;
        storedHashPass = Sha.get256(password, storedRepetitions);
    }

    /**
     * Make to pair not decrypt the private key automatically.
     */
    public final synchronized void transformToNotStored() {
        if (!stored)
            throw new NeguraError("It wasn't stored to begin with.");

        stored = false;
        storedRepetitions = 0;
        storedHashPass = null;
    }

    public final synchronized boolean isPrivateKeyDecrypted() {
        return privateKey != null;
    }

    /**
     * Try to decrypt the private key.
     * @param password          The text password.
     * @return                  True on success.
     */
    public final synchronized boolean decryptPrivateKey(String password) {
        if (isPrivateKeyDecrypted()) {
            throw new NeguraError("Already decrypted.");
        }

        String encryptedWith = Sha.get256(password, repetitions);
        privateKey = decryptPrivateKey(encryptedPrivateKey, encryptedWith);

        return isPrivateKeyDecrypted();
    }

    // No need for synchronized.
    public final RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public final synchronized RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    // No need for synchronized.
    public final int getRepetitions() {
        return repetitions;
    }

    private static String encryptPrivateKey(RSAPrivateKey privateKey,
            String hexPass) {
        byte[] bytes = privateKey.getEncoded();

        try {
            byte[] encrypted = Aes.encrypt(bytes, hexPass);
            return DatatypeConverter.printBase64Binary(encrypted);
        } catch (GeneralSecurityException ex) {
            NeguraLog.severe(ex);
            return null;
        }
    }

    /**
     * Try to decrypt the private key in base64 of the AES ecryption with the
     * supplied key in hex.
     * @param keyBase64     The base64 of the AES encrypted key.
     * @param hexPass       The AES password in hex.
     * @return              The private key or null on failure.
     */
    private static RSAPrivateKey decryptPrivateKey(String keyBase64,
            String hexPass) {
        byte[] bytes = DatatypeConverter.parseBase64Binary(keyBase64);

        try {
            byte[] decrypted = Aes.decrypt(bytes, hexPass);
            return Rsa.privateKeyFromBytes(decrypted);
        } catch (GeneralSecurityException ex) {
            return null;
        }
    }
}
