/*
 * Copyright © 2026 Chiloven945
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package top.chiloven.lukosbot2.util

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Image utility functions.
 * 
 * @author Chiloven945
 */
object ImageUtils {

    /**
     * Convert PNG bytes to JPG bytes with specified quality.
     * Fills transparent areas with a white background.
     * 
     * @param pngBytes the PNG image bytes
     * @param quality  the JPEG quality (0.0 to 1.0)
     * @return the JPG image bytes
     * @throws IOException if conversion fails
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun pngToJpg(pngBytes: ByteArray, quality: Float = 0.9f): ByteArray {
        val src = ImageIO.read(ByteArrayInputStream(pngBytes))
        requireNotNull(src) { "Invalid screenshot image" }
        val rgb = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()

        g.color = Color.WHITE
        g.fillRect(0, 0, rgb.width, rgb.height)
        g.drawImage(src, 0, 0, null)
        g.dispose()

        ByteArrayOutputStream().use { baos ->
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            try {
                ImageIO.createImageOutputStream(baos).use { ios ->
                    writer.setOutput(ios)
                    val param = writer.defaultWriteParam
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
                        param.setCompressionQuality(quality)
                    }
                    writer.write(null, IIOImage(rgb, null, null), param)
                }
            } finally {
                writer.dispose()
            }
            return baos.toByteArray()
        }
    }

}
