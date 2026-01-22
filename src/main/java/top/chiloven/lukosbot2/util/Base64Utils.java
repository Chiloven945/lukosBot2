package top.chiloven.lukosbot2.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Instance-based Base64 utility class (no static methods).
 * <p>
 * Features:
 * 1) Standard Base64 encode/decode (String/byte[])
 * 2) URL-safe Base64 encode/decode (optional padding removal)
 * 3) MIME Base64 encode/decode (line-wrapped)
 * 4) File to Base64 / Base64 to file
 * 5) Basic Base64-like validation and padding fixing
 *
 * @author Chiloven945
 */
public final class Base64Utils {

    private final Charset defaultCharset;
    private final Base64.Encoder encoder;
    private final Base64.Decoder decoder;
    private final Base64.Encoder urlEncoder;
    private final Base64.Decoder urlDecoder;
    private final Base64.Encoder mimeEncoder;
    private final Base64.Decoder mimeDecoder;

    /**
     * Create a Base64Utils with a custom default charset.
     *
     * @param defaultCharset default charset for String conversions; UTF-8 if null
     */
    private Base64Utils(Charset defaultCharset) {
        this.defaultCharset = (defaultCharset != null) ? defaultCharset : StandardCharsets.UTF_8;
        this.encoder = Base64.getEncoder();
        this.decoder = Base64.getDecoder();
        this.urlEncoder = Base64.getUrlEncoder();
        this.urlDecoder = Base64.getUrlDecoder();
        this.mimeEncoder = Base64.getMimeEncoder();
        this.mimeDecoder = Base64.getMimeDecoder();
    }

    /**
     * Get a Base64Utils with a UTF-8 charset.
     *
     * @return a Base64Utils instance
     */
    public static Base64Utils getBase64Utils() {
        return new Base64Utils(StandardCharsets.UTF_8);
    }

    /**
     * Get a Base64Utils with a custom charset
     *
     * @param charset a charset
     * @return a Base64Utils instance
     */
    public static Base64Utils getBase64Utils(Charset charset) {
        return new Base64Utils(charset);
    }

    /* ===================== Basic: byte[] <-> byte[] ===================== */

    /**
     * Encode raw bytes to standard Base64 bytes.
     *
     * @param data raw bytes
     * @return Base64-encoded bytes, or empty array if input is null/empty
     */
    public byte[] encode(byte[] data) {
        if (data == null || data.length == 0) return new byte[0];
        return encoder.encode(data);
    }

    /**
     * Decode standard Base64 bytes to raw bytes.
     *
     * @param base64Bytes Base64-encoded bytes
     * @return decoded raw bytes, or empty array if input is null/empty
     * @throws IllegalArgumentException if input is not valid Base64
     */
    public byte[] decode(byte[] base64Bytes) {
        if (base64Bytes == null || base64Bytes.length == 0) return new byte[0];
        return decoder.decode(base64Bytes);
    }

    /* ===================== Basic: String <-> String ===================== */

    /**
     * Encode a String to standard Base64 using default charset.
     *
     * @param text plain text
     * @return Base64 string, or empty string if input is null/empty
     */
    public String encodeToString(String text) {
        return encodeToString(text, defaultCharset);
    }

    /**
     * Encode a String to standard Base64 using a given charset.
     *
     * @param text    plain text
     * @param charset charset to use; default charset if null
     * @return Base64 string, or empty string if input is null/empty
     */
    public String encodeToString(String text, Charset charset) {
        if (text == null || text.isEmpty()) return "";
        Charset cs = (charset != null) ? charset : defaultCharset;
        return encoder.encodeToString(text.getBytes(cs));
    }

    /**
     * Decode a standard Base64 string to plain text using default charset.
     *
     * @param base64 Base64 string
     * @return decoded plain text, or empty string if input is null/empty
     * @throws IllegalArgumentException if input is not valid Base64
     */
    public String decodeToString(String base64) {
        return decodeToString(base64, defaultCharset);
    }

    /**
     * Decode a standard Base64 string to plain text using a given charset.
     *
     * @param base64  Base64 string
     * @param charset charset to use; default charset if null
     * @return decoded plain text, or empty string if input is null/empty
     * @throws IllegalArgumentException if input is not valid Base64
     */
    public String decodeToString(String base64, Charset charset) {
        if (base64 == null || base64.isEmpty()) return "";
        Charset cs = (charset != null) ? charset : defaultCharset;
        byte[] decoded = decoder.decode(base64);
        return new String(decoded, cs);
    }

