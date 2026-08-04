package csh.back.domain.trip.post.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PostImageProcessorTest {
    private val processor = PostImageProcessor()

    @Test
    @DisplayName("한 원본에서 일반용과 데이터 절약용 WebP 이미지를 생성한다")
    fun createNormalAndDataSaverVariants() {
        val image = createPng(width = 3200, height = 1600)

        val result = processor.createVariants(image)
        val normal = ImageIO.read(ByteArrayInputStream(result.normal.bytes))
        val dataSaver = ImageIO.read(ByteArrayInputStream(result.dataSaver.bytes))

        assertEquals("image/webp", result.normal.contentType)
        assertEquals("webp", result.normal.extension)
        assertEquals(2560, normal.width)
        assertEquals(1280, normal.height)
        assertEquals(1280, dataSaver.width)
        assertEquals(640, dataSaver.height)
        assertTrue(result.normal.bytes.isWebp())
        assertTrue(result.dataSaver.bytes.isWebp())
        assertEquals("#2878DC", result.dominantColor)
    }

    @Test
    @DisplayName("각 모드의 최대 해상도보다 작은 사진은 해상도를 유지한다")
    fun preserveSmallImageResolution() {
        val image = createPng(width = 960, height = 640)

        val result = processor.createVariants(image)
        val normal = ImageIO.read(ByteArrayInputStream(result.normal.bytes))
        val dataSaver = ImageIO.read(ByteArrayInputStream(result.dataSaver.bytes))

        assertEquals(960, normal.width)
        assertEquals(640, normal.height)
        assertEquals(960, dataSaver.width)
        assertEquals(640, dataSaver.height)
    }

    @Test
    @DisplayName("이미지가 아닌 파일은 이미지 변환을 거부한다")
    fun rejectUnsupportedFile() {
        val file = MockMultipartFile(
            "image",
            "not-image.txt",
            "text/plain",
            "not an image".toByteArray()
        )

        assertThrows(IllegalArgumentException::class.java) {
            processor.createVariants(file)
        }
    }

    private fun createPng(width: Int, height: Int): MockMultipartFile {
        val source = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        source.createGraphics().apply {
            color = Color(40, 120, 220)
            fillRect(0, 0, width, height)
            dispose()
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(source, "png", output)

        return MockMultipartFile(
            "image",
            "photo.png",
            "image/png",
            output.toByteArray()
        )
    }

    private fun ByteArray.isWebp(): Boolean =
        size >= 12 &&
            copyOfRange(0, 4).decodeToString() == "RIFF" &&
            copyOfRange(8, 12).decodeToString() == "WEBP"
}
