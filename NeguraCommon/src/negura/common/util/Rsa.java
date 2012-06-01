package negura.common.util;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * @author Paul Nechifor
 */
public class Rsa {
    private Rsa() { }

    public static RSAPublicKey publicKeyFromBytes(byte[] bytes) {
        try {
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(bytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(pubKeySpec);
        } catch (Exception ex) {
            NeguraLog.warning(ex);
            return null;
        }
    }
    
    public static RSAPublicKey publicKeyFromString(String base64) {
        return publicKeyFromBytes(DatatypeConverter.parseBase64Binary(base64));
    }

    public static RSAPrivateKey privateKeyFromBytes(byte[] bytes) {
        try {
            PKCS8EncodedKeySpec pubKeySpec = new PKCS8EncodedKeySpec(bytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(pubKeySpec);
        } catch (Exception ex) {
            NeguraLog.warning(ex);
            return null;
        }
    }

    public static RSAPrivateKey privateKeyFromString(String base64) {
        return privateKeyFromBytes(DatatypeConverter.parseBase64Binary(base64));
    }

    public static String sign(byte[] bytes, RSAPrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance("SHA1WithRSA");
            sig.initSign(privateKey);
            sig.update(bytes);
            byte[] sigBytes = sig.sign();
            return DatatypeConverter.printBase64Binary(sigBytes);
        } catch (Exception e) { }
        return null;
    }

    public static boolean verify(byte[] bytes, String signature,
            RSAPublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("SHA1WithRSA");
            sig.initVerify(publicKey);
            sig.update(bytes);
            byte[] sigBytes = DatatypeConverter.parseBase64Binary(signature);
            return sig.verify(sigBytes);
        } catch (Exception e) { }
        return false;
    }

    public static KeyPair generateKeyPair(int bits)
    {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(bits);
            return keyGen.genKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            NeguraLog.severe(ex);
            return null;
        }
    }

    public static String toString(Key key) {
        return DatatypeConverter.printBase64Binary(key.getEncoded());
    }

    /**
     * Returns the key in hex format with lines no longer than max characters.
     * @param key           The key.
     * @param max           The maximum length of the line.
     * @return              The key in hex format broken into multiple lines.
     */
    public static String toString(Key key, int max) {
        String hex = toString(key);

        StringBuilder ret = new StringBuilder(hex.length());
        int len = hex.length() - max;
        int i;
        for (i = 0; i < len; i += max)
            ret.append(hex.substring(i, i + max)).append('\n');
        ret.append(hex.substring(i, hex.length()));

        return ret.toString();
    }
}