    /**
     * Decode a Base64 (standard or URL-safe) string into a JsonObject using default charset.
     *
     * @param base64 Base64 string (standard or URL-safe)
     * @return decoded JsonObject
     * @throws JsonSyntaxException      if decoded text is not valid JSON
     * @throws IllegalArgumentException if base64 is invalid
     */
    public JsonObject decodeToJsonObject(String base64) throws JsonSyntaxException, IllegalArgumentException {
        return decodeToJsonObject(base64, defaultCharset);
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
    public JsonObject decodeToJsonObject(String base64, Charset charset)
            throws JsonSyntaxException, IllegalArgumentException {
        if (base64 == null || base64.isEmpty()) {
            return new JsonObject();
        }

        Charset cs = (charset != null) ? charset : defaultCharset;
        String fixed = fixPadding(base64);

        byte[] decodedBytes;
        try {
            // Try standard Base64 first
            decodedBytes = decoder.decode(fixed);
        } catch (IllegalArgumentException ex) {
            // Fallback to URL-safe Base64
            decodedBytes = urlDecoder.decode(fixed);
        }

        String jsonText = new String(decodedBytes, cs);
        return JsonParser.parseString(jsonText).getAsJsonObject();
    }

    /* ===================== URL-safe Base64 ===================== */

    /**
     * Encode text to URL-safe Base64 with padding.
     *
     * @param text plain text
     * @return URL-safe Base64 string
     */
    public String encodeUrlSafe(String text) {
        return encodeUrlSafe(text, true);
    }

    /**
     * Encode text to URL-safe Base64.
     *
     * @param text        plain text
     * @param withPadding whether to keep '=' padding
     * @return URL-safe Base64 string, or empty string if input is null/empty
     */
    public String encodeUrlSafe(String text, boolean withPadding) {
        if (text == null || text.isEmpty()) return "";
        String s = urlEncoder.encodeToString(text.getBytes(defaultCharset));
        return withPadding ? s : s.replace("=", "");
    }

    /**
     * Decode a URL-safe Base64 string to plain text.
     * Missing padding will be fixed automatically.
     *
     * @param base64Url URL-safe Base64 string
     * @return decoded plain text, or empty string if input is null/empty
     * @throws IllegalArgumentException if input is not valid Base64URL
     */
    public String decodeUrlSafe(String base64Url) {
        if (base64Url == null || base64Url.isEmpty()) return "";
        String fixed = fixPadding(base64Url);
        byte[] decoded = urlDecoder.decode(fixed);
        return new String(decoded, defaultCharset);
    }

    /* ===================== MIME Base64 (line-wrapped) ===================== */

    /**
     * Encode text to MIME Base64 (line-wrapped at 76 chars).
     *
     * @param text plain text
     * @return MIME Base64 string
     */
    public String encodeMime(String text) {
        if (text == null || text.isEmpty()) return "";
        return mimeEncoder.encodeToString(text.getBytes(defaultCharset));
    }

    /**
     * Decode a MIME Base64 string to plain text.
     *
     * @param mimeBase64 MIME Base64 string
     * @return decoded plain text
     * @throws IllegalArgumentException if input is not valid MIME Base64
     */
    public String decodeMime(String mimeBase64) {
        if (mimeBase64 == null || mimeBase64.isEmpty()) return "";
        byte[] decoded = mimeDecoder.decode(mimeBase64);
        return new String(decoded, defaultCharset);
    }

    /* ===================== File <-> Base64 ===================== */

    /**
     * Read a file and encode its contents to a standard Base64 string.
     *
     * @param file source file
     * @return Base64 string of file bytes, or empty string if file is null
     * @throws IOException if file cannot be read
     */
    public String encodeFileToString(File file) throws IOException {
        if (file == null) return "";
        byte[] bytes = Files.readAllBytes(file.toPath());
        return encoder.encodeToString(bytes);
    }

    /**
     * Decode a standard Base64 string and write the bytes to a file (overwrite).
     *
     * @param base64     Base64 string
     * @param targetFile target file to write
     * @throws IOException              if file cannot be written
     * @throws IllegalArgumentException if targetFile is null or base64 invalid
     */
    public void decodeStringToFile(String base64, File targetFile) throws IOException {
        if (targetFile == null) throw new IllegalArgumentException("targetFile is null");
        byte[] decoded = (base64 == null || base64.isEmpty())
                ? new byte[0]
                : decoder.decode(base64);
        try (OutputStream os = new FileOutputStream(targetFile)) {
            os.write(decoded);
        }
    }

    /* ===================== Helpers ===================== */

    /**
     * A lightweight "looks like Base64" check (not 100% strict).
     *
     * @param s input string
     * @return true if string resembles Base64
     */
    public boolean isBase64Like(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("^[A-Za-z0-9+/=_-]+$");
    }

    /**
     * Fix missing '=' padding for Base64URL strings.
     *
     * @param base64Url URL-safe Base64 string
     * @return padded string ready for decoding
     * @throws IllegalArgumentException if length % 4 == 1 (invalid Base64)
     */
    private String fixPadding(String base64Url) {
        int mod = base64Url.length() % 4;
        if (mod == 2) return base64Url + "==";
        if (mod == 3) return base64Url + "=";
        if (mod == 1) {
            throw new IllegalArgumentException("Invalid Base64URL string length.");
        }
        return base64Url;
    }
}
