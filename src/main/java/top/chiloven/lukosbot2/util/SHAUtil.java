package top.chiloven.lukosbot2.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Instance-based SHA utility class (no static methods).
 * <p>
 * Features:
 * 1) SHA-256/512 hashing (byte[]/String)
 * 2) Hexadecimal string output for hashed data
 * 3) Hash validation methods
 *
 * @author Chiloven945
 */
public final class SHAUtil {

    private final MessageDigest sha256Digest;
    private final MessageDigest sha512Digest;

    /**
     * Create a SHAUtil instance with SHA-256 and SHA-512 digests.
     */
    private SHAUtil() {
        try {
            this.sha256Digest = MessageDigest.getInstance("SHA-256");
            this.sha512Digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA Algorithm not available. This exception should not be threw unless there are some problem with your JRE!", e);
        }
    }

    /**
     * Get an SHAUtil instance.
     *
     * @return a SHAUtil instance
     */
    public static SHAUtil getSHAUtil() {
        return new SHAUtil();
    }

    /* ===================== SHA-256: byte[] <-> byte[] ===================== */

    /**
     * Hash raw bytes using SHA-256.
     *
     * @param data raw bytes
     * @return SHA-256 hashed bytes, or empty array if input is null/empty
     */
    public byte[] hashSHA256(byte[] data) {
        if (data == null || data.length == 0) return new byte[0];
        return sha256Digest.digest(data);
    }

    /**
     * Hash raw bytes using SHA-512.
     *
     * @param data raw bytes
     * @return SHA-512 hashed bytes, or empty array if input is null/empty
     */
    public byte[] hashSHA512(byte[] data) {
        if (data == null || data.length == 0) return new byte[0];
        return sha512Digest.digest(data);
    }

    /* ===================== SHA-256: String <-> String ===================== */

    /**
     * Hash a String using SHA-256 and return the result as a hexadecimal string.
     *
     * @param text plain text
     * @return SHA-256 hashed hexadecimal string, or empty string if input is null/empty
     */
    public String hashSHA256ToHex(String text) {
        if (text == null || text.isEmpty()) return "";
        byte[] hashed = hashSHA256(text.getBytes());
        return bytesToHex(hashed);
    }

    /**
     * Hash a String using SHA-512 and return the result as a hexadecimal string.
     *
     * @param text plain text
     * @return SHA-512 hashed hexadecimal string, or empty string if input is null/empty
     */
    public String hashSHA512ToHex(String text) {
        if (text == null || text.isEmpty()) return "";
        byte[] hashed = hashSHA512(text.getBytes());
        return bytesToHex(hashed);
    }

    /**
     * Convert a byte array to a hexadecimal string.
     *
     * @param bytes byte array
     * @return hexadecimal string representation of the byte array
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /* ===================== Hash Validation ===================== */

    /**
     * Validate if the given hash matches the expected hash.
     *
     * @param originalData the original data to be compared
     * @param expectedHash the expected hash (hexadecimal string)
     * @param algorithm    SHA algorithm to use (SHA-256 or SHA-512)
     * @return true if the hashes match, false otherwise
     * @throws NoSuchAlgorithmException if the SHA algorithm is not available
     */
    public boolean validateHash(String originalData, String expectedHash, String algorithm) throws NoSuchAlgorithmException {
        if (originalData == null || expectedHash == null || algorithm == null) {
            return false;
        }

        String hashedData = null;
        if ("SHA-256".equalsIgnoreCase(algorithm)) {
            hashedData = hashSHA256ToHex(originalData);
        } else if ("SHA-512".equalsIgnoreCase(algorithm)) {
            hashedData = hashSHA512ToHex(originalData);
        }

        return hashedData != null && hashedData.equalsIgnoreCase(expectedHash);
    }
}
