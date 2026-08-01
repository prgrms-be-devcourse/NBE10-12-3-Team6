package csh.back.domain.trip.post.service

import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

@Component
class PostImageProcessor {
    fun createVariants(file: MultipartFile): PostImageVariants {
        require(!file.isEmpty) { "업로드된 이미지가 없습니다." }

        val source = file.inputStream.use(::readImage)
        val normal = createVariant(
            source = source,
            longestEdge = NORMAL_LONGEST_EDGE,
            quality = NORMAL_QUALITY
        )
        val dataSaver = createVariant(
            source = source,
            longestEdge = DATA_SAVER_LONGEST_EDGE,
            quality = DATA_SAVER_QUALITY
        )

        return PostImageVariants(
            normal = normal,
            dataSaver = dataSaver
        )
    }

    private fun createVariant(
        source: BufferedImage,
        longestEdge: Int,
        quality: Float
    ): EncodedImage {
        val resized = resizeIfNeeded(source, longestEdge)
        return EncodedImage(
            bytes = encodeWebp(resized, quality),
            contentType = WEBP_CONTENT_TYPE,
            extension = WEBP_EXTENSION
        )
    }

    private fun readImage(source: InputStream): BufferedImage {
        val input = ImageIO.createImageInputStream(source)
            ?: throw IllegalArgumentException("이미지 파일을 읽을 수 없습니다.")

        input.use { imageInput ->
            val readers = ImageIO.getImageReaders(imageInput)
            require(readers.hasNext()) { "지원하지 않는 이미지 형식입니다." }

            val reader = readers.next()
            try {
                reader.input = imageInput
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0) { "올바르지 않은 이미지 크기입니다." }
                require(width.toLong() * height <= MAX_PIXEL_COUNT) {
                    "이미지 해상도가 너무 큽니다."
                }
                return reader.read(0)
            } finally {
                reader.dispose()
            }
        }
    }

    private fun resizeIfNeeded(
        source: BufferedImage,
        maxLongestEdge: Int
    ): BufferedImage {
        val longestEdge = maxOf(source.width, source.height)
        if (longestEdge <= maxLongestEdge) {
            return source
        }

        val scale = maxLongestEdge.toDouble() / longestEdge
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val imageType = if (source.colorModel.hasAlpha()) {
            BufferedImage.TYPE_INT_ARGB
        } else {
            BufferedImage.TYPE_INT_RGB
        }
        val resized = BufferedImage(targetWidth, targetHeight, imageType)

        resized.createGraphics().use { graphics ->
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            )
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            )
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        }

        return resized
    }

    private fun encodeWebp(
        image: BufferedImage,
        quality: Float
    ): ByteArray {
        val writers = ImageIO.getImageWritersByMIMEType(WEBP_CONTENT_TYPE)
        check(writers.hasNext()) { "WebP 이미지 변환기를 찾을 수 없습니다." }

        val writer = writers.next()
        val outputBytes = ByteArrayOutputStream()
        val output = ImageIO.createImageOutputStream(outputBytes)

        try {
            writer.output = output
            val writeParam = writer.defaultWriteParam
            if (writeParam.canWriteCompressed()) {
                writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
                writeParam.compressionTypes
                    ?.firstOrNull { it.equals("Lossy", ignoreCase = true) }
                    ?.let { writeParam.compressionType = it }
                writeParam.compressionQuality = quality
            }
            writer.write(null, IIOImage(image, null, null), writeParam)
            output.flush()
            return outputBytes.toByteArray()
        } finally {
            output.close()
            writer.dispose()
        }
    }

    data class PostImageVariants(
        val normal: EncodedImage,
        val dataSaver: EncodedImage
    )

    data class EncodedImage(
        val bytes: ByteArray,
        val contentType: String,
        val extension: String
    )

    companion object {
        private const val WEBP_CONTENT_TYPE = "image/webp"
        private const val WEBP_EXTENSION = "webp"
        private const val NORMAL_LONGEST_EDGE = 2560
        private const val DATA_SAVER_LONGEST_EDGE = 1280
        private const val MAX_PIXEL_COUNT = 80_000_000L
        private const val NORMAL_QUALITY = 0.90f
        private const val DATA_SAVER_QUALITY = 0.65f
    }
}

private inline fun <T : java.awt.Graphics2D, R> T.use(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        dispose()
    }
