package negura.common.data;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import negura.common.json.Json;
import negura.common.util.Rsa;
import negura.common.util.Util;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Paul Nechifor
 */
public class RsaKeyPairTest {
    private static String password = "thePassword";
    private static int repetitions = 1000;
    private static RsaKeyPair rsaKeyPair;
    private static String privateKeyStr;

    public RsaKeyPairTest() { }

    @BeforeClass
    public static void setUpClass() throws Exception {
        KeyPair keyPair = Rsa.generateKeyPair(1024);
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKeyStr = Rsa.toString(privateKey);
        rsaKeyPair = RsaKeyPair.createNewPair(publicKey, privateKey, password,
                repetitions);
    }

    @Test
    public void testWriteToDisk() throws IOException {
        JsonObject json = rsaKeyPair.toJsonObject();
        
        System.out.println(Json.toString(json));

        File file = File.createTempFile("test_", ".json");
        Json.toFile(file, json);

        String jsonStr = Util.readStreamAsString(new FileInputStream(file));
        System.out.println(jsonStr);

        RsaKeyPair readPair = Json.fromFile(file, RsaKeyPair.class);

        System.out.println(Json.toString(readPair.toJsonObject()));

        assertEquals(true, readPair.decryptPrivateKey(password));
        assertEquals(privateKeyStr, Rsa.toString(readPair.getPrivateKey()));

        readPair.transformToStored(password, 200);

        System.out.println(Json.toString(readPair.toJsonObject()));

        File file2 = File.createTempFile("text_", ".json");
        Json.toFile(file2, readPair.toJsonObject());

        RsaKeyPair readPair2 = Json.fromFile(file2, RsaKeyPair.class);

        System.out.println(Json.toString(readPair2.toJsonObject()));

        assertEquals(true, readPair2.isPrivateKeyDecrypted());
        assertEquals(privateKeyStr, Rsa.toString(readPair2.getPrivateKey()));
    }
}
