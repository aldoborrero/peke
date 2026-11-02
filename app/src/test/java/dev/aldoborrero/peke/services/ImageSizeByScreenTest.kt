package dev.aldoborrero.peke.services

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Avatars and channel logos are asked at the size they're drawn on the screen: a TV lays out 960×540 dp, so 2 px per
 * dp at 1080p and 4 at 4K
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ImageSizeByScreenTest {
    @Test
    @Config(qualifiers = "w960dp-h540dp-xhdpi")
    fun `at 1080p the picker avatar and the guide logo are asked at twice their dp`() {
        // The picker's circle is 129 dp, the guide's logo 120 dp
        assertEquals(258, ImageUrlService.userImageMaxHeight)
        assertEquals(240, ImageUrlService.channelImageMaxHeight)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-xxxhdpi")
    fun `at 4K they're asked at four times`() {
        assertEquals(516, ImageUrlService.userImageMaxHeight)
        assertEquals(480, ImageUrlService.channelImageMaxHeight)
    }
}
