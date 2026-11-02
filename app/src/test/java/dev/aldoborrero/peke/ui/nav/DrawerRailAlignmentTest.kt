package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every sidebar entry, Grown-ups included, has its icon at the same size and on the same centre line of the closed
 * rail, and its name in the same column of the open sidebar (HomeRailPekeTV, HomeDrawerPekeTV)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrawerRailAlignmentTest {
    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Entries(
        open: Boolean,
        width: Dp,
    ) {
        PekeTheme {
            Column(Modifier.width(width).testTag("sheet")) {
                with(NavigationDrawerScopeImpl(open)) {
                    IconNavItem(
                        text = "Home",
                        icon = painterResource(PekeIcon.HOME.outline),
                        onClick = {},
                        selected = true,
                        drawerOpen = open,
                    )
                    NavItem(library = NavDrawerItem.Favorites, onClick = {}, selected = false, drawerOpen = open)
                    IconNavItem(
                        text = "Grown-ups",
                        icon = painterResource(PekeIcon.GROWN_UPS.outline),
                        onClick = {},
                        selected = false,
                        small = true,
                        drawerOpen = open,
                    )
                }
            }
        }
    }

    @Test
    fun `the closed rail centres every icon, Grown-ups included, at the same size`() {
        rule.setContent { Entries(open = false, width = DrawerTileSize) }
        val sheet = rule.onNodeWithTag("sheet").getUnclippedBoundsInRoot()
        val centre = (sheet.left + sheet.right) / 2
        listOf("Home", "Favorites", "Grown-ups").forEach { name ->
            val icon = rule.onNodeWithContentDescription(name, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("$name centre", centre.value, ((icon.left + icon.right) / 2).value, 0.5f)
            assertEquals("$name width", DrawerIconSize.value, (icon.right - icon.left).value, 0.5f)
        }
    }

    @Test
    fun `the open sidebar starts every name, Grown-ups included, at the same place`() {
        rule.setContent { Entries(open = true, width = 332.dp) }
        val home = rule.onNodeWithText("Home", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        listOf("Favorites", "Grown-ups").forEach { name ->
            assertEquals(
                name,
                home.value,
                rule
                    .onNodeWithText(name, useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
                    .left.value,
                0.5f,
            )
        }
    }
}
