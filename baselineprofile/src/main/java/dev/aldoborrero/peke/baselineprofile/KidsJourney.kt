package dev.aldoborrero.peke.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * Peke's package: the benchmark and baseline profile builds have no application id suffix
 */
internal const val PACKAGE = "dev.aldoborrero.peke"

private const val LOAD_TIMEOUT_MILLIS = 20_000L

/**
 * The row of the kids home that holds series, counted from the top: the first row is usually keep watching, whose
 * cards play straight away. Set it with `-Pandroid.testInstrumentationRunnerArguments.seriesRow=N`.
 */
private val seriesRow: Int
    get() =
        InstrumentationRegistry
            .getArguments()
            .getString("seriesRow")
            ?.toIntOrNull() ?: 1

/**
 * Waits for the kids home: it takes the focus on its first card once a row has loaded
 */
internal fun MacrobenchmarkScope.waitForKidsHome() {
    device.wait(Until.hasObject(By.pkg(PACKAGE).focused(true)), LOAD_TIMEOUT_MILLIS)
    device.waitForIdle()
}

/**
 * Scrolls along the first row of the kids home and back, with the D-pad
 */
internal fun MacrobenchmarkScope.scrollRow(steps: Int = 6) {
    repeat(steps) {
        device.pressDPadRight()
        device.waitForIdle()
    }
    repeat(steps) {
        device.pressDPadLeft()
        device.waitForIdle()
    }
}

/**
 * The kids journey: cold start to the kids home, scroll a row, open the sidebar and close it, then open a series
 *
 * Needs Peke set up on the device with a kid signed in; it never touches the parent gate.
 */
internal fun MacrobenchmarkScope.kidsJourney() {
    waitForKidsHome()
    scrollRow()
    // Left from the first card opens the sidebar on the current page, Right goes back to the row
    device.pressDPadLeft()
    device.waitForIdle()
    device.pressDPadRight()
    device.waitForIdle()
    repeat(seriesRow) {
        device.pressDPadDown()
        device.waitForIdle()
    }
    device.pressDPadCenter()
    device.wait(Until.gone(By.pkg(PACKAGE).focused(true)), 2_000)
    device.wait(Until.hasObject(By.pkg(PACKAGE).focused(true)), LOAD_TIMEOUT_MILLIS)
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}
