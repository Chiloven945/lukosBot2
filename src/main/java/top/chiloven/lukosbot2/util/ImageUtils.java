package top.chiloven.lukosbot2.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Image utility functions.
 *
 * @author Chiloven945
 */
public final class ImageUtils {
    private ImageUtils() {
    }

    public static ImageUtils getImageUtils() {
        return new ImageUtils();
    }

    /**
     * Convert PNG bytes to JPG bytes with specified quality.
     * Fills transparent areas with a white background.
     *
     * @param pngBytes the PNG image bytes
     * @param quality  the JPEG quality (0.0 to 1.0)
     * @return the JPG image bytes
     * @throws IOException if conversion fails
     */
    public byte[] pngToJpg(byte[] pngBytes, float quality) throws IOException {
        var src = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (src == null) throw new IllegalArgumentException("Invalid screenshot image");
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();

        try (var baos = new ByteArrayOutputStream()) {
            var writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try (var ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                var param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return baos.toByteArray();
        }
    }

    /**
     * Convert PNG bytes to JPG bytes with default quality (0.9).
     * Fills transparent areas with a white background.
     *
     * @param pngBytes the PNG image bytes
     * @return the JPG image bytes
     * @throws IOException if conversion fails
     */
    public byte[] pngToJpg(byte[] pngBytes) throws IOException {
        return pngToJpg(pngBytes, 0.9f);
    }
}
