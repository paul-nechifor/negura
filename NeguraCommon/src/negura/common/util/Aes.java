package negura.common.util;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 * AES utility methods.
 * 
 * @author Paul Nechifor
 */
public class Aes {
    private Aes() { }

    public static SecretKeySpec keyFromHex(String hexKey) {
        byte[] keyBytes = DatatypeConverter.parseHexBinary(hexKey);
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static byte[] encrypt(byte[] bytes, String hexKey) throws
            GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

        cipher.init(Cipher.ENCRYPT_MODE, keyFromHex(hexKey));
        byte[] encryptedBytes = new byte[cipher.getOutputSize(bytes.length)];
        int len = cipher.update(bytes, 0, bytes.length, encryptedBytes, 0);
        len += cipher.doFinal(encryptedBytes, len);

        return encryptedBytes;
    }

    public static byte[] decrypt(byte[] bytes, String hexKey) throws
            GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

        cipher.init(Cipher.DECRYPT_MODE, keyFromHex(hexKey));
        byte[] decryptedBytes = new byte[cipher.getOutputSize(bytes.length)];
        int len = cipher.update(bytes, 0, bytes.length, decryptedBytes, 0);
        len += cipher.doFinal(decryptedBytes, len);

        return Arrays.copyOfRange(decryptedBytes, 0, len);
    }
}