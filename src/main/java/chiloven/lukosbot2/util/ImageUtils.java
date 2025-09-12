package chiloven.lukosbot2.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ImageUtils {
    /**
     * Convert PNG bytes to JPG bytes with specified quality.
     * Fills transparent areas with a white background.
     *
     * @param pngBytes the PNG image bytes
     * @param quality  the JPEG quality (0.0 to 1.0)
     * @return the JPG image bytes
     * @throws Exception if conversion fails
     */
    static byte[] pngToJpg(byte[] pngBytes, float quality) throws Exception {
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
                    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return baos.toByteArray();
        }
    }
}
