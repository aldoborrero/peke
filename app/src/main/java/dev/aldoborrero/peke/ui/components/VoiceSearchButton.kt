package dev.aldoborrero.peke.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import kotlinx.coroutines.delay

private const val ERROR_AUTO_DISMISS_DELAY_MS = 3000L
private const val SOUND_LEVEL_SCALE_FACTOR = 0.15f
private const val SOUND_LEVEL_ANIM_MS = 100
private const val BASE_PULSE_ANIM_MS = 800
private const val RIPPLE_ANIM_MS = 1500
private const val RIPPLE_CANVAS_SCALE = 1.8f
private const val MAX_RIPPLE_EXPANSION = 0.35f
private val RIPPLE_STROKE_WIDTH = 2.dp
private const val RIPPLE_MAX_ALPHA = 0.4f

private fun VoiceInputState.shouldShowOverlay() =
    this is VoiceInputState.Starting ||
        this is VoiceInputState.Listening ||
        this is VoiceInputState.Processing ||
        this is VoiceInputState.Error

/**
 * Whether the microphone shows (spec 005 FR-006): a grown-up turned voice search on, the microphone is allowed and the
 * TV has a speech service. Kids never see the permission prompt, so without all three there is no microphone at all.
 */
fun voiceSearchShown(
    enabled: Boolean,
    granted: Boolean,
    available: Boolean,
): Boolean = enabled && granted && available

/**
 * The microphone button, shown only per [voiceSearchShown]. It never asks for the microphone: a grown-up allows it
 * behind the parent gate, see [dev.aldoborrero.peke.ui.preferences.VoiceSearchConsent].
 *
 * @param enabled voice search was turned on for this TV
 */
@Composable
fun VoiceSearchButton(
    onSpeechResult: (String) -> Unit,
    voiceInputManager: VoiceInputManager?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (voiceInputManager == null) return
    // Read again on resume: the permission can be withdrawn in the system settings meanwhile
    var granted by remember(voiceInputManager) { mutableStateOf(voiceInputManager.hasPermission) }
    LifecycleResumeEffect(voiceInputManager) {
        granted = voiceInputManager.hasPermission
        onPauseOrDispose { }
    }
    if (!voiceSearchShown(enabled, granted, voiceInputManager.isAvailable)) return

    val state by voiceInputManager.state.collectAsState()
    val soundLevel by voiceInputManager.soundLevel.collectAsState()
    val partialResult by voiceInputManager.partialResult.collectAsState()

    LaunchedEffect(state) {
        val currentState = state
        when (currentState) {
            is VoiceInputState.Result -> {
                onSpeechResult(currentState.text)
                // Small delay to allow focus to be restored before dialog dismisses
                delay(50)
                voiceInputManager.acknowledge()
            }

            is VoiceInputState.Error -> {
                if (!currentState.isRetryable) {
                    delay(ERROR_AUTO_DISMISS_DELAY_MS)
                    voiceInputManager.acknowledge()
                }
            }

            else -> {}
        }
    }

    if (state.shouldShowOverlay()) {
        val errorState = state as? VoiceInputState.Error
        val errorMessage = errorState?.messageResId?.let { stringResource(it) }
        VoiceSearchOverlay(
            soundLevel = soundLevel,
            partialResult = partialResult,
            isStarting = state is VoiceInputState.Starting,
            isProcessing = state is VoiceInputState.Processing,
            errorMessage = errorMessage,
            isRetryable = errorState?.isRetryable == true,
            onRetry = { voiceInputManager.startListening() },
            onDismiss = { voiceInputManager.stopListening() },
        )
    }

    // KSearchPekeTV: a 72 px circle in tertiary with Peke's voice icon
    val colors = LocalPekeColors.current
    Surface(
        onClick = {
            when (state) {
                is VoiceInputState.Starting,
                is VoiceInputState.Listening,
                -> {
                    voiceInputManager.stopListening()
                }

                else -> {
                    if (voiceInputManager.hasPermission) voiceInputManager.startListening()
                }
            }
        },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.tertiary,
                contentColor = colors.onPrimary,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
            ),
        modifier = modifier.size(72.cpx).pekeFocus(CircleShape),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(PekeIcon.VOICE.outline),
                contentDescription = stringResource(R.string.voice_search),
                modifier = Modifier.size(40.cpx),
            )
        }
    }
}

