package negura.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;

/**
 * SHA message digest utility methods.
 * 
 * @author Paul Nechifor
 */
public class Sha {
    private static final MessageDigest SHA256;

    static {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            NeguraLog.severe(ex);
        }
        SHA256 = md;
    }

    private Sha() { }

    public static String get256(byte[] bytes, int offset, int length) {
        SHA256.update(bytes, offset, length);
        return DatatypeConverter.printHexBinary(SHA256.digest());
    }

    public static String get256(byte[] bytes) {
        return get256(bytes, 0, bytes.length);
    }

    public static String get256(byte[] bytes, int repetitions) {
        if (repetitions <= 0) {
            throw new IllegalArgumentException("Repetitions = " + repetitions);
        }

        byte[] hashed = bytes;

        for (int i = 0; i < repetitions; i++) {
            SHA256.update(hashed, 0, hashed.length);
            hashed = SHA256.digest();
        }

        return DatatypeConverter.printHexBinary(hashed);
    }

    public static String get256(String string, int repetitions) {
        byte[] bytes = string.getBytes();
        return get256(bytes, repetitions);
    }
}
