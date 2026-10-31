package dev.aldoborrero.peke.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start and scrolling the kids home, without and with the baseline profile, to measure what it and other changes
 * give
 *
 * `nix develop -c ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` with a device connected and Peke set
 * up with a kid signed in. The results are in baselineprofile/build/outputs; they stay on this machine.
 */
@RunWith(AndroidJUnit4::class)
class KidsBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollHomeNoCompilation() = scrollHome(CompilationMode.None())

    @Test
    fun scrollHomeBaselineProfile() = scrollHome(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            waitForKidsHome()
        }

    private fun scrollHome(compilationMode: CompilationMode) =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForKidsHome()
            },
        ) {
            scrollRow()
        }
}
