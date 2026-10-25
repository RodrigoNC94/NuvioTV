package com.nuvio.tv.ui.screens.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.nuvio.tv.core.streams.StreamBadgeMatcher
import com.nuvio.tv.domain.model.StreamBadge
import com.nuvio.tv.ui.components.StreamImportedBadgeChip
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

sealed interface PlayerBadge {
    data class Pack(val badge: StreamBadge) : PlayerBadge
    data class BuiltIn(@DrawableRes val res: Int) : PlayerBadge
}

@Composable
internal fun rememberPlayerBadges(viewModel: PlayerViewModel, uiState: PlayerUiState): List<PlayerBadge> {
    val filters = uiState.playerBadgeFilters
    val enabled = uiState.showPlayerBadges
    var mediaInfo by remember { mutableStateOf(PlayerMediaInfo()) }
    var releaseText by remember { mutableStateOf("") }
    val selectedAudio = uiState.audioTracks.firstOrNull { it.isSelected }
    LaunchedEffect(
        enabled,
        filters,
        uiState.showControls,
        uiState.isBuffering,
        uiState.currentStreamUrl,
        uiState.internalPlayerEngine,
        selectedAudio?.index
    ) {
        if (!enabled || !uiState.showControls || uiState.isBuffering) return@LaunchedEffect
        // Read again while the row shows: adaptive streams change resolution, and mpv knows the picture only once decoded.
        while (true) {
            val info = viewModel.playerMediaInfo()
            if (info != mediaInfo) mediaInfo = info
            val release = viewModel.playingReleaseText()
            if (release != releaseText) releaseText = release
            delay(BADGE_REFRESH_MS)
        }
    }
    if (!enabled) return emptyList()
    if (filters.isEmpty()) {
        return remember(mediaInfo, releaseText) {
            builtInPlayerBadges(mediaInfo, releaseText).map { PlayerBadge.BuiltIn(it) }
        }
    }
    val text = playbackMatchText(mediaInfo, playbackLanguageTokens(selectedAudio?.language), releaseText)
    return remember(text, filters) {
        StreamBadgeMatcher.matchedBadges(listOfNotNull(text.takeIf { it.isNotBlank() }), filters)
            .filter { it.imageURL.isNotBlank() }
            .map { PlayerBadge.Pack(it) }
    }
}

/** Not focusable; when the row is too wide the last badges drop first (channels, then Atmos). */
@Composable
internal fun PlayerBadges(
    badges: List<PlayerBadge>,
    modifier: Modifier = Modifier
) {
    val gapDp = NuvioTheme.spacing.xs
    Layout(
        content = {
            badges.forEach { badge ->
                when (badge) {
                    is PlayerBadge.Pack -> StreamImportedBadgeChip(badge = badge.badge)
                    is PlayerBadge.BuiltIn -> Image(
                        painter = painterResource(id = badge.res),
                        contentDescription = null,
                        modifier = Modifier.height(PLAYER_BADGE_HEIGHT),
                        contentScale = ContentScale.FillHeight
                    )
                }
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val gap = gapDp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        var width = 0
        var visibleCount = 0
        for (placeable in placeables) {
            val next = if (visibleCount == 0) placeable.width else width + gap + placeable.width
            if (next > constraints.maxWidth) break
            width = next
            visibleCount++
        }
        val visible = placeables.take(visibleCount)
        val height = visible.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            visible.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}

private val PLAYER_BADGE_HEIGHT = 20.dp
private const val BADGE_REFRESH_MS = 1_000L
