package dev.aldoborrero.peke.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.aldoborrero.peke.R

/**
 * Displays Peke's credit as a fork of Wholphin and dependencies' license information to comply with attribution
 */
@Composable
fun LicenseInfo(modifier: Modifier = Modifier) {
    val libraries by produceLibraries()

    Column(modifier) {
        Text(
            text = stringResource(R.string.peke_license_credit),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LibrariesContainer(libraries, Modifier.weight(1f))
    }
}
