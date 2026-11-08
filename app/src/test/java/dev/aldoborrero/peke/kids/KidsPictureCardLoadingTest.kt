package dev.aldoborrero.peke.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.kids.ui.KidsPictureCard
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekePalette
import dev.aldoborrero.peke.ui.theme.PekeTheme
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID
import kotlin.math.abs

/**
 * A kids card draws no colour under its picture, but while the picture loads it still shows the surface colour, as
 * before (HomeRailPekeTV)
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h320dp-mdpi")
class KidsPictureCardLoadingTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var colors: PekePalette
    private val page = Color(0xFF00FF00)

    @Before
    fun neverLoadingPictures() {
        // Every picture stays loading
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(ApplicationProvider.getApplicationContext())
                .components { add(Interceptor { awaitCancellation() }) }
                .build(),
        )
    }

    @After
    fun reset() = SingletonImageLoader.reset()

    private fun Color.near(other: Color) = abs(red - other.red) < .02f && abs(green - other.green) < .02f && abs(blue - other.blue) < .02f

    @Test
    fun `a card shows the surface colour while its picture loads`() {
        val item =
            BaseItem(
                BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE),
                imageUrlOverride = "https://example.invalid/poster.jpg",
            )
        rule.setContent {
            CompositionLocalProvider(LocalImageUrlService provides mockk<ImageUrlService>(relaxed = true)) {
                PekeTheme {
                    colors = LocalPekeColors.current
                    Box(Modifier.fillMaxSize().background(page).padding(40.dp)) {
                        KidsPictureCard(
                            item = item,
                            title = "Poster",
                            imageType = ImageType.PRIMARY,
                            width = 140.dp,
                            height = 200.dp,
                            corner = 22.dp,
                            onClick = {},
                            onLongClick = {},
                            modifier = Modifier.testTag("card"),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val image = rule.onNodeWithTag("card").captureToImage().toPixelMap()
        // The middle of the picture, clear of the rounded corners
        val middle = image[image.width / 2, 100]
        assertTrue("$middle", middle.near(colors.surface))
    }
}