@Composable
private fun VoiceRippleRings(
    rippleProgress: Float,
    bubbleSize: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rippleStroke =
        remember(density) {
            Stroke(width = with(density) { RIPPLE_STROKE_WIDTH.toPx() })
        }

    Canvas(modifier = modifier.size(bubbleSize * RIPPLE_CANVAS_SCALE)) {
        val canvasCenter = center
        val baseRadius = bubbleSize.toPx() / 2
        val maxExpansion = bubbleSize.toPx() * MAX_RIPPLE_EXPANSION

        for (i in 0..2) {
            val ringProgress = (rippleProgress + (i * 0.33f)) % 1f
            val ringRadius = baseRadius + (ringProgress * maxExpansion)
            val ringAlpha = (1f - ringProgress) * RIPPLE_MAX_ALPHA
            drawCircle(
                color = color.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = canvasCenter,
                style = rippleStroke,
            )
        }
    }
}

@Composable
private fun VoiceSearchOverlay(
    soundLevel: Float,
    partialResult: String,
    isStarting: Boolean,
    isProcessing: Boolean,
    errorMessage: String?,
    isRetryable: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error

    val animatedSoundLevel by animateFloatAsState(
        targetValue = soundLevel,
        animationSpec = tween(durationMillis = SOUND_LEVEL_ANIM_MS),
        label = "soundLevel",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = BASE_PULSE_ANIM_MS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "basePulse",
    )

    // Only animate ripples when actively listening (not starting, processing, or in error)
    val shouldAnimateRipples = !isStarting && !isProcessing && errorMessage == null
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimateRipples) 1f else 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = RIPPLE_ANIM_MS),
                repeatMode = RepeatMode.Restart,
            ),
        label = "ripple",
    )

    val bubbleScale = basePulse + (animatedSoundLevel * SOUND_LEVEL_SCALE_FACTOR)

    val statusFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        statusFocusRequester.requestFocus()
    }

    // KVoicePekeTV: the whole screen, the microphone in two tertiary circles, "Speak to search", what was heard in a
    // pill in the time colour, and how to stop
    val colors = LocalPekeColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.cpx, Alignment.CenterVertically),
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colors.background),
        ) {
            val circle = if (errorMessage != null) errorColor else colors.tertiary
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.cpx)) {
                val rippleAlpha = if (shouldAnimateRipples) 1f else 0f
                Box(modifier = Modifier.graphicsLayer { alpha = rippleAlpha }) {
                    VoiceRippleRings(
                        rippleProgress = rippleProgress,
                        bubbleSize = 220.cpx,
                        color = circle,
                    )
                }
                Box(Modifier.size(300.cpx).background(circle.copy(alpha = .18f), CircleShape))
                Box(
                    modifier =
                        Modifier
                            .size(220.cpx)
                            .graphicsLayer {
                                scaleX = bubbleScale
                                scaleY = bubbleScale
                            }.background(circle.copy(alpha = .3f), CircleShape),
                )
                Icon(
                    painter = painterResource(PekeIcon.VOICE.outline),
                    contentDescription = stringResource(R.string.voice_search),
                    tint = colors.text,
                    modifier = Modifier.size(160.cpx),
                )
            }

            val title =
                when {
                    errorMessage != null -> errorMessage
                    isProcessing -> stringResource(R.string.processing)
                    isStarting -> stringResource(R.string.voice_starting)
                    else -> stringResource(R.string.kids_voice_title)
                }
            Text(
                text = title,
                fontSize = 52.csp,
                fontWeight = FontWeight.W900,
                color = if (errorMessage != null) errorColor else colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 56.cpx).focusRequester(statusFocusRequester),
            )
            if (partialResult.isNotBlank() && errorMessage == null) {
                Text(
                    text = "\"$partialResult\"",
                    fontSize = 30.csp,
                    fontWeight = FontWeight.W900,
                    color = colors.time,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .background(colors.surface, RoundedCornerShape(30.cpx))
                            .padding(horizontal = 28.cpx, vertical = 14.cpx),
                )
            }
            if (errorMessage != null && isRetryable) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                    colors =
                        OutlinedButtonDefaults.colors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.retry),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                text = stringResource(R.string.kids_voice_back),
                fontSize = 20.csp,
                fontWeight = FontWeight.W800,
                color = colors.textSecondary,
            )
        }
    }
}
