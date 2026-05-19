package top.chiloven.lukosbot2.util

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * SHA utility class.
 * 
 * Features:
 *
 * 1) SHA-256/512 hashing (byte[]/String)
 * 2) Hexadecimal string output for hashed data
 * 3) Hash validation methods
 * 
 * @author Chiloven945
 */
object ShaUtils {

    /**
     * Validate if the given hash matches the expected hash.
     * 
     * @param originalData the original data to be compared
     * @param expectedHash the expected hash (hexadecimal string)
     * @param algorithm    SHA algorithm to use (SHA-256 or SHA-512)
     * @return true if the hashes match, false otherwise
     */
    fun validateHash(originalData: String?, expectedHash: String?, algorithm: String?): Boolean {
        if (originalData == null || expectedHash == null || algorithm == null) return false

        val hashedData = when (algorithm.uppercase()) {
            "SHA-256" -> hashSha256ToHex(originalData)
            "SHA-512" -> hashSha512ToHex(originalData)
            else -> null
        }

        return hashedData?.equals(expectedHash, ignoreCase = true) == true
    }

    /**
     * Hash a String using SHA-256 and return the result as a hexadecimal string.
     * 
     * @param text plain text
     * @return SHA-256 hashed hexadecimal string, or empty string if input is null/empty
     */
    fun hashSha256ToHex(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return bytesToHex(hashSha256(text.toByteArray()))
    }

    /**
     * Hash a ByteArray using SHA-256 and return the result as a hexadecimal string.
     *
     * @param data byte array
     * @return SHA-256 hashed hexadecimal string, or empty string if input is null/empty
     */
    fun hashSha256ToHex(data: ByteArray?): String {
        if (data == null || data.isEmpty()) return ""
        return bytesToHex(hashSha256(data))
    }

    /**
     * Hash a String using SHA-512 and return the result as a hexadecimal string.
     * 
     * @param text plain text
     * @return SHA-512 hashed hexadecimal string, or empty string if input is null/empty
     */
    fun hashSha512ToHex(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return bytesToHex(hashSha512(text.toByteArray()))
    }

    /**
     * Hash a ByteArray using SHA-512 and return the result as a hexadecimal string.
     *
     * @param data byte array
     * @return SHA-512 hashed hexadecimal string, or empty string if input is null/empty
     */
    fun hashSha512ToHex(data: ByteArray?): String {
        if (data == null || data.isEmpty()) return ""
        return bytesToHex(hashSha512(data))
    }

    /**
     * Convert a byte array to a hexadecimal string.
     * 
     * @param bytes byte array
     * @return hexadecimal string representation of the byte array
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Hash raw bytes using SHA-256.
     * 
     * @param data raw bytes
     * @return SHA-256 hashed bytes, or empty array if input is null/empty
     */
    fun hashSha256(data: ByteArray?): ByteArray {
        if (data == null || data.isEmpty()) return byteArrayOf()
        return getDigest("SHA-256").digest(data)
    }

    /**
     * Hash raw bytes using SHA-512.
     * 
     * @param data raw bytes
     * @return SHA-512 hashed bytes, or empty array if input is null/empty
     */
    fun hashSha512(data: ByteArray?): ByteArray {
        if (data == null || data.isEmpty()) return byteArrayOf()
        return getDigest("SHA-512").digest(data)
    }

    private fun getDigest(algorithm: String): MessageDigest {
        return try {
            MessageDigest.getInstance(algorithm)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Critical: $algorithm not available.", e)
        }
    }

}
