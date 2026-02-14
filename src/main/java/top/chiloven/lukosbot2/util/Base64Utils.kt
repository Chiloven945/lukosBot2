package top.chiloven.lukosbot2.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*

/**
 * Instance-based Base64 utility class (no static methods).
 * 
 * 
 * Features:
 * 1) Standard Base64 encode/decode (String/byte[])
 * 2) URL-safe Base64 encode/decode (optional padding removal)
 * 3) MIME Base64 encode/decode (line-wrapped)
 * 4) File to Base64 / Base64 to file
 * 5) Basic Base64-like validation and padding fixing
 * 
 * @author Chiloven945
 */
class Base64Utils(private val defaultCharset: Charset = StandardCharsets.UTF_8) {
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()
    private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder()
    private val urlDecoder: Base64.Decoder = Base64.getUrlDecoder()
    private val mimeEncoder: Base64.Encoder = Base64.getMimeEncoder()
    private val mimeDecoder: Base64.Decoder = Base64.getMimeDecoder()

    /* ===================== Basic: byte[] <-> byte[] ===================== */

    /**
     * Encode raw bytes to standard Base64 bytes.
     * 
     * @param data raw bytes
     * @return Base64-encoded bytes, or empty array if input is null/empty
     */
    fun encode(data: ByteArray?): ByteArray? {
        if (data == null || data.isEmpty()) return ByteArray(0)
        return encoder.encode(data)
    }

    /**
     * Decode standard Base64 bytes to raw bytes.
     * 
     * @param base64Bytes Base64-encoded bytes
     * @return decoded raw bytes, or empty array if input is null/empty
     * @throws IllegalArgumentException if input is not valid Base64
     */
    fun decode(base64Bytes: ByteArray?): ByteArray? {
        if (base64Bytes == null || base64Bytes.isEmpty()) return ByteArray(0)
        return decoder.decode(base64Bytes)
    }

    /* ===================== Basic: String <-> String ===================== */

    /**
     * Encode a String to standard Base64 using a given charset.
     * 
     * @param text    plain text
     * @param charset charset to use; default charset if null
     * @return Base64 string, or empty string if input is null/empty
     */
    @JvmOverloads
    fun encodeToString(text: String?, charset: Charset? = defaultCharset): String? {
        if (text.isNullOrEmpty()) return ""
        val cs = charset ?: defaultCharset
        return encoder.encodeToString(text.toByteArray(cs))
    }

    /**
     * Decode a standard Base64 string to plain text using a given charset.
     * 
     * @param base64  Base64 string
     * @param charset charset to use; default charset if null
     * @return decoded plain text, or empty string if input is null/empty
     * @throws IllegalArgumentException if input is not valid Base64
     */
    @JvmOverloads
    fun decodeToString(base64: String?, charset: Charset? = defaultCharset): String {
        if (base64.isNullOrEmpty()) return ""
        val cs = charset ?: defaultCharset
        val decoded = decoder.decode(base64)
        return String(decoded, cs)
    }

    /**
     * Decode a Base64 (standard or URL-safe) string into a JsonObject using given charset.
     * This method:
     * 1) Fixes missing padding if needed
     * 2) Tries standard Base64 decode first, then URL-safe decode
     * 
     * @param base64  Base64 string (standard or URL-safe)
     * @param charset charset for decoded text; default charset if null
     * @return decoded JsonObject
     * @throws JsonSyntaxException      if decoded text is not valid JSON
     * @throws IllegalArgumentException if base64 is invalid
     */
    @JvmOverloads
    @Throws(JsonSyntaxException::class, IllegalArgumentException::class)
    fun decodeToJsonObject(base64: String?, charset: Charset? = defaultCharset): JsonObject? {
        if (base64.isNullOrEmpty()) {
            return JsonObject()
        }

        val cs = charset ?: defaultCharset
        val fixed = fixPadding(base64)

        var decodedBytes: ByteArray
        try {
            decodedBytes = decoder.decode(fixed)
        } catch (_: IllegalArgumentException) {
            decodedBytes = urlDecoder.decode(fixed)
        }

        val jsonText = String(decodedBytes, cs)
        return JsonParser.parseString(jsonText).getAsJsonObject()
    }

