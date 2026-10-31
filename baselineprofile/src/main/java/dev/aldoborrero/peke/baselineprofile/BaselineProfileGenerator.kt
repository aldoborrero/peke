package dev.aldoborrero.peke.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects Peke's baseline profile from the kids journey ([kidsJourney])
 *
 * `nix develop -c ./gradlew :app:generateBaselineProfile` with a device connected; the profile lands in
 * app/src/main/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun kidsJourney() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            kidsJourney()
        }
}
