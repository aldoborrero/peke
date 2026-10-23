package dev.aldoborrero.peke.ui.data

import dev.aldoborrero.peke.data.ChosenStreams
import org.jellyfin.sdk.model.api.MediaSourceInfo

/**
 * Necessary details & action for choosing a version of some content
 */
data class ChooseVersionParams(
    val chosenStreams: ChosenStreams?,
    val mediaSources: List<MediaSourceInfo>,
    val onChooseVersion: (MediaSourceInfo) -> Unit,
)