    /* ===================== URL-safe Base64 ===================== */

    /**
     * Encode text to URL-safe Base64.
     * 
     * @param text        plain text
     * @param withPadding whether to keep '=' padding
     * @return URL-safe Base64 string, or empty string if input is null/empty
     */
    @JvmOverloads
    fun encodeUrlSafe(text: String?, withPadding: Boolean = true): String? {
        if (text.isNullOrEmpty()) return ""
        val s = urlEncoder.encodeToString(text.toByteArray(defaultCharset))
        return if (withPadding) s else s.replace("=", "")
    }

    /**
     * Decode a URL-safe Base64 string to plain text.
     * Missing padding will be fixed automatically.
     * 
     * @param base64Url URL-safe Base64 string
     * @return decoded plain text, or empty string if input is null/empty
     * @throws IllegalArgumentException if input is not valid Base64URL
     */
    fun decodeUrlSafe(base64Url: String?): String {
        if (base64Url.isNullOrEmpty()) return ""
        val fixed = fixPadding(base64Url)
        val decoded = urlDecoder.decode(fixed)
        return String(decoded, defaultCharset)
    }

    /* ===================== MIME Base64 (line-wrapped) ===================== */

    /**
     * Encode text to MIME Base64 (line-wrapped at 76 chars).
     * 
     * @param text plain text
     * @return MIME Base64 string
     */
    fun encodeMime(text: String?): String? {
        if (text.isNullOrEmpty()) return ""
        return mimeEncoder.encodeToString(text.toByteArray(defaultCharset))
    }

    /**
     * Decode a MIME Base64 string to plain text.
     * 
     * @param mimeBase64 MIME Base64 string
     * @return decoded plain text
     * @throws IllegalArgumentException if input is not valid MIME Base64
     */
    fun decodeMime(mimeBase64: String?): String {
        if (mimeBase64.isNullOrEmpty()) return ""
        val decoded = mimeDecoder.decode(mimeBase64)
        return String(decoded, defaultCharset)
    }

    /* ===================== File <-> Base64 ===================== */

    /**
     * Read a file and encode its contents to a standard Base64 string.
     * 
     * @param file source file
     * @return Base64 string of file bytes, or empty string if file is null
     * @throws IOException if file cannot be read
     */
    @Throws(IOException::class)
    fun encodeFileToString(file: File?): String? {
        if (file == null) return ""
        val bytes = Files.readAllBytes(file.toPath())
        return encoder.encodeToString(bytes)
    }

    /**
     * Decode a standard Base64 string and write the bytes to a file (overwrite).
     * 
     * @param base64     Base64 string
     * @param targetFile target file to write
     * @throws IOException              if file cannot be written
     * @throws IllegalArgumentException if targetFile is null or base64 invalid
     */
    @Throws(IOException::class)
    fun decodeStringToFile(base64: String?, targetFile: File) {
        val decoded = if (base64.isNullOrEmpty())
            ByteArray(0)
        else
            decoder.decode(base64)
        FileOutputStream(targetFile).use { os ->
            os.write(decoded)
        }
    }

    /* ===================== Helpers ===================== */
    /**
     * A lightweight "looks like Base64" check (not 100% strict).
     * 
     * @param s input string
     * @return true if string resembles Base64
     */
    fun isBase64Like(s: String?): Boolean {
        if (s.isNullOrEmpty()) return false
        return s.matches("^[A-Za-z0-9+/=_-]+$".toRegex())
    }

    /**
     * Fix missing '=' padding for Base64URL strings.
     * 
     * @param base64Url URL-safe Base64 string
     * @return padded string ready for decoding
     * @throws IllegalArgumentException if length % 4 == 1 (invalid Base64)
     */
    private fun fixPadding(base64Url: String): String {
        val mod = base64Url.length % 4
        if (mod == 2) return "$base64Url=="
        if (mod == 3) return "$base64Url="
        require(mod != 1) { "Invalid Base64URL string length." }
        return base64Url
    }
}
